@echo off

REM Read configuration values from config.txt
setlocal enabledelayedexpansion
for /f "usebackq tokens=1,* delims==" %%i in ("config.txt") do (
    set "%%i=%%j"
)

REM Navigate to the project directory
cd "%PROJECT_DIR%" || exit /b

REM Build the project using Gradle
call .\gradlew.bat clean build || exit /b

REM Navigate to the build output directory
cd "%BUILD_DIR%" || exit /b

REM Delete the old plugin JAR from the server plugins directory
del "%SERVER_DIR%\plugins\%PLUGIN_NAME%" || exit /b

REM Copy the new plugin JAR to the server plugins directory
copy /Y "%PLUGIN_NAME%" "%SERVER_DIR%\plugins\" || exit /b

REM Navigate to the server directory
cd "%SERVER_DIR%" || exit /b

REM Start the Minecraft server
java -Xms2G -Xmx2G -jar "%SERVER_JAR%" --nogui || exit /b
