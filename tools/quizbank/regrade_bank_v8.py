#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""PoHW bank-v8：占位符知识点题逐题重定级 + quiz-challenge 优质题整合。

背景（见「PoHW-知识点年级标签、教材划分与外部题库整合诊断.md」）：
  * 2,932 道题的 topic/chapter 是「知识点 7xxxxx」数字占位符，上游知识点名已丢失；
  * 这些题的 grade 是 rebuild_bank.assign_grade() 用上游 difficulty 系数兜底推的，
    difficulty_level 还是机械切段，与真实学段无关；
  * 全库 term=''，教材单元表只能命中 6.7% 的题。

本脚本做四件事，全部可重放：
  1. 逐题按「小学课标最低适用年级」特征规则重新定级，并把 topic 从数字占位符
     换成真实课标纲目名；命中不了的不硬套难度系数，标 generic + 低置信度；
  2. 剔除确证超小学范围 / 依赖课文 / 格式坏 / 逻辑错 / 重复的劣质题；
  3. 从 quiz-challenge 有年级的小学题中，逐题筛出不重复、题型合规、能按课标
     重定级的优质四选一，统一标记为「通用题（无教材版本）」导入；
  4. 新增 syllabus_scope / grade_basis / grade_verified 三列，重算 qhash、meta、
     knowledge、chapters 与版本戳。

用法：
  python3 tools/quizbank/regrade_bank_v8.py --audit    # 只出统计与逐题台账
  python3 tools/quizbank/regrade_bank_v8.py --apply    # 从 bank-v7 基线重建
  python3 tools/quizbank/regrade_bank_v8.py --check    # 负向校验（契约用）
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
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
DB = os.path.join(ROOT, 'app/assets/quiz.db')
VER = os.path.join(ROOT, 'app/assets/quiz.db.version')
QC = '/tmp/quiz-challenge/data/questions.json'
BUILD = os.path.join(ROOT, '.build')
LEDGER = os.path.join(BUILD, 'bank-v8-signoff.csv')
REVIEW = os.path.join(BUILD, 'bank-v8-review.txt')
DROP_CSV = os.path.join(ROOT, 'tools/quizbank/review/drop-v134.csv')
IMPORT_CSV = os.path.join(ROOT, 'tools/quizbank/review/import-v134.csv')
VERSION = 'pohw-2026.11.10-bank-v8-r2'
# 每次改动 shipped 题库内容都必须推进版本戳：QuizDb 用
# assets/quiz.db.version 与本地 meta.version 比对，戳不变则覆盖安装不刷新题库。
BASE_STAMP = 'bank-v7'

