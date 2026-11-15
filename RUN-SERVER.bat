@echo off
title ECHO Radar Server
cd /d "%~dp0"
echo ==============================================================
echo  Starting ECHO Radar Server...
echo  Web Dashboard: http://192.168.0.214:8080/
echo  Stop: Press Ctrl+C or close this window
echo ==============================================================
python echo_server.py
if errorlevel 1 (
    echo.
    echo Server stopped with error.
    pause
)
