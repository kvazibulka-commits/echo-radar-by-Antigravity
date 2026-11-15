@echo off
title ECHO PC Music Capture
cd /d "%~dp0"
echo ==============================================================
echo  Starting Windows Media Capture...
echo  Capturing: Yandex Music, Spotify, VK, Chrome, etc.
echo  Stop: Press Ctrl+C or close this window
echo ==============================================================
python echo_pc.py
if errorlevel 1 (
    echo.
    echo Process stopped with error.
    pause
)