# ---------------------------------------------------------------- 课标纲目规则
# (最低适用年级, 纲目名, 特征)，取所有命中的最大年级 = 保守定级。
MATH = [
 (1, '20以内的加减法', r'20以内|10以内|分与合|数一数|比大小|第几|前后左右'),
 (1, '认识图形（一）', r'认识图形|长方体.{0,4}正方体.{0,4}圆柱.{0,4}球|哪个是长方形|哪个是正方形|哪个是圆'),
 (1, '认识钟表', r'整时|几时整|认识钟表'),
 (1, '认识人民币', r'认识人民币|1元|1角|买.{0,6}找回'),
 (2, '100以内的加法和减法', r'100以内|两位数加两位数|两位数减两位数|进位加|退位减'),
 (2, '表内乘除法', r'表内乘|表内除|乘法口诀|口诀|九九'),
 (2, '长度单位', r'米.{0,4}厘米|厘米.{0,4}米|长度单位|量一量'),
 (2, '认识时间', r'认识时间|时分|分针.{0,6}走|时针.{0,6}走|一刻'),
 (2, '克和千克', r'克和千克|千克.{0,4}克|克.{0,4}千克'),
 (2, '有余数的除法', r'有余数|余数'),
 (2, '角的初步认识', r'角的初步|直角.{0,4}锐角.{0,4}钝角'),
 (2, '观察物体（一）', r'观察物体|从前面看|从上面看'),
 (2, '数据收集整理', r'统计表|数据收集|正字'),
 (2, '方向与位置', r'东南西北|东北|西北|东南|西南|方向'),
 (3, '万以内的加法和减法', r'万以内|三位数加三位数|三位数减三位数|四则混合'),
 (3, '多位数乘一位数', r'多位数乘一位数|一位数乘|三位数乘一位数'),
 (3, '除数是一位数的除法', r'一位数除|除数是一位'),
 (3, '长方形和正方形', r'周长|长方形.{0,6}周长|正方形.{0,6}周长'),
 (3, '分数的初步认识', r'几分之几|分数的初步|把一个.{0,8}平均分成|真分数|假分数'),
 (3, '年月日', r'年月日|闰年|平年|24\s*时|24小时计时|大月|小月'),
 (3, '测量', r'吨|千米|毫米|分米|面积单位'),
 (3, '小数的初步认识', r'小数的初步|一位小数|元角分.{0,6}小数'),
 (3, '倍的认识', r'倍数是|的几倍|倍\.|倍\('),
 (4, '大数的认识', r'亿|万以上|读作|写作|近似数|省略.{0,4}万'),
 (4, '三位数乘两位数', r'三位数乘两位数|两位数乘三位数|积大约|积是几位数'),
 (4, '除数是两位数的除法', r'除数是两位数|两位数除'),
 (4, '运算定律', r'运算定律|乘法分配律|乘法结合律|乘法交换律|加法结合律|简便计算|简便运算'),
 (4, '角的度量', r'角的度量|平角|周角|钝角|锐角|量角|度数是|\d+\s*°'),
 (4, '平行四边形和梯形', r'平行四边形|梯形|垂直|平行|射线|直线'),
 (4, '小数的意义和性质', r'小数的意义|小数的性质|小数加减|小数点.{0,6}移动|计数单位'),
 (4, '条形统计图', r'条形统计图|统计图'),
 (4, '公顷和平方千米', r'公顷|平方千米'),
 (5, '小数乘法和除法', r'小数乘|小数除|\d+\.\d+\s*[×÷*/]|[×÷*/]\s*\d+\.\d+'),
 (5, '简易方程', r'方程|等式性质|未知数|字母表示数|用字母表示|列方程|解设|[a-zA-Z]\s*=\s*\d'),
 (5, '因数与倍数', r'质因数|最大公因数|最小公倍数|公因数|公倍数|分解质因数|互质|整除|素数|合数|约数|倍数'),
 (5, '长方体和正方体', r'长方体|正方体|体积|容积|表面积|棱长'),
 (5, '多边形的面积', r'平行四边形.{0,6}面积|梯形.{0,6}面积|三角形.{0,6}面积|三角形面积|梯形面积'),
 (5, '分数的意义和性质', r'通分|约分|最简分数|最简真分数|分母是|分子.{0,6}扩大|带分数|倒数'),
 (5, '位置与数对', r'数对|第几列第几行'),
 (6, '分数乘法和除法', r'分数乘|分数除|除以.{0,4}分数|吃了它的|用去它的'),
 (6, '比和比例', r'比例尺|成正比例|成反比例|比值|化简比|比例的|比是\s*\d+|前项|后项|\d+\s*[:：]\s*\d+'),
 (6, '百分数', r'百分数|百分比|%|折扣|利率|纳税|浓度|成数'),
 (6, '圆', r'圆的面积|圆的周长|圆周率|π|半径|直径|扇形'),
 (6, '圆柱和圆锥', r'圆柱|圆锥'),
 (6, '负数', r'负数|有理数|数轴|零下'),
 (6, '数学广角', r'鸽巢|抽屉原理|鸡兔同笼|统筹|烙饼'),
]
CHINESE = [
 (1, '汉语拼音', r'拼音|声母|韵母|整体认读|音节|声调'),
 (1, '识字与写字', r'笔画|笔顺|单字|加一笔|减一笔|偏旁'),
 (2, '查字典与字词', r'部首|音序|音序查字|多音字|形近字|量词|组词|查字典'),
 (2, '词语积累', r'近义词|反义词|叠词|词语搭配|ABB|AABB'),
 (3, '句子与标点', r'标点符号|冒号|引号|把字句|被字句|缩句|扩句|关联词|修改病句|病句'),
 (3, '成语与熟语', r'成语|谚语|歇后语|四字词语'),
 (4, '修辞手法', r'比喻|拟人|排比|夸张|反问句|设问句|对偶|修辞'),
 (5, '古诗文', r'古诗|诗句|诗中|默写|文言|之乎者也|子曰'),
 (5, '阅读理解', r'段落大意|中心思想|过渡句|说明方法|记叙文|议论文|阅读理解'),
 (6, '文学常识', r'作者|朝代|文学常识|名著|《[^》]+》.{0,6}作者'),
]
ENGLISH = [
 (1, '字母与问候', r'\b(Hello|Hi|Good morning|Goodbye|Bye)\b|\b(A|B|C)\b.{0,8}字母'),
 (2, '基础词汇', r'\b(colour|color|red|blue|green|yellow|black|white|cat|dog|bird|apple|banana|one|two|three|book|pen|pencil|ruler)\b'),
 (3, 'be动词与代词', r'\b(am|is|are)\b|\b(I|you|he|she|it|we|they|my|your|his|her|its)\b|\bthere\s+(is|are)\b'),
 (3, '名词复数', r'\b(these|those|many|some)\b|复数|\b(s|es)\b.{0,6}复数'),
 (4, '一般现在时', r'\b(do|does|don\'t|doesn\'t)\b|第三人称单数|\b(always|usually|often|sometimes|never|every day)\b|\b(What|Where|When|Who|How)\b'),
 (4, '现在进行时', r'\b(am|is|are)\s+\w+ing\b|\bdoing\b|\bgoing\b|Look!|Listen!'),
 (4, '介词与情态动词', r'\b(in|on|at|under|behind|near|with|for|from|can|may|must|should)\b'),
 (5, '一般过去时', r'\b(was|were|had|went|bought|saw|said|did|took|worked|played|visited|watched|cleaned|washed|helped|could|would)\b|last week|yesterday|ago'),
 (5, '比较级与最高级', r'\bthan\b|比较级|最高级|\b(bigger|smaller|taller|shorter|longer|better|best|most)\b|\b-er\b.{0,6}than'),
 (6, '复合句', r'\b(because|if|when|while|which|that)\b|宾语从句|定语从句|被动语态|现在完成时|\bbeen\b|\bgone\b'),
]
RULES = {'数学': MATH, '语文': CHINESE, '英语': ENGLISH}

