package com.campus.studyroom.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.studyroom.entity.Reservation;
import com.campus.studyroom.entity.Slot;
import com.campus.studyroom.entity.User;
import com.campus.studyroom.mapper.ReservationMapper;
import com.campus.studyroom.mapper.SlotMapper;
import com.campus.studyroom.mapper.UserMapper;
import com.campus.studyroom.service.WaitingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 预约状态扫描定时任务（每分钟）：
 * 1) 超时未签到 → 违约
 * 2) 已签到且时段已结束 → 已完成（状态机补全）
 * 3) 弹性预约开始后超时未签到 → 自动取消并释放座位
 *
 * 并发安全：所有状态流转使用"条件更新"（WHERE id=? AND status=旧状态），
 * 与用户签到/取消操作互不覆盖（谁先提交谁生效）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationScanTask {

    private final ReservationMapper reservationMapper;
    private final SlotMapper slotMapper;
    private final UserMapper userMapper;
    private final WaitingService waitingService;

    @Value("${studyroom.rule.violate-after-minutes:30}")
    private int violateAfterMinutes;

    @Value("${studyroom.rule.violate-limit:3}")
    private int violateLimit;

    @Value("${studyroom.rule.ban-days:7}")
    private int banDays;

    @Value("${studyroom.rule.flex-no-sign-cancel-minutes:60}")
    private int flexNoSignCancelMinutes;

    @org.springframework.scheduling.annotation.Scheduled(cron = "0 * * * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void scan() {
        LocalDateTime now = LocalDateTime.now();
        // 各环节独立 try/catch：单个环节异常不拖垮整轮扫描（评审低优先级项）
        runSafe("超时违约判定", () -> handleTimeoutViolations(now));
        runSafe("弹性超时取消", () -> handleFlexTimeoutCancel(now));
        runSafe("完成状态收尾", () -> handleFinished(now));
        runSafe("候补过期清理", () -> waitingService.expireOutdated());
    }

    private void runSafe(String name, Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.error("扫描环节 {} 执行失败: {}", name, e.getMessage());
        }
    }

    /**
     * 弹性室超时未签到自动取消（资源回收维度）：开始后 flexNoSignCancelMinutes 分钟仍待签到
     * → 自动取消并释放座位（不计违约；若此前已判违约，状态流转条件不满足自然跳过）
     */
    private void handleFlexTimeoutCancel(LocalDateTime now) {
        List<Reservation> pending = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getStatus, Reservation.STATUS_PENDING)
                .isNotNull(Reservation::getStartTime)
                .le(Reservation::getStartTime, now));
        for (Reservation r : pending) {
            if (r.getStartTime() == null || !now.isAfter(r.getStartTime().plusMinutes(flexNoSignCancelMinutes))) {
                continue;
            }
            if (conditionalUpdate(r.getId(), Reservation.STATUS_PENDING, Reservation.STATUS_CANCELED)) {
                log.info("弹性预约 {} 开始后 {} 分钟未签到，自动取消并释放座位",
                        r.getId(), flexNoSignCancelMinutes);
                waitingService.tryPromoteForReservation(r);
            }
        }
    }

    /**
     * 超时未签到：slotStart + violateAfterMinutes < now → 违约
     */
    private void handleTimeoutViolations(LocalDateTime now) {
        List<Reservation> pending = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getStatus, Reservation.STATUS_PENDING)
                .le(Reservation::getReserveDate, LocalDate.now()));
        for (Reservation r : pending) {
            LocalDateTime start = startTimeOf(r);
            if (start == null) {
                continue;
            }
            if (!now.isAfter(start.plusMinutes(violateAfterMinutes))) {
                continue;
            }
            // 昨日遗留未签到（防隔夜回溯补罚）：预约日期早于今天的记录，取消释放不计违约。
            // 注意：不能按"now 已过 end"判断——离散时段长 30 分钟且 violate-after=30，
            // 用 end 判断会先于违约分支命中，使当日 no-show 违约机制整体失效（回归教训）。
            if (r.getReserveDate() != null && r.getReserveDate().isBefore(LocalDate.now())) {
                if (conditionalUpdate(r.getId(), Reservation.STATUS_PENDING, Reservation.STATUS_CANCELED)) {
                    log.info("昨日预约 {} 仍未签到，取消释放（不计违约）", r.getId());
                    waitingService.tryPromoteForReservation(r);
                }
                continue;
            }
            // 弹性室超时未签到由 handleFlexTimeoutCancel 负责自动取消（资源回收），此处跳过
            if (r.getStartTime() != null) {
                continue;
            }
            // 超时未签到 → 违约
            if (conditionalUpdate(r.getId(), Reservation.STATUS_PENDING, Reservation.STATUS_VIOLATED)) {
                markViolation(r.getUserId());
            }
        }
    }

    /**
     * 统一开始时间基点：弹性室用 startTime，离散室用 reserveDate + slot 开始
     */
    private LocalDateTime startTimeOf(Reservation r) {
        if (r.getStartTime() != null) {
            return r.getStartTime();
        }
        Slot slot = slotMapper.selectById(r.getSlotId());
        if (slot == null) {
            return null;
        }
        return LocalDateTime.of(r.getReserveDate(), slot.getStartTime());
    }

    /**
     * 统一结束时间基点：弹性室用 endTime，离散室用 reserveDate + slot 结束
     */
    private LocalDateTime endTimeOf(Reservation r) {
        if (r.getEndTime() != null) {
            return r.getEndTime();
        }
        Slot slot = slotMapper.selectById(r.getSlotId());
        if (slot == null) {
            return null;
        }
        return LocalDateTime.of(r.getReserveDate(), slot.getEndTime());
    }

    /**
     * 已签到且预约已结束 → 已完成（含昨日遗留的已签到记录）
     * 评审 #24 补充：离散室"时段已结束仍卡 PENDING"补流转——当日离散 PENDING 若被
     * 违约判定跳过，时段结束后置 FINISHED（不按违约）；
     * 弹性 PENDING 由 handleFlexTimeoutCancel 自动取消，不在此处理。
     */
    private void handleFinished(LocalDateTime now) {
        List<Reservation> signed = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getStatus, Reservation.STATUS_SIGNED)
                .le(Reservation::getReserveDate, LocalDate.now()));
        for (Reservation r : signed) {
            LocalDateTime end = endTimeOf(r);
            if (end == null) {
                continue;
            }
            if (!now.isBefore(end)) {
                if (conditionalUpdate(r.getId(), Reservation.STATUS_SIGNED, Reservation.STATUS_FINISHED)) {
                    log.info("预约 {} 已完成（预约区间结束）", r.getId());
                }
            }
        }
        // 当日离散 PENDING 补流转（不处理弹性记录）
        // 注意：必须用严格 now.isAfter(end)——离散时段 30min 且 violate-after=30（start+30 == end），
        // 若用 !now.isBefore(end)（>=，到点即触发）会在违约分支（isAfter 严格）判定之前抢跑收尾，
        // 使当日空座 no-show 被 FINISHED 而非违约（高危回归教训，与 handleTimeoutViolations 内注释一致）。
        List<Reservation> pendingDisc = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getStatus, Reservation.STATUS_PENDING)
                .isNull(Reservation::getStartTime)
                .eq(Reservation::getReserveDate, LocalDate.now()));
        for (Reservation r : pendingDisc) {
            LocalDateTime end = endTimeOf(r);
            if (end == null) {
                continue;
            }
            if (now.isAfter(end)) {
                if (conditionalUpdate(r.getId(), Reservation.STATUS_PENDING, Reservation.STATUS_FINISHED)) {
                    log.info("预约 {} 时段已结束仍待签到，补记为已完成（不按违约）", r.getId());
                }
            }
        }
    }

    /**
     * 条件更新：仅当记录仍处于 expectedStatus 时流转到 newStatus，返回是否成功
     */
    private boolean conditionalUpdate(Long id, int expectedStatus, int newStatus) {
        int rows = reservationMapper.update(null,
                new LambdaUpdateWrapper<Reservation>()
                        .eq(Reservation::getId, id)
                        .eq(Reservation::getStatus, expectedStatus)
                        .set(Reservation::getStatus, newStatus));
        return rows > 0;
    }

    /**
     * 违约计数 + 达到阈值禁约
     */
    private void markViolation(Long userId) {
        // 原子自增（避免并发读改写互相覆盖）；随后读阈值判断禁约
        int rows = userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .setSql("violation_count = violation_count + 1"));
        if (rows == 0) {
            return;
        }
        User user = userMapper.selectById(userId);
        if (user != null && user.getViolationCount() != null && user.getViolationCount() >= violateLimit) {
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, userId)
                    .set(User::getBanUntil, LocalDateTime.now().plusDays(banDays))
                    .setSql("violation_count = 0"));  // 封禁触发即清零，"累计 N 次 → 封禁 N 天"可重复成立
            log.warn("用户 {} 违约达 {} 次，禁约至 {}（计数已清零）",
                    user.getUsername(), violateLimit, LocalDateTime.now().plusDays(banDays));
        }
    }
}
