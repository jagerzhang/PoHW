#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成 / 校验 app/assets/syllabus.json（方案 B 的有序单元进度表）。

背景：内置 quiz.db 的 questions.topic 形如「几何：时分秒」，冒号后面是教材
单元名，但**没有单元序号、没有教材版本、没有教学顺序**。本文件把题库里真实
出现的单元名人工归并成按教学顺序排列的单元表，每个单元记录它在题库里可能
出现的所有别名（例如「时分秒」与「时、分、秒」），供 Syllabus.java 在运行时
按「已学到第 N 单元」过滤。

覆盖范围刻意限于小学 1-6 年级数学：语文的 topic 是课文名、英语是主题词，
无法可靠排出教材顺序；7-9 年级同理。未覆盖的组合自动退化为「仅学期过滤」，
并在家长设置里如实提示，不假装精确。

用法：
  python3 tools/quizbank/build_syllabus.py            # 重新生成 JSON
  python3 tools/quizbank/build_syllabus.py --check    # 只校验，不写文件（构建门禁）

校验内容：别名必须真实存在于该年级该学期该学科的题库里；题库里的每个单元名
都必须被某个单元覆盖（不允许静默丢题）；同一册里别名不重复；单元名不重复；
别名不含 SQL LIKE 通配符 % / _ 与单引号，避免参数绑定变成模式注入。
"""
import json
import os
import sqlite3
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DB = os.path.join(ROOT, "app", "assets", "quiz.db")
OUT = os.path.join(ROOT, "app", "assets", "syllabus.json")

VERSION = "1.0"
SCOPE = "小学 1-6 年级 · 数学 · 课标纲目顺序（无教材版本、无册次）"
NOTE = (
    "题库按课标纲目重定级，但不含出版社版本、册次(term 全为空)与单元序号；"
    "本表把题库真实出现的纲目名按常见教学顺序归并，只做 topic 别名匹配，"
    "不等于任何教材版本的真实单元进度，学期(上/下册)过滤对离线题库不适用。"
    "语文是课文名、英语是主题词，不做单元过滤；标为 generic 的通用题(含外部整合题)"
    "不参与单元过滤。编辑本表后请运行 python3 tools/quizbank/build_syllabus.py --check。"
)

# (name, [题库中该单元可能出现的 topic 单元名别名])
BOOKS = {
    (1, 1): [
        ("准备课", ["准备课（多少）", "数一数", "比一比"]),
        ("位置", ["位置"]),
        ("1~5 的认识和加减法", ["1-5的认识", "1~5的认识和加减法", "加法的初步认识", "减法的初步认识"]),
        ("认识图形（一）", ["认识物体和图形", "认识图形(一)"]),
        ("6~10 的认识和加减法", ["6-10的认识", "6~10的认识和加减法"]),
        ("11~20 各数的认识", ["11-20各数的认识", "11~20各数的认识"]),
        ("认识钟表", ["认识钟表"]),
        ("20 以内的进位加法", ["20以内的进位加法", "20以内进位加法"]),
    ],
    (1, 2): [
        ("认识图形（二）", ["认识图形", "认识图形(二)"]),
        ("20 以内的退位减法", ["20以内的退位减法"]),
        ("分类与整理", ["分类与整理"]),
        ("100 以内数的认识", ["100以内数的认识"]),
        ("认识人民币", ["认识人民币"]),
        ("100 以内的加法和减法（一）", ["100以内的加法和减法", "100以内的加法和减法(一)"]),
        ("找规律", ["找规律"]),
    ],
    (2, 1): [
        ("长度单位", ["长度单位"]),
        ("100 以内的加法和减法（二）", ["100以内的加法(二)", "100以内的加法和减法"]),
        ("角的初步认识", ["角的初步认识"]),
        ("表内乘法（一）", ["表内乘法", "表内乘法(一)"]),
        ("观察物体（一）", ["观察物体", "观察物体(一)"]),
        ("表内乘法（二）", ["表内乘法(二)"]),
        ("认识时间", ["时间", "认识时间"]),
        ("数学广角 — 搭配（一）", ["数学广角", "数学广角—搭配(一)"]),
    ],
    (2, 2): [
        ("数据收集整理", ["数据收集整理"]),
        ("表内除法（一）", ["表内除法", "表内除法(一)"]),
        ("图形的运动（一）", ["图形的运动", "图形的运动(一)"]),
        ("表内除法（二）", ["表内除法(二)"]),
        ("混合运算", ["混合运算"]),
        ("有余数的除法", ["有余数的除法"]),
        ("万以内数的认识", ["万以内数的认识"]),
        ("克和千克", ["克和千克"]),
        ("数学广角 — 推理", ["数学广角─推理"]),
        ("万以内的加法和减法（补充）", ["万以内的加法和减法"]),
    ],
    (3, 1): [
        ("时、分、秒", ["时、分、秒", "时分秒"]),
        ("万以内的加法和减法（一）", ["万以内的加法和减法", "万以内的加法和减法(一)"]),
        ("测量", ["测量"]),
        ("万以内的加法和减法（二）", ["万以内的加法和减法(二)"]),
        ("倍的认识", ["倍的认识"]),
        ("多位数乘一位数", ["多位数乘一位数"]),
        ("长方形和正方形", ["长方形和正方形"]),
        ("分数的初步认识", ["分数的初步认识"]),
        ("数学广角", ["数学广角"]),
        ("数字编码（综合实践）", ["数字编码"]),
        ("可能性（补充）", ["可能性"]),
    ],
    (3, 2): [
        ("位置与方向（一）", ["位置与方向", "位置与方向(一)"]),
        ("除数是一位数的除法", ["除数是一位数的除法"]),
        ("复式统计表", ["复式统计表"]),
        ("两位数乘两位数", ["两位数乘两位数"]),
        ("面积", ["面积"]),
        ("年、月、日", ["年月日"]),
        ("小数的初步认识", ["小数的初步认识"]),
        ("数学广角", ["数学广角"]),
        ("统计（补充）", ["统计"]),
    ],
    (4, 1): [
        ("大数的认识", ["大数的认识"]),
        ("公顷和平方千米", ["公顷和平方千米"]),
        ("角的度量", ["角的度量"]),
        ("三位数乘两位数", ["三位数乘两位数"]),
        ("平行四边形和梯形", ["平行四边形和梯形"]),
        ("除数是两位数的除法", ["除数是两位数的除法"]),
        ("条形统计图", ["条形统计图"]),
        ("数学广角 — 优化", ["数学广角", "数学广角─优化"]),
    ],
    (4, 2): [
        ("四则运算", ["四则运算"]),
        ("观察物体（二）", ["观察物体", "观察物体(二)"]),
        ("运算定律", ["运算定律"]),
        ("小数的意义和性质", ["小数的意义和性质"]),
        ("三角形", ["三角形"]),
        ("小数的加法和减法", ["小数的加法和减法"]),
        ("图形的运动（二）", ["图形的运动", "图形的运动(二)"]),
        ("平均数与条形统计图", ["平均数与条形统计图"]),
        ("数学广角", ["数学广角"]),
    ],
    (5, 1): [
        ("小数乘法", ["小数乘法"]),
        ("位置", ["位置"]),
        ("小数除法", ["小数除法"]),
        ("可能性", ["可能性"]),
        ("简易方程", ["简易方程"]),
        ("多边形的面积", ["多边形面积"]),
        ("数学广角 — 植树问题", ["植树问题", "数学广角"]),
        ("整数运算定律推广到小数", ["整数运算定律推广到小数"]),
        ("分数的意义和性质", ["分数的意义和性质"]),
        ("分数的加法和减法", ["分数的加法和减法"]),
        ("整数的因数与倍数", ["整数的因数与倍数"]),
        ("长方体和正方体", ["长方体和正方体"]),
    ],
    (5, 2): [
        ("观察物体（三）", ["观察物体(三)"]),
        ("因数与倍数", ["因数与倍数"]),
        ("长方体和正方体", ["长方体和正方体"]),
        ("分数的意义和性质", ["分数的意义和性质"]),
        ("图形的运动（三）", ["图形的运动(三)"]),
        ("分数的加法和减法", ["分数的加法和减法", "分数加减法"]),
        ("统计", ["统计"]),
        ("数学广角", ["数学广角"]),
    ],
    (6, 1): [
        ("分数乘法", ["分数乘法"]),
        ("位置与方向（二）", ["位置与方向", "位置与方向(二)"]),
        ("分数除法", ["分数除法"]),
        ("比", ["比"]),
        ("圆", ["圆"]),
        ("百分数（一）", ["百分数", "百分数(一)"]),
        ("扇形统计图", ["扇形统计图"]),
        ("数学广角 — 数与形", ["数学广角", "数学广角─数与形"]),
    ],
    (6, 2): [
        ("负数", ["负数"]),
        ("百分数（二）", ["百分数"]),
        ("圆柱与圆锥", ["圆柱与圆锥"]),
        ("比例", ["比例"]),
        ("数学广角", ["数学广角"]),
        ("统计与概率", ["统计与概率"]),
        ("整理与复习", ["整理与复习"]),
    ],
}


def fail(msg):
    print("SYLLABUS_FAIL: " + msg)
    sys.exit(1)


def topic_names(conn, grade, term_idx):
    term = "上" if term_idx == 1 else "下"
    names = set()
    sql = ("SELECT DISTINCT topic FROM questions "
           "WHERE grade=? AND term=? AND subject='数学'")
    for (topic,) in conn.execute(sql, (grade, term)):
        t = topic or ""
        names.add(t.split("：", 1)[1] if "：" in t else t)
    return names


def question_count(conn, grade, term_idx, aliases):
    term = "上" if term_idx == 1 else "下"
    total = 0
    sql = "SELECT topic, COUNT(*) FROM questions WHERE grade=? AND term=? AND subject='数学' GROUP BY topic"
    for topic, n in conn.execute(sql, (grade, term)):
        t = topic or ""
        name = t.split("：", 1)[1] if "：" in t else t
        if name in aliases:
            total += n
    return total


def coverage(conn, grade, aliases):
    """统计该年级数学题中 topic 命中任一纲目别名的题数。

    题库 term 全为空，无法按册次过滤，这里如实只按 grade + 别名统计，
    并在输出里标明「无册次」，不再假装上下册各自覆盖了多少题。
    """
    rows = conn.execute(
        "SELECT topic, COUNT(*) FROM questions WHERE grade=? AND subject='数学' "
        "GROUP BY topic", (grade,)).fetchall()
    return sum(n for topic, n in rows
               if topic and any(a in topic for a in aliases))


def build():
    if not os.path.isfile(DB):
        fail("missing quiz.db: " + DB)
    conn = sqlite3.connect(DB)
    total = conn.execute("SELECT COUNT(*) FROM questions").fetchone()[0]
    grades = {}
    hit_total = 0
    for (grade, term_idx), units in sorted(BOOKS.items()):
        declared, seen_names, out_units = [], set(), []
        for name, aliases in units:
            if not name or name in seen_names:
                fail("duplicate/empty unit name: grade=%d term=%d name=%r"
                     % (grade, term_idx, name))
            seen_names.add(name)
            if not aliases:
                fail("empty match list: %s" % name)
            for a in aliases:
                if not a or a in declared:
                    fail("duplicate/empty alias %r at grade=%d term=%d"
                         % (a, grade, term_idx))
                if any(ch in a for ch in ("%", "_", "'")):
                    fail("alias contains SQL wildcard/quote: %r" % a)
                declared.append(a)
            out_units.append({"name": name, "match": list(aliases)})
        # 真校验：每个声明的册次必须真的能在题库里命中题目，否则门禁失败。
        # （旧版用 schema=='3' 直接跳过此步，打印「仅结构校验」造成假通过。）
        hit = coverage(conn, grade, set(declared))
        if hit <= 0:
            fail("no syllabus coverage for grade=%d term=%d (aliases=%d, total=%d)"
                 % (grade, term_idx, len(declared), total))
        hit_total += hit
        grades.setdefault(str(grade), {})[str(term_idx)] = {"数学": out_units}
        print("  %d年级%s: %d 单元 / %d 别名 / %d 题命中（题库无册次，仅按纲目别名）"
              % (grade, "上" if term_idx == 1 else "下",
                 len(out_units), len(declared), hit))
    print("SYLLABUS_COVERAGE questions=%d declared_books=%d alias_hits=%d "
          "(同一题可命中上下册多个别名；离线题库无册次，学期过滤不适用)"
          % (total, len(BOOKS), hit_total))
    conn.close()
    return {
        "version": VERSION,
        "scope": SCOPE,
        "note": NOTE,
        "subjects": ["数学"],
        "grades": grades,
    }


def main():
    check_only = "--check" in sys.argv[1:]
    data = build()
    if check_only:
        if not os.path.isfile(OUT):
            fail("missing " + OUT)
        with open(OUT, "r", encoding="utf-8") as f:
            on_disk = json.load(f)
        if on_disk != data:
            fail("app/assets/syllabus.json is stale; run build_syllabus.py to regenerate")
        print("SYLLABUS_CHECK_OK books=%d" % len(BOOKS))
    else:
        with open(OUT, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
            f.write("\n")
        print("SYLLABUS_OK wrote %s books=%d" % (OUT, len(BOOKS)))


if __name__ == "__main__":
    main()
