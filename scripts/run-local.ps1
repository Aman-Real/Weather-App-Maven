# Starts the whole SkyCast stack on Windows (H2 in-memory databases, no Docker needed).
# Usage (from the project root):  powershell -ExecutionPolicy Bypass -File scripts\run-local.ps1
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

Write-Host 'Building all modules (tests skipped)...' -ForegroundColor Cyan
& "$root\mvnw.cmd" -q -DskipTests package
if ($LASTEXITCODE -ne 0) { throw 'Maven build failed' }

function Start-SkyCastModule($module, $port) {
    $jar = Get-ChildItem "$root\$module\target\$module-*.jar" | Select-Object -First 1
    # relative path + WorkingDirectory: the project path contains spaces, which break quoted -Command arguments
    $relative = "$module\target\$($jar.Name)"
    Write-Host "Starting $module on port $port" -ForegroundColor Green
    Start-Process powershell -WorkingDirectory $root -ArgumentList '-NoExit', '-Command', "`$Host.UI.RawUI.WindowTitle='$module'; java -jar $relative"
}

function Wait-Up($url, $name) {
    for ($i = 0; $i -lt 60; $i++) {
        try { if ((Invoke-RestMethod $url -TimeoutSec 2).status -eq 'UP') { Write-Host "$name is UP"; return } } catch { }
        Start-Sleep -Seconds 2
    }
    throw "$name did not start in time"
}

Start-SkyCastModule 'discovery-server' 8761
Wait-Up 'http://localhost:8761/actuator/health' 'discovery-server'
Start-SkyCastModule 'auth-service' 8081
Start-SkyCastModule 'weather-service' 8082
Start-SkyCastModule 'alert-service' 8083
Wait-Up 'http://localhost:8081/actuator/health' 'auth-service'
Wait-Up 'http://localhost:8082/actuator/health' 'weather-service'
Wait-Up 'http://localhost:8083/actuator/health' 'alert-service'
Start-SkyCastModule 'api-gateway' 8080
Wait-Up 'http://localhost:8080/actuator/health' 'api-gateway'

Write-Host ''
Write-Host 'SkyCast is running:  http://localhost:8080' -ForegroundColor Cyan
Write-Host 'Eureka dashboard:    http://localhost:8761'
Write-Host 'Swagger (weather):   http://localhost:8082/swagger-ui.html'
Write-Host 'Give Eureka ~30 s to propagate registrations before the first API call.'
