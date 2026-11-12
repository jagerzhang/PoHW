#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Build and verify a PoHW self-hosted online question bank.

The App side contract lives in BankRepository.java / QuizEngine.Question.fromBank.
This tool keeps docs/online-bank/index.json in sync with the pack_*.json files:
it recomputes every sha256 and count, and --check fails loudly when they drift.

  python3 tools/onlinebank/build_index.py --version 2026-11-12
  python3 tools/onlinebank/build_index.py --check
"""

import argparse
import datetime
import hashlib
import io
import json
import os
import re
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
DEFAULT_DIR = os.path.join(ROOT, "docs", "online-bank")
MAX_BYTES = 4 * 1024 * 1024
SAFE_ID = re.compile(r"^[A-Za-z0-9_-]+$")
HEX64 = re.compile(r"^[0-9a-fA-F]{64}$")


class BankError(Exception):
    pass


def read_bytes(path):
    with open(path, "rb") as f:
        data = f.read()
    if len(data) > MAX_BYTES:
        raise BankError("%s exceeds the App's 4 MB download limit" % os.path.basename(path))
    return data


def parse_json(data, name):
    try:
        return json.loads(data.decode("utf-8"))
    except Exception as e:
        raise BankError("%s is not valid UTF-8 JSON: %s" % (name, e))


def require(obj, key, kind, where):
    if key not in obj:
        raise BankError("%s is missing '%s'" % (where, key))
    val = obj[key]
    if kind == "int":
        if not isinstance(val, int) or isinstance(val, bool):
            raise BankError("%s.%s must be an integer" % (where, key))
    elif kind == "str":
        if not isinstance(val, str) or not val.strip():
            raise BankError("%s.%s must be a non-empty string" % (where, key))
    return val


def validate_question(q, where):
    if not isinstance(q, dict):
        raise BankError("%s is not an object" % where)
    text = q.get("text", "")
    # QuizEngine.fromBank drops the question when text.trim().length() < 3.
    if not isinstance(text, str) or len(text.strip()) < 3:
        raise BankError("%s.text must have at least 3 characters" % where)
    opts = q.get("options")
    if not isinstance(opts, list) or len(opts) != 4:
        raise BankError("%s.options must be an array of exactly 4 strings" % where)
    for o in opts:
        if not isinstance(o, str) or not o.strip():
            raise BankError("%s.options contains an empty or non-string value" % where)
    if len(set(opts)) != 4:
        raise BankError("%s.options contains duplicates (App would discard it)" % where)
    ai = q.get("answerIndex")
    if not isinstance(ai, int) or isinstance(ai, bool) or ai < 0 or ai > 3:
        raise BankError("%s.answerIndex must be 0..3 (camelCase, NOT answer_index)" % where)
    for optional in ("topic", "explain"):
        if optional in q and not isinstance(q[optional], str):
            raise BankError("%s.%s must be a string" % (where, optional))


def validate_pack(pack, name):
    where = name
    if not isinstance(pack, dict):
        raise BankError("%s must be a JSON object" % where)
    if pack.get("schema") != 1:
        raise BankError("%s.schema must be 1" % where)
    pack_id = require(pack, "id", "str", where)
    if not SAFE_ID.match(pack_id):
        raise BankError("%s.id may only contain [A-Za-z0-9_-] (App caches it as pack_<id>.json)" % where)
    require(pack, "grade", "int", where)
    require(pack, "edition", "str", where)
    require(pack, "subject", "str", where)
    questions = pack.get("questions")
    if not isinstance(questions, list) or not questions:
        raise BankError("%s.questions must be a non-empty array" % where)
    for i, q in enumerate(questions):
        validate_question(q, "%s.questions[%d]" % (where, i))
    return {"id": pack_id, "grade": pack["grade"], "edition": pack["edition"],
            "subject": pack["subject"], "url": name,
            "sha256": "", "count": len(questions)}


def build_entries(directory):
    names = sorted(n for n in os.listdir(directory)
                   if n.startswith("pack_") and n.endswith(".json"))
    if not names:
        raise BankError("no pack_*.json found in %s" % directory)
    entries = []
    total = 0
    for name in names:
        data = read_bytes(os.path.join(directory, name))
        pack = parse_json(data, name)
        entry = validate_pack(pack, name)
        entry["sha256"] = hashlib.sha256(data).hexdigest()
        entries.append(entry)
        total += entry["count"]
    ids = [e["id"] for e in entries]
    if len(set(ids)) != len(ids):
        raise BankError("duplicate pack id: %s" % ids)
    return entries, total


def main():
    ap = argparse.ArgumentParser(description=__doc__,
                                 formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--dir", default=DEFAULT_DIR, help="directory holding index.json and pack_*.json")
    ap.add_argument("--version", help="bank version, e.g. 2026-11-12; change it to trigger a refresh")
    ap.add_argument("--updated-at", help="human-readable update date shown in the App status row")
    ap.add_argument("--check", action="store_true", help="verify index.json without writing anything")
    args = ap.parse_args()

    directory = os.path.abspath(args.dir)
    if not os.path.isdir(directory):
        raise BankError("directory not found: %s" % directory)
    entries, total = build_entries(directory)
    index_path = os.path.join(directory, "index.json")

    if args.check:
        if not os.path.isfile(index_path):
            raise BankError("index.json is missing")
        idx = parse_json(read_bytes(index_path), "index.json")
        if idx.get("schema") != 1:
            raise BankError("index.json.schema must be 1")
        require(idx, "version", "str", "index.json")
        current = idx.get("packs")
        if current != entries:
            raise BankError("index.json packs are stale; run build_index.py again\n"
                            "  expected: %s\n  actual:   %s"
                            % (json.dumps(entries, ensure_ascii=False),
                               json.dumps(current, ensure_ascii=False)))
        for e in entries:
            url = e["url"]
            if url.startswith(("http://", "https://")):
                raise BankError("%s uses an absolute URL; same-host relative paths are safer" % url)
        print("ONLINE_BANK_CHECK_OK packs=%d questions=%d version=%s"
              % (len(entries), total, idx["version"]))
        return 0

    if os.path.isfile(index_path):
        old = parse_json(read_bytes(index_path), "index.json")
    else:
        old = {}
    version = args.version or old.get("version") or datetime.date.today().isoformat()
    updated = args.updated_at or datetime.date.today().isoformat()
    idx = {"schema": 1, "version": version, "updatedAt": updated, "packs": entries}
    payload = json.dumps(idx, ensure_ascii=False, indent=2) + "\n"
    with io.open(index_path, "w", encoding="utf-8", newline="\n") as f:
        f.write(payload)
    print("ONLINE_BANK_WRITE_OK packs=%d questions=%d version=%s -> %s"
          % (len(entries), total, version, index_path))
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except BankError as e:
        print("ONLINE_BANK_FAIL: %s" % e)
        sys.exit(1)
