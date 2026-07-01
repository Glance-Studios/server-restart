@echo off
setlocal
rem ============================================================================
rem  server-restart launcher (Windows)
rem  Relaunches the server ONLY when the mod requested a restart (it drops a
rem  "restart.flag" file next to the server before halting). A plain /stop
rem  leaves no flag, so the loop exits normally.
rem
rem  Edit JAR and JAVA_ARGS for your server, then run this instead of your
rem  usual start script. Keep it in the same folder as the server jar.
rem ============================================================================
set JAR=fabric-server-launch.jar
set JAVA_ARGS=-Xmx4G -Xms4G

:loop
if exist restart.flag del /f /q restart.flag
echo [launcher] Starting server...
java %JAVA_ARGS% -jar "%JAR%" nogui
if exist restart.flag (
    echo [launcher] restart requested - relaunching in 3s...
    timeout /t 3 /nobreak >nul
    goto loop
)
echo [launcher] Server stopped (no restart requested). Exiting.
endlocal
