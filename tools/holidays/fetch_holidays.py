#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成 / 校验 app/assets/holidays.json（PoHW 内置节假日日历）。

数据来源：NateScarlet/holiday-cn（MIT），该仓库每日抓取国务院办公厅
《关于 XXXX 年部分节假日安排的通知》生成结构化 JSON。这里只保留**例外日**
（法定放假日 + 调休补班日），正常周末不入库，由 HolidayCalendar 按星期推算。

用法（发版机上）：
    python3 tools/holidays/fetch_holidays.py --raw-dir /tmp/hc   # 生成
    python3 tools/holidays/fetch_holidays.py --check              # 只读门禁

--check 不联网，全部断言基于仓库内的成品文件；生成需要显式给 --raw-dir
或联网（--fetch），避免构建阶段偷偷访问外部服务。
"""
import argparse
import hashlib
import io
import json
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, 'app/assets/holidays.json')
VERSION = os.path.join(ROOT, 'app/assets/holidays.json.version')
DATA_VERSION = '2026.11.11-holiday-cn'
YEARS = (2026, 2027)
LICENSE = 'MIT (NateScarlet/holiday-cn)'
BASE = 'https://raw.githubusercontent.com/NateScarlet/holiday-cn/master/%d.json'

# 黄金断言：来自国务院办公厅通知原文的调休补班日（周末但要上学）。
# 这些日子是「按星期算」模型必然出错的地方，也是本功能相对竞品的差异点。
MAKEUP = {
    2026: ('2026-01-04', '2026-02-14', '2026-02-28',
           '2026-05-09', '2026-09-20', '2026-10-10'),
    2027: ('2027-01-31', '2027-02-14', '2027-05-08',
           '2027-09-26', '2027-10-09'),
}
OFF_COUNT = {2026: 33, 2027: 29}


def fail(msg):
    print('HOLIDAYS_FAIL: ' + msg)
    sys.exit(1)


def read_raw(year, raw_dir=None, fetch=False):
    p = os.path.join(raw_dir or '', '%d.json' % year)
    if raw_dir and os.path.isfile(p):
        return io.open(p, encoding='utf-8').read()
    if fetch:
        return subprocess.check_output(
            ['curl', '-sS', '--max-time', '30', BASE % year]).decode('utf-8')
    fail('missing raw %s (pass --raw-dir or --fetch)' % p)


def build(raw_dir=None, fetch=False):
    papers = []
    days = []
    for year in YEARS:
        d = json.loads(read_raw(year, raw_dir, fetch))
        if d.get('year') != year:
            fail('raw year mismatch: %r' % d.get('year'))
        for u in d.get('papers', []):
            if u not in papers:
                papers.append(u)
        for x in d.get('days', []):
            date = x['date']
            if not re.match(r'^\d{4}-\d{2}-\d{2}$', date):
                fail('bad date %r' % date)
            days.append({'d': date, 'off': 1 if x.get('isOffDay') else 0,
                         'n': x.get('name', '')})
    days.sort(key=lambda x: x['d'])
    doc = {
        'version': DATA_VERSION,
        'source': '国务院办公厅节假日安排通知 / NateScarlet/holiday-cn',
        'license': LICENSE,
        'papers': papers,
        'years': list(YEARS),
        'days': days,
    }
    io.open(OUT, 'w', encoding='utf-8').write(
        json.dumps(doc, ensure_ascii=False, indent=1) + '\n')
    io.open(VERSION, 'w', encoding='utf-8').write(DATA_VERSION + '\n')
    print('HOLIDAYS_OK wrote years=%s days=%d papers=%d sha=%s'
          % (YEARS, len(days), len(papers),
             hashlib.sha256(io.open(OUT, 'rb').read()).hexdigest()[:12]))


def check():
    if not os.path.isfile(OUT) or not os.path.isfile(VERSION):
        fail('missing holidays.json or version stamp')
    d = json.load(io.open(OUT, encoding='utf-8'))
    stamp = io.open(VERSION, encoding='utf-8').read().strip()
    if d.get('version') != DATA_VERSION or stamp != DATA_VERSION:
        fail('version stamp drift: %s / %s' % (d.get('version'), stamp))
    if tuple(d.get('years', ())) != YEARS:
        fail('coverage years drift: %r' % d.get('years'))
    if LICENSE not in d.get('license', ''):
        fail('missing MIT source license')
    papers = d.get('papers', [])
    if len(papers) < len(YEARS) or not all('gov.cn' in u for u in papers):
        fail('papers must trace back to gov.cn notices')
    rows = d.get('days', [])
    by_date = {}
    for x in rows:
        date = x.get('d', '')
        if not re.match(r'^\d{4}-\d{2}-\d{2}$', date):
            fail('bad date %r' % date)
        if date in by_date:
            fail('duplicate date %s' % date)
        if x.get('off') not in (0, 1):
            fail('bad off flag at %s' % date)
        by_date[date] = x['off']
    for year in YEARS:
        off = [k for k, v in by_date.items()
               if k.startswith('%d-' % year) and v == 1]
        work = [k for k, v in by_date.items()
                if k.startswith('%d-' % year) and v == 0]
        if len(off) != OFF_COUNT[year]:
            fail('%d off days %d != %d' % (year, len(off), OFF_COUNT[year]))
        if tuple(sorted(work)) != tuple(sorted(MAKEUP[year])):
            fail('%d makeup workdays mismatch: %r' % (year, sorted(work)))
    # 关键负向断言：补班日必须是 off=0，若有人把 isOffDay 判断写反会立刻炸。
    for year, dates in MAKEUP.items():
        for date in dates:
            if by_date.get(date) != 0:
                fail('makeup workday %s must be off=0' % date)
    print('HOLIDAYS_CHECK_OK version=%s years=%s days=%d makeup=%d papers=%d'
          % (DATA_VERSION, YEARS, len(rows),
             sum(len(v) for v in MAKEUP.values()), len(papers)))


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--check', action='store_true')
    p.add_argument('--raw-dir')
    p.add_argument('--fetch', action='store_true')
    a = p.parse_args()
    if a.check:
        return check()
    return build(a.raw_dir, a.fetch)


if __name__ == '__main__':
    main()