# 确证超出小学课标 / 依赖教材课文 / TV 无法作答
OUT_OF_SCOPE = re.compile(
    r'一次函数|二次函数|不等式|方程组|勾股定理|相似三角形|全等三角形|无理数|绝对值|根号|√|开平方'
    r'|排列组合|容斥原理|中位数|众数|概率|追及问题|相遇问题|工程问题|数阵'
    r'|圆心距|弧长|三角函数|\bsin\b|\bcos\b|求导|积分|等差数列|等比数列|不定方程|复数|向量|矩阵'
    r'|鲁迅|鲁镇|《祝福》|《天狗》|郭沫若|寡人之于国|填然鼓之|辍耕之垄|《论语》|文言虚词'
    r'|之字意义|语句排序|赏析不正确|论证方法|斜边上的高|正弦|余弦|议论文|论据|论证'
    r'|文化对人影响|孟母三迁|东施效颦|耳濡目染|蓬生麻中|唯物|辩证法|哲学|经济生活|政治生活'
    r'|象限|坐标系|通假字|使动用法|意动用法|宾语前置|状语后置|词类活用'
    r'|有氧呼吸|光合作用|减数分裂|基因型|染色体|电解质|摩尔|牛顿第|压强.{0,4}帕'
    r'|下列汉字按笔画数从少到多排列|音序排列|按顺序排列正确',
    re.I)
# 通篇英文的复杂语法题（低年级绝不放行）
HARD_ENGLISH = re.compile(r'\b(although|however|whether|unless|neither|either|since|although|besides)\b'
                          r'|宾语从句|定语从句|虚拟语气|被动语态|现在完成|过去完成', re.I)

PLACE = re.compile(r'[（(]\s*[）)]|____|＿+')
NUMBERED = re.compile(r'[（(]\s*\d{1,2}\s*[）)]')
PH = re.compile(r'^知识点\s*\d+$')


def norm(s):
    return re.sub(r'\s+', '', str(s or ''))


# 「宁可标高，不可标低」：题干里的高年级信号给新年级一个硬下限。
# 宽泛词（「余数」「1元」「米…厘米」）只算低年级线索，不能压过这些信号。
# 初版曾把「7.38÷0.21 的余数」「斐波那契第 5555 项」定成二年级，故引入本层。
FLOOR_COMMON = [
 (5, '代数符号', r'[a-zA-Zχ]\s*[=×÷]|\b[abcxyz]\s*[+×÷]|用式子表示|用字母表示|当\s*[a-zA-Z]\s*=|若\s*[a-zA-Z]\s*='),
 (4, '小数', r'\d+\.\d+'),
 (5, '分数性质', r'真分数|假分数|通分|约分|最简分数|分子|分母|倒数|带分数|分数.{0,4}(乘|除|大小|比较)'),
 (5, '几何性质', r'等腰|等边|三角形.{0,8}(边|围成|面积|周长)|平行四边形|梯形|圆的|圆柱|圆锥|体积|表面积|棱长|内角|对称轴'),
 (6, '比与百分数', r'比例尺|成正比例|成反比例|百分数|百分比|%|化简比|比值|前项|后项|折扣|利率|浓度|北偏|南偏|东偏|西偏'),
 (6, '竞赛推理', r'第\s*\d{3,}\s*个|前两个数的和|一列数|共有.{0,8}种|至少.{0,8}(块|个|种|厘米)|最多.{0,8}(块|个|种)|取值范围|不同.{0,4}填法|余数.{0,6}规律'),
 # 逐题核读 289 道后补：这些题型在小学绝不出现在 1-3 年级，宽泛词命中不能压过去。
 (4, '复合题型', r'余数|商是|除数|被除数|小数|改写|面积|周长与面积|平行四边形|等腰|拼成|至少需要|最少要用|每隔|种树|栽.{0,4}棵'
                r'|错别字|词语搭配|读音完全|划线|感情色彩|缩句|引号的作用|成语.{0,6}(运用|使用|搭配)|源自|查字典|部首查字'
                r'|下列.{0,12}(一组|一项|一句).{0,8}(正确|错误|不同|恰当)|感叹句|过去时'),
]
# 占位符题按学科的年级硬下限：宁可让 1-3 年级池少一点，也不放看不懂的题。
SUB_FLOOR = {'数学': 2, '语文': 3, '英语': 4}
# 上游清洗残留的英语残句 / 单词粘连题，逐题核读确认后剔除
BROKEN_EN = re.compile(
    r'[a-z]{2}\.[a-z]{2,}|\bTVa\b|\b(arenow|isnow)\b'
    r'|\b(is|are|was|were)\s+(teacher|student|friend|doctor|mother|father)\b'
    r'|\bLook\s+(the|my)\s'
    r'|I\'d like a cake lots of|Please look after\.', re.I)
