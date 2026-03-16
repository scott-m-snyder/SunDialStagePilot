@echo off
SETLOCAL

echo ==================================================
echo   StagePilot M5StickC PLUS2 Flashing Utility
echo ==================================================
echo.

:: Define the ABSOLUTE path to the PlatformIO executable.
:: This path is set to the most common default installation location for PlatformIO.
:: If PlatformIO is installed elsewhere on YOUR system, you MUST edit this line.
set "PIO_EXE_PATH=C:\Users\scott\AppData\Local\Python\pythoncore-3.14-64\Scripts\pio.exe"

:: --- DO NOT EDIT BELOW THIS LINE ---

if not exist "%PIO_EXE_PATH%" (
    echo [CRITICAL ERROR] PlatformIO CLI (pio.exe) not found at:
    echo   "%PIO_EXE_PATH%"
    echo.
    echo This usually means PlatformIO is installed in a non-standard location on your system.
    echo Since 'pio' works in your other M5 app, PlatformIO IS installed.
    echo.
    echo To fix this, you MUST manually find where 'pio.exe' is located for that working installation.
    echo (Example: Open your other M5 app's VS Code terminal, type 'where pio' or '(Get-Command pio).Path').
    echo Then, edit THIS 'flash_m5stick.bat' file and replace the 'set "PIO_EXE_PATH="' line
    echo with the correct absolute path you found.
    echo.
    pause
    exit /b 1
)

:: Navigate to the m5stick_hardware directory where platformio.ini and src/main.cpp are located.
cd "%~dp0m5stick_hardware"

echo Found PlatformIO CLI: "%PIO_EXE_PATH%"
echo Compiling and Flashing M5StickC PLUS2...
echo (Ensure your M5StickC PLUS2 is plugged into USB and its drivers are installed)
echo.

"%PIO_EXE_PATH%" run --target upload --upload-port COM6
if errorlevel 1 goto :error_flash

echo.
echo ==================================================
echo   M5StickC PLUS2 Flashing Complete!

echo You may need to unplug and replug the M5StickC PLUS2 after flashing.
echo ==================================================
echo.
pause
exit /b 0

:error_flash
echo.
echo ==================================================
echo   FLASHING FAILED! Please check the output above.
echo   Ensure your M5StickC PLUS2 is plugged in and drivers are installed.
echo   (Windows drivers for ESP32 devices sometimes need manual installation.)
echo ==================================================
echo.
pause
exit /b %errorlevel%
