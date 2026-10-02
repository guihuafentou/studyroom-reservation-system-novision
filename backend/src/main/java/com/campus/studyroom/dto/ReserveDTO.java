package com.campus.studyroom.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 创建预约请求：
 * - 离散室（bookingModel=DISCRETE）：date + slotIds（1~N 个连续时段）
 * - 弹性室（bookingModel=FLEXIBLE）：startTime + endTime（真实起止区间，0.5h ~ max-flex-hours）
 */
@Data
public class ReserveDTO {

    @NotNull(message = "座位不能为空")
    private Long seatId;

    /** 离散室使用 */
    private LocalDate date;

    /** 离散室使用：连续时段 id 列表 */
    private List<Long> slotIds;

    /** 弹性室使用：预约开始时间 */
    private LocalDateTime startTime;

    /** 弹性室使用：预约结束时间 */
    private LocalDateTime endTime;
}
