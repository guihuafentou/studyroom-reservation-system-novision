package com.campus.studyroom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalTime;

/**
 * 自习室表
 */
@Data
@TableName("room")
public class Room {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String building;

    private Integer floor;

    /** 座位容量 */
    private Integer capacity;

    private LocalTime openTime;

    private LocalTime closeTime;

    /** 预约模式：DISCRETE 离散时段 / FLEXIBLE 弹性时长 */
    private String bookingModel;

    /** 0停用 1开放 */
    private Integer status;
}
