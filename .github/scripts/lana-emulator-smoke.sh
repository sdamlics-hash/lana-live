#!/usr/bin/env bash
set -euo pipefail
PACKAGE="ai.dreibus.lanalive.voicepreview"
APK="/tmp/lana-v35-smoke/app/build/outputs/apk/debug/app-debug.apk"
if [ ! -s "$APK" ]; then
  echo "APK not found at expected path: $APK"
  exit 1
fi
adb install -r "$APK"
adb shell pm path "$PACKAGE" | grep -q 'base.apk'
adb shell am start -W -n "$PACKAGE/ai.dreibus.lanalive.MainActivity"
sleep 8
adb shell pidof "$PACKAGE" | grep -Eq '[0-9]+'
adb shell dumpsys activity activities | grep -F "$PACKAGE" >/dev/null
adb shell uiautomator dump /sdcard/lana-ui.xml
adb shell cat /sdcard/lana-ui.xml | grep -F 'ПРОВЕРИТЬ СВЯЗЬ С ЯДРОМ' >/dev/null
echo "LANA V3.5 emulator: install, launch and visible UI passed"
