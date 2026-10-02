package com.campus.studyroom.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 候补队列：目标座位时段被占用时排队，预约释放后按入队顺序自动转正
 */
@Data
@TableName("waiting_queue")
public class WaitingQueue {

    public static final int STATUS_WAITING = 0;   // 候补中
    public static final int STATUS_PROMOTED = 1;  // 已转正（生成预约）
    public static final int STATUS_QUIT = 2;      // 已放弃
    public static final int STATUS_EXPIRED = 3;   // 已过期（用户禁约/区间已过）

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long seatId;

    private Long roomId;

    /** 离散室候补的目标时段ID（弹性室为 null） */
    private Long slotId;

    private LocalDate reserveDate;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer status;

    private LocalDateTime createTime;

    /** 转正时间 */
    private LocalDateTime promoteTime;

    /** 转正后生成的预约ID */
    private Long reservationId;
}
