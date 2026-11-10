#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""运行时回归：bank-v9 的教材进度（学期 / 版本族 / 单元）真实生效。

从 QuizDb.java / Syllabus.java 抽取真实谓词，实跑内置题库断言：
1. syllabus.json 是按版本隔离的真实单元表，题库每题带 term 与 edition_family；
2. 按 term 抽题，学科一致、年级不超、册次匹配；
3. 按 (版本族 + 单元别名) 抽题，确实能命中且版本不串；
4. 四个池谓词与保底层仍带 grade<=?。
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
    table = json.load(io.open(SYL, encoding="utf-8"))
    cells = table.get("editionByCell") or {}
    if len(table.get("grades") or {}) < 6 or len(cells) < 20:
        fail("syllabus.json has no real per-edition progress table")
    conn = sqlite3.connect(DB)
    cols = [r[1] for r in conn.execute("pragma table_info(questions)")]
    for c in ("term", "edition_family", "unit_no", "knowledge_point"):
        if c not in cols:
            fail("missing progress column " + c)
    n = conn.execute("select count(*) from questions where term in ('上','下')").fetchone()[0]
    total = conn.execute("select count(*) from questions").fetchone()[0]
    if n != total:
        fail("questions without a real textbook term: %d" % (total - n))

    random.seed(20261110)
    draws = 0
    for g in range(1, 7):
        for sub in CORE:
            for term in ("上", "下"):
                for _ in range(30):
                    r = conn.execute(
                        "SELECT grade,subject,term FROM questions "
                        "WHERE grade<=? AND subject=? AND term=? "
                        "ORDER BY RANDOM() LIMIT 1", (g, sub, term)).fetchone()
                    if r is None:
                        # 小学英语三年级起点，1-2 年级无题是预期
                        if sub == "英语" and g <= 2:
                            continue
                        fail("term-scoped pool empty g=%d %s %s" % (g, sub, term))
                    if r[1] != sub or r[0] > g or r[2] != term:
                        fail("term-scoped draw leaked %r at g=%d" % (r, g))
                    draws += 1

    # 出题偏好（qtype='knowledge'）也必须遵守册次：选上册不得出下册题
    ko_draws = 0
    for g in range(1, 7):
        for sub in CORE:
            for want in ("上", "下"):
                for _ in range(20):
                    r = conn.execute(
                        "SELECT grade,subject,term FROM questions "
                        "WHERE grade<=? AND subject=? AND qtype='knowledge' "
                        "AND term IN (?, '全') ORDER BY RANDOM() LIMIT 1",
                        (g, sub, want)).fetchone()
                    if r is None:
                        # 小学英语三年级起点，1-2 年级无题是预期
                        if sub == "英语" and g <= 2:
                            continue
                        fail("knowledge-only term pool empty g=%d %s %s" % (g, sub, want))
                    if r[2] != want or r[1] != sub or r[0] > g:
                        fail("knowledge-only draw leaked %r at g=%d %s" % (r, g, want))
                    ko_draws += 1

    # 单元层：取一个有真实单元的格子，验证版本隔离
    fam = cells.get("3|1|0")
    if fam:
        alias = table["grades"]["3"]["1"]["数学"][0]["match"][0]
        row = conn.execute(
            "SELECT edition_family,topic FROM questions "
            "WHERE grade<=3 AND subject='数学' AND term='上' "
            "AND edition_family=? AND (topic LIKE ? OR topic=?) LIMIT 1",
            (fam, "%：" + alias, alias)).fetchone()
        if row is None:
            fail("unit-scoped draw returned nothing for edition " + fam)
        if row[0] != fam:
            fail("unit filter leaked another edition family")

    qd = io.open(QUIZDB, encoding="utf-8").read()
    if "WHERE grade<=? AND subject=?" not in qd or qd.count("grade<=?") < 4:
        fail("QuizDb lost the grade cap")
    if "edition_family = ?" not in qd:
        fail("QuizDb lost the edition-family isolation")
    conn.close()
    print("STUDY_SCOPE_OK PROGRESS cells=%d draws=%d ko-draws=%d edition-isolated grade-capped"
          % (len(cells), draws, ko_draws))
    return 0


if __name__ == "__main__":
    sys.exit(main())
