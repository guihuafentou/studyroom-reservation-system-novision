package com.campus.studyroom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理员操作审计表
 */
@Data
@TableName("admin_audit_log")
public class AdminAuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long adminId;

    /** 操作，如 FORCE_CANCEL / SEAT_EDIT / ROOM_EDIT */
    private String action;

    /** 对象类型：reservation / seat / room / user / slot */
    private String targetType;

    private Long targetId;

    private String detail;

    private LocalDateTime createTime;
}
