# -*- coding: utf-8 -*-
"""PoHW 全库逐题质检（只读）：格式 / 逻辑 / 内容 / 年级 / 重复 五维打标。

产出：
  .build/bank-full-audit.csv        全量明细（每题的标签与判据）
  .build/bank-audit-candidates.csv  命中「确证劣质或需修复」的候选
  .build/bank-full-audit.json       摘要
"""
import csv, io, json, os, re, sqlite3, collections

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
OUT = os.path.join(ROOT, '.build')
os.makedirs(OUT, exist_ok=True)
db = sqlite3.connect(os.path.join(ROOT, 'app/assets/quiz.db'))
db.row_factory = sqlite3.Row
rows = [dict(r) for r in db.execute('select * from questions')]

PLACE = re.compile(r'[（(]\s*[）)]|____|___|＿|\(\s*\)|（\s*）')
ASK = re.compile(r'[?？]|下列|哪个|哪几|多少|几[个只本支条张颗分秒米元人]|选择|判断|填空|计算|正确的是|不正确|错误的是|应填|找出|是什么|读作|写作|等于|约为|一共|还剩|可以|最[大多小少]')
FIFTH = re.compile(r'[EＥ]\s*[.．、]')
TRUNC = re.compile(r'[，、：；,]$|…$|^\s*$')
RARE = re.compile(r'[\u3400-\u4dbf\U00020000-\U0002ffff]')
NUM = re.compile(r'\d+(?:\.\d+)?')
SEQ = re.compile(r'[（(]\s*\d{1,2}\s*[）)]')
SUPER = re.compile(
    r'一次函数|二次函数|不等式|方程组|勾股|相似三角形|全等|无理数|绝对值|根号|√|排列组合|容斥|中位数|众数|概率|'
    r'追及|相遇问题|工程问题|数阵|圆心距|弧长|三角函数|\bsin\b|\bcos\b|求导|积分|等差数列|等比数列|不定方程|向量|矩阵|'
    r'鲁迅|《祝福》|《论语》|文言虚词|赏析不正确|论证方法|议论文|论据|百分数|负数|方程|比例尺|平方[厘分]?米|立方[厘分]?米|'
    r'表面积|体积|因数|倍数|通分|约分|圆面积|π|鸡和兔|鸡兔同笼|同位角|内错角|平行线.{0,4}判定')
CLOCK = re.compile(r'时针|分针|钟面|钟表')
ANGLE = re.compile(r'夹角|所成|旋转|度数|°')
DEPENDENT = re.compile(r'本文结尾|第四节诗|加横线|加线|这段材料|上文|文中|这首诗|这段话')

# 单位换算：目标单位 -> 换算系数
UNITS = {
    ('分米', '毫米'): 100, ('厘米', '毫米'): 10, ('米', '厘米'): 100,
    ('米', '分米'): 10, ('千米', '米'): 1000, ('千克', '克'): 1000,
    ('吨', '千克'): 1000, ('时', '分'): 60, ('分', '秒'): 60,
    ('元', '角'): 10, ('角', '分'): 10, ('米', '毫米'): 1000,
}

def normalize(s):
    # 保留 ASCII 小数点：31.25×10.44 与 312.5×1.044 归一化后不能变成同一个串，
    # 否则大量小数算式题会被误判成「选项重复」。
    return re.sub(r'[\s，。．、：；,;:!！?？（）()_＿]', '', s or '')

def opts(r):
    return [r['opt_a'] or '', r['opt_b'] or '', r['opt_c'] or '', r['opt_d'] or '']

def correct(r):
    o = opts(r)
    i = r['answer_index']
    return o[i] if 0 <= i < 4 else ''

