package com.campus.studyroom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 座位表
 */
@Data
@TableName("seat")
public class Seat {

    /** 实时状态常量 */
    public static final int STATUS_DISABLED = 0;   // 禁用
    public static final int STATUS_FREE = 1;       // 空闲
    public static final int STATUS_RESERVED = 2;   // 已预约
    public static final int STATUS_IN_USE = 3;     // 使用中

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roomId;

    private String seatNo;

    private Integer rowNo;

    private Integer colNo;

    /** 0普通 1靠窗 2电源 3隔断 */
    private Integer seatType;

    private Integer status;
}
