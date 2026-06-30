@echo off
REM Launch the Desktop Head Unit (DHU) against the VF3 Smart app.
REM Works with a physical phone or an AVD emulator.
REM
REM Usage:
REM   start-dhu.bat                 auto-detect the single connected device/emulator
REM   start-dhu.bat emulator-5554   target a specific device/emulator serial
setlocal

set "DEVICE=%~1"
if "%DEVICE%"=="" (
  for /f "skip=1 tokens=1,2" %%a in ('adb devices') do (
    if "%%b"=="device" if not defined DEVICE set "DEVICE=%%a"
  )
)
if "%DEVICE%"=="" (
  echo No adb device/emulator found. Start an AVD or plug in the phone, then retry.
  exit /b 1
)
echo Using device: %DEVICE%

adb -s %DEVICE% forward tcp:5277 tcp:5277
start "" "C:\Users\daotr\AppData\Local\Android\Sdk\extras\google\auto\desktop-head-unit.exe" --config "C:\Users\daotr\AppData\Local\Android\Sdk\extras\google\auto\config\vf3_720p.ini"
