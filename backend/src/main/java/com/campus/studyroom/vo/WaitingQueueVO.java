package com.campus.studyroom.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 候补队列视图：携带房间/座位名称与状态文案
 */
@Data
public class WaitingQueueVO {

    private Long id;

    private Long userId;

    private String studentNo;

    private String username;

    private Long seatId;

    private String seatNo;

    private Long roomId;

    private String roomName;

    private LocalDate reserveDate;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer status;

    private String statusText;

    private LocalDateTime createTime;

    private LocalDateTime promoteTime;

    private Long reservationId;
}
