#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成 / 校验 app/assets/syllabus.json 的「已停用」空表。

背景（2026-11-10 数据核查）：题库无法按教材隔离 —— 无出版社版本字段、
questions.term 全为空、无单元序号；按册次别名硬分时，数学 2351 道里
1496 道（63.6%）无册次归属、248 道同时命中多册，6 年级上下册交集 215 道、
能唯一归属的仅 3 道。因此教材版本 / 上下学期 / 已学单元概念已移除，
本表保留为空表只是为兼容旧版 assets 读取，运行时不再参与出题。

用法：
  python3 tools/quizbank/build_syllabus.py            # 写出停用空表
  python3 tools/quizbank/build_syllabus.py --check   # 构建门禁：必须是停用空表
"""
import io
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "app/assets/syllabus.json")

DATA = {
    "version": "2.0",
    "scope": "已停用：题库按年级 + 学科 + 课标纲目打平，不按教材版本 / 学期 / 单元出题",
    "note": (
        "实测题库无出版社版本字段、term 全为空、无单元序号；按册次别名硬分时数学题"
        "63.6% 无册次归属、248 道同时命中多册，无法准确隔离，故移除该概念。"
        "本文件保留为空表仅为兼容旧版 assets 读取，运行时不加载、不参与抽题。"
    ),
    "subjects": [],
    "grades": {},
}


def fail(msg):
    print("SYLLABUS_FAIL: " + msg)
    sys.exit(1)


def main():
    check_only = "--check" in sys.argv[1:]
    if check_only:
        if not os.path.isfile(OUT):
            fail("missing " + OUT)
        on_disk = json.load(io.open(OUT, encoding="utf-8"))
        if on_disk != DATA:
            fail("app/assets/syllabus.json is not the flattened empty table; "
                 "run build_syllabus.py to regenerate")
        if on_disk.get("grades"):
            fail("textbook / term / unit books must stay empty")
        print("SYLLABUS_CHECK_OK flattened=1 books=0")
    else:
        with io.open(OUT, "w", encoding="utf-8") as f:
            json.dump(DATA, f, ensure_ascii=False, indent=2)
            f.write("\n")
        print("SYLLABUS_OK wrote %s flattened=1 books=0" % OUT)


if __name__ == "__main__":
    main()
