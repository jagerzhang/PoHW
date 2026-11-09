#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""按逐题复核台账生成 PoHW 内置题库 bank-v5（幂等、可复现）。

    python3 tools/quizbank/apply_review.py \
        --src app/assets/quiz.db --out /tmp/quiz-v5.db \
        --version pohw-2026.11.09-bank-v5

输入（均已随仓库提交，可追溯）：
  tools/quizbank/review/disposition-final.csv  全量 3964 道逐题处置签认
  tools/quizbank/review/supplement-v1.json    60 道原创四选一补题
  tools/quizbank/rebuild_bank.py              复用 qhash/qtype/英文修复规则

落地范围（本版只做可确证、可验证的改动）：
  1. DROP_CONFIRMED 32 道确证损坏题剔除（高中语文赏析、选项拼接、多子项、
     答案字段损坏）；另 1 道答案歧义题一并剔除，避免发布带歧义答案的题。
  2. RELOCATE 12 道第一学段字母/音序题下移到 2 年级并重标难度。
  3. REWRITE 中可确定性修复的子集：[] 占位、英文粘连、错别字/标点；
     「正确选项显著长需重写干扰项」的 65 道保留原题，等待逐题命题，
     不用规则硬改以免制造新错误。
  4. 全量修复 topic 册次与 grade 冲突（改为不含册次的中性 topic）。
  5. 入库 60 道原创补题（三段式解析、按课标学段标难度、版本字段中性）。
  6. 重算 qhash / qtype / meta / knowledge / chapters。

