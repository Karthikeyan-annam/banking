# Core Banking Full-Stack Dev Launcher with Health Check Polling
$ErrorActionPreference = "Continue"

Write-Host "=====================================================================" -ForegroundColor Cyan
Write-Host " Starting Secure Core Banking Full-Stack Application" -ForegroundColor Cyan
Write-Host "=====================================================================" -ForegroundColor Cyan

# 1. Resolve Maven PATH if needed
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    $fallbackPaths = @(
        "C:\Tools\apache-maven-3.9.9\bin",
        "$env:USERPROFILE\Downloads\chatbot\apache-maven-3.9.6\bin"
    )
    foreach ($p in $fallbackPaths) {
        if (Test-Path "$p\mvn.cmd") {
            $env:PATH = "$p;$env:PATH"
            Write-Host "[INFO] Loaded Maven from $p" -ForegroundColor DarkGray
            break
        }
    }
}

# 2. Launch Spring Boot in a separate terminal process
Write-Host "[1/3] Launching Spring Boot Backend in a background session..." -ForegroundColor Blue
Start-Process powershell -ArgumentList "-NoExit", "-Command", "mvn spring-boot:run"

# 3. Health Check Polling (Wait until port 8080 responds)
Write-Host "[2/3] Waiting for Backend to be healthy at http://localhost:8080" -NoNewline -ForegroundColor Yellow
$maxAttempts = 60
$ready = $false

for ($i = 0; $i -lt $maxAttempts; $i++) {
    try {
        $res = Invoke-WebRequest -Uri "http://127.0.0.1:8080/actuator/health" -UseBasicParsing -TimeoutSec 2 -ErrorAction Stop
        if ($res.StatusCode -eq 200) {
            $ready = $true
            break
        }
    } catch {
        if ($_.Exception.Response.StatusCode.value__ -in @(200, 401)) {
            $ready = $true
            break
        }
    }
    Write-Host "." -NoNewline -ForegroundColor Yellow
    Start-Sleep -Seconds 2
}

if (-not $ready) {
    Write-Host "`n[ERROR] Timed out waiting for Spring Boot backend." -ForegroundColor Red
    exit 1
}

Write-Host "`n[SUCCESS] Spring Boot backend is active and ready!" -ForegroundColor Green

# 4. Start React Frontend
Write-Host "[3/3] Starting React Vite Frontend (http://localhost:3000)..." -ForegroundColor Magenta
Set-Location "$PSScriptRoot\frontend"
npm run dev
