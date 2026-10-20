@echo off
setlocal
set "ROOT=%~dp0.."
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0check-minecraft-api.ps1" -Root "%ROOT%"
exit /b %ERRORLEVEL%