def arith_answer(text):
    """只处理纯数字四则算式，返回数值答案或 None。"""
    t = (text or '').replace('×', '*').replace('÷', '/').replace('－', '-')
    t = re.sub(r'[（(]\s*[）)]|____|___|\s+', '', t)
    m = re.fullmatch(r'(\d+(?:\.\d+)?)\s*([+\-*/])\s*(\d+(?:\.\d+)?)\s*=?\??', t)
    if not m:
        m = re.fullmatch(r'(\d+(?:\.\d+)?)\s*([+\-*/])\s*(\d+(?:\.\d+)?)\s*([+\-*/])\s*(\d+(?:\.\d+)?)\s*=?\??', t)
        if not m: return None
        a, op1, b, op2, c = m.groups()
        try:
            v = eval('%s%s%s' % (a, op1, b), {'__builtins__': {}}, {})
            v = eval('%s%s%s' % (v, op2, c), {'__builtins__': {}}, {})
            return v
        except Exception:
            return None
    a, op, b = m.groups()
    try:
        return eval('%s%s%s' % (a, op, b), {'__builtins__': {}}, {})
    except Exception:
        return None

def unit_answer(text):
    m = re.search(r'(\d+(?:\.\d+)?)\s*(分米|厘米|毫米|米|千米|千克|克|吨|时|分|秒|元|角)\s*(?:就是|等于|＝|=|是)?\s*[_\s]*\s*(毫米|厘米|分米|米|克|千克|分|秒|角|分)\b', text or '')
    if not m: return None
    n, src, dst = float(m.group(1)), m.group(2), m.group(3)
    k = UNITS.get((src, dst))
    return n * k if k else None

def num_of(s):
    m = NUM.search(str(s or '').replace(',', ''))
    return float(m.group()) if m else None

seen_stem = {}
result = []
for r in rows:
    t = r['text'] or ''
    o = opts(r)
    ans = correct(r)
    f = []

    # ---- 格式 ----
    no_place = not PLACE.search(t)
    no_ask = not ASK.search(t)
    has_space_gap = re.search(r'\S\s{1,3}(?=[\u4e00-\u9fa5])', t) is not None
    if no_place and no_ask and (has_space_gap or re.search(r'[.．。]$', t)):
        f.append('F_STMT_NO_BLANK')          # 可修复：补 ____ 占位
    # 所有「既无占位符也无问句」的题干都要补 ____，不看长度：
    # 长题干（「…第三名是」）同样会让人看不出在问什么。
    if no_place and no_ask and 'F_STMT_NO_BLANK' not in f:
        f.append('F_NO_QUESTION')
    if any(FIFTH.search(x) for x in o): f.append('F_FIFTH_OPTION')
    if any(not x.strip() for x in o): f.append('F_EMPTY_OPTION')
    # 只认「去掉首尾空白后完全相同」的选项；不能归一化括号，
    # 否则「360÷（42+18）」和「360÷42+18」会被误判成重复。
    if len(set(x.strip() for x in o)) < 4: f.append('F_DUP_OPTION')
    if not (0 <= r['answer_index'] < 4) or not ans.strip(): f.append('F_BAD_ANSWER')
    if len(t) > 90: f.append('F_STEM_OVER_90')
    if any(len(x) > 48 for x in o): f.append('F_OPT_OVER_48')
    if TRUNC.search(t.rstrip()): f.append('F_TRUNCATED')
    if re.search(r'_{1,2}(?!_)', t) and not re.search(r'_{3,}', t): f.append('F_WEAK_BLANK')
    if len(SEQ.findall(t)) >= 3: f.append('F_SEQUENCE')
    if DEPENDENT.search(t): f.append('C_DEPENDENT')

    # ---- 逻辑：可重算 ----
    av = arith_answer(t)
    # 答案是英文数词（15+3 -> eighteen）时 num_of 取不到数，跳过数值比对，避免误报。
    if av is not None and num_of(ans) is not None:
        nv = num_of(ans)
        if abs(nv - av) > 1e-6: f.append('L_ARITH_WRONG')
    uv = unit_answer(t)
    if uv is not None:
        nv = num_of(ans)
        if nv is None or abs(nv - uv) > 1e-6: f.append('L_UNIT_WRONG')
    ex = r['explain'] or ''
    m = re.search(r'正确答案\s*[:：]\s*([A-Da-d])\s*[.．、]\s*([^\s，。]+)', ex)
    if m and normalize(m.group(2)) not in normalize(ans):
        f.append('L_EXPLAIN_MISMATCH')

    # ---- 内容 / 年级 ----
    if r['grade'] <= 3 and SUPER.search(t): f.append('C_SUPERGRADE')
    if r['grade'] <= 3 and CLOCK.search(t) and ANGLE.search(t) and re.search(r'\d+\s*[°度]', t):
        f.append('C_SUPERGRADE')
    if RARE.search(t): f.append('C_RARE_CHAR')
    if (len(normalize(t)) < 6 and '?' not in t and '？' not in t
            and '=' not in t and '＝' not in t and '+' not in t
            and not PLACE.search(t)):
        f.append('C_TOO_SHORT')

    # ---- 重复 ----
    key = normalize(t) + '|' + normalize(''.join(o))
    if key in seen_stem:
        f.append('D_DUPLICATE')
    else:
        seen_stem[key] = r['id']

    # ---- 解析质量（只记录，不剔除）----
    if len(ex) < 12 or normalize(ex) == normalize(ans): f.append('E_EXPLAIN_ECHO')

    result.append({
        'id': r['id'], 'grade': r['grade'], 'subject': r['subject'],
        'qtype': r['qtype'], 'flags': '|'.join(f),
        'stem': t, 'opt_a': o[0], 'opt_b': o[1], 'opt_c': o[2], 'opt_d': o[3],
        'correct': ans, 'explain': ex,
        'dup_of': seen_stem.get(key, '') if 'D_DUPLICATE' in f else '',
    })

