#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""方案 B 运行时回归：用 QuizDb.java 里的真实 SQL 实跑内置题库。

静态契约只能守住符号；这里从 QuizDb.java 提取 WHERE 谓词（不是另写一份复刻），
用与 QuizDb.pick() 相同的 OFFSET 采样和参数绑定验证行为：
1. 三科池只返回对应学科、其他池只返回非三科题目，
   因此 QuizDb.subjectFromBank() 反推出的标签与题目真实学科 100% 一致；
2. 四槽配比 5:3:2:2 下，题目真实学科分布接近 41.7/25/16.7/16.7，
   不再像旧 gradeWide 那样由题库题量决定；
3. 全学科关闭（第 4 槽为 0）时绝不出现其他学科题。
零第三方依赖，只用 Python 标准库；由 build.sh 的 [0/9] 调用。
"""
import io
import os
import random
import re
import sqlite3
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DB = os.path.join(ROOT, "app/assets/quiz.db")
QUIZDB = os.path.join(ROOT, "app/src/com/jager/tvquiz/QuizDb.java")
CORE = ("数学", "语文", "英语")
SELECT = "SELECT subject FROM questions "
GRADE_WHERE = "WHERE grade=?"


def fail(msg):
    print("BANK_DISTRIBUTION_FAIL: " + msg)
    sys.exit(1)


def extract_sql():
    """SQL 谓词必须与 Java 源码同源，否则改坏 QuizDb 也测不出来。"""
    src = io.open(QUIZDB, encoding="utf-8").read()
    other = re.search(r'where = "(WHERE grade=\? AND subject NOT IN \([^"]+\))";', src)
    core = re.search(r'where = "(WHERE grade=\? AND subject=\?)";', src)
    if not other:
        fail("QuizDb.java lost the other-subject SQL predicate (NOT IN)")
    if not core:
        fail("QuizDb.java lost the core-subject SQL predicate (grade + subject)")
    if "gradeWide" in src:
        fail("QuizDb.java regressed to gradeWide cross-subject sampling")
    return core.group(1), other.group(1)


CORE_WHERE, OTHER_WHERE = extract_sql()


def pool_sql(pool, grade):
    if pool == 3:
        return OTHER_WHERE, (str(grade),)
    return CORE_WHERE, (str(grade), CORE[pool])


def draw(conn, where, args):
    """复刻 QuizDb.pick()：主查询与 COUNT 子查询各绑一次参数，再同年级兜底。"""
    bind = args * 2
    sql = SELECT + where + \
        " LIMIT 1 OFFSET (ABS(RANDOM()) % (SELECT COUNT(*) FROM questions " + where + "))"
    row = conn.execute(sql, bind).fetchone()
    if row is None:
        g = (args[0],)
        row = conn.execute(
            SELECT + GRADE_WHERE +
            " LIMIT 1 OFFSET (ABS(RANDOM()) % (SELECT COUNT(*) FROM questions " +
            GRADE_WHERE + "))", g * 2).fetchone()
    if row is None:
        row = conn.execute(
            SELECT + " LIMIT 1 OFFSET (ABS(RANDOM()) % (SELECT COUNT(*) FROM questions))"
        ).fetchone()
    return None if row is None else row[0]


def label_of(bank_subject):
    """与 QuizDb.subjectFromBank() 的映射一致。"""
    for i, name in enumerate(CORE):
        if bank_subject == name:
            return i
    return 3


def roulette(weights, r):
    n = r.randrange(sum(weights))
    for i, w in enumerate(weights):
        n -= w
        if n < 0:
            return i
    return 2


def main():
    if not os.path.isfile(DB):
        fail("missing " + DB)
    conn = sqlite3.connect(DB)
    grades = [r[0] for r in conn.execute(
        "SELECT DISTINCT grade FROM questions WHERE grade BETWEEN 1 AND 9 ORDER BY grade")]
    if len(grades) != 9:
        fail("expected grades 1..9, got %r" % (grades,))

    # 1. 每个年级 x 每个池各抽 60 次，验证 SQL 隔离与标签一致性
    total = 0
    for g in grades:
        for pool in range(4):
            where, args = pool_sql(pool, g)
            for _ in range(60):
                bank_subject = draw(conn, where, args)
                if bank_subject is None:
                    fail("no question for grade=%d pool=%d" % (g, pool))
                lab = label_of(bank_subject)
                if lab != pool:
                    fail("pool=%d returned %s (label=%d) at grade=%d; "
                         "subject label would be wrong" % (pool, bank_subject, lab, g))
                total += 1
    print("POOL_ISOLATION_OK grades=9 pools=4 draws=%d" % total)

    # 2. 四槽配比下的题目真实学科分布（不是标签分布）
    r = random.Random(20261105)
    n = 5000
    for g in (3, 9):
        counts = [0, 0, 0, 0]
        for _ in range(n):
            pool = roulette((5, 3, 2, 2), r)
            where, args = pool_sql(pool, g)
            counts[label_of(draw(conn, where, args))] += 1
        pct = [100.0 * c / n for c in counts]
        expect = (500.0 / 12, 300.0 / 12, 200.0 / 12, 200.0 / 12)
        for i in range(4):
            if abs(pct[i] - expect[i]) > 2.5:
                fail("grade=%d subject=%d got %.2f%% expect %.2f%%" % (
                    g, i, pct[i], expect[i]))
        print("DISTRIBUTION_OK grade=%d math/chinese/english/other = "
              "%.2f/%.2f/%.2f/%.2f%% (expect 41.67/25.00/16.67/16.67)"
              % (g, pct[0], pct[1], pct[2], pct[3]))

    # 3. 全学科关闭：第 4 槽为 0，其他学科题必须完全消失
    counts = [0, 0, 0, 0]
    for _ in range(n):
        pool = roulette((5, 3, 2), r)
        where, args = pool_sql(pool, 9)
        counts[label_of(draw(conn, where, args))] += 1
    if counts[3] != 0:
        fail("allSubjects=false still produced %d other-subject questions" % counts[3])
    print("CORE_ONLY_OK other=0 draws=%d" % n)

    print("BANK_DISTRIBUTION_OK")


if __name__ == "__main__":
    main()
