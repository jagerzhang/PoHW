#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""PoHW bank-v9：用 congcong 清洗后的四选一题整体替换题库，并恢复教材进度字段。

输入：/tmp/congcong-audit/output/*/quizzes/*.json（645 文件）
输出：app/assets/quiz.db（临时库构建 + 原子替换）、quiz.db.version
      .build/quiz-v8-snapshot.db（回滚快照）

筛选漏斗（与评估报告一致）：choice 四选一 → 答案可定位 → 解析纯文本 ≥20 字
→ 不依赖课文/插图 → 题干 ≤90 / 选项 ≤48 → 无 LaTeX → 无 AI 生成前缀
→ 选项文本完整 → 1-6 年级数语英 → 去重。

用法：
  python3 tools/quizbank/rebuild_bank_v9.py --audit   # 只统计与导出候选
  python3 tools/quizbank/rebuild_bank_v9.py --apply   # 构建 v9（可重复执行）
  python3 tools/quizbank/rebuild_bank_v9.py --check   # 负向校验
  python3 tools/quizbank/rebuild_bank_v9.py --rollback  # 从快照回滚 v8
"""
import argparse
import collections
import hashlib
import io
import json
import os
import re
import shutil
import sqlite3
import sys

ROOT = '/tmp/PoHW'
AUD = '/tmp/congcong-audit'
DB = os.path.join(ROOT, 'app/assets/quiz.db')
VER = os.path.join(ROOT, 'app/assets/quiz.db.version')
BUILD = os.path.join(ROOT, '.build')
SNAP = os.path.join(BUILD, 'quiz-v8-snapshot.db')
VERSION = 'pohw-2026.11.10-bank-v9'

CN = {'一': 1, '二': 2, '三': 3, '四': 4, '五': 5, '六': 6}
SUB = {'语文': '语文', '数学': '数学', '英语': '英语', '科学': '科学'}
LATEX = re.compile(r'\$|\\[a-zA-Z]+|\^\{|\\frac|\\times|\\div|\\le|\\ge|\\circ')
GEN_MARK = re.compile(r'^(综合复习|单元复习|随堂练|巩固练习)[:：]|'
                      r'（[^）]{2,12}的应用）|\(平均分的应用\)|按题目给定的规则|'
                      r'涉及减法的式子中|根据下面给出的行为|材料介绍[:：]|'
                      r'故事中介绍.{0,10}原句|虚构人物的资料卡')
MATERIAL = re.compile(r'看图|如图|下图|图中|插图|方格纸上设计|自己制作的.{0,8}作品|'
                      r'观察记录如下|统计表包括|根据下表|下表|表格中|'
                      r'课文|文中|原文|《[^》]+》中|燕子对小白兔说|'
                      r'故事里|故事开头|这首诗|这首古诗|按课文内容')
INCOMPLETE_OPT = re.compile(r'(或|和|与|的|把|在|是|了|，|、|：|:|,|\()\s*$')
BLANK = re.compile(r'_{2,}|＿{2,}|（\s*）|\(\s*\)')
NUM_SEQ = re.compile(r'[（(]\s*\d{1,2}\s*[）)]')


def norm(s):
    return re.sub(r'\s+', '', str(s or ''))


def fix_stem(t):
    s = BLANK.sub('____', str(t or ''))
    s = re.sub(r'(____)+', '____', s)
    return s.strip()


def fam_of(tb):
    if '五·四' in tb or '五四' in tb:
        return '五四制'
    if '2026' in tb or '2022' in tb:
        return '2026秋版'
    if '精通' in tb or 'jingtong' in tb.lower():
        return '人教精通版'
    if '教科版' in tb:
        return '教科版'
    if 'PEP' in tb.upper() or '人教版' in tb or '人教' in tb:
        return '人教版PEP'
    if '统编' in tb or '部编' in tb:
        return '统编版'
    if '义务教育教科书' in tb:
        return '通用教材'
    return '其它版本'


def answer_index(q):
    opts = [str(o).strip() for o in q['options']]
    a = str(q.get('answer') or '').strip()
    if a.upper() in ('A', 'B', 'C', 'D') and len(a) == 1:
        return 'ABCD'.index(a.upper())
    if a in opts:
        return opts.index(a)
    stripped = [re.sub(r'^[A-Da-d][\.\)、]\s*', '', o) for o in opts]
    if a in stripped:
        return stripped.index(a)
    return -1


def collect():
    """复用已核读签认的 9,544 道清单，再从原始 quiz 文件补教材 / 单元元数据。

    不重跑漏斗：四级清洗规则已在 .build/congcong-final-candidates.csv 上签认，
    重写规则会产生数量与内容漂移（实测会变成 11,112 道）。
    """
    import csv
    src = os.path.join(BUILD, 'congcong-final-candidates.csv')
    rows = list(csv.DictReader(io.open(src, encoding='utf-8-sig')))
    meta_map = {}
    for root, dirs, fs in os.walk(os.path.join(AUD, 'output')):
        if 'quizzes' not in root:
            continue
        for f in fs:
            if not f.endswith('.json'):
                continue
            d = json.load(io.open(os.path.join(root, f), encoding='utf-8'))
            tb = str(d.get('textbook') or f)
            meta_map[(f, str(d.get('unit_number') or 0))] = {
                'textbook': tb,
                'edition_family': fam_of(tb),
                'unit_title': str(d.get('unit') or ''),
            }
    out, stats = [], collections.Counter()
    for r in rows:
        fn = r['source_file']
        un = int(r['unit'] or 0)
        m = meta_map.get((fn, str(un)), {})
        if not m:
            m = meta_map.get((fn, '0'), {'textbook': fn,
                                        'edition_family': fam_of(fn),
                                        'unit_title': ''})
        opts = [r['opt_a'], r['opt_b'], r['opt_c'], r['opt_d']]
        ai = answer_index({'options': opts, 'answer': r['answer']})
        if ai < 0:
            stats['drop_answer'] += 1
            continue
        out.append({
            'grade': int(r['grade']), 'subject': r['subject'],
            'term': '上' if str(r['term']) == '1' else '下',
            'textbook': m['textbook'], 'edition_family': m['edition_family'],
            'unit_no': un, 'unit_title': m['unit_title'],
            'knowledge_point': r['knowledge_point'],
            'text': fix_stem(r['question']), 'options': opts,
            'answer_index': ai, 'explain': r['explanation'],
            'raw_difficulty': int(r['difficulty'] or 1),
            'source_file': fn,
        })
        stats['kept'] += 1
    stats['all'] = len(rows)
    stats['textbooks'] = len({r['textbook'] for r in out})
    return out, stats


def qhash(r):
    s = '|'.join(str(r.get(k) or '') for k in
                 ('grade', 'subject', 'textbook', 'unit_no', 'text',
                  'opt_a', 'opt_b', 'opt_c', 'opt_d'))
    return hashlib.sha256(s.encode('utf-8')).hexdigest()[:16]


COLS = ('id', 'grade', 'grade_label', 'term', 'subject', 'subject_group',
        'chapter', 'topic', 'text', 'opt_a', 'opt_b', 'opt_c', 'opt_d',
        'answer_index', 'explain', 'source', 'qhash', 'difficulty',
        'difficulty_level', 'qtype', 'syllabus_scope', 'grade_basis',
        'grade_verified', 'textbook', 'edition_family', 'unit_no',
        'unit_title', 'knowledge_point')


def build_rows(rows):
    groups = collections.defaultdict(list)
    for r in rows:
        groups[(r['grade'], r['subject'])].append(r)
    out = []
    for i, r in enumerate(sorted(rows, key=lambda x: (x['grade'], x['subject'],
                                                      x['textbook'], x['unit_no'], x['text']))):
        g = groups[(r['grade'], r['subject'])]
        pos = g.index(r) if False else None  # 分档在下面统一做
        out.append(r)
    # 难度分档：按 (年级,学科) 组内题面复杂度分三档，保证三档齐全
    for key, rs in groups.items():
        rs.sort(key=lambda x: (int(x['raw_difficulty']), len(x['text']), x['text']))
        n = len(rs)
        for i, r in enumerate(rs):
            lv = 1 if i * 3 < n else (2 if i * 3 < 2 * n else 3)
            r['difficulty_level'] = lv
            r['difficulty'] = {1: 0.8, 2: 0.5, 3: 0.2}[lv]
    final = []
    for i, r in enumerate(sorted(rows, key=lambda x: (x['grade'], x['subject'],
                                                      x['textbook'], x['unit_no'], x['text']))):
        final.append({
            'id': 'CC%05d' % (i + 1),
            'grade': r['grade'], 'grade_label': '%d年级' % r['grade'],
            'term': r['term'], 'subject': r['subject'], 'subject_group': 'core',
            'chapter': r['unit_title'], 'topic': r['knowledge_point'],
            'text': r['text'],
            'opt_a': r['options'][0], 'opt_b': r['options'][1],
            'opt_c': r['options'][2], 'opt_d': r['options'][3],
            'answer_index': r['answer_index'], 'explain': r['explain'],
            'source': '课标知识点题库', 'qhash': '', 'difficulty': r['difficulty'],
            'difficulty_level': r['difficulty_level'], 'qtype': 'knowledge',
            'syllabus_scope': 'outline', 'grade_basis': '教材单元(课标知识点)',
            'grade_verified': 1, 'textbook': r['textbook'],
            'edition_family': r['edition_family'], 'unit_no': r['unit_no'],
            'unit_title': r['unit_title'], 'knowledge_point': r['knowledge_point'],
        })
    for r in final:
        r['qhash'] = qhash(r)
    return final


def audit():
    rows, stats = collect()
    print('AUDIT', dict(stats))
    print('BY_GRADE', sorted(collections.Counter(r['grade'] for r in rows).items()))
    print('BY_SUBJECT', collections.Counter(r['subject'] for r in rows))
    print('BY_TERM', collections.Counter(r['term'] for r in rows))
    print('BY_FAMILY', collections.Counter(r['edition_family'] for r in rows))
    print('TEXTBOOKS', len({r['textbook'] for r in rows}),
          'UNITS', len({(r['edition_family'], r['grade'], r['subject'],
                         r['term'], r['unit_no']) for r in rows}),
          'KPS', len({r['knowledge_point'] for r in rows}))
    final = build_rows(rows)
    print('FINAL', len(final),
          'LEVELS', collections.Counter(r['difficulty_level'] for r in final))
    os.makedirs(BUILD, exist_ok=True)
    with io.open(os.path.join(BUILD, 'bank-v9-preview.json'), 'w', encoding='utf-8') as f:
        json.dump(final, f, ensure_ascii=False, indent=1)
    print('PREVIEW', os.path.join(BUILD, 'bank-v9-preview.json'))


def apply():
    rows, stats = collect()
    final = build_rows(rows)
    assert len(final) == 9544, 'unexpected final count %d' % len(final)
    os.makedirs(BUILD, exist_ok=True)
    if not os.path.isfile(SNAP):
        shutil.copyfile(DB, SNAP)
        print('SNAPSHOT_V8', SNAP)
    TMP = os.path.join(BUILD, 'quiz-v9-build.db')
    if os.path.isfile(TMP):
        os.remove(TMP)
    db = sqlite3.connect(TMP)
    db.execute('CREATE TABLE questions (%s)' % ','.join(
        '%s %s' % (c, 'INTEGER' if c in ('grade', 'answer_index', 'difficulty_level',
                                          'grade_verified', 'unit_no') else
                   ('REAL' if c == 'difficulty' else 'TEXT')) for c in COLS))
    ph = ','.join('?' * len(COLS))
    db.executemany('INSERT INTO questions (%s) VALUES (%s)' % (','.join(COLS), ph),
                   [tuple(r[c] for c in COLS) for r in final])
    for idx in ('CREATE INDEX idx_q_grade ON questions(grade,subject)',
                'CREATE INDEX idx_q_qtype ON questions(qtype,grade,subject)',
                'CREATE INDEX idx_q_scope ON questions(syllabus_scope,grade,subject)',
                "CREATE INDEX idx_q_progress ON questions(edition_family,grade,term,unit_no)"):
        db.execute(idx)
    # knowledge / chapters 按新库的知识点与教材单元重算
    db.execute('CREATE TABLE knowledge (id TEXT, raw_id TEXT, grade INTEGER, '
               'grade_label TEXT, term TEXT, subject TEXT, subject_group TEXT, '
               'chapter TEXT, topic TEXT, description TEXT, example TEXT, explanation TEXT)')
    kn = {(r['grade'], r['subject'], r['knowledge_point']) for r in final}
    db.executemany('INSERT INTO knowledge VALUES (?,?,?,?,?,?,?,?,?,?,?,?)',
                   [(hashlib.md5(str(k).encode()).hexdigest()[:12], '', k[0],
                     '%d年级' % k[0], '', k[1], 'core', k[2], k[2], '', '', '')
                    for k in kn])
    db.execute('CREATE TABLE chapters (id TEXT, grade INTEGER, grade_label TEXT, '
               'subject TEXT, chapter TEXT, term TEXT)')
    ch = {(r['grade'], r['subject'], r['unit_title'], r['term']) for r in final}
    db.executemany('INSERT INTO chapters VALUES (?,?,?,?,?,?)',
                   [(None, k[0], '%d年级' % k[0], k[1], k[2], k[3]) for k in ch])
    db.execute('CREATE TABLE meta (k TEXT PRIMARY KEY, v TEXT)')
    old = sqlite3.connect(SNAP)
    old_meta = dict(old.execute('select k,v from meta'))
    old.close()
    lv = collections.Counter(r['difficulty_level'] for r in final)
    meta = {
        'version': VERSION, 'updatedAt': '2026-11-10', 'schema': '3',
        'questions': str(len(final)), 'knowledge': str(len(kn)),
        'subjects': '3', 'grades': '6',
        'source': 'built-in curriculum question bank',
        'sourceFiles': str(len({r['source_file'] for r in rows})),
        'qtypeKnowledge': str(len(final)), 'qtypeRecognition': '0',
        'note': ('bank-v9：题库按教材册次与课标知识点重建，每题带 '
                 'textbook / edition_family / term / unit_no / knowledge_point；'
                 'grade 为教材年级，不再是最低适用年级下界。'),
        'licenseNote': old_meta.get('licenseNote', ''),
        'gradeSemantics': 'grade 为教材年级；单元进度按 (edition_family, grade, term, unit_no) 隔离',
        'genericQuestions': '0', 'importedQuestions': '0', 'droppedQuestions': '0',
        'progressUnits': str(len({(r['edition_family'], r['grade'], r['subject'],
                                   r['term'], r['unit_no']) for r in final})),
        'textbooks': str(len({r['textbook'] for r in final})),
        'difficultyLevels': '%d/%d/%d' % (lv[1], lv[2], lv[3]),
    }
    db.executemany('INSERT INTO meta VALUES (?,?)', list(meta.items()))
    db.commit()
    n = db.execute('select count(*) from questions').fetchone()[0]
    assert n == len(final), (n, len(final))
    db.close()
    os.replace(TMP, DB)
    io.open(VER, 'w', encoding='utf-8').write(VERSION + '\n')
    print('APPLY', dict(stats), 'final=%d textbooks=%s units=%s'
          % (len(final), meta['textbooks'], meta['progressUnits']))
    print('BANK_V9_APPLY_OK')


def check():
    d = sqlite3.connect(DB)
    cols = [r[1] for r in d.execute('pragma table_info(questions)')]
    need = ('textbook', 'edition_family', 'unit_no', 'unit_title',
            'knowledge_point', 'term')
    assert all(c in cols for c in need), cols
    n = d.execute('select count(*) from questions').fetchone()[0]
    v = d.execute("select v from meta where k='version'").fetchone()[0]
    stamp = io.open(VER, encoding='utf-8').read().strip()
    old_ids = d.execute("select count(*) from questions where id like 'TK%' or id like 'QC%'").fetchone()[0]
    no_tb = d.execute("select count(*) from questions where textbook='' or unit_no=0 or term=''").fetchone()[0]
    lv = dict(d.execute('select difficulty_level,count(*) from questions group by difficulty_level'))
    declared = d.execute("select v from meta where k='questions'").fetchone()[0]
    print('CHECK total=%d declared=%s version=%s stamp=%s old_ids=%d '
          'missing_progress=%d levels=%s'
          % (n, declared, v, stamp, old_ids, no_tb, lv))
    if (n < 9000 or declared != str(n) or v != VERSION or stamp != VERSION
            or old_ids or no_tb or sorted(lv) != [1, 2, 3]
            or min(lv.values()) < 100):
        print('CHECK_FAIL'); sys.exit(1)
    d.close()
    print('BANK_V9_CHECK_OK')


def rollback():
    assert os.path.isfile(SNAP), 'missing v8 snapshot'
    shutil.copyfile(SNAP, DB)
    io.open(VER, 'w', encoding='utf-8').write('pohw-2026.11.10-bank-v8-r2\n')
    print('ROLLBACK_TO_V8_OK')


if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('--audit', action='store_true')
    p.add_argument('--apply', action='store_true')
    p.add_argument('--check', action='store_true')
    p.add_argument('--rollback', action='store_true')
    a = p.parse_args()
    if a.audit: audit()
    elif a.apply: apply()
    elif a.check: check()
    elif a.rollback: rollback()
    else: p.print_help()
