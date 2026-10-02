package com.campus.studyroom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统参数表：业务规则（签到窗口/违约阈值/弹性时长上限等）运行时可配置，
 * 管理后台修改后即时生效，无需改代码/重启
 */
@Data
@TableName("sys_config")
public class SysConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 参数键，如 cancel_before_minutes */
    private String configKey;

    /** 参数值（字符串存储，按需解析为 int/boolean） */
    private String configValue;

    private String description;

    private LocalDateTime updateTime;
}