不改动：尾部「( )」填空位（单独删除会造出半截句，须与语义润色一起做）、
REVIEW_SUPERGRADE 57 道待课题、全量解析升级与难度标签重标定（下一轮）。
"""
import argparse
import collections
import csv
import hashlib
import io
import json
import os
import re
import sqlite3
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
sys.path.insert(0, os.path.join(ROOT, 'tools', 'quizbank'))
import rebuild_bank as R  # noqa: E402

REVIEW = os.path.join(ROOT, 'tools', 'quizbank', 'review')
COLS = ('id', 'grade', 'grade_label', 'term', 'subject', 'subject_group',
        'chapter', 'topic', 'text', 'opt_a', 'opt_b', 'opt_c', 'opt_d',
        'answer_index', 'explain', 'source', 'qhash', 'difficulty',
        'difficulty_level', 'qtype')
CN = {'一': 1, '二': 2, '三': 3, '四': 4, '五': 5, '六': 6}
# 人工复核追加剔除：TK02135 / TK02142 是同一道「读音字形完全正确」题的重复
# 录入，仅拼音内部空格不同；保留排版干净的 TK02135。
EXTRA_DROP = {'TK02142'}
TOPIC_GRADE = re.compile(r'([一二三四五六])年级[上下]册')
TOPIC_PREFIX = re.compile(r'^[一二三四五六]年级[上下]册\s*[-—:：·]?\s*')
TYPO = [('拨河', '拔河'), ('分钟指向', '分针指向'), ('No,.', 'No.')]
# 人工逐题核读发现、但 rebuild_bank.fix_english 词表未覆盖的粘连形态。
# 只作用于台账中标记「英文单词粘连」的题，避免规则外溢误改正常题。
GLUED = [
    (re.compile(r'Look!LiMing'), 'Look! Li Ming'),
    (re.compile(r'onhisT-shirt'), 'on his T-shirt'),
    (re.compile(r'—At(\d)'), r'— At \1'),
    (re.compile(r'—When'), '— When'),
    (re.compile(r'\b([A-Z][a-z]+)has\b'), r'\1 has'),
]
LV_DIFFICULTY = {1: 0.85, 2: 0.60, 3: 0.30}


def fix_glued(t):
    s = t or ''
    for pat, rep in GLUED:
        s = pat.sub(rep, s)
    return s


def safe_norm(t):
    """只清版式噪声，绝不删尾部 ( ) 填空位。"""
    s = (t or '').strip()
    s = re.sub(r'\s*\[\s*\]\s*(\(\s*\)|（\s*）)', r' \1', s)
    s = re.sub(r'\s*\[\s*\]\s*$', '', s)
    s = re.sub(r'_{3,}', '____', s)
    s = re.sub(r'\s{2,}', ' ', s)
    return s.strip()


def fix_topic(row):
    """册次与年级打架时去掉册次前缀；无教材实物目录，不推测补齐。"""
    topic = row['topic'] or ''
    m = TOPIC_GRADE.search(topic)
    if not m or CN[m.group(1)] == row['grade']:
        return topic, False
    cleaned = TOPIC_PREFIX.sub('', topic).strip()
    if not cleaned:
        cleaned = '%s综合练习' % row['subject']
    return cleaned, cleaned != topic


def supplement_rows(index, report):
    qs = json.load(io.open(os.path.join(REVIEW, 'supplement-v1.json'),
                           encoding='utf-8'))
    out = []
    for q in qs:
        text = q['text'].strip()
        opts = [str(o).strip() for o in q['options']]
        key = (q['subject'], R.norm_text(text))
        if key in index:
            # 题干已有同题：不制造重复，用三段式解析升级原解析。
            same = index[key]
            if len(q['explain']) > len(same['explain'] or '') \
                    and q['explain'].startswith('判据：'):
                same['explain'] = q['explain']
                report['补题_升级同题解析'] += 1
            else:
                report['补题_题干重复跳过'] += 1
            continue
        if len(set(opts)) != 4 or any(not o for o in opts):
            report['补题_选项非法跳过'] += 1
            continue
        lv = int(q['difficulty_level'])
        row = {
            'id': q['id'], 'grade': int(q['grade']),
            'grade_label': '%d年级' % int(q['grade']), 'term': '',
            'subject': q['subject'], 'subject_group': 'core',
            'chapter': q['content_domain'], 'topic': q['topic'],
            'text': text, 'opt_a': opts[0], 'opt_b': opts[1],
            'opt_c': opts[2], 'opt_d': opts[3],
            'answer_index': int(q['answer_index']), 'explain': q['explain'],
            'source': '教研原创命题(课标通用)',
            'qhash': R.qhash_of(text, opts), 'difficulty': LV_DIFFICULTY[lv],
            'difficulty_level': lv, 'qtype': R.qtype_of(text),
        }
        index[key] = row
        out.append(row)
        report['补题_入库'] += 1
    return out


def build(src, out, version):
    c = sqlite3.connect(src)
    c.row_factory = sqlite3.Row
    rows = [dict(r) for r in c.execute('SELECT * FROM questions')]
    meta = dict(c.execute('SELECT k,v FROM meta'))
    c.close()

    disp = {r['id']: (r['final_disposition'], r['reason'])
            for r in csv.DictReader(io.open(
                os.path.join(REVIEW, 'disposition-final.csv'),
                encoding='utf-8-sig'))}
    report = collections.Counter()
    kept = []
    bracket_orig = {}
    for r in rows:
        d, why = disp.get(r['id'], ('NORMALIZE', '未列入台账，按批量规范化处理'))
        if d == 'DROP_CONFIRMED' or '题干或答案存在歧义' in why:
            report['剔除_确证损坏'] += 1
            continue
        if r['id'] in EXTRA_DROP:
            report['剔除_重复录入'] += 1
            continue
        if d == 'RELOCATE':
            r['grade'] = 2
            r['grade_label'] = '2年级'
            r['difficulty_level'] = 1
            r['difficulty'] = 0.85
            report['下移_学段倒挂'] += 1
        if d == 'REWRITE':
            if '残留 []' in why or '[] 占位' in why:
                old = r['text']
                r['text'] = safe_norm(old)
                if r['text'] != old:
                    bracket_orig[r['id']] = old
                    report['修复_方括号占位'] += 1
            if '英文单词粘连' in why:
                old = r['text']
                r['text'] = fix_glued(R.fix_english(old)[0])
                if r['text'] != old:
                    report['修复_英文粘连'] += 1
        old = r['text']
        r['text'] = safe_norm(old)
        if r['text'] != old:
            report['修复_版式噪声'] += 1
        for a, b in TYPO:
            if a in r['text']:
                r['text'] = r['text'].replace(a, b)
                report['修复_错别字'] += 1
        newtopic, changed = fix_topic(r)
        if changed:
            r['topic'] = newtopic
            report['修复_topic册次冲突'] += 1
        r['qtype'] = R.qtype_of(r['text'])
        r['qhash'] = R.qhash_of(
            r['text'], [r[k] for k in ('opt_a', 'opt_b', 'opt_c', 'opt_d')])
        kept.append(r)
        report['保留'] += 1

    # 清掉 [] 噪声后，原本靠噪声区分的「同题干、不同选项」题会撞成重复题。
    # 这类题确实不同（四个选项不同），不能删；把其中一道恢复原始题干即可。
    for _ in range(2):
        groups = collections.defaultdict(list)
        for r in kept:
            groups[(r['subject'], R.norm_text(r['text']))].append(r)
        for group in groups.values():
            if len(group) <= 1:
                continue
            for r in group:
                if r['id'] in bracket_orig and r['text'] != bracket_orig[r['id']]:
                    r['text'] = bracket_orig[r['id']]
                    report['回滚_保留题干区分度'] += 1
                    break
    for r in kept:
        r['qtype'] = R.qtype_of(r['text'])
        r['qhash'] = R.qhash_of(
            r['text'], [r[k] for k in ('opt_a', 'opt_b', 'opt_c', 'opt_d')])

    index = {(r['subject'], R.norm_text(r['text'])): r for r in kept}
    kept.extend(supplement_rows(index, report))

    if os.path.isfile(out):
        os.remove(out)
    db = sqlite3.connect(out)
    db.execute('''CREATE TABLE questions (
        id TEXT PRIMARY KEY, grade INTEGER, grade_label TEXT, term TEXT, subject TEXT,
        subject_group TEXT, chapter TEXT, topic TEXT, text TEXT,
        opt_a TEXT, opt_b TEXT, opt_c TEXT, opt_d TEXT, answer_index INTEGER,
        explain TEXT, source TEXT, qhash TEXT, difficulty REAL, difficulty_level INTEGER,
        qtype TEXT)''')
    db.execute('''CREATE TABLE knowledge (
        id TEXT, raw_id TEXT, grade INTEGER, grade_label TEXT, term TEXT, subject TEXT,
        subject_group TEXT, chapter TEXT, topic TEXT, description TEXT, example TEXT, explanation TEXT)''')
    db.execute('''CREATE TABLE chapters (
        id TEXT, grade INTEGER, grade_label TEXT, subject TEXT, chapter TEXT, term TEXT)''')
    db.execute('CREATE TABLE meta (k TEXT PRIMARY KEY, v TEXT)')
    db.executemany('INSERT INTO questions VALUES (' + ','.join(['?'] * len(COLS)) + ')',
                   [tuple(r[k] for k in COLS) for r in kept])
    db.execute('CREATE INDEX idx_q_grade ON questions(grade,subject)')
    db.execute('CREATE INDEX idx_q_subject ON questions(subject)')
    db.execute('CREATE INDEX idx_q_level ON questions(difficulty_level,subject)')
    db.execute('CREATE INDEX idx_q_qtype ON questions(qtype,grade,subject)')
    kn, ch = set(), set()
    for r in kept:
        topic = r['topic'] or r['chapter'] or '综合练习'
        kn.add((r['grade'], r['grade_label'], r['subject'], topic))
        ch.add((r['grade'], r['grade_label'], r['subject'], r['chapter'] or '综合练习'))
    db.executemany('INSERT INTO knowledge VALUES (?,?,?,?,?,?,?,?,?,?,?,?)',
                   [(hashlib.sha1(str(k).encode()).hexdigest()[:12], '', k[0], k[1],
                     '', k[2], 'core', '', k[3], '', '', '') for k in kn])
    db.executemany('INSERT INTO chapters VALUES (?,?,?,?,?,?)',
                   [(hashlib.sha1(str(x).encode()).hexdigest()[:12], x[0], x[1],
                     x[2], x[3], '') for x in ch])
    meta['version'] = version
    parts = version.split('-')
    meta['updatedAt'] = parts[1].replace('.', '-')
    meta['questions'] = str(len(kept))
    meta['knowledge'] = str(len(kn))
    meta['grades'] = str(len(set(r['grade'] for r in kept)))
    meta['qtypeKnowledge'] = str(sum(1 for r in kept if r['qtype'] == 'knowledge'))
    meta['qtypeRecognition'] = str(sum(1 for r in kept if r['qtype'] == 'recognition'))
    meta['note'] = ('按逐题复核台账剔除确证损坏题、下移学段倒挂题、入库原创补题；'
                    '处置依据见 tools/quizbank/review/，来源说明见 docs/DATA_SOURCES.md')
    db.executemany('INSERT INTO meta VALUES (?,?)', list(meta.items()))
    db.commit()
    db.close()
    return report, kept


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--src', required=True)
    ap.add_argument('--out', required=True)
    ap.add_argument('--version', required=True)
    a = ap.parse_args()
    report, kept = build(a.src, a.out, a.version)
    print('=== 逐题复核落地报告 ===')
    for k, v in sorted(report.items()):
        print('  %-24s %d' % (k, v))
    dist = collections.Counter((r['grade'], r['subject']) for r in kept)
    print('=== 年级 x 学科（本格题量） ===')
    for g in range(1, 7):
        print('  g%d 数学=%-4d 语文=%-4d 英语=%-4d' %
              (g, dist[(g, '数学')], dist[(g, '语文')], dist[(g, '英语')]))
    print('=== 难度分布 ===', dict(collections.Counter(
        r['difficulty_level'] for r in kept)))
    print('=== 题型分布 ===', dict(collections.Counter(r['qtype'] for r in kept)))
    print('最终题数:', len(kept))
    return 0


if __name__ == '__main__':
    sys.exit(main())
