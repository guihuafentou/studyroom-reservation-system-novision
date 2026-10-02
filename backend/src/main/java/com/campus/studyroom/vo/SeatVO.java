package com.campus.studyroom.vo;

import lombok.Data;

/**
 * 座位视图：座位信息 + 该时段预约占用标记
 */
@Data
public class SeatVO {

    private Long id;
    private Long roomId;
    private String seatNo;
    private Integer rowNo;
    private Integer colNo;
    private Integer seatType;
    /** 0禁用 1空闲 2已预约 3使用中 */
    private Integer status;
    /** 该时段已被当前登录用户预约 */
    private Boolean reservedByMe;
    /** 该时段已被他人预约 */
    private Boolean reservedByOther;
}
