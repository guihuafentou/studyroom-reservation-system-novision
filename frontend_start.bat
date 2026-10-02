@echo off
cd /d "%~dp0frontend"
set PATH=%PATH%;C:\Program Files\nodejs
echo Starting frontend on http://localhost:5173 ...
"C:\Program Files\nodejs\npm.cmd" run dev
pause
