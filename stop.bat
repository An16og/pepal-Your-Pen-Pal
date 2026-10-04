@echo off
setlocal enabledelayedexpansion
title Stopping Pepal — Your Penpal

:: Change directory to script location for double-click support
cd /d "%~dp0"

echo.
echo ==============================================================
echo                 Stopping Pepal: Your Penpal
echo ==============================================================
echo.

echo [*] Gracefully stopping all services (app, db, ollama)...
docker compose down

if %errorlevel% equ 0 (
    echo.
    echo ==============================================================
    echo  [SUCCESS] All Pepal services have stopped safely.
    echo  Your entries and models are safely preserved in Docker volumes.
    echo ==============================================================
) else (
    echo.
    echo [ERROR] Encountered an error while stopping services.
)

echo.
pause
