#!/bin/bash
# Quick rebuild and deploy to connected device with logging

LOGFILE="deploy.log"

echo "=== Deploy started at $(date) ===" | tee -a "$LOGFILE"
echo "Building and installing to device..." | tee -a "$LOGFILE"

# Run Gradle and capture all output
./gradlew installDebug --no-daemon 2>&1 | tee -a "$LOGFILE"

BUILD_STATUS=${PIPESTATUS[0]}  # Gets the exit code of gradle even through tee

if [ $BUILD_STATUS -eq 0 ]; then
    echo "Restarting app..." | tee -a "$LOGFILE"
    adb shell am force-stop com.example.mrbill | tee -a "$LOGFILE"
    adb shell am start -n com.example.mrbill/com.sms.edgenode.ui.MainActivity | tee -a "$LOGFILE"
    echo "App deployed and restarted!" | tee -a "$LOGFILE"
else
    echo "Build failed! Check $LOGFILE for details." | tee -a "$LOGFILE"
fi

echo "=== Deploy ended at $(date) ===" | tee -a "$LOGFILE"
