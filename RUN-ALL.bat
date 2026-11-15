@echo off
title ECHO Launcher
cd /d "%~dp0"
echo Starting Server and Windows Media Capture in separate windows...
rem echo_pc.py — единственный источник ПК-трека, чтобы не было дублей
set ECHO_DISABLE_SMTC=1
start "ECHO Server" cmd /k "RUN-SERVER.bat"
start "ECHO Music" cmd /k "RUN-PC-MUSIC.bat"
