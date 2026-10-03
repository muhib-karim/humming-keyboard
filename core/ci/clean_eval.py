#!/usr/bin/env python3
"""Scores Clean mode on CLEAN_SET.md: exact match and character edit distance per utterance.
usage: clean_eval.py <humming-core CLI>   (the CLI's `cleanup` command is the rule-only pass)"""
import os, re, subprocess, sys

def load(path):
    rows = []
    for line in open(path, encoding="utf-8"):
        m = re.match(r"^\| (\d+) \| `(.*)` \| `(.*)` \|", line)
        if m:
            rows.append((m.group(2), m.group(3).replace("\\n", "\n")))
    return rows

def lev(a, b):
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1]

if __name__ == "__main__":
    cli = sys.argv[1]
    rows = load(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "CLEAN_SET.md"))
    exact = edits = 0
    for i, (inp, want) in enumerate(rows, 1):
        got = subprocess.run([cli, "cleanup", inp], capture_output=True, text=True, timeout=30).stdout.rstrip("\n")
        d = lev(got, want)
        exact += got == want
        edits += d
        print(f"{i:2d} {'OK  ' if d == 0 else 'MISS'} d={d:3d} {got!r}")
    print(f"exact {exact}/{len(rows)}  char edits {edits}  (sum of target lengths {sum(len(w) for _, w in rows)})")
