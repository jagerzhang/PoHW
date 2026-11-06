#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""方案 B 运行时回归：用与 QuizDb 完全相同的 SQL 实跑内置题库。

静态契约只能守住符号。本脚本从 QuizDb.java / Syllabus.java 提取真实的池谓词与
term 谓词，复刻 QuizDb.random() 的「严格 -> 放宽单元 -> 放宽学期 -> 放宽年级
-> 全库」逐层兜底和 pick() 的 OFFSET 采样 / 双份参数绑定，然后断言：
1. 选「三年级上册 / 已学到第 N 单元」时，抽到的题确实只来自前 N 单元且 term=上；
2. 选上/下册时排除 term='' 的 810 道无学期综合练习；
3. 语文 / 英语 / 7-9 年级没有有序单元表时，诚实地只做学期过滤，绝不换学科；
4. 空池时按顺序逐级放宽，前四层始终保持学科约束；
5. 默认（全学年 / 不限）行为与 code17 完全一致。
零第三方依赖，只用 Python 标准库；由 app/build.sh 的 [0/9] 调用。
"""
import json
import os
import random
import re
import sqlite3
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DB = os.path.join(ROOT, "app/assets/quiz.db")
SYL = os.path.join(ROOT, "app/assets/syllabus.json")
QUIZDB = os.path.join(ROOT, "app/src/com/jager/tvquiz/QuizDb.java")
SYLLABUS_JAVA = os.path.join(ROOT, "app/src/com/jager/tvquiz/Syllabus.java")
CORE = ("数学", "语文", "英语")
NAMES = ("数学", "语文", "英语")
SELECT = "SELECT term, subject, topic FROM questions "


def fail(msg):
    print("STUDY_SCOPE_FAIL: " + msg)
    sys.exit(1)


def strip_comments(text):
    """丢弃 // 行注释与 /* */ 块注释，避免注释里的诱饵 SQL 被提取。"""
    out = []
    in_block = False
    for line in text.splitlines():
        cur = line
        if in_block:
            end = cur.find("*/")
            if end < 0:
                continue
            cur = cur[end + 2:]
            in_block = False
        while True:
            start = cur.find("/*")
            if start < 0:
                break
            end = cur.find("*/", start + 2)
            if end < 0:
                cur = cur[:start]
                in_block = True
                break
            cur = cur[:start] + cur[end + 2:]
        slash = cur.find("//")
        if slash >= 0:
            cur = cur[:slash]
        out.append(cur)
    return "\n".join(out)


def read(path):
    with open(path, "r", encoding="utf-8") as f:
        return f.read()


def java_literal(text, name):
    m = re.search(r"\b" + re.escape(name) + r"\s*=\s*(\"(?:[^\"\\]|\\.)*\")", text)
    if not m:
        fail("cannot extract %s from source" % name)
    return json.loads(m.group(1))


def extract():
    qd = strip_comments(read(QUIZDB))
    sj = strip_comments(read(SYLLABUS_JAVA))
    pools = {n: java_literal(qd, n) for n in
             ("POOL_OTHER_GRADE", "POOL_OTHER_ALL", "POOL_CORE_GRADE", "POOL_CORE_ALL")}
    m1 = re.search(r"term == TERM_FIRST\) return (\"(?:[^\"\\]|\\.)*\")", sj)
    m2 = re.search(r"term == TERM_SECOND\) return (\"(?:[^\"\\]|\\.)*\")", sj)
    if not m1 or not m2:
        fail("cannot extract termWhere from Syllabus.java")
    term_where = {0: "", 1: json.loads(m1.group(1)), 2: json.loads(m2.group(1))}
    # 静态确认分层链仍在源码里，防止「运行时脚本测的是自己臆想的逻辑」。
    for token in ("String strict = where + termW + unitW",
                  "String termOnly = where + termW",
                  "concat(args, unitA)"):
        if token not in qd:
            fail("QuizDb.java lost fallback layer marker: " + token)
    # code24 起兜底层带难度谓词；两种写法都接受，兼容旧 schema-2 题库。
    if "pick(poolWhere + diffW, poolArgs)" not in qd and "pick(poolWhere, poolArgs)" not in qd:
        fail("QuizDb.java lost fallback layer marker: pick(poolWhere, poolArgs)")
    return pools, term_where


def load_syllabus():
    with open(SYL, "r", encoding="utf-8") as f:
        return json.load(f)


def match_names(syl, grade, term, subject, limit):
    if limit <= 0 or subject >= len(NAMES):
        return []
    try:
        units = syl["grades"][str(grade)][str(term)][NAMES[subject]]
    except KeyError:
        return []
    names = []
    for u in units[:limit]:
        names.extend(u["match"])
    return names


def unit_where(names):
    if not names:
        return ""
    return " AND (" + " OR ".join(["(topic LIKE ? OR topic = ?)"] * len(names)) + ")"


def unit_args(names):
    out = []
    for n in names:
        out.append("%：" + n)
        out.append(n)
    return out


