package com.campus.studyroom.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.studyroom.common.BusinessException;
import com.campus.studyroom.dto.WaitingDTO;
import com.campus.studyroom.entity.Reservation;
import com.campus.studyroom.entity.Room;
import com.campus.studyroom.entity.Seat;
import com.campus.studyroom.entity.Slot;
import com.campus.studyroom.entity.User;
import com.campus.studyroom.entity.WaitingQueue;
import com.campus.studyroom.mapper.ReservationMapper;
import com.campus.studyroom.mapper.RoomMapper;
import com.campus.studyroom.mapper.SeatMapper;
import com.campus.studyroom.mapper.SlotMapper;
import com.campus.studyroom.mapper.UserMapper;
import com.campus.studyroom.mapper.WaitingQueueMapper;
import com.campus.studyroom.security.UserContext;
import com.campus.studyroom.vo.WaitingQueueVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 候补队列服务：
 * - 学生加入/查看/放弃候补（弹性室按时间区间、离散室按具体时段）
 * - 预约释放（学生取消/系统超时取消/管理端强撤）后按入队顺序自动转正生成预约
 * 转正并发安全：座位行锁 + 区间重叠检查 + 用户行锁，与预约创建同策略；
 * 调用方（cancel/forceCancel/scan）须处于事务内以持锁。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WaitingService {

    private final WaitingQueueMapper waitingQueueMapper;
    private final ReservationMapper reservationMapper;
    private final SeatMapper seatMapper;
    private final RoomMapper roomMapper;
    private final SlotMapper slotMapper;
    private final UserMapper userMapper;
    private final SeatSseService seatSseService;
    private final SysConfigService sysConfigService;

    /**
     * 加入候补：弹性室需时间段；离散室需时段ID，时间由时段推导
     */
    public WaitingQueue enqueue(WaitingDTO dto) {
        Long userId = UserContext.getUserId();
        if (dto.getSeatId() == null) {
            throw BusinessException.badRequest("候补必须提供座位");
        }
        Seat seat = seatMapper.selectById(dto.getSeatId());
        if (seat == null) {
            throw BusinessException.notFound("座位不存在");
        }
        if (seat.getStatus() == null || seat.getStatus() == Seat.STATUS_DISABLED) {
            throw BusinessException.badRequest("该座位已禁用");
        }
        Room room = roomMapper.selectById(seat.getRoomId());
        if (room == null || room.getStatus() == null || room.getStatus() == 0) {
            throw BusinessException.badRequest("自习室未开放");
        }
        // 禁约用户不允许入队（转正前置拦截）
        User user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() == 0) {
            throw BusinessException.forbidden("账号不可用");
        }
        if (user.getBanUntil() != null && user.getBanUntil().isAfter(LocalDateTime.now())) {
            throw BusinessException.badRequest("您当前处于禁约期，不能加入候补");
        }

        boolean flex = "FLEXIBLE".equals(room.getBookingModel());
        LocalDateTime start;
        LocalDateTime end;
        Long slotId = null;
        if (flex) {
            if (dto.getStartTime() == null || dto.getEndTime() == null) {
                throw BusinessException.badRequest("弹性自习室候补需提供时间段");
            }
            start = dto.getStartTime();
            end = dto.getEndTime();
            if (!end.isAfter(start)) {
                throw BusinessException.badRequest("结束时间必须晚于开始时间");
            }
            if (start.isBefore(LocalDateTime.now())) {
                throw BusinessException.badRequest("不能候补已开始的时间段");
            }
            if (!start.toLocalDate().equals(end.toLocalDate())) {
                throw BusinessException.badRequest("候补时段不能跨天");
            }
            LocalTime open = room.getOpenTime();
            LocalTime close = room.getCloseTime();
            if (open != null && close != null
                    && (start.toLocalTime().isBefore(open) || end.toLocalTime().isAfter(close))) {
                throw BusinessException.badRequest("候补时段超出开馆时间（" + open + "-" + close + "）");
            }
            long minutes = Duration.between(start, end).toMinutes();
            int minFlex = sysConfigService.getInt("min_flex_minutes", 30);
            long maxMinutes = (long) sysConfigService.getInt("max_flex_hours", 8) * 60;
            if (minutes < minFlex) {
                throw BusinessException.badRequest("弹性候补最短 " + minFlex + " 分钟");
            }
            if (minutes > maxMinutes) {
                throw BusinessException.badRequest("弹性候补最长 " + maxMinutes / 60 + " 小时");
            }
        } else {
            if (dto.getSlotId() == null) {
                throw BusinessException.badRequest("离散自习室候补需选择具体时段");
            }
            Slot slot = slotMapper.selectById(dto.getSlotId());
            if (slot == null) {
                throw BusinessException.badRequest("时段不存在");
            }
            LocalDate date = dto.getDate() != null ? dto.getDate()
                    : (dto.getStartTime() != null ? dto.getStartTime().toLocalDate() : null);
            if (date == null) {
                throw BusinessException.badRequest("离散自习室候补需提供日期");
            }
            start = LocalDateTime.of(date, slot.getStartTime());
            end = LocalDateTime.of(date, slot.getEndTime());
            if (start.isBefore(LocalDateTime.now())) {
                throw BusinessException.badRequest("不能候补已开始的时间段");
            }
            slotId = slot.getId();
        }

        // 本人同座位同区间已有候补中记录则拒绝
        Long dup = waitingQueueMapper.selectCount(new LambdaQueryWrapper<WaitingQueue>()
                .eq(WaitingQueue::getUserId, userId)
                .eq(WaitingQueue::getSeatId, seat.getId())
                .eq(WaitingQueue::getStatus, WaitingQueue.STATUS_WAITING)
                .lt(WaitingQueue::getStartTime, end)
                .gt(WaitingQueue::getEndTime, start));
        if (dup != null && dup > 0) {
            throw BusinessException.conflict("您已在该时段候补此座位，请勿重复加入");
        }
        WaitingQueue w = new WaitingQueue();
        w.setUserId(userId);
        w.setSeatId(seat.getId());
        w.setRoomId(room.getId());
        w.setSlotId(slotId);
        w.setReserveDate(start.toLocalDate());
        w.setStartTime(start);
        w.setEndTime(end);
        w.setStatus(WaitingQueue.STATUS_WAITING);
        w.setCreateTime(LocalDateTime.now());
        waitingQueueMapper.insert(w);
        log.info("用户 {} 加入候补: 座位 {} {}-{} slot={}", userId, seat.getSeatNo(), start, end, slotId);
        return w;
    }

    /** 我的候补列表（按加入时间倒序） */
    public List<WaitingQueueVO> myList() {
        List<WaitingQueue> list = waitingQueueMapper.selectList(new LambdaQueryWrapper<WaitingQueue>()
                .eq(WaitingQueue::getUserId, UserContext.getUserId())
                .orderByDesc(WaitingQueue::getCreateTime));
        return toVOList(list);
    }

    /** 放弃候补：仅候补中可放弃 */
    public void quit(Long id) {
        WaitingQueue w = waitingQueueMapper.selectById(id);
        if (w == null) {
            throw BusinessException.notFound("候补记录不存在");
        }
        if (!w.getUserId().equals(UserContext.getUserId())) {
            throw BusinessException.forbidden("无权操作他人的候补");
        }
        if (w.getStatus() != WaitingQueue.STATUS_WAITING) {
            throw BusinessException.badRequest("当前状态不允许放弃（已转正/已过期）");
        }
        w.setStatus(WaitingQueue.STATUS_QUIT);
        waitingQueueMapper.updateById(w);
    }

    /** 管理端分页列表 */
    public Page<WaitingQueueVO> listPage(int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        Page<WaitingQueue> p = waitingQueueMapper.selectPage(new Page<>(Math.max(page, 1), safeSize),
                new LambdaQueryWrapper<WaitingQueue>().orderByDesc(WaitingQueue::getCreateTime));
        Page<WaitingQueueVO> voPage = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        voPage.setRecords(toVOList(p.getRecords()));
        return voPage;
    }

    /**
     * 预约释放后触发转正：学生取消 / 超时自动取消 / 管理端强撤均调用。
     * 调用方须在事务内；此处异常不向上抛（转正失败不影响取消主流程）。
     */
    public void tryPromoteForReservation(Reservation r) {
        if (r == null || r.getSeatId() == null) {
            return;
        }
        try {
            LocalDateTime start = startOf(r);
            LocalDateTime end = endOf(r);
            if (start == null || end == null) {
                return;
            }
            promote(r.getSeatId(), start.toLocalDate(), start, end);
        } catch (Exception e) {
            log.warn("候补转正失败(预约 {}): {}", r.getId(), e.getMessage());
        }
    }

    /**
     * 按释放区间尝试转正候补：按入队时间取候选，跳过队头不可行者（重叠/用户不可约/已开始），
     * 命中第一个可行候补即转正；一次仅转正一个。
     */
    private void promote(Long seatId, LocalDate date, LocalDateTime start, LocalDateTime end) {
        List<WaitingQueue> candidates = waitingQueueMapper.selectList(new LambdaQueryWrapper<WaitingQueue>()
                .eq(WaitingQueue::getSeatId, seatId)
                .eq(WaitingQueue::getReserveDate, date)
                .eq(WaitingQueue::getStatus, WaitingQueue.STATUS_WAITING)
                .lt(WaitingQueue::getStartTime, end)
                .gt(WaitingQueue::getEndTime, start)
                .orderByAsc(WaitingQueue::getCreateTime)
                .last("LIMIT 20"));
        if (candidates.isEmpty()) {
            return;
        }
        // 锁座位行：防并发下多人同时转正同一座位
        Seat seat = seatMapper.selectByIdForUpdate(seatId);
        if (seat == null || seat.getStatus() == null || seat.getStatus() == Seat.STATUS_DISABLED) {
            return;
        }
        Room room = roomMapper.selectById(seat.getRoomId());
        for (WaitingQueue w : candidates) {
            // 已开始的时间段不再转正
            if (!w.getStartTime().isAfter(LocalDateTime.now())) {
                continue;
            }
            // 区间重叠检查（有人抢先预约则尝试下一位）
            List<Long> overlap = reservationMapper.findOverlap(seatId, w.getStartTime(), w.getEndTime());
            if (!overlap.isEmpty()) {
                continue;
            }
            // 锁用户行：检查禁约与本人其他活跃预约重叠
            User user = userMapper.selectByIdForUpdate(w.getUserId());
            if (user == null || user.getStatus() == null || user.getStatus() == 0
                    || (user.getBanUntil() != null && user.getBanUntil().isAfter(LocalDateTime.now()))) {
                w.setStatus(WaitingQueue.STATUS_EXPIRED);
                waitingQueueMapper.updateById(w);
                log.info("候补 {} 因用户不可预约已过期", w.getId());
                continue;
            }
            if (userOverlap(user.getId(), w.getStartTime(), w.getEndTime())) {
                w.setStatus(WaitingQueue.STATUS_EXPIRED);
                waitingQueueMapper.updateById(w);
                log.info("候补 {} 因用户已有重叠活跃预约已过期", w.getId());
                continue;
            }
            // 转正：按自习室模式落库（离散室写 slotId，弹性室写区间）
            Reservation r = new Reservation();
            r.setUserId(w.getUserId());
            r.setRoomId(w.getRoomId());
            r.setSeatId(w.getSeatId());
            r.setReserveDate(w.getReserveDate());
            if (room != null && "FLEXIBLE".equals(room.getBookingModel())) {
                r.setSlotId(null);
                r.setStartTime(w.getStartTime());
                r.setEndTime(w.getEndTime());
            } else {
                r.setSlotId(w.getSlotId());
                r.setStartTime(null);
                r.setEndTime(null);
            }
            r.setStatus(Reservation.STATUS_PENDING);
            r.setCreateTime(LocalDateTime.now());
            reservationMapper.insert(r);
            // 标记转正
            w.setStatus(WaitingQueue.STATUS_PROMOTED);
            w.setPromoteTime(LocalDateTime.now());
            w.setReservationId(r.getId());
            waitingQueueMapper.updateById(w);
            // 座位状态同步 + SSE 广播（前端座位图实时刷新）
            if (seat.getStatus() == null || seat.getStatus() != Seat.STATUS_RESERVED) {
                seat.setStatus(Seat.STATUS_RESERVED);
                seatMapper.updateById(seat);
            }
            seatSseService.broadcastSeatStatus(seat.getSeatNo(), "RESERVED", "候补转正");
            log.info("候补转正成功: 候补 {} → 预约 {}（座位 {}，{} - {}）",
                    w.getId(), r.getId(), seat.getSeatNo(), w.getStartTime(), w.getEndTime());
            return;
        }
    }

    /** 把已过期（结束时间已过）的候补中记录置为过期（由扫描任务周期调用） */
    public void expireOutdated() {
        int updated = waitingQueueMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<WaitingQueue>()
                .eq(WaitingQueue::getStatus, WaitingQueue.STATUS_WAITING)
                .lt(WaitingQueue::getEndTime, LocalDateTime.now())
                .set(WaitingQueue::getStatus, WaitingQueue.STATUS_EXPIRED)
                .set(WaitingQueue::getPromoteTime, null));
        if (updated > 0) {
            log.info("候补过期清理: {} 条已过期", updated);
        }
    }

    /** 用户是否有与该区间重叠的活跃预约 */
    private boolean userOverlap(Long userId, LocalDateTime start, LocalDateTime end) {
        List<Reservation> mine = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getUserId, userId)
                .in(Reservation::getStatus, Reservation.STATUS_PENDING, Reservation.STATUS_SIGNED));
        for (Reservation r : mine) {
            LocalDateTime rs = startOf(r);
            LocalDateTime re = endOf(r);
            if (rs != null && re != null && rs.isBefore(end) && re.isAfter(start)) {
                return true;
            }
        }
        return false;
    }

    /** 统一区间起点：弹性用 startTime，离散用 reserveDate + slot 开始 */
    private LocalDateTime startOf(Reservation r) {
        if (r.getStartTime() != null) {
            return r.getStartTime();
        }
        if (r.getSlotId() == null) {
            return null;
        }
        Slot slot = slotMapper.selectById(r.getSlotId());
        if (slot == null || r.getReserveDate() == null) {
            return null;
        }
        return LocalDateTime.of(r.getReserveDate(), slot.getStartTime());
    }

    private LocalDateTime endOf(Reservation r) {
        if (r.getEndTime() != null) {
            return r.getEndTime();
        }
        if (r.getSlotId() == null) {
            return null;
        }
        Slot slot = slotMapper.selectById(r.getSlotId());
        if (slot == null || r.getReserveDate() == null) {
            return null;
        }
        return LocalDateTime.of(r.getReserveDate(), slot.getEndTime());
    }

    private List<WaitingQueueVO> toVOList(List<WaitingQueue> list) {
        List<WaitingQueueVO> vos = new ArrayList<>();
        for (WaitingQueue w : list) {
            WaitingQueueVO vo = new WaitingQueueVO();
            vo.setId(w.getId());
            vo.setUserId(w.getUserId());
            vo.setSeatId(w.getSeatId());
            vo.setRoomId(w.getRoomId());
            vo.setReserveDate(w.getReserveDate());
            vo.setStartTime(w.getStartTime());
            vo.setEndTime(w.getEndTime());
            vo.setStatus(w.getStatus());
            vo.setStatusText(statusText(w.getStatus()));
            vo.setCreateTime(w.getCreateTime());
            vo.setPromoteTime(w.getPromoteTime());
            vo.setReservationId(w.getReservationId());
            Seat seat = seatMapper.selectById(w.getSeatId());
            if (seat != null) {
                vo.setSeatNo(seat.getSeatNo());
            }
            Room room = roomMapper.selectById(w.getRoomId());
            if (room != null) {
                vo.setRoomName(room.getName());
            }
            User user = userMapper.selectById(w.getUserId());
            if (user != null) {
                vo.setUsername(user.getUsername());
                vo.setStudentNo(user.getStudentNo());
            }
            vos.add(vo);
        }
        return vos;
    }

    public static String statusText(int status) {
        return switch (status) {
            case WaitingQueue.STATUS_WAITING -> "候补中";
            case WaitingQueue.STATUS_PROMOTED -> "已转正";
            case WaitingQueue.STATUS_QUIT -> "已放弃";
            case WaitingQueue.STATUS_EXPIRED -> "已过期";
            default -> "未知";
        };
    }
}
