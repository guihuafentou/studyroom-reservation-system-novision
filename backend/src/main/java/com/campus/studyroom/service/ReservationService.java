package com.campus.studyroom.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.studyroom.common.BusinessException;
import com.campus.studyroom.dto.ReserveDTO;
import com.campus.studyroom.entity.Reservation;
import com.campus.studyroom.entity.Room;
import com.campus.studyroom.entity.Seat;
import com.campus.studyroom.entity.Slot;
import com.campus.studyroom.entity.User;
import com.campus.studyroom.mapper.ReservationMapper;
import com.campus.studyroom.mapper.RoomMapper;
import com.campus.studyroom.mapper.SeatMapper;
import com.campus.studyroom.mapper.SlotMapper;
import com.campus.studyroom.mapper.UserMapper;
import com.campus.studyroom.security.UserContext;
import com.campus.studyroom.vo.ReservationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 预约服务：
 * - 离散室（DISCRETE）：原逻辑（slotIds 连续时段 + 唯一索引兜底）
 * - 弹性室（FLEXIBLE）：startTime/endTime 真实区间 + 行锁 + 区间重叠检测（P4）
 * 状态流转沿用"条件更新"约定，与扫描任务并发安全。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    public static final String MODEL_DISCRETE = "DISCRETE";
    public static final String MODEL_FLEXIBLE = "FLEXIBLE";

    private final ReservationMapper reservationMapper;
    private final SeatMapper seatMapper;
    private final RoomMapper roomMapper;
    private final SlotMapper slotMapper;
    private final UserMapper userMapper;
    private final SeatSseService seatSseService;

    @Value("${studyroom.rule.cancel-before-minutes:30}")
    private int cancelBeforeMinutes;

    @Value("${studyroom.rule.sign-before-minutes:15}")
    private int signBeforeMinutes;

    @Value("${studyroom.rule.sign-after-minutes:30}")
    private int signAfterMinutes;

    @Value("${studyroom.rule.violate-limit:3}")
    private int violateLimit;

    @Value("${studyroom.rule.ban-days:7}")
    private int banDays;

    @Value("${studyroom.rule.min-flex-minutes:30}")
    private int minFlexMinutes;

    @Value("${studyroom.rule.max-flex-hours:8}")
    private int maxFlexHours;

    @Value("${studyroom.rule.flex-no-sign-cancel-minutes:60}")
    private int flexNoSignCancelMinutes;

    @Value("${studyroom.rule.open-time:08:00}")
    private String openTime;

    @Value("${studyroom.rule.close-time:22:00}")
    private String closeTime;

    /**
     * 创建预约：按自习室预约模式分流
     */
    @Transactional(rollbackFor = Exception.class)
    public List<Reservation> create(ReserveDTO dto) {
        Long userId = UserContext.getUserId();

        // 1. 用户禁约检查
        User user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == 0) {
            throw BusinessException.forbidden("账号状态异常，无法预约");
        }
        if (user.getBanUntil() != null && user.getBanUntil().isAfter(LocalDateTime.now())) {
            throw BusinessException.forbidden("因违约累计达 " + violateLimit + " 次，预约权限暂停至 " + user.getBanUntil() + "，请届时再试");
        }

        // 2. 座位与自习室检查
        Seat seat = seatMapper.selectById(dto.getSeatId());
        if (seat == null) {
            throw BusinessException.notFound("座位不存在");
        }
        if (seat.getStatus() == Seat.STATUS_DISABLED) {
            throw BusinessException.badRequest("该座位已禁用");
        }
        Room room = roomMapper.selectById(seat.getRoomId());
        if (room == null || room.getStatus() == 0) {
            throw BusinessException.badRequest("自习室未开放");
        }

        String model = trim(room.getBookingModel());
        if (MODEL_FLEXIBLE.equalsIgnoreCase(model)) {
            return createFlexible(dto, room, seat, user);
        }
        if (MODEL_DISCRETE.equalsIgnoreCase(model)) {
            return createDiscrete(dto, room, seat, user);
        }
        throw BusinessException.badRequest("未知的预约模式: " + model + "（仅支持 DISCRETE/FLEXIBLE）");
    }

    private static String trim(String s) {
        return s == null ? null : s.trim();
    }

    /** 解析自习室开放/关闭时间，缺失时用全局配置兜底 */
    private LocalTime resolveOpenTime(Room room) {
        return room.getOpenTime() == null ? LocalTime.parse(openTime) : room.getOpenTime();
    }

    private LocalTime resolveCloseTime(Room room) {
        return room.getCloseTime() == null ? LocalTime.parse(closeTime) : room.getCloseTime();
    }

    // ---------------- 弹性室（FLEXIBLE） ----------------

    private List<Reservation> createFlexible(ReserveDTO dto, Room room, Seat seat, User user) {
        LocalDateTime start = dto.getStartTime();
        LocalDateTime end = dto.getEndTime();
        LocalDateTime now = LocalDateTime.now();

        // 3.1 区间合法性与时长
        if (start == null || end == null) {
            throw BusinessException.badRequest("弹性时长预约必须提供开始与结束时间");
        }
        if (!end.isAfter(start)) {
            throw BusinessException.badRequest("结束时间必须晚于开始时间");
        }
        long minutes = Duration.between(start, end).toMinutes();
        if (minutes < minFlexMinutes) {
            throw BusinessException.badRequest("最短预约时长 " + minFlexMinutes + " 分钟");
        }
        long maxMinutes = (long) maxFlexHours * 60;
        if (minutes > maxMinutes) {
            throw BusinessException.badRequest("单次预约最长 " + maxFlexHours + " 小时（试点），如需更长请联系管理员");
        }
        if (start.isBefore(now)) {
            throw BusinessException.badRequest("预约开始时间必须晚于当前时间");
        }

        // 3.2 P1 闭馆规则：区间必须完整落在每日开馆时段内（跨天按每日开馆时段切分校验）
        if (!withinOpenHours(start, end, room)) {
            throw BusinessException.badRequest("预约区间须完整位于自习室开放时间内（开馆 " + resolveOpenTime(room)
                    + " ~ " + resolveCloseTime(room) + "，跨天区间逐日校验）");
        }

        // 3.2b 一人同时仅可持有一个活跃预约：与本人其他 PENDING/SIGNED 预约区间重叠即拒绝
        checkUserOverlap(user.getId(), start, end);

        // 3.3 P4 并发锁：锁定座位行后做区间重叠检测（FOR SHARE 锁定读，避免快照漏检）
        Seat lockedSeat = seatMapper.selectByIdForUpdate(seat.getId());
        if (lockedSeat == null || lockedSeat.getStatus() == Seat.STATUS_DISABLED) {
            throw BusinessException.badRequest("该座位已禁用");
        }
        List<Long> overlap = reservationMapper.findOverlap(seat.getId(), start, end);
        if (!overlap.isEmpty()) {
            throw BusinessException.conflict("该时间段内座位已被预约，请选择其他时间");
        }

        // 3.4 落库（单条记录承载完整区间）
        Reservation r = new Reservation();
        r.setUserId(user.getId());
        r.setRoomId(room.getId());
        r.setSeatId(seat.getId());
        r.setSlotId(null);
        r.setReserveDate(start.toLocalDate());
        r.setStartTime(start);
        r.setEndTime(end);
        r.setStatus(Reservation.STATUS_PENDING);
        r.setCreateTime(now);
        reservationMapper.insert(r);

        seatSseService.broadcastSeatStatus(seat.getSeatNo(), "RESERVED", "弹性预约");
        // 预约落库后同步座位状态
        syncSeatStatus(seat, room.getId(), Seat.STATUS_RESERVED);
        log.info("弹性预约创建成功: 座位 {} 区间 {} ~ {}", seat.getSeatNo(), start, end);
        return List.of(r);
    }

    /**
     * 区间必须完整落在每天的开馆时段内（P1 严格版）：
     * 首日从 start 起到当日闭馆、末日从当日开馆到 end、中间整天按开馆~闭馆，
     * 任一天出现"段起点早于开馆或段终点晚于闭馆"即拒绝。
     * 旧实现只要求"有交集"，07:00–08:30、20:00–23:00 这类跨出开馆时段的预约可绕过。
     */
    private boolean withinOpenHours(LocalDateTime start, LocalDateTime end, Room room) {
        LocalTime open = resolveOpenTime(room);
        LocalTime close = resolveCloseTime(room);
        LocalDate firstDay = start.toLocalDate();
        LocalDate lastDay = end.toLocalDate();
        // 区间可能跨多天，最多检查 8 天（覆盖 72h 时长上限的跨天场景）
        for (int i = 0; i <= 8; i++) {
            LocalDate d = firstDay.plusDays(i);
            if (d.isAfter(lastDay)) {
                break;
            }
            LocalDateTime dayOpen = d.atTime(open);
            LocalDateTime dayClose = d.atTime(close);
            LocalDateTime segStart = d.isEqual(firstDay) ? start : dayOpen;
            LocalDateTime segEnd = d.isEqual(lastDay) ? end : dayClose;
            // 任一天越界（早于开馆/晚于闭馆）或末日区间倒置/为空
            // （如 21:00~次日02:00：末日 08:00~02:00 为空，说明凌晨时段不在开馆内）→ 拒绝
            if (segStart.isBefore(dayOpen) || segEnd.isAfter(dayClose) || !segEnd.isAfter(segStart)) {
                return false;
            }
        }
        return true;
    }

    // ---------------- 离散室（DISCRETE） ----------------

    private List<Reservation> createDiscrete(ReserveDTO dto, Room room, Seat seat, User user) {
        // 日期与时段校验
        LocalDate today = LocalDate.now();
        if (dto.getDate() == null || dto.getDate().isBefore(today)) {
            throw BusinessException.badRequest("不能预约过去的日期");
        }
        if (dto.getSlotIds() == null || dto.getSlotIds().isEmpty()) {
            throw BusinessException.badRequest("请选择至少一个时段");
        }
        List<Slot> slots = slotMapper.selectList(new LambdaQueryWrapper<Slot>()
                .eq(Slot::getRoomId, seat.getRoomId())
                .in(Slot::getId, dto.getSlotIds()));
        if (slots.size() != dto.getSlotIds().size()) {
            throw BusinessException.badRequest("存在无效时段，请刷新后重试");
        }
        slots.sort(Comparator.comparing(Slot::getStartTime));

        // 时段须连续
        for (int i = 1; i < slots.size(); i++) {
            if (!slots.get(i).getStartTime().equals(slots.get(i - 1).getEndTime())) {
                throw BusinessException.badRequest("所选时段必须连续");
            }
        }
        // 时段在自习室开放时间内
        LocalTime rOpen = resolveOpenTime(room);
        LocalTime rClose = resolveCloseTime(room);
        for (Slot s : slots) {
            if (s.getStartTime().isBefore(rOpen) || s.getEndTime().isAfter(rClose)) {
                throw BusinessException.badRequest("所选时段超出自习室开放时间");
            }
            if (dto.getDate().equals(today) && s.getStartTime().isBefore(LocalTime.now())) {
                throw BusinessException.badRequest("所选时段已开始，无法预约");
            }
        }

        // 3.2b 一人同时仅可持有一个活跃预约：整个连续时段区间与本人其他活跃预约重叠即拒绝
        checkUserOverlap(user.getId(),
                LocalDateTime.of(dto.getDate(), slots.get(0).getStartTime()),
                LocalDateTime.of(dto.getDate(), slots.get(slots.size() - 1).getEndTime()));

        // 批量插入
        List<Reservation> created = new ArrayList<>();
        for (Slot s : slots) {
            Reservation r = new Reservation();
            r.setUserId(user.getId());
            r.setRoomId(room.getId());
            r.setSeatId(seat.getId());
            r.setSlotId(s.getId());
            r.setReserveDate(dto.getDate());
            r.setStatus(Reservation.STATUS_PENDING);
            r.setCreateTime(LocalDateTime.now());
            reservationMapper.insert(r);
            created.add(r);
        }
        seatSseService.broadcastSeatStatus(seat.getSeatNo(), "RESERVED", "时段预约");
        // 预约落库后同步座位状态
        syncSeatStatus(seat, room.getId(), Seat.STATUS_RESERVED);
        return created;
    }

    /**
     * 一人多占防护（评审 #21）：弹性记录 slot_id=NULL 使 uk_user_active 唯一索引失效，
     * 弹性室内部、弹性+离散跨房间可重叠多占——改为应用层显式检查：
     * 先锁用户行（并发串行化），再查本人全部 PENDING/SIGNED 预约
     * （弹性按 start~end、离散按 reserveDate+slot 展开为真实区间），与目标区间重叠即拒绝。
     */
    private void checkUserOverlap(Long userId, LocalDateTime start, LocalDateTime end) {
        // 锁用户行：同用户并发创建（尤其不同座位同区间）被串行化，
        // 避免快照读互相看不见对方的插入而双双通过（Claude 复核 #21 并发绕过）
        userMapper.selectByIdForUpdate(userId);
        List<Reservation> mine = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getUserId, userId)
                .in(Reservation::getStatus, Reservation.STATUS_PENDING, Reservation.STATUS_SIGNED));
        for (Reservation r : mine) {
            LocalDateTime rs;
            LocalDateTime re;
            if (r.getStartTime() != null) {
                rs = r.getStartTime();
                re = r.getEndTime();
            } else {
                Slot slot = slotMapper.selectById(r.getSlotId());
                if (slot == null) {
                    continue;
                }
                rs = LocalDateTime.of(r.getReserveDate(), slot.getStartTime());
                re = LocalDateTime.of(r.getReserveDate(), slot.getEndTime());
            }
            if (rs.isBefore(end) && re.isAfter(start)) {
                throw BusinessException.conflict("您已有时间段重叠的活跃预约（" + rs + " ~ " + re
                        + "），请先取消后再预约其他时段");
            }
        }
    }

    /**
     * 座位状态同步：预约创建/取消/签到落 seat.status。
     */
    private void syncSeatStatus(Seat seat, Long roomId, int targetStatus) {
        try {
            if (seat.getStatus() == null || seat.getStatus() != targetStatus) {
                seat.setStatus(targetStatus);
                seatMapper.updateById(seat);
            }
        } catch (Exception e) {
            log.warn("座位状态同步失败: {}", e.getMessage());
        }
    }

    // ---------------- 取消 / 签到 ----------------

    /**
     * 取消预约：本人 + 待签到 + 开始前 cancelBeforeMinutes 以上（条件更新防竞态）
     */
    public void cancel(Long reservationId) {
        Reservation r = getOwnPending(reservationId);
        LocalDateTime start = getStartTime(r);
        if (LocalDateTime.now().isAfter(start.minusMinutes(cancelBeforeMinutes))) {
            throw BusinessException.badRequest("预约开始前 " + cancelBeforeMinutes + " 分钟内不可取消");
        }
        int rows = reservationMapper.update(null,
                new LambdaUpdateWrapper<Reservation>()
                        .eq(Reservation::getId, reservationId)
                        .eq(Reservation::getStatus, Reservation.STATUS_PENDING)
                        .set(Reservation::getStatus, Reservation.STATUS_CANCELED));
        if (rows == 0) {
            throw BusinessException.conflict("预约状态已变更（可能已被系统判违约/取消），请刷新后重试");
        }
        r.setStatus(Reservation.STATUS_CANCELED);
        broadcast(r, "CANCELED");
    }

    /**
     * 签到：本人 + 待签到 + 宽限期内（开始前 signBefore 至 开始后 signAfter，条件更新防竞态）
     */
    public ReservationVO sign(Long reservationId) {
        Reservation r = getOwnPending(reservationId);
        LocalDateTime start = getStartTime(r);
        LocalDateTime now = LocalDateTime.now();
        // 弹性预约：补签窗口与自动取消阈值一致（flexNoSignCancelMinutes），
        // 避免"签不了但也不取消"的死区；离散预约：沿用 sign-after-minutes（违约判定前的可补签窗口）
        int signAfter = r.getStartTime() != null ? flexNoSignCancelMinutes : signAfterMinutes;
        boolean inWindow = !now.isBefore(start.minusMinutes(signBeforeMinutes))
                && !now.isAfter(start.plusMinutes(signAfter));
        if (!inWindow) {
            throw BusinessException.badRequest("不在签到时间窗口内（预约开始前 " + signBeforeMinutes
                    + " 分钟至开始后 " + signAfter + " 分钟）");
        }
        int rows = reservationMapper.update(null,
                new LambdaUpdateWrapper<Reservation>()
                        .eq(Reservation::getId, reservationId)
                        .eq(Reservation::getStatus, Reservation.STATUS_PENDING)
                        .set(Reservation::getStatus, Reservation.STATUS_SIGNED)
                        .set(Reservation::getSignTime, now));
        if (rows == 0) {
            throw BusinessException.conflict("预约状态已变更（可能已被系统判违约），请刷新后重试");
        }
        r.setStatus(Reservation.STATUS_SIGNED);
        r.setSignTime(now);
        broadcast(r, "SIGNED");
        return toVO(r);
    }

    /**
     * 我的预约列表（按日期倒序）
     */
    public List<ReservationVO> myReservations() {
        List<Reservation> list = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getUserId, UserContext.getUserId())
                .orderByDesc(Reservation::getReserveDate)
                .orderByAsc(Reservation::getId));
        List<ReservationVO> vos = new ArrayList<>();
        for (Reservation r : list) {
            vos.add(toVO(r));
        }
        return vos;
    }

    private Reservation getOwnPending(Long reservationId) {
        Reservation r = reservationMapper.selectById(reservationId);
        if (r == null) {
            throw BusinessException.notFound("预约记录不存在");
        }
        if (!r.getUserId().equals(UserContext.getUserId())) {
            throw BusinessException.forbidden("无权操作他人的预约");
        }
        if (r.getStatus() != Reservation.STATUS_PENDING) {
            throw BusinessException.badRequest("当前状态不允许该操作");
        }
        return r;
    }

    /**
     * 统一时间基点：弹性室用真实 startTime，离散室用 reserveDate + slot 开始时间
     */
    private LocalDateTime getStartTime(Reservation r) {
        if (r.getStartTime() != null) {
            return r.getStartTime();
        }
        Slot slot = slotMapper.selectById(r.getSlotId());
        if (slot == null) {
            throw BusinessException.badRequest("预约时段数据缺失，请刷新后重试");
        }
        return LocalDateTime.of(r.getReserveDate(), slot.getStartTime());
    }

    private LocalDateTime getEndTime(Reservation r) {
        if (r.getEndTime() != null) {
            return r.getEndTime();
        }
        Slot slot = slotMapper.selectById(r.getSlotId());
        if (slot == null) {
            throw BusinessException.badRequest("预约时段数据缺失，请刷新后重试");
        }
        return LocalDateTime.of(r.getReserveDate(), slot.getEndTime());
    }

    /**
     * 组装视图：房间/座位/区间信息 + 可操作标记
     */
    public ReservationVO toVO(Reservation r) {
        ReservationVO vo = new ReservationVO();
        vo.setId(r.getId());
        vo.setReserveDate(r.getReserveDate());
        vo.setStatus(r.getStatus());
        vo.setStatusText(statusText(r.getStatus()));
        vo.setCreateTime(r.getCreateTime());
        vo.setSignTime(r.getSignTime());

        Room room = roomMapper.selectById(r.getRoomId());
        if (room != null) {
            vo.setRoomName(room.getName());
            vo.setBuilding(room.getBuilding());
            vo.setBookingModel(room.getBookingModel());
        }
        Seat seat = seatMapper.selectById(r.getSeatId());
        if (seat != null) {
            vo.setSeatNo(seat.getSeatNo());
            vo.setSeatType(seat.getSeatType());
        }
        if (r.getStartTime() != null) {
            vo.setStartDateTime(r.getStartTime());
            vo.setEndDateTime(r.getEndTime());
        } else {
            Slot slot = slotMapper.selectById(r.getSlotId());
            if (slot != null) {
                vo.setStartTime(slot.getStartTime());
                vo.setEndTime(slot.getEndTime());
            }
        }
        if (r.getStatus() == Reservation.STATUS_PENDING) {
            LocalDateTime start = getStartTime(r);
            vo.setCancelExpired(LocalDateTime.now().isAfter(start.minusMinutes(cancelBeforeMinutes)));
            int signAfter = r.getStartTime() != null ? flexNoSignCancelMinutes : signAfterMinutes;
            boolean inWindow = !LocalDateTime.now().isBefore(start.minusMinutes(signBeforeMinutes))
                    && !LocalDateTime.now().isAfter(start.plusMinutes(signAfter));
            vo.setSignable(inWindow);
        }
        return vo;
    }

    public static String statusText(int status) {
        return switch (status) {
            case Reservation.STATUS_PENDING -> "待签到";
            case Reservation.STATUS_SIGNED -> "已签到";
            case Reservation.STATUS_FINISHED -> "已完成";
            case Reservation.STATUS_CANCELED -> "已取消";
            case Reservation.STATUS_VIOLATED -> "违约";
            case Reservation.STATUS_EARLY_END -> "提前结束";
            default -> "未知";
        };
    }

    private void broadcast(Reservation r, String event) {
        try {
            Seat seat = seatMapper.selectById(r.getSeatId());
            if (seat != null) {
                int target = switch (event) {
                    case "SIGNED" -> Seat.STATUS_IN_USE;
                    case "CANCELED" -> Seat.STATUS_FREE;
                    default -> Seat.STATUS_RESERVED;
                };
                syncSeatStatus(seat, r.getRoomId(), target);
                seatSseService.broadcastSeatStatus(seat.getSeatNo(), event, statusText(r.getStatus()));
            }
        } catch (Exception e) {
            log.warn("SSE 推送失败: {}", e.getMessage());
        }
    }

    public Map<String, Object> statsBucket(LocalDate date) {
        Map<String, Object> map = new HashMap<>();
        map.put("date", date);
        return map;
    }
}
