#!/usr/bin/env python3
"""Host benchmark of the humming-core engine (the same Rust + whisper.cpp code the Android .so runs), used to see
where STT time goes and to pick model / CPU variant / decode settings without an emulator in the loop.

Usage: bench.py --cli NAME=PATH [--cli ...] --model NAME=PATH [...] --clips DIR --espeak DIR --mid DIR --out DIR
       [--plan quick|full] [--threads N]
Each run: `humming-core transcribe <wav> --model M --reps 2 [--warmup] <speed flags> --threads N`, rep 1 (warm) counted.
Writes results.jsonl (one line per clip x config) and BENCH.md (tables)."""
import argparse, json, os, re, statistics, subprocess, sys, time, wave

NUM = {"zero": "0", "one": "1", "two": "2", "three": "3", "four": "4", "five": "5", "six": "6", "seven": "7",
       "eight": "8", "nine": "9", "ten": "10"}


def words(s):
    s = s.lower().replace("\u2019", "'")
    s = re.sub(r"(\d)\s*([ap])\.?\s?m\b\.?", r"\1 \2m", s)
    s = re.sub(r"[^a-z0-9' ]+", " ", s)
    return [NUM.get(w, w) for w in s.split()]


def wer(ref, hyp):
    r, h = words(ref), words(hyp)
    if not r:
        return (0.0 if not h else 1.0), (0 if not h else len(h)), 0
    d = list(range(len(h) + 1))
    for i in range(1, len(r) + 1):
        prev, d[0] = d[0], i
        for j in range(1, len(h) + 1):
            cur = d[j]
            d[j] = min(d[j] + 1, d[j - 1] + 1, prev + (r[i - 1] != h[j - 1]))
            prev = cur
    return d[len(h)] / len(r), d[len(h)], len(r)


CONFIGS = {
    "m2": ["--m2"],                          # M2 decode path: 30 s window, temperature fallback, timestamps
    "fast": [],                              # M3 default: window = speech + 1 s (>= 512 frames), one greedy pass, no timestamps, loop retry
    "fast_fullctx": ["--audio-ctx", "full"],  # M3 minus the dynamic window (isolates its effect)
    "fast_pad2000": ["--pad-ms", "2000"],
    "fast_nofloor": ["--min-ctx", "0"],       # the 15:03Z default (no 512-frame floor): loops on 2-4 s phrases
}


def clip_set(a):
    refs = json.load(open(os.path.join(a.clips, "refs.json")))
    out = []
    for n in ["plain", "long", "fillers", "repeats", "question", "p0_3", "p0_8", "p0_9", "p0_10", "silence", "noise"]:
        out.append(("kokoro_" + n, os.path.join(a.clips, n + ".wav"), refs[n]["expected"], "set20"))
    if a.espeak and os.path.isdir(a.espeak):
        for f in sorted(os.listdir(a.espeak)):
            if f.endswith(".wav"):
                n = f[:-4]
                e = os.path.join(a.espeak, n + ".expect")
                out.append(("espeak_" + n, os.path.join(a.espeak, f), open(e).read() if os.path.exists(e) else "", "set20"))
    if a.mid and os.path.isdir(a.mid):
        mref = json.load(open(os.path.join(a.mid, "refs_mid.json")))
        for n, m in mref.items():
            out.append((n, os.path.join(a.mid, n + ".wav"), m["expected"], "mid"))
    return out


def run_one(cli, model, wav, cfg, threads):
    flags = CONFIGS[cfg]
    cmd = [cli, "transcribe", wav, "--model", model, "--threads", str(threads), "--reps", "2"] + flags
    if cfg != "m2":
        cmd.append("--warmup")
    t = time.time()
    p = subprocess.run(cmd, capture_output=True, text=True, timeout=900)
    if p.returncode != 0:
        return {"error": p.stderr[-400:], "wall_s": round(time.time() - t, 2)}
    reps = [json.loads(l) for l in p.stdout.splitlines() if l.startswith("{")]
    return {"reps": reps, "wall_s": round(time.time() - t, 2)}


def plan(a, clis, models):
    runs = []
    base = [c for c in clis if c.endswith("base")]
    fast = [c for c in clis if c.endswith("fast")]
    for c in base:
        for m in ("tiny.en", "base.en"):
            if m in models:
                runs += [(c, m, "m2"), (c, m, "fast")]
    for c in fast:
        for m in models:
            runs.append((c, m, "fast"))
        if a.plan == "full":
            for m in ("tiny.en", "base.en"):
                if m in models:
                    runs += [(c, m, "m2"), (c, m, "fast_fullctx"), (c, m, "fast_pad2000"), (c, m, "fast_nofloor")]
    return runs


