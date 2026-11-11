@echo off
title ECHO Launcher
cd /d "%~dp0"
echo Starting Server and Windows Media Capture in separate windows...
start "ECHO Server" cmd /k "RUN-SERVER.bat"
start "ECHO Music" cmd /k "RUN-PC-MUSIC.bat"