FLOOR_SUBJECT = {
 '语文': [(4, '句式与阅读', r'通假字|文言|古诗|病句|修辞|阅读|段落大意|中心思想|作者|朝代|说明方法|关联词|句式转换|赏析')],
 '英语': [(4, '语法填空', r'____|What\b|Where\b|How\b|does\b|did\b|was\b|were\b|than\b|because\b'),
          (5, '时态与比较', r'yesterday|last week|ago|\bbeen\b|\bgone\b|过去时|比较级|最高级')],
}
# 确证不是小学课内题：中学内容 / 竞赛 / 依赖缺失材料 / TV 无法作答
OUT_HARD = re.compile(
    r'第\s*\d{3,}\s*个|前两个数的和|影长.{0,12}(速度|移动)|相似三角形|勾股'
    r'|含有两个通假字|文言虚词|之字意义|论证方法|赏析不正确'
    r'|象限|坐标系|一次函数|二次函数|不等式|方程组|无理数|概率|中位数|众数'
    r'|下列汉字按笔画数从少到多排列|音序排列|按顺序排列正确', re.I)


def classify(r):
    """逐题给结论。返回 (action, new_grade, new_topic, scope, basis, conf, reason)。"""
    t = r['text'] or ''
    sub = r['subject']
    old = r['grade']
    opts = [r[k] or '' for k in ('opt_a', 'opt_b', 'opt_c', 'opt_d')]

    # --- 确证劣质：格式坏 / 编号序列 / 超小学 / 复杂英语语法 ---
    if len(set(o.strip() for o in opts)) < 4:
        return ('DROP', old, r['topic'], '', 'format', 'high', '选项重复或空')
    if len(NUMBERED.findall(t)) >= 3 or any(len(NUMBERED.findall(o)) >= 3 for o in opts):
        return ('DROP', old, r['topic'], '', 'format', 'high', '编号序列题 TV 不可读')
    if OUT_OF_SCOPE.search(t) or OUT_HARD.search(t):
        return ('DROP', old, r['topic'], '', 'content', 'high', '确证超出小学课标或依赖缺失材料')
    if sub == '英语' and HARD_ENGLISH.search(t):
        return ('DROP', old, r['topic'], '', 'content', 'high', '中学英语语法')
    if sub == '英语' and BROKEN_EN.search(t):
        return ('DROP', old, r['topic'], '', 'content', 'high', '英语题干残句/单词粘连')

    # --- 高年级信号（硬下限） ---
    sig = [(g, n) for g, n, p in FLOOR_COMMON if re.search(p, t, re.I)]
    sig += [(g, n) for g, n, p in FLOOR_SUBJECT.get(sub, []) if re.search(p, t, re.I)]
    sg = max([g for g, _ in sig] + [0])
    if len(t) > 55 or (len(t) > 45 and len(re.findall(r'\d', t)) >= 4):
        sg = max(sg, 4)   # 长多步应用题
        sig.append((4, '多步应用'))

    # --- 课标特征（低年级线索） ---
    hits = [(g, name) for g, name, pat in RULES.get(sub, []) if re.search(pat, t, re.I)]
    rg = max([h[0] for h in hits] + [0])
    base = {'数学': 4, '语文': 4, '英语': 4}.get(sub, 6)
    g = min(6, max(rg, sg, base if not hits else 1, SUB_FLOOR.get(sub, 4)))

    if hits:
        cand = [name for gg, name in hits if gg == rg]
        topic = cand[0] if cand else hits[-1][1]
        if sg > rg:
            topic = '综合练习（含高阶知识）'  # 不假装是低年级单元
        basis = '课标特征:' + '/'.join(sorted(set(n for _, n in hits))[:2])
        if sg > rg:
            basis += ' + 信号:' + '/'.join(sorted(set(n for gg, n in sig if gg == sg))[:2])
        conf = 'high' if sg <= rg else 'medium'
        action = 'REASSIGN'
        reason = '命中 %d 条课标特征' % len(hits)
        if sg > rg:
            reason += '；高年级信号抬到 g%d' % g
    else:
        topic = '综合练习'
        basis = ('高年级信号:' + '/'.join(sorted(set(n for gg, n in sig if gg == sg))[:2])
                 if sig else '无课标特征-保守推定')
        conf, action = 'low', 'GENERIC'
        reason = '不硬套难度系数，隔离为通用题'

    # outline 只给「确证简单、短题干、无任何高年级信号」的 1-2 年级数学题
    scope = ('outline' if (g <= 2 and sub == '数学' and sg == 0
                           and len(t) <= 28 and rg <= 2 and conf == 'high')
             else 'generic')
    return (action, g, topic, scope, basis, conf, reason)


# ---------------------------------------------------------------- 外部题库
CN_GRADE = {'一年级': 1, '二年级': 2, '三年级': 3, '四年级': 4,
            '五年级': 5, '六年级': 6}
ARITH = re.compile(r'^\s*(\d+(?:\.\d+)?)\s*([+\-−×÷*/])\s*(\d+(?:\.\d+)?)\s*=\s*[?？]?\s*$')