DROP_TAGS = {'F_FIFTH_OPTION', 'F_EMPTY_OPTION', 'F_DUP_OPTION', 'F_BAD_ANSWER',
             'F_STEM_OVER_90', 'F_OPT_OVER_48', 'F_TRUNCATED', 'F_SEQUENCE',
             'L_ARITH_WRONG', 'L_UNIT_WRONG', 'L_EXPLAIN_MISMATCH',
             'C_SUPERGRADE', 'C_DEPENDENT', 'C_RARE_CHAR', 'C_TOO_SHORT',
             'D_DUPLICATE'}
# 「无问句 / 缺占位 / 弱占位」是可确定性修复的格式问题，不删题。
FIX_TAGS = {'F_STMT_NO_BLANK', 'F_WEAK_BLANK', 'F_NO_QUESTION'}

cand = []
for x in result:
    fs = set(x['flags'].split('|')) - {''}
    action = 'DROP' if fs & DROP_TAGS else ('FIX' if fs & FIX_TAGS else '')
    if action:
        y = dict(x); y['action'] = action; cand.append(y)

cols = ['id', 'grade', 'subject', 'qtype', 'flags', 'stem', 'opt_a', 'opt_b',
        'opt_c', 'opt_d', 'correct', 'explain', 'dup_of']
def dump(path, data, extra=()):
    with io.open(path, 'w', encoding='utf-8-sig', newline='') as f:
        w = csv.DictWriter(f, fieldnames=list(cols) + list(extra), extrasaction='ignore')
        w.writeheader()
        for x in data: w.writerow(x)

dump(os.path.join(OUT, 'bank-full-audit.csv'), result)
dump(os.path.join(OUT, 'bank-audit-candidates.csv'), cand, ('action',))

summary = {
    'total': len(rows),
    'flag_counts': dict(collections.Counter(f for x in result for f in x['flags'].split('|') if f)),
    'candidates': len(cand),
    'action_counts': dict(collections.Counter(x['action'] for x in cand)),
    'by_grade_drop': dict(sorted(collections.Counter(
        x['grade'] for x in cand if x['action'] == 'DROP').items())),
}
io.open(os.path.join(OUT, 'bank-full-audit.json'), 'w', encoding='utf-8').write(
    json.dumps(summary, ensure_ascii=False, indent=2))
print('TOTAL', summary['total'])
for k, v in sorted(summary['flag_counts'].items(), key=lambda kv: -kv[1]):
    print('  %-22s %d' % (k, v))
print('CANDIDATES', summary['candidates'], summary['action_counts'])
print('BY_GRADE_DROP', summary['by_grade_drop'])
