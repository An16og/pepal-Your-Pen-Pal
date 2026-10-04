@echo off
setlocal enabledelayedexpansion
title Stopping pepal Journal

:: Change directory to script location for double-click support
cd /d "%~dp0"

echo.
echo ==============================================================
echo                   Stopping pepal Journal
echo ==============================================================
echo.

echo [*] Gracefully stopping all services (app, db, ollama)...
docker compose down

if %errorlevel% equ 0 (
    echo.
    echo ==============================================================
    echo  [SUCCESS] All pepal Journal services have stopped safely.
    echo  Your entries and models are safely preserved in Docker volumes.
    echo ==============================================================
) else (
    echo.
    echo [ERROR] Encountered an error while stopping services.
)

echo.
pause
