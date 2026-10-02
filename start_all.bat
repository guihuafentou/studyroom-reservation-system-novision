@echo off
title StudyRoom System Launcher
cd /d "%~dp0"
echo ============================================
echo   StudyRoom Reservation System - Launcher
echo   Backend :8080  Frontend :5173
echo   Admin   : admin (密码取环境变量 STUDYROOM_ADMIN_PASSWORD)
echo   URL     : http://localhost:5173
echo ============================================
echo Opening backend window...
start "backend" cmd /k "%~dp0backend_start.bat"
echo Opening frontend window...
start "frontend" cmd /k "%~dp0frontend_start.bat"
echo.
echo Two windows opened. Wait about 20 seconds, then open:
echo   http://localhost:5173
echo.
pause
