package com.campus.studyroom.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 统计概览（今日）
 */
@Data
public class StatsOverviewVO {

    private long todayReservations;   // 今日预约量
    private long todaySigned;         // 今日已签到
    private long todayFinished;       // 今日已完成
    private long todayViolations;     // 今日违约
    private long totalSeats;          // 座位总数
    private long inUseSeats;          // 当前使用中
    private BigDecimal occupancyRate; // 上座率（当前使用中/总座位）
    private BigDecimal violationRate; // 违约率（今日违约/今日预约）
}
