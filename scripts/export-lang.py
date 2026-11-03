#!/usr/bin/env python3
"""Writes static/lang/{en,si,ta}.json from the built-in translations in js/i18n.js.

The interface keeps its copy inside js/i18n.js so a page still works when the browser cannot fetch
anything, and it merges lang/<code>.json on top when that file is reachable. This script keeps the
JSON files in step with the JavaScript, so a translator only ever edits JSON.

Run:  python3 scripts/export-lang.py
"""
import json
import re
from pathlib import Path

BASE = Path(__file__).resolve().parent.parent / "src/main/resources/static"
SRC = BASE / "js/i18n.js"
OUT = BASE / "lang"

PAIR = re.compile(r"'([\w.]+)'\s*:\s*'((?:[^'\\]|\\.)*)'")


def parse_dicts(text):
    """Reads { en: {...}, si: {...}, ta: {...} } out of the JavaScript file.

    Every entry is a plain 'key': 'value' pair on its own line, so a small regex reader is enough
    and the script does not need a JavaScript runtime.
    """
    start = text.index("const DICT = {")
    end = text.index("\n  };", start)
    block = text[start:end]
    dicts, current = {}, None
    for line in block.splitlines():
        head = re.match(r"\s*(en|si|ta)\s*:\s*\{", line)
        if head:
            current = head.group(1)
            dicts[current] = {}
            continue
        if current and re.match(r"\s*\},?\s*$", line):
            current = None
            continue
        if current:
            for key, value in PAIR.findall(line):
                dicts[current][key] = value.replace("\\'", "'")
    return dicts


def main():
    dicts = parse_dicts(SRC.read_text(encoding="utf-8"))
    OUT.mkdir(parents=True, exist_ok=True)
    for code in ("en", "si", "ta"):
        entries = dicts.get(code) or {}
        path = OUT / f"{code}.json"
        path.write_text(json.dumps(entries, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
                        encoding="utf-8")
        print(f"wrote {path.relative_to(BASE.parent.parent.parent.parent)} ({len(entries)} keys)")


if __name__ == "__main__":
    main()
