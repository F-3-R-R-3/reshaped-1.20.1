@echo off
setlocal

call .\gradlew.bat runClient --no-daemon -PreshapedProfileClient=true

set "RC=%ERRORLEVEL%"
exit /b %RC%
