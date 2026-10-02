-- ============================================================
-- 校园自习室预约管理系统 - V2 迁移脚本 (在旧库上执行, 支持弹性预约改造)
-- 适用: 已按 V1 init.sql 建库运行的系统
-- 幂等: 每步先查 information_schema, 已迁移则跳过 (可安全重复执行)
-- 执行方式: mysql -uroot -p studyroom < migrate_v2.sql
-- ============================================================

USE studyroom;

-- 1. 自习室表: 新增预约模式列 (DISCRETE 离散时段 / FLEXIBLE 弹性时长)
SET @has_col := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = 'studyroom' AND TABLE_NAME = 'room' AND COLUMN_NAME = 'booking_model');
SET @sql := IF(@has_col = 0,
    'ALTER TABLE room ADD COLUMN booking_model VARCHAR(20) NOT NULL DEFAULT ''DISCRETE'' COMMENT ''预约模式:DISCRETE离散时段/FLEXIBLE弹性时长'' AFTER close_time',
    'SELECT ''room.booking_model already exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. 预约表: 新增弹性区间字段; slot_id 改为可空 (弹性室为 NULL)
SET @has_slot_null := (SELECT COUNT(*) FROM information_schema.COLUMNS
                       WHERE TABLE_SCHEMA = 'studyroom' AND TABLE_NAME = 'reservation'
                         AND COLUMN_NAME = 'slot_id' AND IS_NULLABLE = 'YES');
SET @sql := IF(@has_slot_null = 0,
    'ALTER TABLE reservation MODIFY COLUMN slot_id BIGINT DEFAULT NULL COMMENT ''时段(离散室必填;弹性室为空)''',
    'SELECT ''reservation.slot_id already nullable''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_start := (SELECT COUNT(*) FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = 'studyroom' AND TABLE_NAME = 'reservation' AND COLUMN_NAME = 'start_time');
SET @sql := IF(@has_start = 0,
    'ALTER TABLE reservation ADD COLUMN start_time DATETIME DEFAULT NULL COMMENT ''弹性预约实际开始时间'' AFTER reserve_date',
    'SELECT ''reservation.start_time already exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_end := (SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = 'studyroom' AND TABLE_NAME = 'reservation' AND COLUMN_NAME = 'end_time');
SET @sql := IF(@has_end = 0,
    'ALTER TABLE reservation ADD COLUMN end_time DATETIME DEFAULT NULL COMMENT ''弹性预约实际结束时间'' AFTER start_time',
    'SELECT ''reservation.end_time already exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @has_idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = 'studyroom' AND TABLE_NAME = 'reservation'
                   AND INDEX_NAME = 'idx_seat_interval');
SET @sql := IF(@has_idx = 0,
    'ALTER TABLE reservation ADD KEY idx_seat_interval (seat_id, start_time, end_time)',
    'SELECT ''idx_seat_interval already exists''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. 管理员审计表: admin_id 改为可空 (系统自动检测的违规占座告警无操作管理员)
SET @has_admin_null := (SELECT COUNT(*) FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = 'studyroom' AND TABLE_NAME = 'admin_audit_log'
                          AND COLUMN_NAME = 'admin_id' AND IS_NULLABLE = 'YES');
SET @sql := IF(@has_admin_null = 0,
    'ALTER TABLE admin_audit_log MODIFY COLUMN admin_id BIGINT DEFAULT NULL COMMENT ''操作管理员(系统自动检测类告警为 NULL)''',
    'SELECT ''admin_audit_log.admin_id already nullable''');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4. 试点: 将自习室 2 (图书馆二楼自习室B) 设为弹性时长模式
UPDATE room SET booking_model = 'FLEXIBLE' WHERE id = 2;
UPDATE room SET booking_model = 'DISCRETE' WHERE id = 1;

-- ============================================================
-- 说明
-- 1. 弹性室防重叠依赖应用层(座位行锁 FOR UPDATE + 区间重叠查询), 无唯一索引兜底;
--    离散室唯一索引 (uk_seat_active / uk_user_active) 对弹性记录(slot_id=NULL)自动不生效。
-- 2. 修改业务参数(最长弹性时长/超时自动取消/登录锁定等)见 backend application.yml 的 studyroom.rule.*。
-- 3. 管理员后台"自习室编辑"可直接修改 booking_model, 试点范围随时可调整。
-- ============================================================
