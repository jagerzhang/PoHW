#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""PoHW 内置题库质量门禁（零第三方依赖）。

bank-v9 口径（题库按真实教材册次与课标知识点重建，非抓取文本）：
  1. 每题都有 1–6 年级标签、真实 term / textbook / unit_no / edition_family；
  2. 四个选项非空且互异、答案下标合法、题干不截断；
  3. 不含确证中学内容（旧 OUT_OF_SCOPE 的「复数 / 弧长 / 论据 / 鲁迅」等词
     对语法复数、六上圆、六下语文课文误报，故换成精简规则）；
  4. 不再做「低年级字母变量」与「英语单词粘连」检查：新库年级来自教材目录，
     文本为结构化生成，不存在旧库「代数等式错标三年级 / 抓取丢空格」的问题；
  5. 同一 (教材, 单元) 内无重复题；跨教材同题是真实存在的，按版本隔离后不会串；
  6. meta 与版本戳一致，难度三档齐全；小学英语三年级起点，1–2 年级英语允许空池。
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

# 只保留确证的中学内容；旧规则里的「复数 / 弧长 / 论证 / 论据 / 鲁迅 /
# 概率 / 数阵」会把小学英语语法复数、六上圆的弧长、六下议论文、课文与
# 五年级可能性题全部误判，因此 bank-v9 换用这份精简白名单。
V9_OUT_OF_SCOPE = re.compile(
    r'一次函数|二次函数|不等式|方程组|勾股定理|相似三角形|全等三角形|'
    r'无理数|绝对值|开平方|求导|微积分|等差数列|等比数列|不定方程|向量|矩阵|'
    r'三角函数|\bsin\b|\bcos\b|圆心距|中位数|众数|'
    r'排列组合|容斥原理|追及问题|相遇问题|工程问题', re.I)


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
        if V9_OUT_OF_SCOPE.search(t):
            problems["out_of_primary_scope"] += 1
        # 低年级字母（点 A/B/C）与结构化英文长词不再是质量问题，见文件头说明。
        if (r.get("edition_family") or "") == "" or not (r.get("unit_no") or 0):
            problems["missing_edition_binding"] += 1

    qtypes = collections.Counter(r['qtype'] for r in rows)
    if qtypes['knowledge'] < 9000 or set(qtypes) - {'knowledge', 'recognition'}:
        fail('qtype distribution looks wrong: %r' % qtypes)
    if (meta.get('qtypeKnowledge') != str(qtypes['knowledge'])
            or meta.get('qtypeRecognition') != str(qtypes['recognition'])):
        fail('meta qtype counters mismatch')

    seen = set()
    for r in rows:
        # 同一 (教材, 单元) 内、题干与四个选项完全一致才算重复。
        # AI 生成题常复用题干模板但换选项（如 "Which word doesn't belong?"），
        # 那些是有效题；跨教材同题按版本隔离，也属真实存在。
        key = (r["subject"], R.norm_text(r["text"]),
               R.norm_text(r["opt_a"]), R.norm_text(r["opt_b"]),
               R.norm_text(r["opt_c"]), R.norm_text(r["opt_d"]),
               r.get("textbook"), r.get("unit_no"))
        if key in seen:
            problems["duplicate_question"] += 1
        seen.add(key)

    dist = collections.Counter((r["grade"], r["subject"]) for r in rows)
    for g in range(1, 7):
        for sub in ("数学", "语文", "英语"):
            n = sum(dist[(k, sub)] for k in range(1, g + 1))
            # 小学英语三年级起点，1-2 年级无英语题是题库的真实边界
            if sub == "英语" and g <= 2 and n == 0:
                continue
            if n < 10:
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
                        if sub124 == "英语" and g124 <= 2:
                            continue
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
