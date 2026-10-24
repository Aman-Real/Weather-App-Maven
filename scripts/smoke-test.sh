#!/bin/sh
# Smoke test against a running stack. Usage: sh scripts/smoke-test.sh [http://localhost:8080]
BASE=${1:-http://localhost:8080}
fail=0
check() { name=$1; shift; if out=$("$@" 2>&1); then echo "PASS  $name"; else echo "FAIL  $name: $out"; fail=$((fail + 1)); fi; }

check "gateway ping"    curl -fs "$BASE/api/system/ping"
check "current weather" curl -fs "$BASE/api/weather/current?lat=30.32&lon=78.03&name=Dehradun"
check "forecast"        curl -fs "$BASE/api/weather/forecast?lat=30.32&lon=78.03"
check "geocode"         curl -fs "$BASE/api/weather/geocode?q=Dubai"
TOKEN=$(curl -fs -H 'Content-Type: application/json' -d '{"username":"demo","password":"demo123"}' "$BASE/api/auth/login" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
check "login demo"      test -n "$TOKEN"
check "alert rules"     curl -fs -H "Authorization: Bearer $TOKEN" "$BASE/api/alerts/rules"
check "test alert"      curl -fs -X POST -H "Authorization: Bearer $TOKEN" "$BASE/api/alerts/test"
check "401 w/o token"   sh -c "[ \"\$(curl -s -o /dev/null -w '%{http_code}' $BASE/api/alerts)\" = 401 ]"
[ "$fail" -eq 0 ] && echo "All smoke checks passed" || { echo "$fail check(s) failed"; exit 1; }
