# Humming (Apache-2.0). Usage: python demote_english.py LANG...  (run from keyboard/lm, corpora in corpus/)
# The AOSP word lists for German, Spanish, French, Italian and Portuguese carry some English words with high
# frequencies ("in", "for", "and", "is"), so with no usable context the strip could offer English words while
# typing Portuguese. A lowercase word that is common in the English list, common in this list, but (almost) never
# written in this language's own sentences (Tatoeba + Common Voice) keeps its place in the dictionary (it can still
# be typed and corrected to) with a low frequency. Rewrites ../app/src/main/assets/predict/lexicon_LANG.tsv in place.
import collections, re, sys
from hlm_common import raw_sentences
P = "../app/src/main/assets/predict/"
LOW = 60
en = {}
for line in open(P + "lexicon_en.tsv", encoding="utf-8"):
    p = line.rstrip("\n").split("\t")
    if len(p) >= 2 and p[1].strip().isdigit():
        en[p[0]] = int(p[1])
for lang in sys.argv[1:]:
    cnt = collections.Counter()
    for n, (src, s) in enumerate(raw_sentences(lang)):
        cnt.update(re.findall(r"[^\W\d_]+", s.lower()))
        if n >= 300000:
            break
    lines = open(P + f"lexicon_{lang}.tsv", encoding="utf-8").read().split("\n")
    out, demoted = [], []
    for line in lines:
        p = line.split("\t")
        if len(p) >= 2 and p[1].strip().isdigit():
            w, f = p[0], int(p[1])
            if w.islower() and len(w) <= 8 and f >= 105 and en.get(w, 0) >= 140 and cnt[w] <= 10:
                p[1] = str(LOW); demoted.append(w)
        out.append("\t".join(p))
    open(P + f"lexicon_{lang}.tsv", "w", encoding="utf-8").write("\n".join(out))
    print(lang, "demoted", len(demoted), demoted, flush=True)
