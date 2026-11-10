#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成 / 校验 app/assets/syllabus.json（bank-v9 按教材版本隔离的单元进度表）。

新题库每题带 edition_family / textbook / term / unit_no / unit_title / knowledge_point。
同一个「三年级上册」下可能有多套教材，U1 完全不是一回事，因此：
  * 每个 (年级, 册次, 学科) 格子里，选题量最多的那个教材版本族作为该格子的进度表；
  * editionByCell 记录该选择，QuizDb 按单元过滤时附加 edition_family = ? 约束，
    保证不会把别的教材同一单元序号的题混进来；
  * 单元别名用该单元下的课标知识点名（与 questions.topic 一致），跨单元去重。

用法：
  python3 tools/quizbank/build_syllabus.py            # 生成
  python3 tools/quizbank/build_syllabus.py --check   # 构建门禁：与题库同源
"""
import collections
import io
import json
import os
import sqlite3
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DB = os.path.join(ROOT, 'app/assets/quiz.db')
OUT = os.path.join(ROOT, 'app/assets/syllabus.json')
MAX_UNITS = 24
SUBJECT_ID = {'数学': 0, '语文': 1, '英语': 2}
TERM_ID = {'上': 1, '下': 2}


def fail(msg):
    print('SYLLABUS_FAIL: ' + msg)
    sys.exit(1)


def unsafe(s):
    return not s or any(c in s for c in ('%', '_', "'"))


def build():
    conn = sqlite3.connect(DB)
    cells = collections.defaultdict(lambda: collections.Counter())
    units = collections.defaultdict(lambda: collections.defaultdict(set))
    unit_titles = collections.defaultdict(dict)
    for fam, g, term, sub, uno, title, kp in conn.execute(
            'select edition_family,grade,term,subject,unit_no,unit_title,'
            'knowledge_point from questions'):
        if term not in TERM_ID or sub not in SUBJECT_ID or not uno:
            continue
        cell = (g, TERM_ID[term], sub)
        cells[cell][fam] += 1
        key = cell + (fam,)
        units[key][uno].add(kp)
        unit_titles[key][uno] = unit_titles[key].get(uno) or title
    conn.close()

    grades = {}
    edition_by_cell = {}
    report = []
    for cell in sorted(cells):
        g, t, sub = cell
        if not cells[cell]:
            continue
        fam = cells[cell].most_common(1)[0][0]
        edition_by_cell['%d|%d|%d' % (g, t, SUBJECT_ID[sub])] = fam
        key = cell + (fam,)
        seen_alias = set()
        out_units = []
        for uno in sorted(units[key])[:MAX_UNITS]:
            title = (unit_titles[key].get(uno) or '').strip() or ('第 %d 单元' % uno)
            aliases = []
            for kp in sorted(units[key][uno]):
                if unsafe(kp) or kp in seen_alias:
                    continue
                aliases.append(kp)
                seen_alias.add(kp)
                if len(aliases) >= 8:
                    break
            if not unsafe(title) and title not in seen_alias:
                aliases.append(title)
                seen_alias.add(title)
            if not aliases:
                continue
            name = title if title not in [u['name'] for u in out_units] else '%s（%d）' % (title, uno)
            out_units.append({'name': name, 'match': aliases})
        if out_units:
            grades.setdefault(str(g), {}).setdefault(str(t), {})[sub] = out_units
            report.append((g, t, sub, fam, len(out_units), len(seen_alias)))
    return {
        'version': '3.0',
        'scope': '按教材版本隔离的单元进度（edition_family × 年级 × 册次 × 学科）',
        'note': ('新题库每题带真实教材册次、单元与课标知识点。同一格子可能有多套教材，'
                 '此处选题量最多的版本族作为进度表，并由 QuizDb 附加 edition_family 约束，'
                 '避免不同教材的同一单元序号串题。编辑后请运行 --check。'),
        'subjects': ['数学', '语文', '英语'],
        'editionByCell': edition_by_cell,
        'grades': grades,
    }, report


def main():
    data, report = build()
    check_only = '--check' in sys.argv[1:]
    if check_only:
        if not os.path.isfile(OUT):
            fail('missing ' + OUT)
        on_disk = json.load(io.open(OUT, encoding='utf-8'))
        if on_disk != data:
            fail('app/assets/syllabus.json is stale; run build_syllabus.py to regenerate')
        books = sum(len(u) for g in data['grades'].values()
                    for t in g.values() for u in t.values())
        if not books or not data['editionByCell']:
            fail('no progress units generated')
        print('SYLLABUS_CHECK_OK books=%d cells=%d units=%d'
              % (len(report), len(data['editionByCell']), books))
    else:
        with io.open(OUT, 'w', encoding='utf-8') as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
            f.write('\n')
        for g, t, sub, fam, nu, na in report:
            print('  g%d T%d %s -> %-10s units=%d aliases=%d' % (g, t, sub, fam, nu, na))
        print('SYLLABUS_OK wrote %s cells=%d' % (OUT, len(report)))


if __name__ == '__main__':
    main()
