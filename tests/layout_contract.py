#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""PoHW UI 布局契约：源码级静态断言，零 Android 依赖，可由构建门禁直接执行。

首页 3 列 × 2 排与家长设置单列卡片流是 TV 遥控器可用性的硬约束，
纯 JVM 单测无法实例化 android.view.*，因此用源码符号契约守住结构，
再配合 build.sh 的 javac/apksigner 保证符号真的被编译进 dex。

注意：调用次数类断言必须基于「去注释代码行」，否则把调用注释掉也会被计数放行；
而三栏残留这类负向断言仍基于原始文件，注释里的旧符号同样视为残留。
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MAIN = os.path.join(ROOT, "app/src/com/jager/tvquiz/MainActivity.java")
SET = os.path.join(ROOT, "app/src/com/jager/tvquiz/SettingsActivity.java")

TOTAL = 0


def read(path):
    with io.open(path, encoding="utf-8") as fh:
        return fh.read()


def strip_comments(text):
    return "\n".join(
        line for line in text.splitlines()
        if not line.lstrip().startswith(("//", "*", "/*"))
    )


def check(ok, msg):
    global TOTAL
    if not ok:
        print("LAYOUT_CONTRACT_FAIL: " + msg)
        sys.exit(1)
    TOTAL += 1
    print("[PASS] " + msg)


raw_m = read(MAIN)
raw_s = read(SET)
m = strip_comments(raw_m)
s = strip_comments(raw_s)

# ---- 首页：3 列 × 2 排 ----
check("private static final int GRID_COLS = 3;" in m, "home declares 3 columns")
check(m.count("new Button[GRID_COLS]") == 2, "home has exactly 2 grid rows")
check(len(re.findall(r"actionButton\(row[12],", m)) == 6, "home has exactly 6 action buttons")
check("row3" not in m, "legacy third button row removed")
check(m.count("l.addView(row") == 2, "exactly two grid rows added to the page")
check("new LinearLayout.LayoutParams(0, Ui.dp(this, 54), 1f)" in m,
      "home buttons are equal-width instead of hardcoded 190dp")
check(m.count("wireGrid()") == 2, "home declares and calls the grid focus chain")
for direction in ("setNextFocusLeftId", "setNextFocusRightId",
                  "setNextFocusUpId", "setNextFocusDownId"):
    check(direction in m, "home focus chain includes " + direction)
check("Ui.fixed(this, 46)" in m, "home title has enough height to avoid clipping")
check("getPackageInfo" in m, "home version still read from PackageManager")

# ---- 家长设置：单列卡片流 ----
check(s.count("new ScrollView(this)") == 1, "settings has exactly one scroll container")
check(s.count("addCard(") == 6, "settings declares 5 grouped cards + helper")
check(len(re.findall(r'addCard\(col, "0\d', s)) == 5, "settings has 5 numbered cards")
check("addColumn" not in raw_s and "wireColumns" not in raw_s,
      "legacy three-column helpers are gone")
check(s.count("wireVerticalFocus()") == 2, "settings declares and calls the vertical focus chain")
check(s.count("wireSubjectRows()") == 2, "subject rows keep their left/right chain")
check("setNextFocusUpId" in s and "setNextFocusDownId" in s,
      "settings vertical chain is explicit across nested cards")
check("root.addView(footer, Ui.fixed(this, 54))" in s,
      "save / change-pin footer stays fixed while the form scrolls")
check(s.count('"保存并返回"') == 1, "exactly one save action")
check(s.count('"检查题库更新"') == 1, "bank update entry lives in settings only")
check("track(urlEdit)" in s, "URL field participates in the focus chain")
check("setMinHeight(Ui.dp(this, 72))" in s,
      "bank status uses min-height instead of a clipping fixed height")
check("Ui.dp(this, GUTTER_DP)" in s, "cards and footer share one centred gutter")
check("new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1f)" in s,
      "row values fill the narrow column by weight")

print("LAYOUT_CONTRACT_OK total=%d" % TOTAL)
