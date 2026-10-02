package com.campus.studyroom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公告表：管理员发布开闭馆/节假日/考试周等通知，学生端首页展示
 */
@Data
@TableName("announcement")
public class Announcement {

    public static final int STATUS_OFF = 0;   // 下架
    public static final int STATUS_ON = 1;    // 发布

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String content;

    /** 是否置顶：0否 1是 */
    private Integer isTop;

    /** 状态：0下架 1发布 */
    private Integer status;

    /** 发布管理员 */
    private Long createBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
