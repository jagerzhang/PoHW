#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Android 4.0 低配包硬门禁：dex / manifest / 源码不得出现 API>14 的类或主题。

用 SDK 自带的 api-versions.xml 逐类核对引入版本；内部类按完整名（含 $）优先查，
否则外部类的 since 会掩盖 API 16 才加入的 Notification$BigTextStyle。

用法：
  python3 tests/android4_compat_check.py           # 构建前查源码与 manifest
  python3 tests/android4_compat_check.py --apk X  # 构建后查成品 dex（更权威）
"""
import io
import os
import re
import sys
import xml.etree.ElementTree as ET
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, 'app/src/com/jager/tvquiz')
MANIFEST = os.path.join(ROOT, 'app/AndroidManifest.xml')
MIN_SDK = 14
MIN_CODE = 49
API_XML = os.path.join(os.environ.get('ANDROID_HOME', '/data/workspace/apkbuild/sdk'),
                       'platforms/android-34/data/api-versions.xml')


def fail(msg):
    print('ANDROID4_COMPAT_FAIL: ' + msg)
    sys.exit(1)


def since_map():
    if not os.path.isfile(API_XML):
        fail('missing ' + API_XML)
    m = {}
    for c in ET.parse(API_XML).getroot().iter('class'):
        name, s = c.get('name'), c.get('since')
        if name and s:
            m[name] = int(s)
    return m


def bad_refs(names, since):
    out = []
    for c in sorted(names):
        lv = since.get(c)
        if lv is None:
            lv = since.get(c.split('$')[0])
        if lv and lv > MIN_SDK:
            out.append((lv, c))
    return out


def check_source(since):
    mf = io.open(MANIFEST, encoding='utf-8').read()
    if 'Theme.Material' in mf:
        fail('manifest still uses the API 21 Material theme')
    if 'Theme.Holo.Light.NoActionBar' not in mf:
        fail('manifest must use an API 14 compatible Holo theme')
    if 'android:minSdkVersion="14"' not in mf:
        fail('manifest minSdkVersion must be 14')
    code = re.search(r'android:versionCode="(\d+)"', mf)
    if not code or int(code.group(1)) < MIN_CODE:
        fail('versionCode must be >= %d so devices on 48 can upgrade' % MIN_CODE)

    names = set()
    for f in os.listdir(SRC):
        if not f.endswith('.java'):
            continue
        s = io.open(os.path.join(SRC, f), encoding='utf-8').read()
        for m in re.finditer(r'^import\s+(android\.[A-Za-z0-9_.$]+);', s, re.M):
            names.add(m.group(1).replace('.', '/'))
        # 注意：Class.forName("android.app.NotificationChannel") 这类反射字符串
        # 不是类型引用，不进 dex 的类型常量池，成品 --apk 门禁会做权威校验。
    bad = bad_refs(names, since)
    if bad:
        fail('source references API>%d classes: %s' % (
            MIN_SDK, ', '.join('%s(%d)' % (c, lv) for lv, c in bad)))
    for token in ('extends JobService', 'new NotificationChannel',
                  'new AudioFocusRequest', 'new AudioAttributes',
                  'Notification.BigTextStyle', 'Notification.Builder(c, CHANNEL)'):
        for f in os.listdir(SRC):
            if f.endswith('.java') and token in io.open(
                    os.path.join(SRC, f), encoding='utf-8').read():
                fail('forbidden API>14 usage %r in %s' % (token, f))
    print('ANDROID4_COMPAT_OK source minSdk=14 classes-checked=%d' % len(names))
    return 0


def check_apk(path, since):
    if not os.path.isfile(path):
        fail('missing APK ' + path)
    with zipfile.ZipFile(path) as z:
        dex = z.read('classes.dex')
    names = set(x.decode() for x in re.findall(
        rb'L(android/[A-Za-z0-9_/$]+);', dex))
    bad = bad_refs(names, since)
    if bad:
        fail('dex references API>%d classes: %s' % (
            MIN_SDK, ', '.join('%s(%d)' % (c, lv) for lv, c in bad)))
    print('ANDROID4_COMPAT_OK apk minSdk=14 dex-classes=%d high-api=0' % len(names))
    return 0


def main():
    since = since_map()
    if '--apk' in sys.argv:
        i = sys.argv.index('--apk')
        if i + 1 >= len(sys.argv):
            fail('--apk needs a path')
        return check_apk(sys.argv[i + 1], since)
    return check_source(since)


if __name__ == '__main__':
    sys.exit(main())