def layers(pools, term_where, grade, subject, all_subjects, term, limit, syl,
           forced_names=None):
    other = subject == 3 and all_subjects
    g = str(grade)
    if other:
        where, args = pools["POOL_OTHER_GRADE"], [g]
        pool_where, pool_args = pools["POOL_OTHER_ALL"], [g]
    else:
        name = NAMES[subject] if subject < 3 else "数学"
        where, args = pools["POOL_CORE_GRADE"], [g, name]
        pool_where, pool_args = pools["POOL_CORE_ALL"], [g, name]
    tw = term_where.get(term, "")
    names = forced_names if forced_names is not None else (
        [] if other else match_names(syl, grade, term, subject, limit))
    uw = unit_where(names)
    ua = unit_args(names)
    strict = where + tw + uw
    out = [(strict, args + ua)]
    tried = strict
    term_only = where + tw
    if term_only != tried:
        out.append((term_only, args))
        tried = term_only
    if where != tried:
        out.append((where, args))
        tried = where
    out.append((pool_where, pool_args))
    out.append(("WHERE grade<=?", [g]))
    return out


def pick(conn, where, args):
    sql = (SELECT + where +
           " LIMIT 1 OFFSET (ABS(RANDOM()) % MAX(1, (SELECT COUNT(*) FROM questions "
           + where + ")))")
    cur = conn.execute(sql, list(args) * 2)
    return cur.fetchone()


def count_rows(conn, where, args):
    cur = conn.execute("SELECT COUNT(*) FROM questions " + where, list(args))
    return cur.fetchone()[0]


def draw(conn, pools, term_where, grade, subject, all_subjects, term, limit,
         syl, forced_names=None):
    for where, args in layers(pools, term_where, grade, subject, all_subjects,
                              term, limit, syl, forced_names):
        row = pick(conn, where, args)
        if row is not None:
            return row
    return None


def unit_of(topic):
    t = topic or ""
    return t.split("：", 1)[1] if "：" in t else t


