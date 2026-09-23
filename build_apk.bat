@echo off
setlocal enabledelayedexpansion
cd /d "%~dp0"

echo ============================================
echo   Metro Ekaterinburg - build debug APK
echo ============================================
echo.

rem Podstavlyaem JAVA_HOME, tolko esli ne zadan i put sushchestvuet.
if "%JAVA_HOME%"=="" (
  if exist "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot\bin\java.exe" (
    set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
  )
)
echo JAVA_HOME = %JAVA_HOME%
echo.
echo Building... (first run downloads Gradle + deps, may take several minutes)
echo.

call "%~dp0gradlew.bat" assembleDebug
if errorlevel 1 (
  echo.
  echo ============================================
  echo   BUILD FAILED - error text is above.
  echo   Copy it and send to me.
  echo ============================================
  pause
  exit /b 1
)

set "APK=%~dp0app\build\outputs\apk\debug\app-debug.apk"
if not exist "%APK%" (
  echo APK not found: %APK%
  pause
  exit /b 1
)

rem Nadyozhno beryom put k Desktop (uchityvaet OneDrive-redirect).
for /f "usebackq delims=" %%D in (`powershell -NoProfile -Command "[Environment]::GetFolderPath('Desktop')"`) do set "DESKTOP=%%D"
if "%DESKTOP%"=="" set "DESKTOP=%USERPROFILE%\Desktop"

copy /Y "%APK%" "%DESKTOP%\MetroEkb.apk" >nul
echo.
echo ============================================
echo   DONE:  %DESKTOP%\MetroEkb.apk
echo ============================================
pause
