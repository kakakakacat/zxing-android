#!/usr/bin/env python3
"""Rewrite Java package/import statements for a package move.

Only `package` and `import` lines are touched. String literals are left alone on
purpose: this project's Intent action names (com.google.zxing.client.android.SCAN and
friends) are the public contract and are strings, not class names — a blanket
search-and-replace would silently break every existing caller.

Also used by tools/maint/sync-core-from-upstream.sh to re-apply this fork's namespace
after pulling fresh core sources from upstream.

Usage:
  python3 tools/maint/rename_packages.py --map OLD=NEW [--map OLD=NEW ...] PATH [PATH ...]
"""
import argparse
import io
import os
import re
import sys

LINE = re.compile(r'^(package|import)\s+(static\s+)?([\w.]+)\s*;')


def rewrite(path, mapping):
    src = io.open(path, encoding='utf-8').read()
    out = []
    touched = False
    for line in src.split('\n'):
        m = LINE.match(line)
        if m:
            fqn = m.group(3)
            for old, new in mapping:
                if fqn == old or fqn.startswith(old + '.'):
                    line = line.replace(fqn, new + fqn[len(old):], 1)
                    touched = True
                    break
        out.append(line)
    if touched:
        io.open(path, 'w', encoding='utf-8').write('\n'.join(out))
    return touched


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--map', action='append', required=True,
                    help='OLD=NEW package prefix pair; longest prefix wins')
    ap.add_argument('paths', nargs='+')
    args = ap.parse_args()

    mapping = []
    for pair in args.map:
        if '=' not in pair:
            sys.exit('--map expects OLD=NEW, got %r' % pair)
        old, new = pair.split('=', 1)
        mapping.append((old, new))
    # Longest prefix first, so a.b.c is not swallowed by a rule for a.b.
    mapping.sort(key=lambda p: len(p[0]), reverse=True)

    changed = 0
    for root in args.paths:
        for dirpath, dirs, files in os.walk(root):
            dirs[:] = [d for d in dirs if d != 'build']
            for name in files:
                if name.endswith('.java') and rewrite(os.path.join(dirpath, name), mapping):
                    changed += 1
    print('rewrote package/import statements in %d files' % changed)


if __name__ == '__main__':
    main()
