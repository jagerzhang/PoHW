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

    qtypes = collections.Counter(r['qtype'] for r in rows)
    if set(qtypes) != {'knowledge', 'recognition'}:
        fail('qtype must contain exactly knowledge and recognition: %r' % qtypes)
    if qtypes['recognition'] < 5 or qtypes['knowledge'] < 3000:
        fail('qtype distribution looks wrong: %r' % qtypes)
    if (meta.get('qtypeKnowledge') != str(qtypes['knowledge'])
            or meta.get('qtypeRecognition') != str(qtypes['recognition'])):
        fail('meta qtype counters mismatch')

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
    # ---- v1.2.4：练习模式抽题覆盖 ----
    # 复刻 QuizDb.random(grade, subject, allSubjects=false, ..., matchLevel)
    # 的首选池 SQL。练习页的题干与 A/B/C/D 必须能真的取到题库题，且内容合法，
    # 否则用户看到的就是「自由练习题目没有内容」。
    cur = c.cursor()
    combos124 = 0
    empty124 = []
    invalid124 = []
    for g124 in range(1, 7):
        for sub124 in ("数学", "语文", "英语"):
            for level124 in (0, 1, 2, 3):
                for match124 in (False, True):
                    diff124 = (" AND difficulty_level=%d" % level124
                               if match124 and level124 else "")
                    where124 = "WHERE grade<=? AND subject=?" + diff124
                    select124 = ("SELECT topic,text,opt_a,opt_b,opt_c,opt_d,"
                                 "answer_index,explain,subject FROM questions ")
                    sql124 = (select124 + where124 +
                              " LIMIT 1 OFFSET (ABS(RANDOM()) % MAX(1,"
                              "(SELECT COUNT(*) FROM questions " + where124 + ")))")
                    row124 = cur.execute(sql124,
                                         (g124, sub124, g124, sub124)).fetchone()
                    combos124 += 1
                    if row124 is None:
                        empty124.append((g124, sub124, level124, match124))
                        continue
                    text124 = (row124[1] or "").strip()
                    opts124 = [(row124[i] or "").strip() for i in (2, 3, 4, 5)]
                    ai124 = row124[6]
                    if (len(text124) < 3 or any(not o for o in opts124)
                            or len(set(opts124)) < 4 or not 0 <= ai124 <= 3):
                        invalid124.append((g124, sub124, level124, match124))
    if empty124:
        fail("practice pool empty: %r" % empty124[:5])
    if invalid124:
        fail("practice question has blank content: %r" % invalid124[:5])

    print("BANK_QUALITY_OK questions=%d grades=1..6 qtype=%d/%d pools=18 levels=%s "
          "practice_combos=%d"
          % (len(rows), qtypes["knowledge"], qtypes["recognition"], sorted(lv),
             combos124))
    return 0


if __name__ == "__main__":
    sys.exit(main())
