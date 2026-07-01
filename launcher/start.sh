#!/usr/bin/env bash
# ============================================================================
#  server-restart launcher (Linux/macOS)
#  Relaunches the server ONLY when the mod requested a restart (it drops a
#  "restart.flag" file next to the server before halting). A plain /stop
#  leaves no flag, so the loop exits normally.
#
#  Edit JAR and JAVA_ARGS for your server, then run this instead of your
#  usual start script. Keep it in the same folder as the server jar.
# ============================================================================
JAR="fabric-server-launch.jar"
JAVA_ARGS="-Xmx4G -Xms4G"

while true; do
  rm -f restart.flag
  echo "[launcher] Starting server..."
  java $JAVA_ARGS -jar "$JAR" nogui
  if [ -f restart.flag ]; then
    echo "[launcher] restart requested - relaunching in 3s..."
    sleep 3
  else
    echo "[launcher] Server stopped (no restart requested). Exiting."
    break
  fi
done
