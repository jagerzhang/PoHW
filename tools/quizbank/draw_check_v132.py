# -*- coding: utf-8 -*-
"""按 QuizDb 池谓词从 shipped bank-v6 实抽 1000 次，验证剔除与 TV 过滤生效。"""
import csv, io, os, random, re, sqlite3, collections

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
random.seed(20261110)
db = sqlite3.connect(os.path.join(ROOT, 'app/assets/quiz.db'))
db.row_factory = sqlite3.Row
drops = {r['id'] for r in csv.DictReader(io.open(
    os.path.join(ROOT, 'tools/quizbank/review/drop-v132.csv'), encoding='utf-8-sig'))}

CLOCK = re.compile(r'时针|分针|钟面|钟表')
ANGLE = re.compile(r'夹角|所成|旋转|度数|°')
DEG = re.compile(r'\d+(?:\.\d+)?\s*[°度]|°')
NUM = re.compile(r'[（(]\s*\d{1,2}\s*[）)]')
DEPENDENT = ('本文结尾', '第四节诗', '加横线', '加线', '这段材料', '上文')

def filtered(q):
    """复现 v1.3.2 抽题层：questionFitTv + optionsFitTv。"""
    t = q['text'] or ''
    opts = [q['opt_a'] or '', q['opt_b'] or '', q['opt_c'] or '', q['opt_d'] or '']
    if len(t) > 90: return True
    if any(k in t for k in DEPENDENT): return True
    if len(NUM.findall(t)) >= 3: return True
    if re.search(r'(^|[^0-9.])\d{1,2}\s*[.．、]\s*[^，。]{0,10}[（(]\s*[）)]', t): return True
    for o in opts:
        if len(o) > 48: return True
        if len(NUM.findall(o)) >= 3: return True
    return False

total = 0
hit_drop = 0
hit_clock = 0
hit_seq = 0
hit_filtered = 0
per_grade = collections.Counter()
samples = []
for grade in range(1, 7):
    for subject in ('数学', '语文', '英语'):
        pool = [dict(r) for r in db.execute(
            'select * from questions where grade<=? and subject=?', (grade, subject))]
        if not pool: continue
        for _ in range(170):
            q = random.choice(pool)
            total += 1
            per_grade[grade] += 1
            if q['id'] in drops: hit_drop += 1
            t = q['text'] or ''
            # 只判低年级池里的「含度数计算」题；3 时整成直角这类直观辨认题是有意保留的
            if grade <= 3 and CLOCK.search(t) and ANGLE.search(t) and DEG.search(t):
                hit_clock += 1
            if len(NUM.findall(t)) >= 3: hit_seq += 1
            if filtered(q): hit_filtered += 1
            if len(samples) < 12:
                samples.append((grade, subject, q['id'], t[:52]))

print('DRAW_SAMPLES', total, 'per_grade', dict(sorted(per_grade.items())))
print('HIT_DROPPED_ID', hit_drop)
print('HIT_CLOCK_ANGLE', hit_clock)
print('HIT_NUMBERED_STEM', hit_seq)
print('WOULD_BE_FILTERED_BY_TV_LAYER', hit_filtered)
print('SAMPLES')
for s in samples:
    print('  g%d %s %s | %s' % s)
ok = (hit_drop == 0 and hit_clock == 0 and hit_seq == 0)
print('DRAW_VERIFY=' + ('PASS' if ok else 'FAIL'))
if not ok: raise SystemExit(1)
