#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""解析 adb uiautomator dump 的 XML，找出可能被裁切的长文本节点。

用法：
    python3 tools/ui_check/analyze_dump.py .build/ui_check/window.xml

输出每个文本节点的边界、字数与风险标记。uiautomator dump 不带字号信息，
所以这里只做「长文本 + 小高度」的启发式筛查；最终判断配合 screencap 截图
（多模态目测）一起做。阈值按 density 自动换算，可用 --density 覆盖。
"""
import argparse
import io
import re
import sys
import xml.etree.ElementTree as ET

BOUNDS = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


def bounds(s):
    m = BOUNDS.match(s or "")
    if not m:
        return None
    x1, y1, x2, y2 = (int(v) for v in m.groups())
    return x2 - x1, y2 - y1, x1, y1


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("xml")
    ap.add_argument("--density", type=float, default=2.0)
    ap.add_argument("--min-chars", type=int, default=12)
    a = ap.parse_args()

    root = ET.parse(a.xml).getroot()
    rows = []
    for node in root.iter("node"):
        text = (node.get("text") or "").strip()
        if not text:
            continue
        b = bounds(node.get("bounds"))
        if not b:
            continue
        w, h, x, y = b
        wdp, hdp = w / a.density, h / a.density
        n = len(text)
        # 粗估：16sp 汉字约 16dp 宽 / 18dp 行高
        per_line = max(1, int(wdp / 16))
        lines = max(1, (n + per_line - 1) // per_line)
        need_dp = lines * 18
        suspect = n >= a.min_chars and need_dp > hdp + 4
        rows.append((suspect, n, hdp, wdp, x, y, text, node.get("class") or ""))

    print("UI_DUMP_NODES text=%d density=%.1f" % (len(rows), a.density))
    print("node  chars  height_dp  width_dp  need_dp  verdict  text")
    bad = 0
    for i, (suspect, n, hdp, wdp, x, y, text, cls) in enumerate(rows, 1):
        per_line = max(1, int(wdp / 16))
        need_dp = max(1, (n + per_line - 1) // per_line) * 18
        verdict = "SUSPECT" if suspect else "ok"
        if suspect:
            bad += 1
        print("%4d %5d %9.1f %9.1f %8d  %-7s %s"
              % (i, n, hdp, wdp, need_dp, verdict, text[:72]))
    print("UI_TEXT_SUSPECTS %d" % bad)
    print("HINT: 把 screen.png 与本报告一起回传，可用截图目测复核 SUSPECT 节点")
    return 0


if __name__ == "__main__":
    sys.exit(main())
