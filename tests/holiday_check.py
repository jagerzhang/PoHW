#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""全年日期类型判定回归：与 HolidayCalendar.java 的判定顺序一一对应。

判定顺序（不可调换）：例外日表 -> 按星期推算 -> 覆盖年份外返回 UNKNOWN。
重点锁死调休补班日：那些是周末，但按国务院通知要上学，纯星期模型必错。
"""
import datetime
import io
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DOC = os.path.join(ROOT, 'app/assets/holidays.json')

SCHOOL, HOME, UNKNOWN = 'SCHOOL', 'HOME', 'UNKNOWN'
MAKEUP = {
    2026: ('2026-01-04', '2026-02-14', '2026-02-28',
           '2026-05-09', '2026-09-20', '2026-10-10'),
    2027: ('2027-01-31', '2027-02-14', '2027-05-08',
           '2027-09-26', '2027-10-09'),
}


def fail(msg):
    print('HOLIDAY_CHECK_FAIL: ' + msg)
    sys.exit(1)


def load():
    d = json.load(io.open(DOC, encoding='utf-8'))
    table = {}
    for x in d['days']:
        table[x['d']] = int(x['off'])
    return set(d['years']), table


def day_type(years, table, day):
    key = day.isoformat()
    if day.year not in years:
        return UNKNOWN
    if key in table:
        return HOME if table[key] == 1 else SCHOOL
    return HOME if day.weekday() >= 5 else SCHOOL


def main():
    years, table = load()
    checked = 0
    school = home = 0
    for year in sorted(years):
        day = datetime.date(year, 1, 1)
        end = datetime.date(year + 1, 1, 1)
        while day < end:
            t = day_type(years, table, day)
            if t == UNKNOWN:
                fail('covered year %d returned UNKNOWN at %s' % (year, day))
            if t == SCHOOL:
                school += 1
            else:
                home += 1
            checked += 1
            day += datetime.timedelta(days=1)

    # 负向断言 1：补班日（周末）必须是上学日。
    for year, dates in MAKEUP.items():
        for s in dates:
            d = datetime.date(*map(int, s.split('-')))
            if day_type(years, table, d) != SCHOOL:
                fail('makeup workday %s must be SCHOOL' % s)
            if d.weekday() < 5:
                fail('makeup workday %s is no longer a weekend, data stale?' % s)

    # 负向断言 2：法定放假日里的周一至周五必须是在家日。
    weekday_holidays = 0
    for k, off in table.items():
        if off != 1:
            continue
        d = datetime.date(*map(int, k.split('-')))
        if d.weekday() < 5:
            weekday_holidays += 1
            if day_type(years, table, d) != HOME:
                fail('weekday holiday %s must be HOME' % k)
    if weekday_holidays < 10:
        fail('expected many weekday holidays, got %d' % weekday_holidays)

    # 边界：覆盖年份外必须 UNKNOWN，调用方需降级到星期掩码并提示升级。
    if day_type(years, table, datetime.date(2028, 1, 1)) != UNKNOWN:
        fail('out-of-coverage date must be UNKNOWN')
    if day_type(years, table, datetime.date(2025, 12, 31)) != UNKNOWN:
        fail('out-of-coverage date must be UNKNOWN')

    # 普通周一 / 周六的基准，防止有人把星期映射写反。
    if day_type(years, table, datetime.date(2026, 3, 2)) != SCHOOL:
        fail('ordinary Monday must be SCHOOL')
    if day_type(years, table, datetime.date(2026, 3, 7)) != HOME:
        fail('ordinary Saturday must be HOME')

    print('HOLIDAY_CHECK_OK days=%d school=%d home=%d makeup=%d '
          'weekday_holidays=%d out_of_coverage=UNKNOWN'
          % (checked, school, home,
             sum(len(v) for v in MAKEUP.values()), weekday_holidays))


if __name__ == '__main__':
    main()
