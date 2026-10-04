@echo off
setlocal enabledelayedexpansion
title Pepal — Your Penpal

:: Change directory to script location for double-click support
cd /d "%~dp0"

echo.
echo ==============================================================
echo                   Pepal: Your Penpal
echo          Offline, Local-First AI Physical Journal
echo ==============================================================
echo.

:: 1. Verify Docker is installed
where docker >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Docker is not installed or not found in your PATH.
    echo Please install Docker Desktop: https://www.docker.com/products/docker-desktop/
    echo.
    pause
    exit /b 1
)

:: 2. Verify Docker daemon is running
docker info >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Docker Desktop is installed but is not currently running.
    echo Please launch Docker Desktop, wait for it to be ready, and try again.
    echo.
    pause
    exit /b 1
)

:: 3. Avoid port conflict if host Ollama is already using port 11434
netstat -ano | findstr /R /C:":11434 .*LISTENING" >nul 2>&1
if %errorlevel% equ 0 (
    echo [*] Detected local Ollama service running on host port 11434.
    echo [*] Mapping container Ollama port to host 11435 to avoid port conflict.
    set OLLAMA_HOST_PORT=11435
)

echo [*] Building and launching containers (app, db, ollama, model-puller)...
echo [*] First-time launch will build the image and download models.
echo.

docker compose up -d --build
if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Failed to start Docker Compose services.
    echo Check the error messages above for details.
    echo.
    pause
    exit /b 1
)

echo.
echo [*] Waiting for Pepal to be ready at http://localhost:8080 ...
set /a ATTEMPTS=0

:WAIT_LOOP
set /a ATTEMPTS+=1
curl -s -f http://localhost:8080/ >nul 2>&1
if %errorlevel% equ 0 goto READY
if %ATTEMPTS% geq 90 goto TIMEOUT
timeout /t 2 /nobreak >nul
goto WAIT_LOOP

:READY
echo.
echo ==============================================================
echo  [SUCCESS] Pepal (Your Penpal) is online at http://localhost:8080
echo ==============================================================
echo.
echo [*] Opening http://localhost:8080 in your default browser...
start http://localhost:8080
echo.
echo To stop all services at any time, double-click stop.bat
echo.
pause
exit /b 0

:TIMEOUT
echo.
echo [WARNING] Application took longer than expected to report ready.
echo It may still be pulling models or initializing the database.
echo Check container status with: docker compose ps
echo View live logs with: docker compose logs -f
echo.
echo Opening http://localhost:8080 in case it is ready now...
start http://localhost:8080
echo.
pause
exit /b 0
