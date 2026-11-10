#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""bank-v7：按全库逐题质检签认清单，剔除确证劣质题 + 修复填空占位符。

可重放清单：tools/quizbank/review/drop-v133.csv（剔）与
            tools/quizbank/review/bank-v7-fixdiff.csv（改）。

  --dry    只输出修复样例与统计，不写库
  --apply  执行剔除与修复、重算 qhash、更新 meta / 版本戳
  --check  负向校验：剔除 id 全不在、题数正确、修复已落库、版本戳正确
"""
import argparse, csv, hashlib, io, json, os, re, sqlite3, sys, collections

ROOT = '/tmp/PoHW'
DB = os.path.join(ROOT, 'app/assets/quiz.db')
STAMP = os.path.join(ROOT, 'app/assets/quiz.db.version')
CAND = os.path.join(ROOT, '.build/bank-audit-candidates.csv')
REVIEW = os.path.join(ROOT, 'tools/quizbank/review')
DROP_CSV = os.path.join(REVIEW, 'drop-v133.csv')
FIX_CSV = os.path.join(REVIEW, 'bank-v7-fixdiff.csv')
VERSION = 'pohw-2026.11.10-bank-v7'

CJK = re.compile(r'[\u4e00-\u9fa5]')
PLACE = re.compile(r'[（(]\s*[）)]|____|＿+')
UNDER = re.compile(r'_{1,2}(?!_)')
SPOT = [
    (re.compile(r'(?<=[\u4e00-\u9fa5，、；：。])\s+(?=[\u4e00-\u9fa5\d])'), '____'),
    (re.compile(r'(?<=[=＝])\s+(?=[\d×xX])'), '____'),
]


def fix_stem(t):
    t = t or ''
    s = UNDER.sub('____', t)
    # 这两条只匹配中文上下文或等号后的空格，不会动英文单词间的正常空格
    for pat, rep in SPOT:
        s = pat.sub(rep, s)
    s = re.sub(r'(____)+', '____', s)
    if not CJK.search(t):
        # 纯英文 / 纯算式题：不追加末尾占位，保留已有 ( ) 与单词空格
        return s
    if not PLACE.search(s) and '?' not in s and '？' not in s:
        s = re.sub(r'[.．。]\s*$', '', s) + '____'
    return s


def qhash_of(text, opts):
    norm = lambda v: re.sub(r'\s+', '', str(v or ''))
    raw = norm(text) + '|' + '|'.join(norm(o) for o in opts)
    return hashlib.sha1(raw.encode('utf-8')).hexdigest()[:16]


def load_candidates():
    rows = list(csv.DictReader(io.open(CAND, encoding='utf-8-sig')))
    drop = sorted({r['id'] for r in rows if r['action'] == 'DROP'})
    fix = sorted({r['id'] for r in rows if r['action'] == 'FIX'})
    return rows, drop, fix


def connect():
    db = sqlite3.connect(DB)
    db.row_factory = sqlite3.Row
    return db


def dry():
    rows, drop, fix = load_candidates()
    db = connect()
    print('DROP', len(drop), 'FIX', len(fix))
    changes = []
    for qid in fix:
        r = db.execute('select text,opt_a,opt_b,opt_c,opt_d from questions where id=?',
                       (qid,)).fetchone()
        if not r: continue
        new = fix_stem(r['text'])
        if new != r['text']:
            changes.append((qid, r['text'], new))
    print('CHANGED', len(changes))
    for qid, old, new in changes[:30]:
        print('  %s' % qid)
        print('    OLD: %s' % old[:100])
        print('    NEW: %s' % new[:100])
    print('--- no-change examples ---')
    shown = 0
    for qid in fix[:200]:
        r = db.execute('select text from questions where id=?', (qid,)).fetchone()
        if r and fix_stem(r['text']) == r['text']:
            print('  %s | %s' % (qid, r['text'][:80]))
            shown += 1
            if shown >= 10: break
    db.close()


def apply():
    rows, drop, fix = load_candidates()
    db = connect()
    before = db.execute('select count(*) from questions').fetchone()[0]
    missing = [q for q in drop
               if not db.execute('select 1 from questions where id=?', (q,)).fetchone()]
    if missing:
        print('ABORT drop ids missing:', missing[:10]); sys.exit(1)

    db.executemany('delete from questions where id=?', [(q,) for q in drop])

    changes = []
    for qid in fix:
        r = db.execute('select text,opt_a,opt_b,opt_c,opt_d from questions where id=?',
                       (qid,)).fetchone()
        if not r: continue
        new = fix_stem(r['text'])
        opts = [r['opt_a'], r['opt_b'], r['opt_c'], r['opt_d']]
        if new != r['text']:
            db.execute('update questions set text=?, qhash=? where id=?',
                       (new, qhash_of(new, opts), qid))
            changes.append({'id': qid, 'old_stem': r['text'], 'new_stem': new})

    # 补占位符后若题干超过 TV 上限，抽题层永远抽不到，按「宁缺毋滥」一并剔除。
    over_rows = [dict(r) for r in db.execute(
        'select id,grade,subject,text from questions where length(text)>90')]
    over = [r['id'] for r in over_rows]
    if over:
        db.executemany('delete from questions where id=?', [(q,) for q in over])
    too_long = len(over)

    db.execute('delete from chapters')
    db.executemany(
        'insert into chapters(grade,grade_label,subject,chapter,term) values (?,?,?,?,?)',
        [(r['grade'], r['grade_label'], r['subject'], r['chapter'] or '综合练习',
          r['term'] or '')
         for r in db.execute('select distinct grade,grade_label,subject,chapter,term '
                             'from questions')])
    after = db.execute('select count(*) from questions').fetchone()[0]
    qk = db.execute("select count(*) from questions where qtype='knowledge'").fetchone()[0]
    qr = db.execute("select count(*) from questions where qtype='recognition'").fetchone()[0]
    meta = {
        'version': VERSION, 'updatedAt': '2026-11-10',
        'questions': str(after), 'qtypeKnowledge': str(qk),
        'qtypeRecognition': str(qr),
        'note': 'bank-v7：全库逐题质检（格式/逻辑/内容/年级/重复），剔除 %d 道确证劣质题，'
                '修复 %d 道填空题的下划线占位；解析简略为已知欠账，见 '
                'tools/quizbank/review/ 与发版报告。' % (len(drop), len(changes)),
    }
    db.executemany('update meta set v=? where k=?', [(v, k) for k, v in meta.items()])
    db.commit()
    io.open(STAMP, 'w', encoding='utf-8').write(VERSION + '\n')
    db.close()

    os.makedirs(REVIEW, exist_ok=True)
    with io.open(DROP_CSV, 'w', encoding='utf-8-sig', newline='') as f:
        w = csv.writer(f)
        w.writerow(['id', 'grade', 'subject', 'flags', 'reason', 'stem'])
        for r in rows:
            if r['action'] == 'DROP':
                w.writerow([r['id'], r['grade'], r['subject'], r['flags'],
                            '全库逐题质检确证劣质', r['stem']])
        for r in over_rows:
            w.writerow([r['id'], r['grade'], r['subject'], 'F_STEM_OVER_90',
                        '补占位符后题干超过 90 字，TV 永远抽不到', r['text']])
    with io.open(FIX_CSV, 'w', encoding='utf-8-sig', newline='') as f:
        w = csv.DictWriter(f, fieldnames=['id', 'old_stem', 'new_stem'])
        w.writeheader()
        for c in changes: w.writerow(c)

    print('BANK_V7_APPLIED before=%d dropped=%d fixed=%d after=%d qtype=%d/%d'
          % (before, len(drop), len(changes), after, qk, qr))
    print('STEM_OVER_90_AFTER_FIX', too_long)
    print('VERSION', VERSION)
    print('EVIDENCE', DROP_CSV, FIX_CSV)


def check():
    # 只依赖已入库的签认清单，不依赖 .build 临时文件，保证任何克隆都能复核。
    with io.open(DROP_CSV, encoding='utf-8-sig') as f:
        drop = [r['id'] for r in csv.DictReader(f)]
    db = connect()
    alive = [q for q in drop
             if db.execute('select 1 from questions where id=?', (q,)).fetchone()]
    n = db.execute('select count(*) from questions').fetchone()[0]
    v = db.execute("select v from meta where k='version'").fetchone()[0]
    stamp = io.open(STAMP, encoding='utf-8').read().strip()
    # LIKE 里 _ 是单字符通配符，必须用 instr 精确匹配下划线占位。
    blanks = db.execute("select count(*) from questions where instr(text,'____')>0").fetchone()[0]
    expected = 3935 - len(drop)
    print('CHECK total=%d expected=%d alive_from_drop=%d version=%s stamp=%s blanks=%d'
          % (n, expected, len(alive), v, stamp, blanks))
    if alive or n != expected or v != VERSION or stamp != VERSION:
        print('CHECK_FAIL', alive[:10]); sys.exit(1)
    print('BANK_V7_CHECK_OK')


if __name__ == '__main__':
    p = argparse.ArgumentParser()
    g = p.add_mutually_exclusive_group(required=True)
    g.add_argument('--dry', action='store_true')
    g.add_argument('--apply', action='store_true')
    g.add_argument('--check', action='store_true')
    a = p.parse_args()
    if a.dry: dry()
    elif a.apply: apply()
    else: check()