def qc_regrade(x):
    """按算式本身的课标要求重定级，完全不信任 quiz-challenge 的 grade 字段。"""
    q = str(x.get('question') or '')
    m = ARITH.match(q)
    if not m:
        return None, None, None, '非标准算式题'
    a, op, b = float(m.group(1)), m.group(2), float(m.group(3))
    op = {'−': '-', '*': '×', '/': '÷'}.get(op, op)
    # 按课标判定「最低适用年级」：加减看和/被减数范围，乘除看乘数与除数的位数。
    # 初版用 max(a,b) 会把 22÷11 判成二年级表内除法、11×10 判成三年级，
    # 与课标不符；逐题抽样核读后改为下面这套。
    if op in ('+', '-'):
        v = a + b if op == '+' else a - b
        if v < 0:
            return None, None, None, '减法结果为负'
        if max(a, b, v) <= 20:
            g, topic = 1, '20以内的加减法'
        elif a <= 100 and b <= 100 and v <= 100:
            g, topic = 2, '100以内的加减法'
        else:
            g, topic = 3, '万以内的加减法'
    elif op == '×':
        v = a * b
        if max(a, b) <= 9:
            g, topic = 2, '表内乘法'
        elif min(a, b) <= 9:
            g, topic = 3, '多位数乘一位数'
        else:
            g, topic = 4, '两位数乘两位数'
    else:
        if b == 0 or a % b != 0:
            return None, None, None, '非整除/除零'
        v = a / b
        if b <= 9 and a <= 81:
            g, topic = 2, '表内除法'
        elif b < 10:
            g, topic = 3, '除数是一位数的除法'
        else:
            g, topic = 4, '除数是两位数的除法'
    # 验算答案
    ans = x.get('answer')
    chosen = (x.get('options') or [])[ans] if isinstance(ans, int) and 0 <= ans < 4 else None
    try:
        cv = float(str(chosen).strip())
    except (TypeError, ValueError):
        return None, None, None, '答案非数值'
    if abs(v - cv) > 1e-6:
        return None, None, None, '答案验算不符'
    return g, topic, op, ''


# 按「PoHW 该年级该运算是否稀缺」给导入配额，宁缺毋滥。
# 实测 PoHW 纯算式题只有 1-2 年级共 127 道，3 年级以上为 0；只在稀缺格子少量补充，
# 不把 quiz-challenge 的 505 道同质口算整批搬进来。
QUOTA = {(2, '+'): 15, (2, '-'): 15, (2, '×'): 10, (2, '÷'): 5,
         (3, '+'): 10, (3, '-'): 10, (3, '×'): 10, (3, '÷'): 10,
         (4, '+'): 5, (4, '-'): 5, (4, '×'): 5, (4, '÷'): 5}


def load_qc(existing_stems):
    data = json.load(io.open(QC, encoding='utf-8'))
    prim = [x for x in data if str(x.get('stage')) == '小学'
            and str(x.get('grade', '')).strip()]
    out = []
    seen = set(existing_stems)
    used = collections.Counter()
    stats = collections.Counter()
    for x in prim:
        stats['total'] += 1
        if str(x.get('type')) != 'single':
            stats['not_4choice'] += 1; continue
        opts = x.get('options') or []
        if not isinstance(opts, list) or len(opts) != 4 or any(not str(o).strip() for o in opts):
            stats['bad_options'] += 1; continue
        if x.get('subject') != '数学':
            stats['not_core_subject'] += 1; continue
        q = norm(x.get('question'))
        if q in seen:
            stats['duplicate'] += 1; continue
        g, topic, op, why = qc_regrade(x)
        if g is None:
            stats['regrade_fail:' + why] += 1; continue
        if g == 1:
            stats['too_trivial'] += 1; continue   # PoHW 一年级已有 69 道纯算式
        key = (g, op)
        if used[key] >= QUOTA.get(key, 0):
            stats['quota_full'] += 1; continue
        used[key] += 1
        seen.add(q)
        out.append({
            'id': 'QC%04d' % (len(out) + 1),
            'grade': g, 'grade_label': '%d年级' % g, 'term': '',
            'subject': '数学', 'subject_group': 'core',
            'chapter': topic, 'topic': topic,
            'text': str(x['question']).strip(),
            'opt_a': str(opts[0]), 'opt_b': str(opts[1]),
            'opt_c': str(opts[2]), 'opt_d': str(opts[3]),
            'answer_index': int(x['answer']),
            'explain': str(x.get('explain') or '').strip() or '按运算规则计算即可。',
            'source': 'quiz-challenge 整合(课标重定级)',
            'qtype': 'knowledge',
            'difficulty': 0.5, 'difficulty_level': 2,
            'syllabus_scope': 'generic', 'grade_basis': '课标重定级(算式验算)',
            'grade_verified': 1,
            '_orig_id': x.get('id'), '_orig_grade': x.get('grade'), '_topic': topic,
        })
        stats['import'] += 1
    return out, stats


# ---------------------------------------------------------------- 主流程
def connect():
    d = sqlite3.connect(DB)
    d.row_factory = sqlite3.Row
    return d


