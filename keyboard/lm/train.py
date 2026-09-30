# Humming neural next-word model (Apache-2.0 tooling): a small word-level LSTM, trained on CPU, exported as int8.
# Usage: python train.py --minutes 150 --out out   (English; corpus = Tatoeba CC BY 2.0 FR + Common Voice CC0,
# minus the 1 % held-out split). Writes out/hlm_en.bin, out/nn_ref.tsv, out/train_log.txt.
import argparse, collections, math, os, random, struct, sys, time
import numpy as np
import torch, torch.nn as nn, torch.nn.functional as F
from hlm_common import raw_sentences, is_test, clauses, load_lexicon, names

ap = argparse.ArgumentParser()
ap.add_argument("--minutes", type=float, default=150)
ap.add_argument("--out", default="out")
ap.add_argument("--vocab", type=int, default=16000)
ap.add_argument("--emb", type=int, default=128)
ap.add_argument("--hidden", type=int, default=256)
ap.add_argument("--batch", type=int, default=128)
ap.add_argument("--maxlen", type=int, default=24)
ap.add_argument("--limit", type=int, default=0, help="sentences to read (0 = all), for a smoke run")
ap.add_argument("--lexicon", default="../app/src/main/assets/predict/lexicon_en.tsv")
a = ap.parse_args()
os.makedirs(a.out, exist_ok=True)
torch.manual_seed(1); random.seed(1)
torch.set_num_threads(os.cpu_count() or 2)
log = open(f"{a.out}/train_log.txt", "a")
def say(*x):
    s = " ".join(str(v) for v in x); print(s, flush=True); log.write(s + "\n"); log.flush()
say("threads", torch.get_num_threads(), "args", vars(a))

lex = load_lexicon(a.lexicon)
NM = names("en")
def sentences():
    seen = set(); n = 0
    for src, s in raw_sentences("en"):
        h = hash(s)
        if h in seen or is_test(s):
            continue
        seen.add(h); n += 1
        if a.limit and n > a.limit:
            break
        for ws in clauses(s):
            yield ws

cnt = collections.Counter()
for ws in sentences():
    cnt.update(ws)
cands = [w for w, _ in cnt.most_common() if w in lex and w not in NM]
itos = ["<unk>", "<s>"] + cands[: a.vocab - 2]
stoi = {w: i for i, w in enumerate(itos)}
V = len(itos)
cover = sum(cnt[w] for w in itos[2:]) / max(1, sum(cnt.values()))
say("vocab", V, "token coverage", round(cover, 4))

seqs = []
for ws in sentences():
    ids = [1] + [stoi.get(w, 0) for w in ws]
    for i in range(0, len(ids) - 1, a.maxlen):
        chunk = ids[i : i + a.maxlen + 1]
        if len(chunk) >= 2:
            seqs.append(np.array(chunk, dtype=np.int32))
random.shuffle(seqs)
valid, train = seqs[:3000], seqs[3000:]
ntok = sum(len(s) - 1 for s in train)
say("train sequences", len(train), "tokens", ntok)

def batches(data, bs, shuffle=True):
    order = sorted(range(len(data)), key=lambda i: len(data[i]))
    bl = [order[i : i + bs] for i in range(0, len(order), bs)]
    if shuffle:
        random.shuffle(bl)
    for b in bl:
        L = max(len(data[i]) for i in b)
        x = np.zeros((len(b), L - 1), dtype=np.int64); y = np.full((len(b), L - 1), -100, dtype=np.int64)
        for r, i in enumerate(b):
            s = data[i]; x[r, : len(s) - 1] = s[:-1]; y[r, : len(s) - 1] = s[1:]
        yield torch.from_numpy(x), torch.from_numpy(y)

