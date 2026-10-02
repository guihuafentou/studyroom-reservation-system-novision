package com.campus.studyroom.controller;

import com.campus.studyroom.common.Result;
import com.campus.studyroom.entity.Room;
import com.campus.studyroom.entity.Slot;
import com.campus.studyroom.security.UserContext;
import com.campus.studyroom.service.RoomService;
import com.campus.studyroom.vo.SeatVO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 自习室 / 时段 / 座位查询接口
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @GetMapping("/rooms")
    public Result<List<Room>> rooms() {
        return Result.ok(roomService.listRooms());
    }

    @GetMapping("/rooms/{id}")
    public Result<Room> room(@PathVariable Long id) {
        return Result.ok(roomService.getRoom(id));
    }

    @GetMapping("/rooms/{id}/slots")
    public Result<List<Slot>> slots(@PathVariable Long id) {
        return Result.ok(roomService.getSlots(id));
    }

    @GetMapping("/rooms/{id}/seats")
    public Result<List<SeatVO>> seats(@PathVariable Long id,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                      @RequestParam(required = false) Long slotId,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return Result.ok(roomService.getSeats(id, date, slotId, start, end, UserContext.getUserId()));
    }
}
