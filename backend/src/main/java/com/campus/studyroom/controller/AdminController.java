package com.campus.studyroom.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.studyroom.common.Result;
import com.campus.studyroom.entity.AdminAuditLog;
import com.campus.studyroom.entity.Announcement;
import com.campus.studyroom.entity.Room;
import com.campus.studyroom.entity.Seat;
import com.campus.studyroom.entity.Slot;
import com.campus.studyroom.entity.User;
import com.campus.studyroom.security.RequireRole;
import com.campus.studyroom.service.AdminService;
import com.campus.studyroom.service.StatsService;
import com.campus.studyroom.vo.HeatmapVO;
import com.campus.studyroom.vo.StatsOverviewVO;
import com.campus.studyroom.vo.TrendVO;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 管理端接口（全部要求管理员角色）
 */
@RestController
@RequestMapping("/api/admin")
@RequireRole(1)
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final StatsService statsService;

    // ---------- 用户管理 ----------

    @GetMapping("/users")
    public Result<Page<User>> users(@RequestParam(defaultValue = "1") int page,
                                    @RequestParam(defaultValue = "10") int size,
                                    @RequestParam(required = false) String keyword) {
        return Result.ok(adminService.listUsers(page, size, keyword));
    }

    @PutMapping("/users/{id}/status")
    public Result<Void> setUserStatus(@PathVariable Long id, @RequestParam Integer status) {
        adminService.setUserStatus(id, status);
        return Result.ok();
    }

    // ---------- 自习室 / 座位 / 时段管理 ----------

    @PostMapping("/rooms")
    public Result<Room> addRoom(@RequestBody Room room) {
        room.setId(null);
        return Result.ok(adminService.saveRoom(room));
    }

    @PutMapping("/rooms")
    public Result<Room> editRoom(@RequestBody Room room) {
        return Result.ok(adminService.saveRoom(room));
    }

    @DeleteMapping("/rooms/{id}")
    public Result<Void> deleteRoom(@PathVariable Long id) {
        adminService.deleteRoom(id);
        return Result.ok();
    }

    @PostMapping("/seats")
    public Result<Seat> addSeat(@RequestBody Seat seat) {
        seat.setId(null);
        return Result.ok(adminService.saveSeat(seat));
    }

    @PutMapping("/seats")
    public Result<Seat> editSeat(@RequestBody Seat seat) {
        return Result.ok(adminService.saveSeat(seat));
    }

    @DeleteMapping("/seats/{id}")
    public Result<Void> deleteSeat(@PathVariable Long id) {
        adminService.deleteSeat(id);
        return Result.ok();
    }

    @PostMapping("/slots")
    public Result<Slot> addSlot(@RequestBody Slot slot) {
        slot.setId(null);
        return Result.ok(adminService.saveSlot(slot));
    }

    @PutMapping("/slots")
    public Result<Slot> editSlot(@RequestBody Slot slot) {
        return Result.ok(adminService.saveSlot(slot));
    }

    @DeleteMapping("/slots/{id}")
    public Result<Void> deleteSlot(@PathVariable Long id) {
        adminService.deleteSlot(id);
        return Result.ok();
    }

    // ---------- 预约管理 ----------

    @GetMapping("/reservations")
    public Result<Page<Map<String, Object>>> reservations(@RequestParam(defaultValue = "1") int page,
                                                          @RequestParam(defaultValue = "10") int size,
                                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                          @RequestParam(required = false) Integer status) {
        return Result.ok(adminService.listReservations(page, size, date, status));
    }

    @PutMapping("/reservations/{id}/cancel")
    public Result<Void> forceCancel(@PathVariable Long id) {
        adminService.forceCancel(id);
        return Result.ok();
    }

    @GetMapping("/audit-logs")
    public Result<Page<AdminAuditLog>> auditLogs(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int size) {
        return Result.ok(adminService.listAuditLogs(page, size));
    }

    // ---------- 公告管理 ----------

    @GetMapping("/announcements")
    public Result<Page<Announcement>> announcements(@RequestParam(defaultValue = "1") int page,
                                                    @RequestParam(defaultValue = "10") int size) {
        return Result.ok(adminService.listAnnouncements(page, size));
    }

    @PostMapping("/announcements")
    public Result<Announcement> addAnnouncement(@RequestBody Announcement announcement) {
        announcement.setId(null);
        return Result.ok(adminService.saveAnnouncement(announcement));
    }

    @PutMapping("/announcements")
    public Result<Announcement> editAnnouncement(@RequestBody Announcement announcement) {
        return Result.ok(adminService.saveAnnouncement(announcement));
    }

    @DeleteMapping("/announcements/{id}")
    public Result<Void> deleteAnnouncement(@PathVariable Long id) {
        adminService.deleteAnnouncement(id);
        return Result.ok();
    }

    // ---------- 系统参数配置 ----------

    @GetMapping("/configs")
    public Result<java.util.List<com.campus.studyroom.entity.SysConfig>> configs() {
        return Result.ok(adminService.listConfigs());
    }

    @PutMapping("/configs")
    public Result<Void> saveConfigs(@RequestBody java.util.Map<String, String> configs) {
        adminService.saveConfigs(configs);
        return Result.ok();
    }

    // ---------- 候补队列 ----------

    @GetMapping("/waiting")
    public Result<Page<com.campus.studyroom.vo.WaitingQueueVO>> waiting(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(adminService.listWaiting(page, size));
    }

    // ---------- 统计报表 ----------

    @GetMapping("/stats/overview")
    public Result<StatsOverviewVO> overview() {
        return Result.ok(statsService.overview());
    }

    @GetMapping("/stats/trend")
    public Result<List<TrendVO>> trend(@RequestParam(defaultValue = "7") int days) {
        return Result.ok(statsService.trend(days));
    }

    @GetMapping("/stats/heatmap")
    public Result<List<HeatmapVO>> heatmap() {
        return Result.ok(statsService.heatmap());
    }
}
