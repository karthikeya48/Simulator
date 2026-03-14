@echo off
REM Vehicular Edge Computing Simulator - Build & Run Script
REM ML-Driven Offloading Decision System v2.0

setlocal enabledelayedexpansion

echo.
echo ========================================
echo   VEC Simulator Build ^& Run (ML v2.0)
echo ========================================
echo.

set "PROJECT_DIR=D:\JavaDev\iFogSim"
set "SRC_DIR=%PROJECT_DIR%\src"
set "BIN_DIR=%PROJECT_DIR%\bin"
set "MAIN_CLASS=org.fog.test.VEC.StartSimulation"

REM Create bin directory if not exists
if not exist "%BIN_DIR%" (
    echo [INFO] Creating bin directory...
    mkdir "%BIN_DIR%"
)

REM Clean previous VEC class files
echo [INFO] Cleaning previous build...
if exist "%BIN_DIR%\org\fog\test\VEC" (
    rmdir /s /q "%BIN_DIR%\org\fog\test\VEC"
)

REM Compile all VEC source files (including utils and scheduler packages)
echo [1/2] Compiling source files...
cd "%PROJECT_DIR%"
javac -d "%BIN_DIR%" ^
    "%SRC_DIR%\org\fog\test\VEC\config\*.java" ^
    "%SRC_DIR%\org\fog\test\VEC\infrastructure\*.java" ^
    "%SRC_DIR%\org\fog\test\VEC\utils\*.java" ^
    "%SRC_DIR%\org\fog\test\VEC\task\*.java" ^
    "%SRC_DIR%\org\fog\test\VEC\scheduler\*.java" ^
    "%SRC_DIR%\org\fog\test\VEC\*.java" 2>&1

if errorlevel 1 (
    echo [ERROR] Compilation failed!
    pause
    exit /b 1
)

echo [SUCCESS] Compilation completed.
echo.

REM Run
echo [2/2] Starting VEC Simulator (ML-Driven)...
echo.
java -cp "%BIN_DIR%" "%MAIN_CLASS%"

echo.
echo [INFO] Simulator finished.
pause

