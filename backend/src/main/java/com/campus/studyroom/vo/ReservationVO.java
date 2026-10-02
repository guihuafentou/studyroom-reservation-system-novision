package com.campus.studyroom.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 预约记录视图
 */
@Data
public class ReservationVO {

    private Long id;
    private String roomName;
    private String building;
    private String seatNo;
    private Integer seatType;
    private LocalDate reserveDate;
    private LocalTime startTime;
    private LocalTime endTime;
    /** 预约模式：DISCRETE / FLEXIBLE */
    private String bookingModel;
    /** 弹性室真实开始时间（离散室为空） */
    private LocalDateTime startDateTime;
    /** 弹性室真实结束时间（离散室为空） */
    private LocalDateTime endDateTime;
    private Integer status;
    private String statusText;
    private LocalDateTime createTime;
    private LocalDateTime signTime;
    /** 是否已过取消截止时间 */
    private Boolean cancelExpired;
    /** 是否在签到宽限期内 */
    private Boolean signable;
}
