#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""v1.3.2 题库处置：按逐题签认清单剔除 54 道题，生成 bank-v6。

可重放：清单在 tools/quizbank/review/drop-v132.csv。
  python3 tools/quizbank/apply_drop_v132.py --apply    # 执行剔除（幂等保护）
  python3 tools/quizbank/apply_drop_v132.py --check    # 负向校验：清单 id 全部不在库中
  python3 tools/quizbank/apply_drop_v132.py --ledger   # 生成 69 道逐题签认结果
"""
import argparse, csv, io, os, sqlite3, sys, collections

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DB = os.path.join(ROOT, 'app/assets/quiz.db')
STAMP = os.path.join(ROOT, 'app/assets/quiz.db.version')
DROP_CSV = os.path.join(ROOT, 'tools/quizbank/review/drop-v132.csv')
CAND_CSV = os.path.join(ROOT, '.build/bank-review-ledger-v131.csv')
OUT_CSV = os.path.join(ROOT, '.build/bank-review-final-v132.csv')
VERSION = 'pohw-2026.11.10-bank-v6'

KEEP_REASON = {
    'TK02619': '26 个字母中元音数量，字母入门常识，低年级英语合适',
    'TK02094': '字母表顺序辨识，第一学段音序查字法前置内容',
    'TK02095': '按字母表顺序排列，低年级合适',
    'TK02097': '首字母排列，音序查字法应用，低年级合适',
    'TK02099': '音序顺序辨错，低年级合适',
    'TK00022': '读钟面几时几分，二年级「认识时间」内容',
    'TK01406': '3 时整成直角，角的直观辨认，无需计算',
    'TK01420': '3 时整成直角，角的直观辨认，无需计算',
    'TK01422': '3 时整成直角，角的直观辨认，无需计算',
    'TK01517': '9 时整成直角，角的直观辨认，无需计算',
    'TK01664': '分针指 12 时针指 9 成直角，整点直观题',
    'TK02081': '12 时整三针重合，钟面常识',
    'TK02090': '分针从 4 走到 8 经过 20 分钟，二年级认识时间',
    'TK01438': '8 点整夹角 120 度，四年级已学角的度量，整点可分',
    'TK01477': '8 点整夹角 120 度，四年级已学角的度量，整点可分',
}


def load_drop():
    with io.open(DROP_CSV, encoding='utf-8-sig') as f:
        rows = list(csv.DictReader(f))
    ids = [r['id'] for r in rows]
    assert len(ids) == len(set(ids)), 'duplicate id in drop list'
    return rows


def connect():
    db = sqlite3.connect(DB)
    db.row_factory = sqlite3.Row
    return db


def apply():
    drops = load_drop()
    db = connect()
    before = db.execute('select count(*) from questions').fetchone()[0]
    missing = [r['id'] for r in drops
               if db.execute('select 1 from questions where id=?', (r['id'],)).fetchone() is None]
    if missing:
        print('ABORT: drop ids not found in bank:', missing[:10])
        sys.exit(1)
    grades = {}
    for r in drops:
        q = db.execute('select grade,subject from questions where id=?', (r['id'],)).fetchone()
        k = (q['grade'], q['subject'])
        grades[k] = grades.get(k, 0) + 1
    db.executemany('delete from questions where id=?', [(r['id'],) for r in drops])

    # chapters 是从 questions 派生的组合表，重算避免留下空章节
    db.execute('delete from chapters')
    db.executemany(
        'insert into chapters(grade,grade_label,subject,chapter,term) values (?,?,?,?,?)',
        [(r['grade'], r['grade_label'], r['subject'],
          r['chapter'] or '综合练习', r['term'] or '')
         for r in db.execute('select distinct grade,grade_label,subject,chapter,term '
                             'from questions')])

    after = db.execute('select count(*) from questions').fetchone()[0]
    qk = db.execute("select count(*) from questions where qtype='knowledge'").fetchone()[0]
    qr = db.execute("select count(*) from questions where qtype='recognition'").fetchone()[0]
    meta = {
        'version': VERSION,
        'updatedAt': '2026-11-10',
        'questions': str(after),
        'qtypeKnowledge': str(qk),
        'qtypeRecognition': str(qr),
        'note': 'bank-v6：按 tools/quizbank/review/drop-v132.csv 逐题签认剔除 54 道'
                '学段错配 / TV 不适配 / 结构损坏题；15 道规则误报题保留。'
                '处置依据见 tools/quizbank/review/，来源说明见 docs/DATA_SOURCES.md',
    }
    db.executemany('update meta set v=? where k=?', [(v, k) for k, v in meta.items()])
    db.commit()
    io.open(STAMP, 'w', encoding='utf-8').write(VERSION + '\n')
    db.close()
    print('DROP_APPLIED before=%d removed=%d after=%d qtype=%d/%d'
          % (before, len(drops), after, qk, qr))
    print('REMOVED_BY_GRADE', dict(sorted(grades.items())))
    print('VERSION', VERSION)


def check():
    drops = load_drop()
    db = connect()
    alive = [r['id'] for r in drops
             if db.execute('select 1 from questions where id=?', (r['id'],)).fetchone()]
    n = db.execute('select count(*) from questions').fetchone()[0]
    v = db.execute("select v from meta where k='version'").fetchone()[0]
    stamp = io.open(STAMP, encoding='utf-8').read().strip()
    print('CHECK total=%d alive_from_drop_list=%d version=%s stamp=%s'
          % (n, len(alive), v, stamp))
    if alive or v != VERSION or stamp != VERSION:
        print('CHECK_FAIL', alive[:10])
        sys.exit(1)
    print('DROP_CHECK_OK')


def ledger():
    drops = {r['id']: r for r in load_drop()}
    if not os.path.isfile(CAND_CSV):
        print('LEDGER_SKIP: candidate csv missing')
        return
    rows = list(csv.DictReader(io.open(CAND_CSV, encoding='utf-8-sig')))
    cols = ['id', 'grade', 'subject', 'prior_disposition', 'audit_features',
            'final_action', 'category', 'reason', 'stem', 'correct']
    with io.open(OUT_CSV, 'w', encoding='utf-8-sig', newline='') as f:
        w = csv.DictWriter(f, fieldnames=cols)
        w.writeheader()
        for r in rows:
            d = drops.get(r['id'])
            w.writerow({
                'id': r['id'], 'grade': r['grade'], 'subject': r['subject'],
                'prior_disposition': r['prior_disposition'],
                'audit_features': r['audit_features'],
                'final_action': 'DROP' if d else 'KEEP',
                'category': d['category'] if d else 'RULE_FALSE_POSITIVE',
                'reason': d['reason'] if d else KEEP_REASON.get(r['id'], ''),
                'stem': r['stem'], 'correct': r['correct'],
            })
    print('LEDGER_OK rows=%d drop=%d keep=%d out=%s'
          % (len(rows), len(drops), len(rows) - len(drops), OUT_CSV))


if __name__ == '__main__':
    p = argparse.ArgumentParser()
    g = p.add_mutually_exclusive_group(required=True)
    g.add_argument('--apply', action='store_true')
    g.add_argument('--check', action='store_true')
    g.add_argument('--ledger', action='store_true')
    a = p.parse_args()
    if a.apply: apply()
    elif a.check: check()
    else: ledger()
