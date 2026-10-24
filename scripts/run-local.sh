#!/bin/sh
# Starts the whole SkyCast stack on Linux/macOS (H2 in-memory databases, no Docker needed).
# Usage: sh scripts/run-local.sh      (logs in ./logs, stop with: kill $(cat logs/*.pid))
set -e
cd "$(dirname "$0")/.."
./mvnw -q -DskipTests package
mkdir -p logs

start() {
  java -jar "$1"/target/"$1"-*.jar > "logs/$1.log" 2>&1 &
  echo $! > "logs/$1.pid"
  echo "started $1 (pid $!)"
}
wait_up() {
  i=0
  until curl -fs "http://localhost:$1/actuator/health" | grep -q UP; do
    i=$((i + 1)); [ "$i" -gt 60 ] && { echo "$2 did not start"; exit 1; }
    sleep 2
  done
  echo "$2 is UP"
}

start discovery-server; wait_up 8761 discovery-server
start auth-service; start weather-service; start alert-service
wait_up 8081 auth-service; wait_up 8082 weather-service; wait_up 8083 alert-service
start api-gateway; wait_up 8080 api-gateway
echo "SkyCast is running on http://localhost:8080 (Eureka: http://localhost:8761)"
