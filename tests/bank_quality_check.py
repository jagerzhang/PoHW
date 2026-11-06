#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""PoHW 内置题库质量门禁（零第三方依赖）。

把 tools/quizbank/rebuild_bank.py 的清洗规则反过来当断言用，防止题库回归：
  1. 每题都有 1–6 年级标签，不再有 grade=-1 的「通用」题；
  2. 四个选项非空且互异、答案下标合法、题干不截断；
  3. 英语题不存在单词粘连，也不含中学课文 / 超小学课标内容；
  4. 低年级（<=3）不出现字母变量 / 代数题；
  5. 无重复题；每个年级的累积池（grade<=g）三科都有足够题量；
  6. meta 与版本戳一致，难度三档齐全。
"""
import collections
import io
import os
import re
import sqlite3
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DB = os.path.join(ROOT, "app/assets/quiz.db")
VER = os.path.join(ROOT, "app/assets/quiz.db.version")
sys.path.insert(0, os.path.join(ROOT, "tools/quizbank"))
import rebuild_bank as R   # noqa: E402


def fail(msg):
    print("BANK_QUALITY_FAIL: " + msg)
    sys.exit(1)


def main():
    if not os.path.isfile(DB):
        fail("missing quiz.db")
    c = sqlite3.connect(DB)
    c.row_factory = sqlite3.Row
    rows = [dict(r) for r in c.execute("SELECT * FROM questions")]
    meta = dict(c.execute("SELECT k,v FROM meta"))
    if not rows:
        fail("empty bank")
    if meta.get("questions") != str(len(rows)):
        fail("meta.questions mismatch")
    if meta.get("grades") != "6":
        fail("meta.grades must be 6 (grades 1..6)")
    if not os.path.isfile(VER):
        fail("missing quiz.db.version")
    if io.open(VER, encoding="utf-8").read().strip() != meta.get("version"):
        fail("version stamp mismatch with meta.version")

    problems = collections.Counter()
    for r in rows:
        g, t = r["grade"], r["text"] or ""
        if not 1 <= g <= 6:
            problems["grade_out_of_1_6"] += 1
        opts = [(r[k] or "").strip() for k in ("opt_a", "opt_b", "opt_c", "opt_d")]
        if any(not o for o in opts) or len(set(opts)) < 4:
            problems["bad_options"] += 1
        if not 0 <= r["answer_index"] <= 3:
            problems["bad_answer_index"] += 1
        if len(t.strip()) < 3:
            problems["short_text"] += 1
        if R.TRUNCATED.search(t) and "(" not in t and "（" not in t:
            problems["truncated_text"] += 1
        if R.OUT_OF_SCOPE.search(t):
            problems["out_of_primary_scope"] += 1
        if (r["subject"] == "数学" and g <= 3
                and re.search(r"[a-zA-Z]\s*[×÷=]\s*[0-9a-zA-Z]", t)):
            problems["algebra_in_low_grade"] += 1
        if r["subject"] == "英语":
            for col in ("text", "opt_a", "opt_b", "opt_c", "opt_d", "explain"):
                for m in R.TOKEN.finditer(r[col] or ""):
                    w = m.group(0)
                    if len(w) < 5:
                        continue
                    if R.split_words(w) or (len(w) >= 8 and not R.is_legal_long(w)):
                        problems["english_glued"] += 1
                        break

    seen = set()
    for r in rows:
        key = (r["subject"], R.norm_text(r["text"]))
        if key in seen:
            problems["duplicate_question"] += 1
        seen.add(key)

    dist = collections.Counter((r["grade"], r["subject"]) for r in rows)
    for g in range(1, 7):
        for sub in ("数学", "语文", "英语"):
            if sum(dist[(k, sub)] for k in range(1, g + 1)) < 10:
                problems["thin_grade_pool"] += 1

    if problems:
        fail(", ".join("%s=%d" % (k, v) for k, v in sorted(problems.items())))

    lv = dict(c.execute("SELECT difficulty_level,COUNT(*) FROM questions "
                        "GROUP BY difficulty_level"))
    if sorted(lv) != [1, 2, 3] or min(lv.values()) < 100:
        fail("difficulty levels incomplete %r" % lv)
    print("BANK_QUALITY_OK questions=%d grades=1..6 pools=18 levels=%s"
          % (len(rows), sorted(lv)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
