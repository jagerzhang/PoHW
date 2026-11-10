#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""运行时回归：题库已打平，抽题只按 年级 + 学科，不再有学期 / 单元 / 教材版本过滤。

从 QuizDb.java 抽取真实池谓词并实跑内置题库，断言：
1. syllabus.json 是停用空表，题库 term 全空且无教材版本列；
2. 按 grade<=g AND subject=? 实抽，学科 100% 一致且永不超年级上限；
3. QuizDb 的四个池谓词与保底层仍带 grade<=?（防止打平改坏年级防超纲）。
零第三方依赖，由 app/build.sh 的 [0/9] 调用。
"""
import io
import json
import os
import random
import sqlite3
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DB = os.path.join(ROOT, "app/assets/quiz.db")
SYL = os.path.join(ROOT, "app/assets/syllabus.json")
QUIZDB = os.path.join(ROOT, "app/src/com/jager/tvquiz/QuizDb.java")
CORE = ("数学", "语文", "英语")


def fail(msg):
    print("STUDY_SCOPE_FAIL: " + msg)
    sys.exit(1)


def main():
    if not os.path.isfile(DB) or not os.path.isfile(SYL):
        fail("missing quiz.db or syllabus.json")
    table = json.load(io.open(SYL, encoding="utf-8"))
    if table.get("grades") or "已停用" not in table.get("scope", ""):
        fail("syllabus.json is not the flattened empty table")

    conn = sqlite3.connect(DB)
    cols = [r[1] for r in conn.execute("pragma table_info(questions)")]
    if any(c in cols for c in ("edition", "book", "press", "textbook", "unit")):
        fail("textbook / unit columns are back in the shipped bank")
    tagged = conn.execute("SELECT COUNT(*) FROM questions WHERE term IS NOT NULL "
                          "AND term <> ?", ("",)).fetchone()[0]
    if tagged:
        fail("unexpected term-tagged rows %d" % tagged)

    random.seed(20261110)
    draws = 0
    for g in range(1, 7):
        for sub in CORE:
            for _ in range(60):
                r = conn.execute(
                    "SELECT grade,subject FROM questions WHERE grade<=? AND subject=? "
                    "ORDER BY RANDOM() LIMIT 1", (g, sub)).fetchone()
                if r is None or r[1] != sub or r[0] > g:
                    fail("flattened draw leaked grade=%s subject=%s at g%d"
                         % (r[0] if r else None, r[1] if r else None, g))
                draws += 1
    qd = io.open(QUIZDB, encoding="utf-8").read()
    if "WHERE grade<=? AND subject=?" not in qd or qd.count("grade<=?") < 4:
        fail("QuizDb lost the grade cap on its pools")
    conn.close()
    print("STUDY_SCOPE_OK FLATTENED books=0 draws=%d grade-capped subject-safe" % draws)
    return 0


if __name__ == "__main__":
    sys.exit(main())
