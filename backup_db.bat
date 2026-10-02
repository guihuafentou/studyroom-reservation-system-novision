@echo off
rem ============================================================
rem 校园自习室预约管理系统 - 数据库备份脚本（评审改进第 14 条）
rem 用法: 双击运行, 或命令行执行 backup_db.bat
rem 依赖: 本机已安装 MySQL 客户端(mysqldump), 环境变量 MYSQL_PASSWORD 已设置
rem 产物: backups\studyroom_YYYYMMDD_HHmmss.sql, 自动保留最近 14 份
rem 注意: 密码包含特殊字符(如 % ! ^)时可能被 cmd 解析, 建议密码仅用字母数字
rem ============================================================

setlocal enabledelayedexpansion

set "DB_NAME=studyroom"
set "BACKUP_DIR=%~dp0backups"

if "%MYSQL_PASSWORD%"=="" (
    echo [错误] 未设置环境变量 MYSQL_PASSWORD, 请先执行:
    echo   set MYSQL_PASSWORD=你的MySQL密码
    exit /b 1
)

if not exist "%BACKUP_DIR%" mkdir "%BACKUP_DIR%"

rem 获取时间戳 (YYYYMMDD_HHmmss)
for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmmss"') do set "TS=%%i"

set "OUT=%BACKUP_DIR%\studyroom_%TS%.sql"

echo 正在备份数据库 %DB_NAME% ...
mysqldump -uroot --password=%MYSQL_PASSWORD% --default-character-set=utf8mb4 --single-transaction --routines %DB_NAME% > "%OUT%"

if errorlevel 1 (
    echo [错误] 备份失败, 请检查 MYSQL_PASSWORD 是否正确、MySQL 服务是否启动
    exit /b 1
)

echo 备份完成: %OUT%

rem 只保留最近 14 份
powershell -NoProfile -Command "Get-ChildItem -Path '%BACKUP_DIR%' -Filter 'studyroom_*.sql' | Sort-Object LastWriteTime -Descending | Select-Object -Skip 14 | Remove-Item -Force"

echo 已清理过期备份, 当前备份目录:
dir /b "%BACKUP_DIR%"

endlocal
