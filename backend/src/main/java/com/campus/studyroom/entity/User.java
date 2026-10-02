package com.campus.studyroom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户表
 */
@Data
@TableName("`user`")
public class User {

    /** 角色常量 */
    public static final int ROLE_STUDENT = 0;
    public static final int ROLE_ADMIN = 1;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 学号 */
    private String studentNo;

    private String username;

    /** BCrypt 加密密码（序列化时忽略，防止泄露） */
    @JsonIgnore
    private String password;

    private String realName;

    /** 0学生 1管理员 */
    private Integer role;

    /** 0禁用 1正常 */
    private Integer status;

    /** 违约累计次数 */
    private Integer violationCount;

    /** 禁约截止时间 */
    private LocalDateTime banUntil;

    private LocalDateTime createTime;
}
