#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""生成 / 校验站点在线更新清单（零第三方依赖）。

发布前（主线与低配 APK 都构建完成后）：
  python3 tools/publish/update_manifest.py --write \
      --version 1.2.5 --main-code 39 --legacy-code 40 \
      --main-apk dist/PoHW-v1.2.5.apk \
      --legacy-apk dist/PoHW-v1.2.5-legacy.apk --notes "在线版本更新"

构建门禁（只读，不写文件）：
  python3 tools/publish/update_manifest.py --check
"""
import argparse
import datetime
import hashlib
import io
import json
import os
import re
import shutil
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
SITE = os.path.join(ROOT, 'site')
DL = os.path.join(SITE, 'dl')
MANIFEST = os.path.join(SITE, 'update.json')
MF = os.path.join(ROOT, 'app', 'AndroidManifest.xml')
BASE = 'https://pohw.zhangge.net/dl/'


def fail(msg):
    print('UPDATE_MANIFEST_FAIL: ' + msg)
    sys.exit(1)


def sha_and_size(path):
    h = hashlib.sha256()
    n = 0
    with open(path, 'rb') as f:
        while True:
            b = f.read(8192)
            if not b:
                break
            h.update(b)
            n += len(b)
    return h.hexdigest(), n


def manifest_version():
    s = io.open(MF, encoding='utf-8').read()
    name = re.search(r'android:versionName="([^"]+)"', s)
    code = re.search(r'android:versionCode="(\d+)"', s)
    if not name or not code:
        fail('cannot read version from manifest')
    return name.group(1), int(code.group(1))


def channel(version, code, min_sdk, apk_path, suffix, notes):
    sha, size = sha_and_size(apk_path)
    versioned = 'PoHW-%s%s.apk' % (version, suffix)
    latest = 'latest-%s.apk' % ('legacy' if suffix else 'main')
    doc = {
        'versionName': version,
        'versionCode': code,
        'minSdk': min_sdk,
        'latest': BASE + latest,
        'versioned': BASE + versioned,
        'sha256': sha,
        'size': size,
        'notes': notes,
    }
    return doc, versioned, latest


def write(args):
    legacy_version = args.legacy_version or args.version
    if not os.path.isdir(DL):
        os.makedirs(DL)
    main, main_ver, main_latest = channel(
        args.version, args.main_code, 21, args.main_apk, '', args.notes)
    legacy, leg_ver, leg_latest = channel(
        legacy_version, args.legacy_code, 14, args.legacy_apk, '-legacy', args.notes)
    doc = {
        'schema': 1,
        'publishedAt': args.date,
        'main': main,
        'legacy': legacy,
    }
    io.open(MANIFEST, 'w', encoding='utf-8').write(
        json.dumps(doc, ensure_ascii=False, indent=2) + '\n')
    for src, names in ((args.main_apk, (main_ver, main_latest)),
                       (args.legacy_apk, (leg_ver, leg_latest))):
        for name in names:
            shutil.copyfile(src, os.path.join(DL, name))
    keep = set((main_ver, main_latest, leg_ver, leg_latest))
    for f in os.listdir(DL):
        if f.startswith('PoHW-') and f.endswith('.apk') and f not in keep:
            os.remove(os.path.join(DL, f))
    print('UPDATE_MANIFEST_OK main=%d legacy=%d files=%d'
          % (args.main_code, args.legacy_code, len(os.listdir(DL))))


def check(strict=False):
    if not os.path.isfile(MANIFEST):
        fail('missing site/update.json')
    try:
        doc = json.load(io.open(MANIFEST, encoding='utf-8'))
    except Exception as e:
        fail('bad JSON: %s' % e)
    name, code = manifest_version()
    m = doc.get('main') or {}
    if strict and (m.get('versionName') != name or m.get('versionCode') != code):
        fail('main channel does not match manifest %s/%d' % (name, code))
    for key in ('main', 'legacy'):
        ch = doc.get(key) or {}
        for f in ('versionName', 'versionCode', 'minSdk', 'latest',
                  'versioned', 'sha256', 'size', 'notes'):
            if f not in ch:
                fail('%s missing field %s' % (key, f))
        if not re.match(r'^[0-9a-f]{64}$', str(ch['sha256'] or '')):
            fail('%s bad sha256' % key)
        if not str(ch['versioned'] or '').startswith(BASE) \
                or not str(ch['versioned'] or '').endswith('.apk'):
            fail('%s bad versioned url' % key)
        if not str(ch['latest'] or '').startswith(BASE):
            fail('%s bad latest url' % key)
        if not isinstance(ch['size'], int) or ch['size'] <= 0:
            fail('%s bad size' % key)
    apk = os.path.join(ROOT, 'dist', 'PoHW-%s.apk' % name)
    if strict and os.path.isfile(apk):
        sha, size = sha_and_size(apk)
        if sha != m['sha256'] or size != m['size']:
            fail('main APK does not match update.json')
    leg = doc.get('legacy') or {}
    if strict and os.path.isdir(DL):
        allowed = set((os.path.basename(m['versioned']),
                       os.path.basename(leg['versioned'])))
        for f in os.listdir(DL):
            if f.startswith('PoHW-') and f.endswith('.apk') and f not in allowed:
                fail('stale versioned apk in site/dl: ' + f)
    edge = os.path.join(SITE, 'edgeone.json')
    if not os.path.isfile(edge):
        fail('missing site/edgeone.json')
    if 'no-store' not in io.open(edge, encoding='utf-8').read():
        fail('edgeone.json must set no-store for update.json')
    print('UPDATE_MANIFEST_OK%s version=%s code=%d'
          % ('_STRICT' if strict else '', name, code))


def main():
    p = argparse.ArgumentParser()
    g = p.add_mutually_exclusive_group(required=True)
    g.add_argument('--write', action='store_true')
    g.add_argument('--check', action='store_true')
    g.add_argument('--check-strict', action='store_true')
    p.add_argument('--version')
    p.add_argument('--main-code', type=int)
    p.add_argument('--legacy-code', type=int)
    p.add_argument('--legacy-version')
    p.add_argument('--main-apk')
    p.add_argument('--legacy-apk')
    p.add_argument('--notes', default='')
    p.add_argument('--date', default=datetime.date.today().isoformat())
    a = p.parse_args()
    if a.check:
        return check()
    if a.check_strict:
        return check(True)
    for f in ('version', 'main_code', 'legacy_code', 'main_apk', 'legacy_apk'):
        if getattr(a, f) is None:
            fail('--write needs --' + f.replace('_', '-'))
    for f in (a.main_apk, a.legacy_apk):
        if not os.path.isfile(f):
            fail('missing apk: ' + f)
    name, code = manifest_version()
    if a.version != name or a.main_code != code:
        fail('args do not match manifest %s/%d' % (name, code))
    return write(a)


if __name__ == '__main__':
    main()