def pct(xs, q):
    xs = sorted(xs)
    if not xs:
        return None
    k = min(len(xs) - 1, max(0, int(round(q * (len(xs) - 1)))))
    return xs[k]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cli", action="append", default=[])
    ap.add_argument("--model", action="append", default=[])
    ap.add_argument("--clips", required=True)
    ap.add_argument("--espeak")
    ap.add_argument("--mid")
    ap.add_argument("--out", required=True)
    ap.add_argument("--plan", default="full")
    ap.add_argument("--threads", type=int, default=os.cpu_count() or 2)
    ap.add_argument("--label", default="")
    ap.add_argument("--resume", action="store_true", help="keep rows already in <out>/results.jsonl and run only the missing ones")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    clis = dict(x.split("=", 1) for x in a.cli)
    models = dict(x.split("=", 1) for x in a.model)
    clips = clip_set(a)
    rows = []
    rpath = os.path.join(a.out, "results.jsonl")
    if a.resume and os.path.exists(rpath):
        rows = [json.loads(l) for l in open(rpath) if l.strip()]
    done = {(r["variant"], r["model"], r["config"], r["clip"]) for r in rows}
    jl = open(rpath, "a" if a.resume else "w")
    for (c, m, cfg) in plan(a, clis, models):
        for (name, wav, expected, group) in clips:
            if (c, m, cfg, name) in done:
                continue
            try:
                r = run_one(clis[c], models[m], wav, cfg, a.threads)
            except subprocess.TimeoutExpired as e:
                r = {"error": f"timeout after {e.timeout} s"}
            row = {"variant": c, "model": m, "config": cfg, "clip": name, "group": group, "expected": expected,
                   "audio_s": round(wave.open(wav).getnframes() / 16000, 2)}
            if "error" in r:
                row["error"] = r["error"]
            else:
                warm = r["reps"][-1]
                row.update({"text": warm["text"], "stt_ms": warm["stt_ms"], "cold_stt_ms": r["reps"][0]["stt_ms"],
                            "load_ms": warm["load_ms"], "timing": warm["timing"], "warmup": warm.get("warmup")})
                row["wer"], row["errors"], row["ref_words"] = wer(expected, warm["text"])
            rows.append(row)
            jl.write(json.dumps(row) + "\n"); jl.flush()
            print(json.dumps({k: row.get(k) for k in ("variant", "model", "config", "clip", "stt_ms", "wer", "text", "error")}), flush=True)
    jl.close()
    # summary
    md = [f"# humming-core host bench {a.label}", "", f"threads={a.threads}, cpu: {cpu_name()}", "",
          "set20 = the emulator proof's 20-clip set (core cleanup only; the app adds its own cleanup on top).",
          "mid = 10-15 s dictations transcribed in ONE call (the no-pause worst case: the whole clip is the tail).",
          "Times are warm (second run with the engine loaded). mel/enc/dec = medians over the mid clips.", "",
          "| variant | model | config | set20 WER (err/words) | set20 median ms | mid median ms | mid p90 ms | mid mel/enc/dec ms | audio_ctx (mid) | mid WER |",
          "|---|---|---|---|---|---|---|---|---|---|"]
    keys = []
    for r in rows:
        k = (r["variant"], r["model"], r["config"])
        if k not in keys:
            keys.append(k)
    summary = []
    for k in keys:
        rs = [r for r in rows if (r["variant"], r["model"], r["config"]) == k and "error" not in r]
        s20 = [r for r in rs if r["group"] == "set20"]
        mid = [r for r in rs if r["group"] == "mid"]
        e20 = sum(r["errors"] for r in s20 if r["expected"]); w20 = sum(r["ref_words"] for r in s20 if r["expected"])
        emid = sum(r["errors"] for r in mid); wmid = sum(r["ref_words"] for r in mid)
        sp20 = [r["stt_ms"] for r in s20 if r["expected"]]
        spm = [r["stt_ms"] for r in mid]
        med = lambda xs: statistics.median(xs) if xs else None
        mel = med([r["timing"]["mel_ms"] for r in mid]); enc = med([r["timing"]["encode_ms"] for r in mid])
        dec = med([r["timing"]["decode_ms"] for r in mid]); actx = med([r["timing"]["audio_ctx"] for r in mid])
        errs = len([r for r in rows if (r["variant"], r["model"], r["config"]) == k and "error" in r])
        s = {"variant": k[0], "model": k[1], "config": k[2], "set20_wer": round(e20 / max(w20, 1), 4), "set20_err": [e20, w20],
             "set20_median_ms": med(sp20), "mid_median_ms": med(spm), "mid_p90_ms": pct(spm, 0.9),
             "mid_breakdown_ms": [mel, enc, dec], "mid_audio_ctx": actx, "mid_wer": round(emid / max(wmid, 1), 4), "errors": errs}
        summary.append(s)
        md.append(f"| {k[0]} | {k[1]} | {k[2]} | {s['set20_wer']:.3f} ({e20}/{w20}) | {s['set20_median_ms']} | {s['mid_median_ms']} | "
                  f"{s['mid_p90_ms']} | {mel}/{enc}/{dec} | {actx} | {s['mid_wer']:.3f}" + (f" ({errs} errors)" if errs else "") + " |")
    json.dump(summary, open(os.path.join(a.out, "summary.json"), "w"), indent=1)
    open(os.path.join(a.out, "BENCH.md"), "w").write("\n".join(md) + "\n")
    print("\n".join(md))


def cpu_name():
    try:
        for l in open("/proc/cpuinfo"):
            if l.lower().startswith(("model name", "cpu part", "hardware")):
                return l.split(":", 1)[1].strip()
    except OSError:
        pass
    return "?"


if __name__ == "__main__":
    main()
