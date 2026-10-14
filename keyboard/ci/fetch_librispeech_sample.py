#!/usr/bin/env python3
"""Fetch a fixed LibriSpeech sample without storing the whole archive.

usage: fetch_librispeech_sample.py <out_root> [split=test-clean] [n=200]

Streams https://www.openslr.org/resources/12/<split>.tar.gz once and keeps only the first N utterances sorted by id
(the same subset keyboard/ci/wer_bench.py takes), written in the standard layout
<out_root>/LibriSpeech/<split>/<speaker>/<chapter>/{<uid>.flac,<speaker>-<chapter>.trans.txt}, so wer_bench.py runs on
it unchanged. Disk use is about 30 MB for 200 test-clean clips instead of 350 MB. LibriSpeech is CC BY 4.0 (OpenSLR 12).
"""
import heapq, os, sys, tarfile, urllib.request

out = sys.argv[1]
split = sys.argv[2] if len(sys.argv) > 2 else "test-clean"
n = int(sys.argv[3]) if len(sys.argv) > 3 else 200
url = f"https://www.openslr.org/resources/12/{split}.tar.gz"

keep = []          # max-heap (negated ids) of the n smallest utterance ids seen so far, with their FLAC bytes
trans = {}
with urllib.request.urlopen(url, timeout=120) as r, tarfile.open(fileobj=r, mode="r|gz") as tf:
    for m in tf:
        name = os.path.basename(m.name)
        if name.endswith(".flac"):
            uid = name[:-5]
            if len(keep) < n or uid < keep[0][1]:
                data = tf.extractfile(m).read()
                item = ([-ord(c) for c in uid], uid, data)
                if len(keep) < n:
                    heapq.heappush(keep, item)
                else:
                    heapq.heapreplace(keep, item)
        elif name.endswith(".trans.txt"):
            for line in tf.extractfile(m).read().decode().splitlines():
                k, t = line.split(" ", 1)
                trans[k] = t

chosen = sorted((uid, data) for _, uid, data in keep)
by_dir = {}
for uid, data in chosen:
    spk, chap, _ = uid.split("-")
    d = os.path.join(out, "LibriSpeech", split, spk, chap)
    os.makedirs(d, exist_ok=True)
    open(os.path.join(d, uid + ".flac"), "wb").write(data)
    by_dir.setdefault((d, spk, chap), []).append(uid)
for (d, spk, chap), uids in by_dir.items():
    with open(os.path.join(d, f"{spk}-{chap}.trans.txt"), "w") as f:
        for uid in sorted(uids):
            f.write(f"{uid} {trans[uid]}\n")
print(f"{split}: kept {len(chosen)} utterances, first {chosen[0][0]}, last {chosen[-1][0]}")
