@echo off
rem Maven Wrapper for Windows (only-script style): downloads the Maven version
rem pinned in .mvn\wrapper\maven-wrapper.properties on first use, then runs it.
setlocal

set "WRAPPER_PROPS=%~dp0.mvn\wrapper\maven-wrapper.properties"
set "DIST_URL="
for /f "usebackq tokens=1,* delims==" %%A in ("%WRAPPER_PROPS%") do (
  if "%%A"=="distributionUrl" set "DIST_URL=%%B"
)
if not defined DIST_URL (
  echo distributionUrl missing in %WRAPPER_PROPS%
  exit /b 1
)

for %%F in ("%DIST_URL%") do set "DIST_ZIP=%%~nxF"
set "DIST_NAME=%DIST_ZIP:-bin.zip=%"
if not defined MAVEN_USER_HOME set "MAVEN_USER_HOME=%USERPROFILE%\.m2"
set "MAVEN_DIR=%MAVEN_USER_HOME%\wrapper\dists\%DIST_NAME%"

if exist "%MAVEN_DIR%\bin\mvn.cmd" goto run

echo Downloading %DIST_URL%
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; $tmp=Join-Path $env:TEMP ('mvnw-'+[guid]::NewGuid()); New-Item -ItemType Directory -Path $tmp | Out-Null; $zip=Join-Path $tmp 'maven.zip'; Invoke-WebRequest -UseBasicParsing -Uri $env:DIST_URL -OutFile $zip; Expand-Archive -Path $zip -DestinationPath $tmp; New-Item -ItemType Directory -Force -Path (Split-Path $env:MAVEN_DIR) | Out-Null; Move-Item -Path (Join-Path $tmp $env:DIST_NAME) -Destination $env:MAVEN_DIR; Remove-Item -Recurse -Force $tmp"
if errorlevel 1 (
  echo Failed to download Maven from %DIST_URL%
  exit /b 1
)

:run
set "MAVEN_PROJECTBASEDIR=%~dp0"
if "%MAVEN_PROJECTBASEDIR:~-1%"=="\" set "MAVEN_PROJECTBASEDIR=%MAVEN_PROJECTBASEDIR:~0,-1%"
"%MAVEN_DIR%\bin\mvn.cmd" %*
exit /b %ERRORLEVEL%
