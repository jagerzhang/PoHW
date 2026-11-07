#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""PoHW 内置题库质量重建（可复现；只读输入库，输出新库）。

    python3 tools/quizbank/rebuild_bank.py \
        --src app/assets/quiz.db --out /tmp/quiz_new.db --version pohw-2026.11.07-bank-v3

四件事：
  1. 年级重标定。题面自带/上游带来的年级标签不可信（曾把代数等式标成三年级），
     因此按小学课标知识点白名单重新定级：只有明确命中 1–3 年级课标、且不含
     任何高年级特征的题才允许进入低年级池；其余题保守标到 4 年级及以上。
     程序生成的原创题保留原年级（0 归一到 1）。
  2. 英文空格修复。词表切分还原被压掉的单词间空格，专有名词首字母还原，
     拒绝 Hebei -> he|be|i 这类误切，句点后与下划线前补空格。
  3. 脏题剔除。超出小学课标（含中学课文）、题干截断、依赖缺失材料、
     英文粘连无法解释、重复冗余题。
  4. 重算 qhash / meta / knowledge / chapters，并写出版本戳。

配套 tests/bank_quality_check.py 把同样的规则作为 CI 门禁跑一遍，
保证以后题库改动不会让质量问题回归。
"""
import argparse
import collections
import hashlib
import io
import os
import re
import sqlite3
import sys

# ---------------------------------------------------------------- 课标年级规则
MATH_RULES = [
 (6, r'按\s*\d+\s*[:：]\s*\d+\s*放大|面积是原来面积|南偏|北偏|东偏|西偏|\d+\s*°|边长的比|面积的比|比是\s*\d+|度数比|\d+\s*[:：]\s*\d+\s*[:：]\s*\d+|百分数|百分比|%|比例|比例尺|成正比例|成反比例|比值|化简比|圆柱|圆锥|圆周率|π|扇形|负数|有理数|数轴|鸽巢|抽屉原理|浓度|利率|折扣|纳税|圆的面积|圆的周长'),
 (5, r'最简真分数|分母是|分子.{0,6}扩大|通分|约分|小数点右边第|计数单位|列成等式|甲袋有|乙袋有|小数.{0,4}除|除以.{0,6}小数|等腰三角形|等边三角形|\d+\.\d+\s*[÷/]|[÷/]\s*\d+\.\d+|年龄.{0,8}倍|年后|内角|方程|等式性质|未知数|字母表示数|用字母表示|当\s*[a-zA-Z]\s*=|若\s*[a-zA-Z]\s*=|已知\s*[a-zA-Z]\s*=|[a-zA-Z]\s*[×÷*=]\s*[0-9a-zA-Z]|[0-9]\s*[a-zA-Z]\b|[a-zA-Z]{2}\s*[+×÷=]|[a-zA-Z]\s*[×÷*]\s*[a-zA-Z]|[0-9][a-zA-Z]\s*[+\-]|质因数|最大公因数|最小公倍数|公因数|公倍数|分解质因数|互质|整除|素数|合数|约数|长方体|正方体|体积|容积|表面积|平行四边形.{0,6}面积|梯形.{0,6}面积|三角形.{0,6}面积|通分|约分|最简分数|假分数|带分数|倒数|小数乘|小数除|数对'),
 (4, r'\d+\.\d+|\d{4,}\s*[×÷]|亿|万以上|三位数乘两位数|除数是两位数|运算定律|乘法分配律|乘法结合律|乘法交换律|四则混合|角的度量|平角|周角|钝角|锐角|公顷|平方千米|小数的意义|小数加减|条形统计图|平行四边形|梯形|垂直|平行|射线|直线'),
 (3, r'万以内|多位数乘一位数|一位数除|周长|长方形.{0,6}周长|正方形.{0,6}周长|分数|几分之几|年月日|闰年|24\s*时|24小时计时|吨|千克|千米|毫米|分米|面积单位|小数|时间|时针|分针|秒'),
 (2, r'100以内|表内乘|乘法口诀|口诀|米.{0,4}厘米|认识时间|时分|东南西北|方向|角的初步|统计表'),
 (1, r'20以内|10以内|数一数|比大小|认识图形|分与合|第几|前后左右'),
]
CHINESE_RULES = [
 (6, r'诗中.{0,6}作用|引号.{0,6}作用|文言|之乎者也|子曰|文学常识|作者|朝代|说明方法|议论文|记叙文|中心思想|段落大意|过渡句|病句|修改病句|修辞|阅读理解|古诗.{0,4}作者'),
 (5, r'古诗|诗句|诗中|错别字|词语搭配|第[一二三]节诗|关联词|句式转换|把字句|被字句|缩句|扩句|反问句|设问句|排比|拟人|比喻|夸张|对偶|谚语|歇后语|成语故事|名言|警句'),
 (3, r'成语|近义词|反义词|多音字|形近字|量词|标点符号|部首|偏旁|笔顺|组词|词语搭配|叠词'),
 (1, r'拼音|声母|韵母|整体认读|笔画|单字|声母表'),
]
ENGLISH_RULES = [
 (6, r'\b(worked|played|visited|watched|cleaned|washed|helped|went|bought)\b|last week|\b(was|were|had|been|being|gone|took|saw|said|did|could|would|should)\b|一般过去时|现在完成时|比较级|最高级|宾语从句|定语从句|被动语态|\bthan\b|\bbecause\b'),
 (5, r'\bdoing\b|\bgoing\b|twin sisters|\bIf\b|\boften\b|library|from Monday to Friday|\b(is|are|am)\s+\w+ing\b|现在进行时|第三人称单数|频率副词|情态动词|\bbefore\b|\bafter\b|\bwhile\b|\bwhich\b'),
 (3, r'\bthere\s+(is|are)\b|名词复数|人称代词|物主代词|介词|\bWhat\b|\bWhere\b|\bWhen\b|\bWho\b|\bHow\b|\bCan\b|\bDo\b|\bDoes\b|\bmy\b|\byour\b|\bhis\b|\bher\b'),
 (1, r'\b(Hello|Hi|Good morning|colour|color|red|blue|green|yellow|cat|dog|bird|apple|banana|one|two|three|book|pen|pencil)\b'),
]
OUT_OF_SCOPE = re.compile(
    r'一次函数|二次函数|不等式|方程组|勾股定理|相似三角形|全等三角形|无理数|绝对值|根号|√|开平方'
    r'|排列组合|容斥原理|中位数|众数|概率|追及问题|相遇问题|工程问题|数阵|1004个|2008个'
    r'|圆心距|弧长|三角函数|\bsin\b|\bcos\b|求导|积分|等差数列|等比数列|不定方程|复数|向量|矩阵'
    r'|鲁迅|鲁镇|《祝福》|《天狗》|郭沫若|寡人之于国|填然鼓之|辍耕之垄|《论语》|文言虚词'
    r'|之字意义|语句排序|赏析不正确|论证方法|斜边上的高|正弦|余弦|议论文|论据|论证'    r'|文化对人影响|孟母三迁|东施效颦|耳濡目染|蓬生麻中|唯物|辩证法|哲学|经济生活|政治生活',
    re.I)


def keyword_grade(subject, text):
    """返回 (-1 表示超小学, 命中的年级列表)。"""
    t = text or ''
    if OUT_OF_SCOPE.search(t):
        return -1, []
    rules = MATH_RULES if subject == '数学' else (
        CHINESE_RULES if subject == '语文' else ENGLISH_RULES)
    return 0, [g for g, pat in rules if re.search(pat, t, re.I)]


def difficulty_grade(d):
    """无课标关键词命中时的保守年级：至少 4，偏难的题 5。"""
    return 5 if (d is not None and d < 0.40) else 4


def assign_grade(row):
    if str(row['id']).startswith('SG'):
        return max(1, int(row['grade'] or 1))
    flag, hits = keyword_grade(row['subject'], row['text'])
    if flag < 0:
        return None
    if not hits:
        return difficulty_grade(row['difficulty'])
    top = max(hits)
    # 只有明确且单纯命中低年级课标的题才进 1–3 年级池；
    # 任何高年级特征都会把题抬到 4 年级以上。
    return top if top <= 3 else max(4, top)


# ---------------------------------------------------------------- 英文空格修复
COMMON = set(w.lower() for w in '''a an the i you he she it we they my your his her its our their this that these those
is am are was were be been being do does did have has had can could will would shall should may might must
to of in on at for with from by about after before under over near up down out off into
and or but so because if then than as not no yes please thank thanks hello hi goodbye good morning afternoon evening night
what who whom whose which where when why how many much old color colour time day today tomorrow yesterday
name book pen pencil ruler bag desk chair door window school teacher student boy girl friend father mother brother sister
apple banana orange pear grape red blue green yellow black white big small long short tall new old hot cold
cat dog bird fish pig duck cow horse panda monkey tiger elephant run jump swim read write sing dance play go come eat drink sleep
like love want need get make see look listen speak say ask answer open close sit stand walk study learn happen shop watch clean
clean listen happen speak learn watch study friends bags cups bottles pop nice meet great wall word tom mike lily lingling
let lets us go going to have an apple water some three bag of row four in on at is it this that these those
rice milk oranges juice bread cake tea coffee bus car bike train plane ship bed room home work classroom
season spring summer autumn winter weather sunny rainy cloudy windy snowy hot warm cool cold
than ago although though until unless besides upon across among between behind below
me him them us he she we do does did can may was were am are be been its mine yours hers ours theirs
Sunday Monday Tuesday Wednesday Thursday Friday Saturday birthday holiday present party family
don't can't didn't doesn't isn't aren't wasn't weren't won't it's i'm let's he's she's that's what's where's know knows'''.split())
PROPER = set('''chinese english beijing shanghai tianjin guangzhou america american china france japan london washington
january february march april may june july august september october november december
sunday monday tuesday wednesday thursday friday saturday tom lily lucy mike john mary green smith
australia canberra japanese christmas hebei zhejiang yinchuan nanjing hangzhou qingdao
shenzhen xiamen xian chengdu wuhan suzhou lingling meimei xiaoming'''.split())
SAFE_LONGS = set('''blackboard homework classroom playground basketball football breakfast lunch dinner supermarket hospital library
restaurant museum computer internet environment science technology university strawberry watermelon pineapple
pencil window teacher student elephant monkey tiger panda afternoon evening tomorrow yesterday
something anything everything everybody someone grandma grandpa father mother brother sister
australia canberra japanese christmas birthday beautiful interesting different children favourite bathroom
eighteen fourteen thirteen seventeen sixteen nineteen twenty thirty forty fifty sixty seventy eighty ninety
umbrella policeman grandmother triangle shopping learning watching speaking swimming happened cleaning
listening sleeping brighter bottles students teachers brothers friends twelfth twelveth'''.split())
STEMS = set('''study learn happen shop watch clean listen speak read write sing dance play jump swim run walk
work teach friend brother sister mother father grandma grandpa bright'''.split())
TOKEN = re.compile(r"[A-Za-z][A-Za-z']*")


def split_words(word):
    """能切成 >=2 个常见词 = 单词间空格被压掉；拒绝明显的误切。"""
    low = word.lower()
    if low in COMMON or low in SAFE_LONGS or low in PROPER:
        return None
    parts, i = [], 0
    while i < len(low):
        found = None
        for j in range(len(low), i, -1):
            if low[i:j] in COMMON:
                found = low[i:j]
                break
        if not found:
            return None
        parts.append(found)
        i += len(found)
    if len(parts) < 2:
        return None
    # Hebei -> he|be|i 这类连续双字母词切法不可信；
    # she|is|a|girl、what|a 这类含单个单字母功能词的正常短语要保留。
    if len([p for p in parts if len(p) == 1]) > 1:
        return None
    for a, b in zip(parts, parts[1:]):
        if len(a) == 2 and len(b) == 2:
            return None
    return parts


def is_legal_long(word):
    """合法英文长词（含常见屈折变化）。"""
    low = word.lower()
    if low in COMMON or low in SAFE_LONGS or low in PROPER:
        return True
    cand = [low]
    for suf in ('ies', 'es', 's', 'ing', 'ed', 'er', 'est', 'ly'):
        if low.endswith(suf) and len(low) - len(suf) >= 3:
            cand.append(low[:-len(suf)])
            if suf in ('ing', 'ed') and len(low) - len(suf) - 1 >= 3:
                cand.append(low[:-len(suf) - 1])
    if low.endswith('ing') and len(low) > 5:
        cand.append(low[:-3] + 'e')
    if low.endswith('ies') and len(low) > 4:
        cand.append(low[:-3] + 'y')
    return any(x in COMMON or x in STEMS for x in cand)


def restore_case(parts, original):
    out = []
    for k, p in enumerate(parts):
        if p in PROPER or (k == 0 and original[:1].isupper()):
            out.append(p.capitalize())
        else:
            out.append(p)
    return ' '.join(out)


def fix_english(text):
    """返回 (修复后文本, 修复处数, 是否仍有无法解释的粘连)。"""
    if not text:
        return text, 0, False
    n = 0
    def repl(m):
        nonlocal n
        w = m.group(0)
        if len(w) < 3:
            return w
        parts = split_words(w)
        if not parts:
            return w
        n += 1
        return restore_case(parts, w)
    fixed = TOKEN.sub(repl, text)
    fixed = re.sub(r'\.([A-Z])', r'. \1', fixed)
    fixed = re.sub(r'([A-Za-z])([_＿])', r'\1 \2', fixed)
    residual = False
    for m in TOKEN.finditer(fixed):
        w = m.group(0)
        if len(w) < 3:
            continue
        if len(w) < 8:
            if split_words(w):
                residual = True
                break
            continue
        if split_words(w) or not is_legal_long(w):
            residual = True
            break
    return fixed, n, residual


# ---------------------------------------------------------------- 其他质量规则
TRUNCATED = re.compile(r'[，,、；;]\s*$')
PLACEHOLDER = re.compile(r'\[图片\]|\[图\]|\[表格\]|如下图|如下表|听录音')
PREFIX = re.compile(r'^\s*参考答案\s*[:：]\s*')
FIELDS = ('text', 'opt_a', 'opt_b', 'opt_c', 'opt_d', 'explain')


# 「仅知识 / 计算题」偏好要排除的辨识 / 分类模板题：
# 这类题只需认出图形、颜色或类别，不是真正的知识 / 计算题。
RECOGNITION = re.compile(
    r'下面哪个是|下面哪个数|下面哪个数字|属于哪|哪个学科|归为|选出.*同类|'
    r'下面哪一组|下列.*属于哪|哪一(类|种学科)')


def qtype_of(text):
    return 'recognition' if RECOGNITION.search(text or '') else 'knowledge'


def norm_text(s):
    return re.sub(r'\s+', '', re.sub(r'[_＿]+', '_', s or '')).lower()


def qhash_of(text, opts):
    norm = lambda v: re.sub(r'\s+', '', str(v))
    raw = norm(text) + '|' + '|'.join(norm(o) for o in opts)
    return hashlib.sha1(raw.encode('utf-8')).hexdigest()[:16]


# ---------------------------------------------------------------- 主流程
def rebuild(src, out, version):
    c = sqlite3.connect(src)
    c.row_factory = sqlite3.Row
    rows = [dict(r) for r in c.execute('SELECT * FROM questions')]
    c.close()
    report, kept, seen, samples = collections.Counter(), [], set(), []

    for r in rows:
        g = assign_grade(r)
        if g is None:
            report['剔除_超出小学课标'] += 1
            continue
        t = r['text'] or ''
        if TRUNCATED.search(t) and '(' not in t and '（' not in t:
            report['剔除_题干截断'] += 1
            continue
        if PLACEHOLDER.search(t) or any(PLACEHOLDER.search(r[k] or '') for k in FIELDS[1:5]):
            report['剔除_依赖缺失材料'] += 1
            continue
        opts = [(r[k] or '').strip() for k in FIELDS[1:5]]
        if any(not o for o in opts) or len(set(opts)) < 4:
            report['剔除_选项空或重复'] += 1
            continue
        if r['subject'] == '英语':
            bad, touched = False, 0
            for col in FIELDS:
                fixed, n, residual = fix_english(r[col] or '')
                if n and len(samples) < 12:
                    samples.append((r['id'], col, (r[col] or '')[:60], fixed[:60]))
                r[col] = fixed
                report['修复_英文空格处'] += n
                touched += n
                if residual:
                    bad = True
            if bad:
                report['剔除_英文粘连无法修复'] += 1
                continue
            if touched:
                report['修复_英文题数'] += 1
            # 修复后大小写被归一，四个选项可能变得重复（RowFour / Rowfour），
            # 此时答案不再唯一，必须剔除而不是带着歧义入库。
            opts2 = [(r[k] or '').strip() for k in FIELDS[1:5]]
            if any(not o for o in opts2) or len(set(opts2)) < 4:
                report['剔除_修复后选项重复'] += 1
                continue
        if PREFIX.search(r['explain'] or ''):
            r['explain'] = PREFIX.sub('', r['explain'])
            report['清理_解析答案前缀'] += 1
        key = (r['subject'], norm_text(r['text']))
        if key in seen:
            report['剔除_重复冗余'] += 1
            continue
        seen.add(key)
        r['grade'] = g
        r['grade_label'] = '%d年级' % g
        r['qtype'] = qtype_of(r['text'])
        r['qhash'] = qhash_of(r['text'], [r[k] for k in FIELDS[1:5]])
        kept.append(r)
        report['保留'] += 1

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
    cols = ('id','grade','grade_label','term','subject','subject_group','chapter','topic',
            'text','opt_a','opt_b','opt_c','opt_d','answer_index','explain','source',
            'qhash','difficulty','difficulty_level','qtype')
    db.executemany('INSERT INTO questions VALUES (' + ','.join(['?'] * len(cols)) + ')',
                   [tuple(r[k] for k in cols) for r in kept])
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
        [(hashlib.sha1(str(k).encode()).hexdigest()[:12], '', k[0], k[1], '',
          k[2], 'core', '', k[3], '', '', '') for k in kn])
    db.executemany('INSERT INTO chapters VALUES (?,?,?,?,?,?)',
        [(hashlib.sha1(str(x).encode()).hexdigest()[:12], x[0], x[1], x[2], x[3], '')
         for x in ch])
    meta = {
        'version': version, 'updatedAt': '2026-11-07', 'schema': '3',
        'questions': str(len(kept)), 'knowledge': str(len(kn)), 'subjects': '3',
        'grades': str(len(set(r['grade'] for r in kept))),
        'source': 'self-generated + compiled from public Internet sources',
        'sourceFiles': str(len(rows)),
        'qtypeKnowledge': str(sum(1 for r in kept if r['qtype'] == 'knowledge')),
        'qtypeRecognition': str(sum(1 for r in kept if r['qtype'] == 'recognition')),
        'note': '按课标重打年级、修复英文空格并剔除不合格题后的题库；来源说明见 docs/DATA_SOURCES.md',
        'licenseNote': '部分题目整理自互联网公开渠道，仅供个人非商业学习使用；如有侵权请通过 GitHub Issue 联系删除。详见 docs/DATA_SOURCES.md',
    }
    db.executemany('INSERT INTO meta VALUES (?,?)', list(meta.items()))
    db.commit()
    report['题型_知识计算'] = sum(1 for r in kept if r['qtype'] == 'knowledge')
    report['题型_辨识分类'] = sum(1 for r in kept if r['qtype'] == 'recognition')
    dist = collections.Counter((r['grade'], r['subject']) for r in kept)
    db.close()
    return report, dist, samples, len(kept)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--src', required=True)
    ap.add_argument('--out', required=True)
    ap.add_argument('--version', required=True)
    ap.add_argument('--write-version-file', default=None)
    a = ap.parse_args()
    report, dist, samples, n = rebuild(a.src, a.out, a.version)
    print('=== 重建报告 ===')
    for k, v in sorted(report.items()):
        print('  %-28s %d' % (k, v))
    print('=== 年级×学科 ===')
    cum = 0
    for g in range(1, 7):
        c3 = [dist[(g, s)] for s in ('数学', '语文', '英语')]
        cum += sum(c3)
        print('  grade=%d  数学=%-4d 语文=%-4d 英语=%-4d  合计=%-4d 累积(<=%d)=%d'
              % (g, c3[0], c3[1], c3[2], sum(c3), g, cum))
    print('=== 英文修复样本 ===')
    for s in samples:
        print('  ', s)
    print('最终题数:', n)
    if a.write_version_file:
        io.open(a.write_version_file, 'w', encoding='utf-8').write(a.version + '\n')
    return 0


if __name__ == '__main__':
    sys.exit(main())
