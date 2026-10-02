package com.campus.studyroom.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.campus.studyroom.common.BusinessException;
import com.campus.studyroom.entity.Reservation;
import com.campus.studyroom.entity.Room;
import com.campus.studyroom.entity.Seat;
import com.campus.studyroom.entity.Slot;
import com.campus.studyroom.mapper.ReservationMapper;
import com.campus.studyroom.mapper.RoomMapper;
import com.campus.studyroom.mapper.SeatMapper;
import com.campus.studyroom.mapper.SlotMapper;
import com.campus.studyroom.vo.SeatVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 自习室 / 座位 / 时段查询服务
 */
@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomMapper roomMapper;
    private final SeatMapper seatMapper;
    private final SlotMapper slotMapper;
    private final ReservationMapper reservationMapper;

    public List<Room> listRooms() {
        return roomMapper.selectList(new LambdaQueryWrapper<Room>()
                .eq(Room::getStatus, 1)
                .orderByAsc(Room::getBuilding, Room::getFloor));
    }

    public Room getRoom(Long id) {
        Room room = roomMapper.selectById(id);
        if (room == null) {
            throw BusinessException.notFound("自习室不存在");
        }
        return room;
    }

    public List<Slot> getSlots(Long roomId) {
        getRoom(roomId);
        return slotMapper.selectList(new LambdaQueryWrapper<Slot>()
                .eq(Slot::getRoomId, roomId)
                .orderByAsc(Slot::getStartTime));
    }

    /**
     * 座位列表：携带预约占用标记
     * 离散室：date + slotId 定位时段；
     * 弹性室：date + start/end 真实区间（与预约区间重叠即标记占用）
     */
    public List<SeatVO> getSeats(Long roomId, LocalDate date, Long slotId,
                                 LocalDateTime start, LocalDateTime end, Long userId) {
        getRoom(roomId);
        List<Seat> seats = seatMapper.selectList(new LambdaQueryWrapper<Seat>()
                .eq(Seat::getRoomId, roomId)
                .orderByAsc(Seat::getRowNo, Seat::getColNo));
        List<Long> seatIds = seats.stream().map(Seat::getId).toList();

        // 离散室：指定时段的有效预约
        Set<Long> reservedSeatIds = new HashSet<>();
        Set<Long> mySeatIds = new HashSet<>();
        if (date != null && slotId != null && !seatIds.isEmpty()) {
            List<Reservation> reservations = reservationMapper.selectList(
                    new LambdaQueryWrapper<Reservation>()
                            .in(Reservation::getSeatId, seatIds)
                            .eq(Reservation::getReserveDate, date)
                            .eq(Reservation::getSlotId, slotId)
                            .in(Reservation::getStatus, Reservation.STATUS_PENDING, Reservation.STATUS_SIGNED));
            for (Reservation r : reservations) {
                reservedSeatIds.add(r.getSeatId());
                if (r.getUserId().equals(userId)) {
                    mySeatIds.add(r.getSeatId());
                }
            }
        }

        // 弹性室：与目标区间 [start, end) 重叠的有效预约。
        // 注意：不做 reserve_date 单日过滤——跨午夜预约（reserveDate=开始日）在次日查询时
        // 会被 .eq(reserveDate, date) 误过滤漏显，导致"显示空闲、提交 409"的前后端口径不一致。
        // overlaps 已按真实区间判断（含跨天），全量活跃预约扫描在演示规模下代价可接受。
        if (start != null && end != null && !seatIds.isEmpty()) {
            List<Reservation> all = reservationMapper.selectList(
                    new LambdaQueryWrapper<Reservation>()
                            .in(Reservation::getSeatId, seatIds)
                            .in(Reservation::getStatus, Reservation.STATUS_PENDING, Reservation.STATUS_SIGNED));
            for (Reservation r : all) {
                if (overlaps(r, start, end)) {
                    reservedSeatIds.add(r.getSeatId());
                    if (r.getUserId().equals(userId)) {
                        mySeatIds.add(r.getSeatId());
                    }
                }
            }
        }

        List<SeatVO> result = new ArrayList<>();
        for (Seat s : seats) {
            SeatVO vo = new SeatVO();
            vo.setId(s.getId());
            vo.setRoomId(s.getRoomId());
            vo.setSeatNo(s.getSeatNo());
            vo.setRowNo(s.getRowNo());
            vo.setColNo(s.getColNo());
            vo.setSeatType(s.getSeatType());
            vo.setStatus(s.getStatus());
            vo.setReservedByMe(mySeatIds.contains(s.getId()));
            vo.setReservedByOther(reservedSeatIds.contains(s.getId()) && !mySeatIds.contains(s.getId()));
            result.add(vo);
        }
        return result;
    }

    /**
     * 预约与目标区间重叠判断（口径与 ReservationMapper.findOverlap 一致）
     */
    private boolean overlaps(Reservation r, LocalDateTime start, LocalDateTime end) {
        if (r.getStartTime() != null) {
            return r.getStartTime().isBefore(end) && r.getEndTime().isAfter(start);
        }
        Slot slot = slotMapper.selectById(r.getSlotId());
        if (slot == null) {
            return false;
        }
        LocalDateTime slotStart = LocalDateTime.of(r.getReserveDate(), slot.getStartTime());
        LocalDateTime slotEnd = LocalDateTime.of(r.getReserveDate(), slot.getEndTime());
        return slotStart.isBefore(end) && slotEnd.isAfter(start);
    }
}
