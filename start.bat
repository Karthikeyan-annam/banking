@echo off
setlocal EnableDelayedExpansion

title Core Banking Full-Stack Dev Server

echo =====================================================================
echo  Starting Secure Core Banking Full-Stack Application
echo =====================================================================

:: Check if Maven is available in PATH
where mvn >nul 2>nul
if %errorlevel% neq 0 (
    echo [INFO] 'mvn' not in system PATH. Checking standard local paths...
    if exist "C:\Tools\apache-maven-3.9.9\bin\mvn.cmd" (
        set "PATH=C:\Tools\apache-maven-3.9.9\bin;%PATH%"
    ) else if exist "C:\Users\%USERNAME%\Downloads\chatbot\apache-maven-3.9.6\bin\mvn.cmd" (
        set "PATH=C:\Users\%USERNAME%\Downloads\chatbot\apache-maven-3.9.6\bin;%PATH%"
    )
)

echo [1/3] Launching Spring Boot Backend in a separate window...
start "Spring Boot Backend (:8080)" cmd /k "mvn spring-boot:run"

echo [2/3] Waiting for Backend to be active on http://localhost:8080...
set "ATTEMPTS=0"

:CHECK_LOOP
set /a ATTEMPTS+=1
powershell -Command "$response = try { (Invoke-WebRequest -Uri 'http://127.0.0.1:8080/actuator/health' -UseBasicParsing -TimeoutSec 2).StatusCode } catch { if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 } }; if ($response -eq 200 -or $response -eq 401) { exit 0 } else { exit 1 }" >nul 2>nul
if %errorlevel% equ 0 (
    echo.
    echo [SUCCESS] Spring Boot backend is LIVE and responding!
    goto START_FRONTEND
)

if %ATTEMPTS% geq 60 (
    echo.
    echo [ERROR] Timed out waiting for Spring Boot backend. Please check the backend window for errors.
    pause
    exit /b 1
)

<nul set /p=.
timeout /t 2 /nobreak >nul
goto CHECK_LOOP

:START_FRONTEND
echo.
echo [3/3] Starting Vite Frontend on http://localhost:3000...
cd frontend
npm run dev
