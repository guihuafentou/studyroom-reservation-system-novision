package com.campus.studyroom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalTime;

/**
 * 时段表（离散时段模型核心：唯一索引防重叠抢座）
 */
@Data
@TableName("slot")
public class Slot {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roomId;

    private LocalTime startTime;

    private LocalTime endTime;
}
