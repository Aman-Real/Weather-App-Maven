@echo off
rem Builds and tests every module, saving the full log for review.
rem Double-click this file, or run it from the project folder.
setlocal
cd /d "%~dp0.."
if not exist ".master-runs" mkdir ".master-runs"
echo Java version: > ".master-runs\build.log"
java -version >> ".master-runs\build.log" 2>&1
if errorlevel 1 (
  echo Java was not found. Install JDK 17 or newer and try again.
  > ".master-runs\build-exit.txt" echo EXIT=JAVA_MISSING
  pause
  exit /b 1
)
echo Running mvnw.cmd verify ... this takes a few minutes the first time.
call mvnw.cmd -B verify >> ".master-runs\build.log" 2>&1
set "RC=%ERRORLEVEL%"
> ".master-runs\build-exit.txt" echo EXIT=%RC%
if "%RC%"=="0" (echo BUILD SUCCESS - all tests passed.) else (echo BUILD FAILED - see .master-runs\build.log)
pause