def audit():
    os.makedirs(BUILD, exist_ok=True)
    d = connect()
    rows = [dict(r) for r in d.execute('select * from questions')]
    stems = {norm(r['text']) for r in rows}
    d.close()
    ph_rows = [r for r in rows if PH.match(str(r['topic'] or ''))]
    out_rows, decisions = [], []
    stats = collections.Counter()
    for r in rows:
        if not PH.match(str(r['topic'] or '')):
            decisions.append(dict(action='KEEP', id=r['id'], subject=r['subject'],
                                  old_grade=r['grade'], new_grade=r['grade'],
                                  old_topic=r['topic'], new_topic=r['topic'],
                                  scope='outline', basis='原有课标纲目', conf='high',
                                  reason='非占位符题，上一轮已质检', stem=r['text']))
            stats['keep_outline'] += 1
            continue
        action, g, topic, scope, basis, conf, reason = classify(r)
        decisions.append(dict(action=action, id=r['id'], subject=r['subject'],
                              old_grade=r['grade'], new_grade=g,
                              old_topic=r['topic'], new_topic=topic,
                              scope=scope, basis=basis, conf=conf,
                              reason=reason, stem=r['text']))
        stats[action] += 1
        stats['scope_' + (scope or 'drop')] += 1
    imports, qstats = load_qc(stems)
    for q in imports:
        decisions.append(dict(action='IMPORT', id=q['id'], subject='数学',
                              old_grade=q['_orig_grade'], new_grade=q['grade'],
                              old_topic=q['_orig_id'], new_topic=q['_topic'],
                              scope='generic', basis=q['grade_basis'], conf='high',
                              reason='算式验算通过 + 课标重定级 + 去重', stem=q['text']))
    stats['IMPORT'] = len(imports)

    with io.open(LEDGER, 'w', encoding='utf-8-sig', newline='') as f:
        w = csv.DictWriter(f, fieldnames=['action', 'id', 'subject', 'old_grade',
                                          'new_grade', 'old_topic', 'new_topic',
                                          'scope', 'basis', 'conf', 'reason', 'stem'])
        w.writeheader()
        for x in decisions:
            w.writerow(x)
    # 人工核读清单：所有 DROP / 所有进入 1-3 年级池的题 / 所有 IMPORT
    review = [x for x in decisions if x['action'] == 'DROP'
              or x['action'] == 'IMPORT'
              or (x['action'] in ('REASSIGN', 'GENERIC') and x['new_grade'] <= 3)]
    with io.open(REVIEW, 'w', encoding='utf-8') as f:
        for x in review:
            f.write('%-7s %-8s %-4s g%s->g%s %-8s %-5s | %s | %s\n' % (
                x['action'], x['id'], x['subject'], x['old_grade'], x['new_grade'],
                x['scope'] or '-', x['conf'],
                str(x['stem'])[:78].replace('\n', ' '), x['reason'][:44]))
    print('AUDIT total=%d placeholder=%d' % (len(rows), len(ph_rows)))
    for k, v in sorted(stats.items()):
        print('  %-28s %d' % (k, v))
    print('QC_STATS', dict(qstats))
    bygrade = collections.Counter(x['new_grade'] for x in decisions
                                  if x['action'] in ('REASSIGN', 'GENERIC', 'KEEP', 'IMPORT'))
    print('NEW_GRADE_DIST', sorted(bygrade.items()))
    print('REVIEW_LINES', len(review), 'LEDGER_ROWS', len(decisions))
    print('LEDGER', LEDGER)
    print('REVIEW', REVIEW)


COLS = ('id', 'grade', 'grade_label', 'term', 'subject', 'subject_group', 'chapter',
        'topic', 'text', 'opt_a', 'opt_b', 'opt_c', 'opt_d', 'answer_index',
        'explain', 'source', 'qhash', 'difficulty', 'difficulty_level', 'qtype',
        'syllabus_scope', 'grade_basis', 'grade_verified')


def qhash(r):
    s = '|'.join(str(r.get(k) or '') for k in
                 ('grade', 'subject', 'topic', 'text', 'opt_a', 'opt_b', 'opt_c', 'opt_d'))
    return hashlib.sha256(s.encode('utf-8')).hexdigest()[:16]


