package com.campus.studyroom.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.studyroom.common.BusinessException;
import com.campus.studyroom.entity.AdminAuditLog;
import com.campus.studyroom.entity.Announcement;
import com.campus.studyroom.entity.Reservation;
import com.campus.studyroom.entity.Room;
import com.campus.studyroom.entity.Seat;
import com.campus.studyroom.entity.Slot;
import com.campus.studyroom.entity.SysConfig;
import com.campus.studyroom.entity.User;
import com.campus.studyroom.mapper.AdminAuditLogMapper;
import com.campus.studyroom.mapper.AnnouncementMapper;
import com.campus.studyroom.mapper.ReservationMapper;
import com.campus.studyroom.mapper.RoomMapper;
import com.campus.studyroom.mapper.SeatMapper;
import com.campus.studyroom.mapper.SlotMapper;
import com.campus.studyroom.mapper.SysConfigMapper;
import com.campus.studyroom.mapper.UserMapper;
import com.campus.studyroom.mapper.WaitingQueueMapper;
import com.campus.studyroom.vo.WaitingQueueVO;
import com.campus.studyroom.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端服务：用户/自习室/座位/时段/预约管理 + 操作审计
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserMapper userMapper;
    private final RoomMapper roomMapper;
    private final SeatMapper seatMapper;
    private final SlotMapper slotMapper;
    private final ReservationMapper reservationMapper;
    private final AdminAuditLogMapper auditLogMapper;
    private final AnnouncementMapper announcementMapper;
    private final SysConfigMapper sysConfigMapper;
    private final SysConfigService sysConfigService;
    private final WaitingQueueMapper waitingQueueMapper;
    private final WaitingService waitingService;

    // ---------- 用户管理 ----------

    public Page<User> listUsers(int page, int size, String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(User::getUsername, keyword)
                    .or().like(User::getStudentNo, keyword)
                    .or().like(User::getRealName, keyword);
        }
        wrapper.orderByDesc(User::getCreateTime);
        return userMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public void setUserStatus(Long id, Integer status) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw BusinessException.notFound("用户不存在");
        }
        if (user.getRole() == User.ROLE_ADMIN) {
            throw BusinessException.badRequest("不能操作管理员账号");
        }
        user.setStatus(status);
        userMapper.updateById(user);
        audit("USER_STATUS", "user", id, "设置状态=" + status);
    }

    // ---------- 自习室 / 座位 / 时段管理 ----------

    @Transactional(rollbackFor = Exception.class)
    public Room saveRoom(Room room) {
        if (room.getId() == null) {
            roomMapper.insert(room);
        } else {
            roomMapper.updateById(room);
        }
        audit("ROOM_EDIT", "room", room.getId(), room.getName());
        return room;
    }

    public void deleteRoom(Long id) {
        Long seatCount = seatMapper.selectCount(new LambdaQueryWrapper<Seat>().eq(Seat::getRoomId, id));
        if (seatCount != null && seatCount > 0) {
            throw BusinessException.badRequest("自习室下仍有座位，请先删除座位");
        }
        roomMapper.deleteById(id);
        audit("ROOM_DELETE", "room", id, "");
    }

    @Transactional(rollbackFor = Exception.class)
    public Seat saveSeat(Seat seat) {
        if (seat.getId() == null) {
            seatMapper.insert(seat);
        } else {
            seatMapper.updateById(seat);
        }
        audit("SEAT_EDIT", "seat", seat.getId(), seat.getSeatNo());
        return seat;
    }

    public void deleteSeat(Long id) {
        Long count = reservationMapper.selectCount(new LambdaQueryWrapper<Reservation>().eq(Reservation::getSeatId, id));
        if (count != null && count > 0) {
            throw BusinessException.badRequest("座位存在历史预约记录，不可删除，可改为禁用");
        }
        seatMapper.deleteById(id);
        audit("SEAT_DELETE", "seat", id, "");
    }

    @Transactional(rollbackFor = Exception.class)
    public Slot saveSlot(Slot slot) {
        if (slot.getRoomId() == null) {
            throw BusinessException.badRequest("时段缺少自习室");
        }
        if (slot.getStartTime() == null || slot.getEndTime() == null) {
            throw BusinessException.badRequest("时段缺少起止时间");
        }
        if (!slot.getStartTime().isBefore(slot.getEndTime())) {
            throw BusinessException.badRequest("开始时间必须早于结束时间");
        }
        // 区间重叠校验：start < 新end AND end > 新start（编辑时排除自身）
        List<Slot> overlaps = slotMapper.selectList(new LambdaQueryWrapper<Slot>()
                .eq(Slot::getRoomId, slot.getRoomId())
                .lt(Slot::getStartTime, slot.getEndTime())
                .gt(Slot::getEndTime, slot.getStartTime())
                .ne(slot.getId() != null, Slot::getId, slot.getId()));
        if (!overlaps.isEmpty()) {
            Slot o = overlaps.get(0);
            throw BusinessException.conflict("该时段与既有时段 [" + o.getStartTime() + "-" + o.getEndTime() + "] 重叠");
        }
        if (slot.getId() == null) {
            slotMapper.insert(slot);
        } else {
            slotMapper.updateById(slot);
        }
        audit("SLOT_EDIT", "slot", slot.getId(), slot.getStartTime() + "-" + slot.getEndTime());
        return slot;
    }

    public void deleteSlot(Long id) {
        Long count = reservationMapper.selectCount(new LambdaQueryWrapper<Reservation>().eq(Reservation::getSlotId, id));
        if (count != null && count > 0) {
            throw BusinessException.badRequest("时段已有预约记录，不可删除");
        }
        slotMapper.deleteById(id);
        audit("SLOT_DELETE", "slot", id, "");
    }

    // ---------- 预约管理 ----------

    public Page<Map<String, Object>> listReservations(int page, int size, LocalDate date, Integer status) {
        LambdaQueryWrapper<Reservation> wrapper = new LambdaQueryWrapper<>();
        if (date != null) {
            wrapper.eq(Reservation::getReserveDate, date);
        }
        if (status != null) {
            wrapper.eq(Reservation::getStatus, status);
        }
        wrapper.orderByDesc(Reservation::getCreateTime);
        Page<Reservation> p = reservationMapper.selectPage(new Page<>(page, size), wrapper);

        Page<Map<String, Object>> result = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        List<Map<String, Object>> records = new ArrayList<>();
        for (Reservation r : p.getRecords()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId());
            m.put("reserveDate", r.getReserveDate());
            m.put("status", r.getStatus());
            m.put("statusText", ReservationService.statusText(r.getStatus()));
            m.put("createTime", r.getCreateTime());
            m.put("signTime", r.getSignTime());

            User user = userMapper.selectById(r.getUserId());
            if (user != null) {
                m.put("userName", user.getRealName() != null ? user.getRealName() : user.getUsername());
                m.put("studentNo", user.getStudentNo());
            }
            Room room = roomMapper.selectById(r.getRoomId());
            m.put("roomName", room != null ? room.getName() : "");
            Seat seat = seatMapper.selectById(r.getSeatId());
            m.put("seatNo", seat != null ? seat.getSeatNo() : "");
            Slot slot = slotMapper.selectById(r.getSlotId());
            if (slot != null) {
                m.put("timeRange", slot.getStartTime() + "-" + slot.getEndTime());
            }
            records.add(m);
        }
        result.setRecords(records);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public void forceCancel(Long reservationId) {
        Reservation r = reservationMapper.selectById(reservationId);
        if (r == null) {
            throw BusinessException.notFound("预约记录不存在");
        }
        if (r.getStatus() == Reservation.STATUS_PENDING || r.getStatus() == Reservation.STATUS_SIGNED) {
            r.setStatus(Reservation.STATUS_CANCELED);
            reservationMapper.updateById(r);
            // 强撤释放时段后触发候补转正（评审 #26）
            waitingService.tryPromoteForReservation(r);
        }
        audit("FORCE_CANCEL", "reservation", reservationId, "强制取消预约");
    }

    public Page<AdminAuditLog> listAuditLogs(int page, int size) {
        return auditLogMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<AdminAuditLog>().orderByDesc(AdminAuditLog::getCreateTime));
    }

    // ---------- 公告管理 ----------

    public Page<Announcement> listAnnouncements(int page, int size) {
        return announcementMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<Announcement>()
                        .orderByDesc(Announcement::getIsTop)
                        .orderByDesc(Announcement::getCreateTime));
    }

    @Transactional(rollbackFor = Exception.class)
    public Announcement saveAnnouncement(Announcement announcement) {
        if (announcement.getTitle() == null || announcement.getTitle().isBlank()) {
            throw BusinessException.badRequest("公告标题不能为空");
        }
        if (announcement.getContent() == null || announcement.getContent().isBlank()) {
            throw BusinessException.badRequest("公告内容不能为空");
        }
        if (announcement.getIsTop() == null) {
            announcement.setIsTop(0);
        }
        if (announcement.getStatus() == null) {
            announcement.setStatus(Announcement.STATUS_ON);
        }
        if (announcement.getId() == null) {
            announcement.setCreateBy(UserContext.getUserId());
            announcement.setCreateTime(LocalDateTime.now());
            announcement.setUpdateTime(LocalDateTime.now());
            announcementMapper.insert(announcement);
        } else {
            announcement.setUpdateTime(LocalDateTime.now());
            announcementMapper.updateById(announcement);
        }
        audit("ANNOUNCEMENT_EDIT", "announcement", announcement.getId(), announcement.getTitle());
        return announcement;
    }

    public void deleteAnnouncement(Long id) {
        announcementMapper.deleteById(id);
        audit("ANNOUNCEMENT_DELETE", "announcement", id, "");
    }

    // ---------- 系统参数配置 ----------

    public List<SysConfig> listConfigs() {
        return sysConfigMapper.selectList(new LambdaQueryWrapper<SysConfig>()
                .orderByAsc(SysConfig::getId));
    }

    public void saveConfigs(Map<String, String> configs) {
        if (configs == null || configs.isEmpty()) {
            throw BusinessException.badRequest("参数列表为空");
        }
        int n = 0;
        for (Map.Entry<String, String> e : configs.entrySet()) {
            String key = e.getKey();
            String value = e.getValue();
            if (key == null || key.isBlank()) {
                continue;
            }
            if (value == null || value.isBlank()) {
                throw BusinessException.badRequest("参数 " + key + " 的值不能为空");
            }
            sysConfigService.save(key.trim(), value.trim());
            n++;
        }
        audit("SYS_CONFIG_EDIT", "config", null, "更新参数 " + n + " 项");
    }

    // ---------- 候补队列（管理端） ----------

    public Page<WaitingQueueVO> listWaiting(int page, int size) {
        return waitingService.listPage(page, size);
    }

    // ---------- 审计 ----------

    private void audit(String action, String targetType, Long targetId, String detail) {
        AdminAuditLog log = new AdminAuditLog();
        log.setAdminId(UserContext.getUserId());
        log.setAction(action);
        log.setTargetType(targetType);
        // 部分操作无业务主键（如批量参数更新），target_id 非空约束下用 0 占位
        log.setTargetId(targetId == null ? 0L : targetId);
        log.setDetail(detail);
        log.setCreateTime(LocalDateTime.now());
        auditLogMapper.insert(log);
    }
}
