#!/usr/bin/env bash
# PoHW 真机 UI 自检：连电视 -> 装包 -> 弹题 -> 抓 UI 层级与截图 -> 分析文本裁切
#
# 前置：电视与本机同一 WiFi，已开启「开发者选项 -> 网络 ADB 调试」，
#       本机已装 Android platform-tools（adb 在 PATH 中）。
# 用法：TV_IP=192.168.1.50 APK=dist/PoHW-v1.3.1.apk \
#         bash tools/ui_check/overlay_check.sh
set -e
: "${TV_IP:?请设置 TV_IP，例如 TV_IP=192.168.1.50}"
APK="${APK:-dist/PoHW-v1.3.1.apk}"
OUT="${OUT:-.build/ui_check}"
mkdir -p "$OUT"

echo "[1/6] connect"
adb connect "$TV_IP:5555"
adb wait-for-device

echo "[2/6] install"
adb install -r "$APK"

echo "[3/6] restart guard"
adb shell am force-stop com.jager.tvquiz
adb shell am start -n com.jager.tvquiz/.MainActivity >/dev/null
sleep 2
adb shell am start-foreground-service -a com.jager.tvquiz.START \
    -n com.jager.tvquiz/.QuizOverlayService >/dev/null 2>&1 || true
sleep 1

echo "[4/6] trigger quiz"
adb shell am start-foreground-service -a com.jager.tvquiz.QUIZ_NOW \
    -n com.jager.tvquiz/.QuizOverlayService >/dev/null 2>&1 || true
sleep 2

echo "[5/6] dump UI + screenshot"
adb shell uiautomator dump /sdcard/pohw_window.xml >/dev/null
adb pull /sdcard/pohw_window.xml "$OUT/window.xml" >/dev/null
adb shell screencap -p /sdcard/pohw_screen.png
adb pull /sdcard/pohw_screen.png "$OUT/screen.png" >/dev/null

echo "[6/6] analyze"
python3 tools/ui_check/analyze_dump.py "$OUT/window.xml" | tee "$OUT/report.txt"
echo "UI_CHECK_ARTIFACTS $OUT/window.xml $OUT/screen.png $OUT/report.txt"