def apply():
    # 幂等：每次从 bank-v7 基线恢复，避免重复应用
    stamp = io.open(VER, encoding='utf-8').read().strip()
    if VERSION in stamp:
        print('BANK_V8_ALREADY_APPLIED (no-op)'); return
    if BASE_STAMP not in stamp:
        print('APPLY_FAIL unexpected baseline stamp', stamp); sys.exit(1)
    d0 = connect()
    n0 = d0.execute('select count(*) from questions').fetchone()[0]
    d0.close()
    if n0 != 3818:
        subprocess.check_call(['git', 'checkout', '--', 'app/assets/quiz.db',
                               'app/assets/quiz.db.version'], cwd=ROOT)
        print('RESTORED_BASELINE_FROM_GIT (questions=%d)' % n0)
    d = connect()
    rows = [dict(r) for r in d.execute('select * from questions')]
    old_meta = dict(d.execute('select k,v from meta'))
    stems = {norm(r['text']) for r in rows}
    d.close()
    out, dropped, imported = [], [], []
    for r in rows:
        r.setdefault('syllabus_scope', 'outline')
        r.setdefault('grade_basis', '原有课标纲目')
        r.setdefault('grade_verified', 1)
        if PH.match(str(r['topic'] or '')):
            action, g, topic, scope, basis, conf, reason = classify(r)
            if action == 'DROP':
                dropped.append(dict(id=r['id'], grade=r['grade'], subject=r['subject'],
                                    topic=r['topic'], flags=reason, reason='逐题重定级确证劣质',
                                    stem=r['text']))
                continue
            r['grade'] = g
            r['grade_label'] = '%d年级' % g
            r['chapter'] = topic
            r['topic'] = topic
            r['syllabus_scope'] = scope
            r['grade_basis'] = basis
            r['grade_verified'] = 0 if conf == 'low' else 1
            # 只有 1-2 年级数学的课标纲目题才带册次；其余保持 term='' 且 generic
            if scope == 'outline':
                r['term'] = ''
        out.append(r)
    imports, qstats = load_qc(stems)
    for q in imports:
        imported.append(q)
        q.pop('_orig_id', None); q.pop('_orig_grade', None); q.pop('_topic', None)
        out.append(q)
    # 废掉上游噪声 difficulty：按 (年级, 学科) 组内题面复杂度重分三档。
    # 原值来自上游站点的连续系数（与题面无关），且重定级后低年级某些
    # (年级, 学科, 难度档) 组合会空池，导致练习页抽不到题。
    groups = collections.defaultdict(list)
    for r in out:
        groups[(r['grade'], r['subject'])].append(r)
    for key, rs in groups.items():
        rs.sort(key=lambda x: (len(x['text'] or ''), str(x['id'])))
        n = len(rs)
        for i, r in enumerate(rs):
            lv = 1 if i * 3 < n else (2 if i * 3 < 2 * n else 3)
            r['difficulty_level'] = lv
            r['difficulty'] = {1: 0.8, 2: 0.5, 3: 0.2}[lv]
    for r in out:
        r['qhash'] = qhash(r)

    # 构建到临时库，全部成功后再原子替换 shipped 库。
    # （首版直接在 shipped 库上 DROP TABLE 后异常，DDL 已提交而 INSERT 未提交，
    #   一度留下空题库；自此改为全程不碰 quiz.db，最后一步 os.replace。）
    os.makedirs(BUILD, exist_ok=True)
    TMP = os.path.join(BUILD, 'quiz-v8-build.db')
    if os.path.isfile(TMP):
        os.remove(TMP)
    db = sqlite3.connect(TMP)
    db.execute('DROP TABLE IF EXISTS questions')
    db.execute('''CREATE TABLE questions (
        id TEXT PRIMARY KEY, grade INTEGER, grade_label TEXT, term TEXT,
        subject TEXT, subject_group TEXT, chapter TEXT, topic TEXT, text TEXT,
        opt_a TEXT, opt_b TEXT, opt_c TEXT, opt_d TEXT, answer_index INTEGER,
        explain TEXT, source TEXT, qhash TEXT, difficulty REAL,
        difficulty_level INTEGER, qtype TEXT,
        syllabus_scope TEXT, grade_basis TEXT, grade_verified INTEGER)''')
    ph = ','.join('?' * len(COLS))
    db.executemany('INSERT INTO questions (%s) VALUES (%s)' % (','.join(COLS), ph),
                   [tuple(r.get(k) for k in COLS) for r in out])
    db.execute('CREATE INDEX idx_q_grade ON questions(grade,subject)')
    db.execute('CREATE INDEX idx_q_qtype ON questions(qtype,grade,subject)')
    db.execute('CREATE INDEX idx_q_scope ON questions(syllabus_scope,grade,subject)')
    # knowledge / chapters 从重建后的题面重算（纲目名已是真实名）
    db.execute('DROP TABLE IF EXISTS knowledge')
    db.execute('''CREATE TABLE knowledge (id TEXT, raw_id TEXT, grade INTEGER,
        grade_label TEXT, term TEXT, subject TEXT, subject_group TEXT,
        chapter TEXT, topic TEXT, description TEXT, example TEXT, explanation TEXT)''')
    kn = {(r['grade'], r['grade_label'], r['subject'], r['topic']) for r in out}
    db.executemany('INSERT INTO knowledge VALUES (?,?,?,?,?,?,?,?,?,?,?,?)',
                   [(hashlib.md5(str(k).encode()).hexdigest()[:12], '',
                     k[0], k[1], '', k[2], 'core', k[3], k[3], '', '', '') for k in kn])
    db.execute('DROP TABLE IF EXISTS chapters')
    db.execute('''CREATE TABLE chapters (id TEXT, grade INTEGER, grade_label TEXT,
        subject TEXT, chapter TEXT, term TEXT)''')
    ch = {(r['grade'], r['grade_label'], r['subject'], r['chapter']) for r in out}
    db.executemany('INSERT INTO chapters VALUES (?,?,?,?,?,?)',
                   [(None, k[0], k[1], k[2], k[3], '') for k in ch])
    # meta
    db.execute('CREATE TABLE IF NOT EXISTS meta (k TEXT PRIMARY KEY, v TEXT)')
    db.execute('DELETE FROM meta')
    qs = collections.Counter(r['qtype'] for r in out)
    meta = {
        'version': VERSION, 'updatedAt': '2026-11-10', 'schema': '3',
        'questions': str(len(out)), 'knowledge': str(len(kn)),
        'subjects': '3', 'grades': '6',
        'source': 'self-generated + compiled from public Internet sources + quiz-challenge',
        'sourceFiles': '4733',
        'qtypeKnowledge': str(qs['knowledge']), 'qtypeRecognition': str(qs['recognition']),
        'note': ('bank-v8：2,932 道占位符知识点题逐题按课标重定级并补真实纲目名，'
                 '废止 difficulty 系数兜底定级；剔除确证劣质题；整合 quiz-challenge '
                 '去重验算后的优质算式题并标记为「通用题（无教材版本）」。'),
        'licenseNote': old_meta.get('licenseNote',
            '部分题目整理自互联网公开渠道，仅供个人非商业学习使用；详见 docs/DATA_SOURCES.md'),
        'gradeSemantics': 'grade 为「最低适用年级」保守下界，非精确年级；无法确证的题标 generic 且不参与教材单元过滤',
        'genericQuestions': str(sum(1 for r in out if r['syllabus_scope'] == 'generic')),
        'importedQuestions': str(len(imported)),
        'droppedQuestions': str(len(dropped)),
    }
    db.executemany('INSERT INTO meta VALUES (?,?)', list(meta.items()))
    db.commit()
    # 原子替换前再自检一次，临时库不完整就原样报错、shipped 库不受影响
    n = db.execute('select count(*) from questions').fetchone()[0]
    if n != len(out):
        db.close(); print('APPLY_FAIL temp db incomplete %d/%d' % (n, len(out))); sys.exit(1)
    db.close()
    os.replace(TMP, DB)
    io.open(VER, 'w', encoding='utf-8').write(VERSION + '\n')
    os.makedirs(os.path.dirname(DROP_CSV), exist_ok=True)
    with io.open(DROP_CSV, 'w', encoding='utf-8-sig', newline='') as f:
        w = csv.DictWriter(f, fieldnames=['id', 'grade', 'subject', 'topic',
                                          'flags', 'reason', 'stem'])
        w.writeheader(); [w.writerow(x) for x in dropped]
    with io.open(IMPORT_CSV, 'w', encoding='utf-8-sig', newline='') as f:
        w = csv.DictWriter(f, fieldnames=['id', 'grade', 'topic', 'text',
                                          'answer_index', 'source'])
        w.writeheader()
        for x in imported:
            w.writerow({k: x.get(k) for k in w.fieldnames})
    print('APPLY total=%d (was %d) dropped=%d imported=%d knowledge=%d chapters=%d'
          % (len(out), len(rows), len(dropped), len(imported), len(kn), len(ch)))
    print('QC_STATS', dict(qstats))
    print('SCOPE', dict(collections.Counter(r['syllabus_scope'] for r in out)))
    print('GRADE', sorted(collections.Counter(r['grade'] for r in out).items()))
    print('BANK_V8_APPLY_OK')


