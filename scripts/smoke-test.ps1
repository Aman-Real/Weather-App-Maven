# Smoke test against a running stack (run-local.ps1 or docker compose up).
# Usage: powershell -ExecutionPolicy Bypass -File scripts\smoke-test.ps1 [-Base http://localhost:8080]
param([string]$Base = 'http://localhost:8080')
$fail = 0
function Check($name, [scriptblock]$test) {
    try { $r = & $test; Write-Host "PASS  $name  $r" -ForegroundColor Green }
    catch { Write-Host "FAIL  $name  $($_.Exception.Message)" -ForegroundColor Red; $script:fail++ }
}

Check 'gateway ping' { (Invoke-RestMethod "$Base/api/system/ping").status }
Check 'eureka services' { ((Invoke-RestMethod "$Base/api/system/services").services -join ',') }
Check 'current weather' { $c = Invoke-RestMethod "$Base/api/weather/current?lat=30.32&lon=78.03&name=Dehradun"; "$($c.temperature) C, $($c.condition), $($c.source)" }
Check '7-day forecast' { (Invoke-RestMethod "$Base/api/weather/forecast?lat=30.32&lon=78.03").daily.Count }
$start = (Get-Date).AddDays(-8).ToString('yyyy-MM-dd'); $end = (Get-Date).AddDays(-2).ToString('yyyy-MM-dd')
Check 'history' { $h = Invoke-RestMethod "$Base/api/weather/history?lat=30.32&lon=78.03&start=$start&end=$end"; "$($h.days.Count) days from $($h.source)" }
Check 'geocode' { (Invoke-RestMethod "$Base/api/weather/geocode?q=Dubai")[0].name }
Check 'map grid' { (Invoke-RestMethod "$Base/api/weather/map?north=32&south=28&east=80&west=75").Count }
$token = $null
Check 'login demo' { $script:token = (Invoke-RestMethod "$Base/api/auth/login" -Method Post -ContentType 'application/json' -Body '{"username":"demo","password":"demo123"}').token; 'token ok' }
$h = @{ Authorization = "Bearer $token" }
Check 'favourites' { (Invoke-RestMethod "$Base/api/users/me/favorites" -Headers $h).Count }
Check 'alert rules' { (Invoke-RestMethod "$Base/api/alerts/rules" -Headers $h).Count }
Check 'test alert' { (Invoke-RestMethod "$Base/api/alerts/test" -Method Post -Headers $h).type }
Check 'protected without token = 401' {
    try { Invoke-RestMethod "$Base/api/alerts" | Out-Null; throw 'expected 401' }
    catch { if ($_.Exception.Response.StatusCode.value__ -eq 401) { '401' } else { throw } }
}
if ($fail -gt 0) { Write-Host "$fail check(s) failed" -ForegroundColor Red; exit 1 } else { Write-Host 'All smoke checks passed' -ForegroundColor Green }
