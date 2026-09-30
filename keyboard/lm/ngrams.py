# Humming next-word tables (Apache-2.0 tooling). Usage: python ngrams.py LANG LEXICON_TSV OUT_DIR [--no-table]
# Counts one- and two-word contexts over the training split (Tatoeba CC BY 2.0 FR + Common Voice CC0) and writes
# the app's table format (context<TAB>total<TAB>word:count ..., sorted by UTF-8 bytes) plus the held-out
# benchmark sentences the unit tests read.
import collections, io, os, random, re, sys
from hlm_common import raw_sentences, is_test, clauses, load_lexicon, names

lang, lexpath, out = sys.argv[1], sys.argv[2], sys.argv[3]
no_table = "--no-table" in sys.argv
os.makedirs(out + "/bench", exist_ok=True)
lex = load_lexicon(lexpath)
top = sorted((w for w in lex if not lex[w][1]), key=lambda w: -lex[w][0])
V2 = set(top[:40000]); V3 = set(top[:12000])
NM = names(lang)
# Words to keep out of the shipped tables (a regex in HUMMING_LM_BANNED; nothing by default),
# matched case-insensitively inside a word; they are dropped from contexts, suggestions and benchmark sentences.
BANNED = re.compile(os.environ.get("HUMMING_LM_BANNED", r"(?!x)x"), re.I)  # regex of words to keep out
def banned(text):
    return any(BANNED.search(w) for w in text.split())
bi = collections.Counter(); tri = collections.Counter(); seen = set()
test = {"tatoeba": [], "cv": []}
n = ntok = 0
for src, s in raw_sentences(lang):
    h = hash(s)
    if h in seen:
        continue
    seen.add(h)
    if is_test(s):
        cl = clauses(s)
        if len(cl) == 1 and 3 <= len(cl[0]) <= 20 and "\t" not in s:
            test[src].append(s)
        continue
    if no_table:
        continue
    for ws in clauses(s):
        ws = ["<n>" if w in NM else (w if w in lex else "<u>") for w in ws]
        p2, p1 = None, "<s>"
        for w in ws:
            ntok += 1
            if p1 in V2 or p1 == "<s>":
                bi[(p1, w)] += 1
            if p2 is not None and (p2 in V3 or p2 == "<s>") and p1 in V3:
                tri[(p2, p1, w)] += 1
            p2, p1 = p1, w
    n += 1
    if n % 200000 == 0:
        print(lang, n, ntok, len(bi), len(tri), flush=True)
        if len(tri) > 5_000_000:
            tri = collections.Counter({k: v for k, v in tri.items() if v > 1})
print(lang, "sentences", n, "tokens", ntok, "held-out", {k: len(v) for k, v in test.items()}, flush=True)

rnd = random.Random(7)
want = {"tatoeba": 1000 if lang == "en" else 400, "cv": 500 if lang == "en" else 200}
with open(f"{out}/bench/{lang}.txt", "w", encoding="utf-8") as f:
    for src in ("tatoeba", "cv"):
        lst = sorted(test[src]); rnd.shuffle(lst)
        for s in [x for x in lst if not banned(x)][: want[src]]:
            f.write(src + "\t" + s + "\n")

if not no_table:
    def emit(counter, keyf, minc, topn, mintotal, buf):
        ctx = collections.defaultdict(list); tot = collections.Counter()
        for k, v in counter.items():
            c = keyf(k)
            if "<u>" in c or "<n>" in c:
                continue
            tot[c] += v
            if v >= minc and k[-1] not in ("<u>", "<n>"):
                ctx[c].append((v, k[-1]))
        for c, lst in ctx.items():
            if tot[c] < mintotal or banned(c):
                continue
            lst = [(v, w) for v, w in lst if not banned(w)]
            if not lst:
                continue
            lst.sort(key=lambda x: (-x[0], x[1]))
            buf.write(c + "\t" + str(tot[c]) + "\t" + " ".join(f"{w}:{v}" for v, w in lst[:topn]) + "\n")
    buf = io.StringIO()
    emit(bi, lambda k: k[0], 2, 16, 3, buf)
    emit(tri, lambda k: k[0] + " " + k[1], 2, 10, 8, buf)
    lines = sorted(buf.getvalue().splitlines(), key=lambda l: l.split("\t")[0].encode("utf-8"))
    with open(f"{out}/ngrams_{lang}.tsv", "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")
    print(lang, "contexts", len(lines), "bytes", os.path.getsize(f"{out}/ngrams_{lang}.tsv"), flush=True)