class LM(nn.Module):
    def __init__(s):
        super().__init__()
        s.emb = nn.Embedding(V, a.emb)
        s.rnn = nn.LSTM(a.emb, a.hidden, batch_first=True)
        s.proj = nn.Linear(a.hidden, a.emb)
        s.bias = nn.Parameter(torch.zeros(V))
        s.drop = nn.Dropout(0.1)
        nn.init.normal_(s.emb.weight, std=0.05)
    def forward(s, x):
        h, _ = s.rnn(s.drop(s.emb(x)))
        e = s.proj(s.drop(h))
        return e @ s.emb.weight.t() + s.bias

m = LM()
opt = torch.optim.Adam(m.parameters(), lr=2e-3)
budget = a.minutes * 60; t0 = time.time(); step = 0; best = 1e9; seen_tok = 0
def evaluate():
    m.eval(); tot = 0.0; n = 0
    with torch.no_grad():
        for x, y in batches(valid, 256, False):
            lo = m(x); tot += F.cross_entropy(lo.reshape(-1, V), y.reshape(-1), ignore_index=-100, reduction="sum").item(); n += (y != -100).sum().item()
    m.train(); return tot / n
done = False; epoch = 0
while not done:
    epoch += 1
    for x, y in batches(train, a.batch):
        frac = (time.time() - t0) / budget
        if frac >= 1:
            done = True; break
        lr = 2e-3 * (0.05 + 0.95 * 0.5 * (1 + math.cos(math.pi * frac)))
        for g in opt.param_groups: g["lr"] = lr
        lo = m(x)
        loss = F.cross_entropy(lo.reshape(-1, V), y.reshape(-1), ignore_index=-100)
        opt.zero_grad(); loss.backward(); nn.utils.clip_grad_norm_(m.parameters(), 1.0); opt.step()
        step += 1; seen_tok += (y != -100).sum().item()
        if step % 500 == 0:
            el = time.time() - t0
            say(f"ep {epoch} step {step} tok {seen_tok} ({seen_tok/el:.0f}/s) loss {loss.item():.3f} lr {lr:.5f} min {el/60:.1f}")
        if step % 4000 == 0:
            v = evaluate(); say(f"valid ppl {math.exp(v):.1f}")
            if v < best:
                best = v; torch.save(m.state_dict(), f"{a.out}/best.pt")
v = evaluate(); say(f"final valid ppl {math.exp(v):.1f} epochs {epoch} tokens seen {seen_tok}")
if v < best:
    best = v; torch.save(m.state_dict(), f"{a.out}/best.pt")
m.load_state_dict(torch.load(f"{a.out}/best.pt"))

# ---- int8 export (format read by HummingLm.kt) ----
def q(w):
    w = w.detach().float().numpy()
    s = np.abs(w).max(axis=1) / 127.0; s[s == 0] = 1e-8
    return np.clip(np.round(w / s[:, None]), -127, 127).astype(np.int8), s.astype(np.float32)
E, H = a.emb, a.hidden
embq, embs = q(m.emb.weight); ihq, ihs = q(m.rnn.weight_ih_l0); hhq, hhs = q(m.rnn.weight_hh_l0)
b = (m.rnn.bias_ih_l0 + m.rnn.bias_hh_l0).detach().numpy().astype(np.float32)
pq, ps = q(m.proj.weight); pb = m.proj.bias.detach().numpy().astype(np.float32); ob = m.bias.detach().numpy().astype(np.float32)
vocab = "\n".join(itos).encode("utf-8")
with open(f"{a.out}/hlm_en.bin", "wb") as f:
    f.write(b"HLM1"); f.write(struct.pack("<iiii", V, E, H, len(vocab))); f.write(vocab)
    for arr in (embq, embs, ihq, ihs, hhq, hhs, b, pq, ps, pb, ob):
        f.write(arr.astype("<f4" if arr.dtype == np.float32 else np.int8).tobytes())
say("exported", os.path.getsize(f"{a.out}/hlm_en.bin"), "bytes")
np.savez(f"{a.out}/qmodel.npz", embq=embq, embs=embs, ihq=ihq, ihs=ihs, hhq=hhq, hhs=hhs, b=b, pq=pq, ps=ps, pb=pb, ob=ob, itos=np.array(itos))
