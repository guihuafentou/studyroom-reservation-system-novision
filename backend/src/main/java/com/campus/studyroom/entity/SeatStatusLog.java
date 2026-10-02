package com.campus.studyroom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 座位状态检测日志表（仅状态翻转时写入）
 */
@Data
@TableName("seat_status_log")
public class SeatStatusLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long seatId;

    /** 0空 1占用 */
    private Integer occupied;

    /** 置信度 0~1 */
    private BigDecimal confidence;

    /** 检测器标识，如 mog2 / yolo */
    private String detector;

    private LocalDateTime detectTime;
}
