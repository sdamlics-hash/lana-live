#!/usr/bin/env bash
set -euo pipefail
PACKAGE="ai.dreibus.lanalive.voicepreview"
APK="/tmp/lana-v36/app/build/outputs/apk/debug/app-debug.apk"
test -s "$APK"
adb install -r "$APK"
adb shell pm path "$PACKAGE" | grep -q "base.apk"
adb shell am start -W -n "$PACKAGE/ai.dreibus.lanalive.MainActivity"
sleep 7
adb shell pidof "$PACKAGE" | grep -Eq '[0-9]+'
adb shell uiautomator dump /sdcard/lana-v36.xml
adb shell cat /sdcard/lana-v36.xml | grep -F 'СЛУШАТЬ' >/dev/null
echo 'LANA V3.6 virtual Android install, launch and visible UI: PASS'
