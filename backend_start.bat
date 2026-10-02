@echo off
cd /d "%~dp0backend"
rem 凭据一律从环境变量注入(代码零密钥)。启动前请先设置:
rem   set MYSQL_PASSWORD=你的MySQL密码
rem   set JWT_SECRET=不少于32位的随机长字符串
rem   set STUDYROOM_ADMIN_PASSWORD=初始管理员密码
if "%MYSQL_PASSWORD%"=="" goto :missing
if "%JWT_SECRET%"=="" goto :missing
if "%STUDYROOM_ADMIN_PASSWORD%"=="" goto :missing
echo Starting backend on http://localhost:8080 ...
java -jar target\studyroom-backend-1.0.0.jar
pause
exit /b 0
:missing
echo [错误] 未设置全部三个环境变量 (MYSQL_PASSWORD / JWT_SECRET / STUDYROOM_ADMIN_PASSWORD)
echo 请先执行上面的 set 命令后再运行本脚本。
pause
exit /b 1