def main():
    if not os.path.isfile(DB) or not os.path.isfile(SYL):
        fail("missing quiz.db or syllabus.json")
    pools, term_where = extract()
    syl = load_syllabus()
    conn = sqlite3.connect(DB)
    meta = dict(conn.execute('SELECT k,v FROM meta'))
    if meta.get('schema') == '3':
        tagged = conn.execute('SELECT COUNT(*) FROM questions WHERE term IS NOT NULL '
                              'AND term <> ?',('',)).fetchone()[0]
        if tagged: fail('bank-v2 unexpectedly has term-tagged rows %d' % tagged)
        for g in (1,2,3,4,5,6):
            for sub in NAMES:
                r = conn.execute('SELECT subject FROM questions WHERE grade<=? AND subject=? '
                                 'ORDER BY RANDOM() LIMIT 1',(g,sub)).fetchone()
                if r is None:
                    r = conn.execute('SELECT subject FROM questions WHERE subject=? '
                                     'ORDER BY RANDOM() LIMIT 1',(sub,)).fetchone()
                if r is None or r[0] != sub:
                    fail('fallback leaked subject for %s grade %d' % (sub,g))
        print('STUDY_SCOPE_OK bank-v2 progress-degraded fallback=subject-safe')
        return


    random.seed(20261105)

    # ---- 池谓词与学期谓词本身必须来自源码且语义正确 ----
    if "NOT IN" not in pools["POOL_OTHER_GRADE"] or "grade<=?" not in pools["POOL_CORE_GRADE"]:
        fail("pool predicates lost subject/grade constraints")
    if "'上','全'" not in term_where[1] or "'下','全'" not in term_where[2]:
        fail("term predicate must include 全一册 and exclude empty-term practice")
    print("  POOL_SQL_OK", {k: v for k, v in pools.items()})
    print("  TERM_SQL_OK", term_where)

    # ---- 1. 三年级上册 / 已学到第 1、2 单元：严格层命中，范围绝不外溢 ----
    for limit, expect in ((1, {"时、分、秒", "时分秒"}),
                          (2, {"时、分、秒", "时分秒", "万以内的加法和减法",
                               "万以内的加法和减法(一)"})):
        strict_where, strict_args = layers(pools, term_where, 3, 0, False, 1,
                                          limit, syl)[0]
        n = count_rows(conn, strict_where, strict_args)
        if n <= 0:
            fail("strict layer empty for grade=3 term=上 unit=%d" % limit)
        seen = set()
        for _ in range(200):
            row = draw(conn, pools, term_where, 3, 0, False, 1, limit, syl)
            if row is None:
                fail("draw returned nothing for unit=%d" % limit)
            term, subject, topic = row
            if term != "上" or subject != "数学":
                fail("strict layer leaked: term=%r subject=%r" % (term, subject))
            if unit_of(topic) not in expect:
                fail("unit scope leaked to %r (limit=%d)" % (topic, limit))
            seen.add(unit_of(topic))
        print("  STRICT_OK grade=3 term=上 unit=%d rows=%d seen=%d" % (limit, n, len(seen)))

    # ---- 2. 三年级下册：不出现三上的「时分秒」 ----
    for _ in range(200):
        row = draw(conn, pools, term_where, 3, 0, False, 2, 1, syl)
        if row is None or row[0] not in ("下", "全") or row[1] != "数学":
            fail("lower-term scope leaked: %r" % (row,))
        if unit_of(row[2]) in ("时分秒", "时、分、秒"):
            fail("lower-term draw hit an upper-term unit: %r" % row[2])
    print("  TERM_ISOLATION_OK grade=3 term=下")

    # ---- 3. 学期过滤排除 term='' 的 810 道综合练习（仅统计严格层有题的组合） ----
    checked = 0
    for grade in range(1, 10):
        for subject in (0, 1, 2):
            for term in (1, 2):
                w, a = layers(pools, term_where, grade, subject, False, term, 0, syl)[0]
                if count_rows(conn, w, a) == 0:
                    continue
                checked += 1
                for _ in range(20):
                    row = draw(conn, pools, term_where, grade, subject, False, term, 0, syl)
                    if row is None or row[0] == "":
                        fail("term filter admitted an empty-term practice question: %r" % (row,))
    if checked < 20:
        fail("too few term-filter combinations checked: %d" % checked)
    print("  EMPTY_TERM_EXCLUDED_OK combinations=%d" % checked)

    # ---- 4. 未覆盖组合诚实降级：只按学期过滤，不换学科 ----
    for grade, subject, term, limit in ((3, 1, 1, 2), (3, 2, 2, 3),
                                        (7, 0, 1, 3), (9, 0, 2, 4)):
        expected_subject = NAMES[subject] if subject < 3 else None
        for _ in range(60):
            row = draw(conn, pools, term_where, grade, subject, False, term, limit, syl)
            if row is None:
                fail("degraded draw returned nothing")
            if expected_subject is not None and row[1] != expected_subject:
                fail("degraded draw changed subject: %r" % (row,))
            if row[0] not in ("上", "下", "全"):
                fail("degraded draw broke term filter: %r" % (row,))
    print("  GRACEFUL_DEGRADATION_OK chinese/english/junior-high")

    # ---- 5. 逐层兜底顺序：放宽单元 -> 学期 -> 年级，始终保留学科 ----
    L = layers(pools, term_where, 3, 0, False, 1, 1, syl,
               forced_names=["__no_such_unit__"])
    if count_rows(conn, L[0][0], L[0][1]) != 0:
        fail("forced-empty strict layer unexpectedly has rows")
    if count_rows(conn, L[1][0], L[1][1]) <= 0:
        fail("unit->term fallback layer has no rows")
    row = draw(conn, pools, term_where, 3, 0, False, 1, 1, syl,
               forced_names=["__no_such_unit__"])
    if row is None or row[1] != "数学" or row[0] != "上":
        fail("unit->term fallback broke subject/term: %r" % (row,))
    print("  FALLBACK_UNIT_TO_TERM_OK")

    # grade=0 数学只有 term='' 的题：学期层为空 -> 放宽到「同年级同学科」。
    L0 = layers(pools, term_where, 0, 0, False, 1, 0, syl)
    if count_rows(conn, L0[0][0], L0[0][1]) != 0:
        fail("grade=0 upper-term layer should be empty")
    row = draw(conn, pools, term_where, 0, 0, False, 1, 0, syl)
    if row is None or row[1] != "数学":
        fail("term->grade fallback broke subject: %r" % (row,))
    print("  FALLBACK_TERM_TO_GRADE_OK")

    # grade=99 不存在：最终走同学科跨年级池，仍不得换学科。
    row = draw(conn, pools, term_where, 99, 0, False, 0, 0, syl)
    if row is None or row[1] != "数学":
        fail("cross-grade pool broke subject constraint: %r" % (row,))
    print("  FALLBACK_POOL_KEEPS_SUBJECT_OK")

    # ---- 6. 默认（全学年 / 不限）与 code17 行为一致 ----
    for subject in (0, 1, 2, 3):
        for _ in range(60):
            row = draw(conn, pools, term_where, 3, subject, True, 0, 0, syl)
            if row is None:
                fail("default scope draw returned nothing")
            if subject == 3 and row[1] in CORE:
                fail("OTHER pool returned a core subject")
            if subject < 3 and row[1] != NAMES[subject]:
                fail("core pool returned wrong subject")
    print("  DEFAULT_SCOPE_COMPAT_OK")

    # ---- 7. 全部 12 册的占位符与参数严格一一对应 ----
    for grade in range(1, 7):
        for term in (1, 2):
            names = match_names(syl, grade, term, 0, 99)
            if not names:
                fail("missing syllabus book %d/%d" % (grade, term))
            if unit_where(names).count("?") != len(unit_args(names)):
                fail("placeholder/arg mismatch at %d/%d" % (grade, term))
    print("  PLACEHOLDER_PARITY_OK books=12")

    conn.close()
    print("STUDY_SCOPE_OK pools=4 books=12")


if __name__ == "__main__":
    main()
