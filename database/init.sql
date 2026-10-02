-- ============================================================
-- 校园自习室预约管理系统 - 数据库初始化脚本 (MySQL 8.0+)
-- 执行方式: mysql -uroot -p < init.sql
-- 字符集: utf8mb4
-- ============================================================

CREATE DATABASE IF NOT EXISTS studyroom DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE studyroom;

-- ---------- 用户表 ----------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    student_no      VARCHAR(20)  NOT NULL COMMENT '学号',
    username        VARCHAR(50)  NOT NULL COMMENT '用户名',
    password        VARCHAR(100) NOT NULL COMMENT 'BCrypt加密密码',
    real_name       VARCHAR(50)  DEFAULT NULL COMMENT '真实姓名',
    role            TINYINT      NOT NULL DEFAULT 0 COMMENT '角色:0学生 1管理员',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态:0禁用 1正常',
    violation_count INT          NOT NULL DEFAULT 0 COMMENT '违约累计次数',
    ban_until       DATETIME     DEFAULT NULL COMMENT '禁约截止时间',
    create_time     DATETIME     NOT NULL COMMENT '注册时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_student_no (student_no),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ---------- 自习室表 ----------
DROP TABLE IF EXISTS room;
CREATE TABLE room (
    id         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '自习室ID',
    name       VARCHAR(50) NOT NULL COMMENT '自习室名称',
    building   VARCHAR(50) NOT NULL COMMENT '楼栋',
    floor      INT         NOT NULL COMMENT '楼层',
    capacity   INT         NOT NULL COMMENT '座位容量',
    open_time  TIME        NOT NULL COMMENT '开放时间',
    close_time TIME        NOT NULL COMMENT '关闭时间',
    booking_model VARCHAR(20) NOT NULL DEFAULT 'DISCRETE' COMMENT '预约模式:DISCRETE离散时段/FLEXIBLE弹性时长',
    status     TINYINT     NOT NULL DEFAULT 1 COMMENT '状态:0停用 1开放',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='自习室表';

-- ---------- 座位表 ----------
DROP TABLE IF EXISTS seat;
CREATE TABLE seat (
    id       BIGINT      NOT NULL AUTO_INCREMENT COMMENT '座位ID',
    room_id  BIGINT      NOT NULL COMMENT '所属自习室',
    seat_no  VARCHAR(20) NOT NULL COMMENT '座位编号,如 A-01',
    row_no   INT         NOT NULL COMMENT '排号(座位图坐标)',
    col_no   INT         NOT NULL COMMENT '列号(座位图坐标)',
    seat_type TINYINT    NOT NULL DEFAULT 0 COMMENT '类型:0普通 1靠窗 2电源 3隔断',
    status   TINYINT     NOT NULL DEFAULT 1 COMMENT '实时状态:0禁用 1空闲 2已预约 3使用中',
    PRIMARY KEY (id),
    UNIQUE KEY uk_room_seat (room_id, seat_no),
    UNIQUE KEY uk_room_pos (room_id, row_no, col_no),
    KEY idx_room (room_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='座位表';

-- ---------- 时段表(离散时段模型核心) ----------
DROP TABLE IF EXISTS slot;
CREATE TABLE slot (
    id         BIGINT   NOT NULL AUTO_INCREMENT COMMENT '时段ID',
    room_id    BIGINT   NOT NULL COMMENT '所属自习室',
    start_time TIME     NOT NULL COMMENT '开始时间',
    end_time   TIME     NOT NULL COMMENT '结束时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_room_slot (room_id, start_time),
    KEY idx_room (room_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='时段表';

-- ---------- 预约记录表 ----------
-- 离散室: slot_id + reserve_date 定位时段; 弹性室: start_time/end_time 承载真实起止区间(slot_id 为 NULL)
DROP TABLE IF EXISTS reservation;
CREATE TABLE reservation (
    id           BIGINT   NOT NULL AUTO_INCREMENT COMMENT '预约ID',
    user_id      BIGINT   NOT NULL COMMENT '预约学生',
    room_id      BIGINT   NOT NULL COMMENT '自习室',
    seat_id      BIGINT   NOT NULL COMMENT '座位',
    slot_id      BIGINT   DEFAULT NULL COMMENT '时段(离散室必填;弹性室为空)',
    reserve_date DATE     NOT NULL COMMENT '预约日期',
    start_time   DATETIME DEFAULT NULL COMMENT '弹性预约实际开始时间',
    end_time     DATETIME DEFAULT NULL COMMENT '弹性预约实际结束时间',
    status       TINYINT  NOT NULL DEFAULT 0 COMMENT '状态:0待签到 1已签到 2已完成 3已取消 4违约 5提前结束',
    create_time  DATETIME NOT NULL COMMENT '创建时间',
    sign_time    DATETIME DEFAULT NULL COMMENT '签到时间',
    -- 生成列条件唯一索引:仅"进行中"(status∈{0,1})的预约占用唯一性,取消/违约/完成/提前结束后自动释放,可重新预约
    -- 弹性室 slot_id 为 NULL 时 CONCAT 结果为 NULL,唯一索引自动不生效(防重叠依赖应用层行锁+区间查询)
    active_key       VARCHAR(80) GENERATED ALWAYS AS (IF(status IN (0,1), CONCAT(seat_id,'_',reserve_date,'_',slot_id), NULL)) STORED COMMENT '座位防重叠键',
    active_user_key  VARCHAR(80) GENERATED ALWAYS AS (IF(status IN (0,1), CONCAT(user_id,'_',reserve_date,'_',slot_id), NULL)) STORED COMMENT '用户防多占键',
    PRIMARY KEY (id),
    UNIQUE KEY uk_seat_active (active_key),
    UNIQUE KEY uk_user_active (active_user_key),
    KEY idx_user (user_id),
    KEY idx_seat_date (seat_id, reserve_date),
    KEY idx_seat_interval (seat_id, start_time, end_time),
    KEY idx_status_time (status, reserve_date, slot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预约记录表';

-- ---------- 座位状态检测日志表 ----------
DROP TABLE IF EXISTS seat_status_log;
CREATE TABLE seat_status_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    seat_id     BIGINT       NOT NULL COMMENT '座位',
    occupied    TINYINT      NOT NULL COMMENT '是否占用:0空 1占用',
    confidence  DECIMAL(5,2) DEFAULT NULL COMMENT '置信度 0~1',
    detector    VARCHAR(20)  NOT NULL DEFAULT 'mog2' COMMENT '检测器标识',
    detect_time DATETIME     NOT NULL COMMENT '检测时间',
    PRIMARY KEY (id),
    KEY idx_seat_time (seat_id, detect_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='座位状态检测日志表';

-- ---------- 管理员操作审计表 ----------
DROP TABLE IF EXISTS admin_audit_log;
CREATE TABLE admin_audit_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    admin_id    BIGINT       DEFAULT NULL COMMENT '操作管理员(系统自动检测类告警为 NULL)',
    action      VARCHAR(50)  NOT NULL COMMENT '操作,如 FORCE_CANCEL/SUSPICIOUS_OCCUPY',
    target_type VARCHAR(30)  NOT NULL COMMENT '对象类型:reservation/seat/room/user/slot',
    target_id   BIGINT       NOT NULL COMMENT '对象ID',
    detail      VARCHAR(500) DEFAULT NULL COMMENT '操作详情',
    create_time DATETIME     NOT NULL COMMENT '操作时间',
    PRIMARY KEY (id),
    KEY idx_admin (admin_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员操作审计表';

-- ============================================================
-- 初始化数据
-- ============================================================

-- 管理员账号: 由后端启动时自动创建(DataInitializer), 用户名 admin,
-- 密码通过环境变量 STUDYROOM_ADMIN_PASSWORD 注入(无默认值)。

-- 测试学生: 通过前端注册页面自行注册 (学号+用户名+密码)。

-- 自习室 (1 为离散时段模式示范; 2 为弹性时长模式试点)
INSERT INTO room (id, name, building, floor, capacity, open_time, close_time, booking_model, status) VALUES
(1, '图书馆一楼自习室A', '图书馆', 1, 30, '08:00:00', '22:00:00', 'DISCRETE', 1),
(2, '图书馆二楼自习室B', '图书馆', 2, 20, '08:00:00', '22:00:00', 'FLEXIBLE', 1);

-- 座位(自习室1: 5排x6列=30座; 自习室2: 4排x5列=20座)
INSERT INTO seat (room_id, seat_no, row_no, col_no, seat_type, status) VALUES
(1, 'A-01', 1, 1, 1, 1), (1, 'A-02', 1, 2, 2, 1), (1, 'A-03', 1, 3, 0, 1), (1, 'A-04', 1, 4, 0, 1), (1, 'A-05', 1, 5, 2, 1), (1, 'A-06', 1, 6, 1, 1),
(1, 'B-01', 2, 1, 1, 1), (1, 'B-02', 2, 2, 2, 1), (1, 'B-03', 2, 3, 0, 1), (1, 'B-04', 2, 4, 0, 1), (1, 'B-05', 2, 5, 2, 1), (1, 'B-06', 2, 6, 1, 1),
(1, 'C-01', 3, 1, 1, 1), (1, 'C-02', 3, 2, 2, 1), (1, 'C-03', 3, 3, 0, 1), (1, 'C-04', 3, 4, 0, 1), (1, 'C-05', 3, 5, 2, 1), (1, 'C-06', 3, 6, 1, 1),
(1, 'D-01', 4, 1, 1, 1), (1, 'D-02', 4, 2, 2, 1), (1, 'D-03', 4, 3, 0, 1), (1, 'D-04', 4, 4, 0, 1), (1, 'D-05', 4, 5, 2, 1), (1, 'D-06', 4, 6, 1, 1),
(1, 'E-01', 5, 1, 1, 1), (1, 'E-02', 5, 2, 2, 1), (1, 'E-03', 5, 3, 0, 1), (1, 'E-04', 5, 4, 0, 1), (1, 'E-05', 5, 5, 2, 1), (1, 'E-06', 5, 6, 1, 1),
(2, 'A-01', 1, 1, 1, 1), (2, 'A-02', 1, 2, 2, 1), (2, 'A-03', 1, 3, 0, 1), (2, 'A-04', 1, 4, 0, 1), (2, 'A-05', 1, 5, 1, 1),
(2, 'B-01', 2, 1, 1, 1), (2, 'B-02', 2, 2, 2, 1), (2, 'B-03', 2, 3, 0, 1), (2, 'B-04', 2, 4, 0, 1), (2, 'B-05', 2, 5, 1, 1),
(2, 'C-01', 3, 1, 1, 1), (2, 'C-02', 3, 2, 2, 1), (2, 'C-03', 3, 3, 0, 1), (2, 'C-04', 3, 4, 0, 1), (2, 'C-05', 3, 5, 1, 1),
(2, 'D-01', 4, 1, 1, 1), (2, 'D-02', 4, 2, 2, 1), (2, 'D-03', 4, 3, 0, 1), (2, 'D-04', 4, 4, 0, 1), (2, 'D-05', 4, 5, 1, 1);

-- 时段(自习室1: 08:00-22:00 每30分钟一段, 共28段; 自习室2同)
INSERT INTO slot (room_id, start_time, end_time)
WITH RECURSIVE seq AS (SELECT 0 AS n UNION ALL SELECT n+1 FROM seq WHERE n < 27)
SELECT 1, SEC_TO_TIME(8*3600 + n*1800), SEC_TO_TIME(8*3600 + (n+1)*1800)
FROM seq WHERE 8*3600 + (n+1)*1800 <= 22*3600;

INSERT INTO slot (room_id, start_time, end_time)
WITH RECURSIVE seq AS (SELECT 0 AS n UNION ALL SELECT n+1 FROM seq WHERE n < 27)
SELECT 2, SEC_TO_TIME(8*3600 + n*1800), SEC_TO_TIME(8*3600 + (n+1)*1800)
FROM seq WHERE 8*3600 + (n+1)*1800 <= 22*3600;

-- ============================================================
-- 说明
-- 1. 管理员账号由后端启动时自动创建: admin (密码经环境变量 STUDYROOM_ADMIN_PASSWORD 注入)。
-- 2. 测试学生账号: 通过前端注册页面自行注册 (学号+用户名+密码)。
-- 3. 座位编号与自习室、行列号一一对应（seat 表）。
-- 4. 本脚本仅建库建表与基础数据, 不插入预约/日志等业务数据。
-- ============================================================
