package com.campus.studyroom.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 加入候补请求：座位 + 时间段。
 * 弹性室传 startTime/endTime；离散室传 slotId + date（时间由时段推导）
 */
@Data
public class WaitingDTO {

    private Long seatId;

    /** 离散室候补的目标时段ID */
    private Long slotId;

    /** 离散室候补的日期 */
    private LocalDate date;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
