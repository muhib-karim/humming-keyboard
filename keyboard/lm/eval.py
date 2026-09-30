# Humming model evaluation (Apache-2.0 tooling): held-out next-word top-3 accuracy for the n-gram tables alone
# (as Predictor.lm scores them), the int8 LSTM alone (exactly as HummingLm.kt computes it) and blends.
# Usage: python eval.py OUT_DIR BENCH_TXT  -> OUT_DIR/eval.json, OUT_DIR/nn_ref.tsv
import json, math, sys
import numpy as np
from hlm_common import clauses, load_lexicon

out, bench = sys.argv[1], sys.argv[2]
A = "../app/src/main/assets/predict/"
z = np.load(f"{out}/qmodel.npz")
itos = list(z["itos"]); stoi = {w: i for i, w in enumerate(itos)}
embq, embs = z["embq"].astype(np.float32), z["embs"]; ihq, ihs = z["ihq"].astype(np.float32), z["ihs"]
hhq, hhs = z["hhq"].astype(np.float32), z["hhs"]; b = z["b"]; pq, ps = z["pq"].astype(np.float32), z["ps"]; pb, ob = z["pb"], z["ob"]
H = hhq.shape[1]
def sig(x): return 1 / (1 + np.exp(-x))
def step(tok, h, c):
    x = embq[tok] * embs[tok]
    g = (ihq @ x) * ihs + (hhq @ h) * hhs + b
    i, f, gg, o = sig(g[:H]), sig(g[H:2*H]), np.tanh(g[2*H:3*H]), sig(g[3*H:])
    c = f * c + i * gg; h = o * np.tanh(c)
    e = (pq @ h) * ps + pb
    lo = (embq @ e) * embs + ob
    lo = lo - lo.max(); lp = lo - math.log(np.exp(lo).sum())
    return lp, h, c

lex = load_lexicon(A + "lexicon_en.tsv")
ng = {}
for line in open(A + "ngrams_en.tsv", encoding="utf-8"):
    p = line.rstrip("\n").split("\t")
    if len(p) < 3: continue
    d = {}
    for pr in p[2].split(" "):
        k = pr.rfind(":")
        if k > 0: d[pr[:k]] = int(pr[k+1:])
    ng[p[0]] = (int(p[1]), d)
FALLBACK = ["and", "the", "to", "a", "i", "in", "is", "for", "of", "it"]
def p_ng(w, ctx):
    pu = math.exp(lex[w][0]) if w in lex else 1e-9
    bi = ng.get(ctx[-1]); tri = ng.get(ctx[0] + " " + ctx[1]) if len(ctx) >= 2 else None
    pb_ = bi[1].get(w, 0) / max(1, bi[0]) if bi else 0.0
    if tri: return 0.5 * tri[1].get(w, 0) / max(1, tri[0]) + 0.35 * pb_ + 0.15 * pu
    if bi: return 0.75 * pb_ + 0.25 * pu
    return pu
def ng_pool(ctx):
    pool = []
    if len(ctx) >= 2 and (ctx[0] + " " + ctx[1]) in ng: pool += list(ng[ctx[0] + " " + ctx[1]][1])[:12]
    if ctx[-1] in ng: pool += list(ng[ctx[-1]][1])[:12]
    if len(set(pool)) < 3: pool += FALLBACK
    return [w for w in dict.fromkeys(pool) if w in lex and not lex[w][1]]

LAMBDAS = [0.0, 0.3, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0]
hits = {src: {l: 0 for l in LAMBDAS} for src in ("tatoeba", "cv", "all")}; n = {"tatoeba": 0, "cv": 0, "all": 0}
nn_only = {"tatoeba": 0, "cv": 0, "all": 0}
ref = open(f"{out}/nn_ref.tsv", "w", encoding="utf-8"); nref = 0
for line in open(bench, encoding="utf-8"):
    src, s = line.rstrip("\n").split("\t", 1)
    cl = clauses(s)
    if not cl: continue
    ws = cl[0]
    h = np.zeros(H, np.float32); c = np.zeros(H, np.float32)
    lp, h, c = step(1, h, c)
    ctx = ["<s>"]
    for t, w in enumerate(ws):
        pn = np.exp(lp)
        top_nn = [itos[i] for i in np.argsort(-lp)[:40] if i > 1 and itos[i] in lex and not lex[itos[i]][1]]
        if nref < 300:
            ref.write(" ".join(ws[:t]) + "\t" + " ".join(f"{itos[i]}:{lp[i]:.4f}" for i in np.argsort(-lp)[:5]) + "\n"); nref += 1
        pool = list(dict.fromkeys(ng_pool(ctx) + top_nn[:20]))
        for l in LAMBDAS:
            pl = ng_pool(ctx) if l == 0 else pool
            sc = sorted(pl, key=lambda x: -(l * (pn[stoi[x]] if x in stoi else 0.0) + (1 - l) * p_ng(x, ctx)))[:3]
            if w in sc:
                hits[src][l] += 1; hits["all"][l] += 1
        if w in top_nn[:3]:
            nn_only[src] += 1; nn_only["all"] += 1
        n[src] += 1; n["all"] += 1
        tok = stoi.get(w, 0)
        lp, h, c = step(tok, h, c)
        ctx = (ctx + [w])[-2:]
res = {"predictions": n, "top3": {src: {str(l): round(hits[src][l] / max(1, n[src]), 4) for l in LAMBDAS} for src in hits},
       "nn_only_top3": {k: round(nn_only[k] / max(1, n[k]), 4) for k in n}}
json.dump(res, open(f"{out}/eval.json", "w"), indent=1)
print(json.dumps(res, indent=1))