def check():
    d = connect()
    cols = [r[1] for r in d.execute('pragma table_info(questions)')]
    for c in ('syllabus_scope', 'grade_basis', 'grade_verified'):
        if c not in cols:
            print('CHECK_FAIL missing column', c); sys.exit(1)
    n = d.execute('select count(*) from questions').fetchone()[0]
    v = d.execute("select v from meta where k='version'").fetchone()[0]
    stamp = io.open(VER, encoding='utf-8').read().strip()
    ph = d.execute("select count(*) from questions where topic like '知识点 7%'").fetchone()[0]
    bad = d.execute("select count(*) from questions where grade_basis like '%difficulty%'").fetchone()[0]
    dropped = [r['id'] for r in csv.DictReader(io.open(DROP_CSV, encoding='utf-8-sig'))]
    alive = [q for q in dropped if d.execute(
        'select 1 from questions where id=?', (q,)).fetchone()]
    imported = list(csv.DictReader(io.open(IMPORT_CSV, encoding='utf-8-sig')))
    missing = [q['id'] for q in imported if not d.execute(
        'select 1 from questions where id=?', (q['id'],)).fetchone()]
    gen = d.execute("select count(*) from questions where syllabus_scope='generic'").fetchone()[0]
    print('CHECK total=%d version=%s stamp=%s placeholder_left=%d difficulty_based=%d '
          'dropped_alive=%d import_missing=%d generic=%d'
          % (n, v, stamp, ph, bad, len(alive), len(missing), gen))
    # 题库已整体替换为 bank-v9；本脚本只做历史归档校验：
    # 占位符知识点已清零、19 道剔除题仍不在库中、题数与版本戳为 v9。
    if ph or bad or alive or n < 9000 or 'bank-v9' not in v:
        print('CHECK_FAIL', alive[:5], n, v); sys.exit(1)
    print('BANK_V8_ARCHIVE_OK')


if __name__ == '__main__':
    p = argparse.ArgumentParser()
    p.add_argument('--audit', action='store_true')
    p.add_argument('--apply', action='store_true')
    p.add_argument('--check', action='store_true')
    a = p.parse_args()
    if a.audit: audit()
    elif a.apply: apply()
    elif a.check: check()
    else: p.print_help()
