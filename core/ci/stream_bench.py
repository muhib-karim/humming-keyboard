#!/usr/bin/env python3
"""Stop->text simulation of the keyboard's live pipeline on a host, with the real humming-core CLI and real models.

It replays a WAV through a line-for-line Python port of android/.../Segmenter.kt (20 ms frames, 450 ms pause,
1.5 s min, 24 s max), transcribes every emitted segment with `humming-core transcribe <seg> --reps R --warmup`
(warm, model already loaded, the way the keyboard keeps it resident) and then replays the timeline the keyboard
lives through: segment k is handed to the single STT worker the moment the segmenter emits it (its cut time in the
audio), the worker runs segments in order, and "stop" is tapped when the audio ends. stop->text = the moment the
worker finishes the last segment (the tail flushed at stop) minus the stop moment, plus the final cleanup call.
Per-segment timing (mel/encode/decode) comes from the engine's own breakdown.

Usage: stream_bench.py --cli PATH --model NAME=PATH [...] --mid DIR [--long WAV --long-ref TXT] --out DIR
                       [--threads N] [--reps R] [--label TEXT]"""
import argparse, json, math, os, re, statistics, subprocess, sys, tempfile, time, wave
from array import array

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from bench import wer, pct  # same word normaliser as the emulator proof


class Segmenter:
    def __init__(self, sr=16000, min_seg=1.5, max_seg=24.0, pause_ms=450):
        self.sr, self.min_seg, self.max_seg, self.pause_ms = sr, min_seg, max_seg, pause_ms
        self.frame = sr // 50
        self.buf = []          # float samples of the current segment
        self.db = []
        self.floor = float("nan")
        self.silent = 0
        self.consumed = 0      # samples already emitted or dropped (absolute position of buf[0])
        self.out = []          # (start_sample, end_sample, samples) per emitted segment

    def thr(self):
        return max(-48.0, (-60.0 if math.isnan(self.floor) else self.floor) + 12.0)

    def has_speech(self, a, b):
        t = self.thr()
        return sum(1 for k in range(a, b) if self.db[k] > t) >= 8

    def loud(self, a, b):
        return sum(1 for k in range(a, b) if self.db[k] > -40.0)

    def add(self, f):
        self.buf.extend(f)
        s = sum(x * x for x in f)
        rms = math.sqrt(s / max(len(f), 1))
        d = -140.0 if rms <= 1e-7 else 20 * math.log10(rms)
        self.db.append(d)
        self.floor = d if (math.isnan(self.floor) or d < self.floor) else self.floor + 0.01

    def frame_in(self, f):
        self.add(f)
        d = self.db[-1]
        sp = self.thr()
        self.silent = self.silent + 1 if (d < sp - 4 and d < -35) else 0
        seg = len(self.buf) / self.sr
        if seg >= self.min_seg and self.silent * 20 >= self.pause_ms and self.has_speech(0, len(self.db)):
            self.emit(len(self.buf))
        elif seg >= self.max_seg:
            n = len(self.db); frm = max(0, n - 250); best = n - 1
            for k in range(frm, n):
                if self.db[k] <= self.db[best]:
                    best = k
            self.emit((best + 1) * self.frame)

    def emit(self, cut):
        cut = max(0, min(cut, len(self.buf)))
        cf = min(len(self.db), (cut + self.frame - 1) // self.frame)
        if cut > 0 and (self.has_speech(0, cf) or self.loud(0, cf) >= 8):
            self.out.append((self.consumed, self.consumed + cut, self.buf[:cut]))
        self.consumed += cut
        self.buf = self.buf[cut:]
        self.db = self.db[cf:]
        self.silent = 0

    def run(self, samples):
        fr = self.frame
        n = len(samples) // fr * fr
        for i in range(0, n, fr):
            self.frame_in(samples[i:i + fr])
        if n < len(samples):
            self.add(samples[n:])
        self.emit(len(self.buf))  # flush at stop
        return self.out


def read_wav(p):
    w = wave.open(p)
    assert w.getframerate() == 16000 and w.getnchannels() == 1 and w.getsampwidth() == 2
    a = array("h"); a.frombytes(w.readframes(w.getnframes()))
    return [x / 32768.0 for x in a]


def write_wav(p, fl):
    a = array("h", [max(-32768, min(32767, int(round(x * 32768)))) for x in fl])
    with wave.open(p, "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(16000); w.writeframes(a.tobytes())


FLAGS = []


def stt(cli, model, wav, threads, reps):
    cmd = [cli, "transcribe", wav, "--model", model, "--threads", str(threads), "--reps", str(reps), "--no-cleanup"] + FLAGS
    if "--m2" not in FLAGS:
        cmd.append("--warmup")
    p = subprocess.run(cmd, capture_output=True, text=True, timeout=600)
    if p.returncode != 0:
        raise RuntimeError(p.stderr[-300:])
    rs = [json.loads(l) for l in p.stdout.splitlines() if l.startswith("{")]
    warm = rs[1:] or rs
    med = sorted(warm, key=lambda r: r["stt_ms"])[len(warm) // 2]
    return med, rs[0]["load_ms"]


def cleanup(cli, text):
    t = time.time()
    p = subprocess.run([cli, "cleanup", text], capture_output=True, text=True, timeout=60)
    return p.stdout.strip(), (time.time() - t) * 1000


def simulate(cli, model, wav, threads, reps, tmp):
    x = read_wav(wav)
    segs = Segmenter().run(x)
    audio_ms = len(x) * 1000 / 16000
    rows, raws, load = [], [], None
    for i, (a, b, s) in enumerate(segs):
        p = os.path.join(tmp, f"seg{i}.wav")
        write_wav(p, s)
        r, load = stt(cli, model, p, threads, reps)
        rows.append({"i": i, "start_ms": round(a / 16), "cut_ms": round(b / 16), "audio_ms": round(len(s) / 16),
                     "stt_ms": r["stt_ms"], "timing": r["timing"], "raw": r["raw"]})
        if r["raw"].strip():
            raws.append(r["raw"].strip())
    # timeline: worker free at 0; segment i available at its cut time (the tail's cut is the stop moment)
    free = 0.0
    for r in rows:
        start = max(free, r["cut_ms"])
        r["done_ms"] = round(start + r["stt_ms"])
        free = start + r["stt_ms"]
    stop = audio_ms
    text, cl_ms = cleanup(cli, " ".join(raws))
    stop_to_text = max(0.0, free - stop) + cl_ms if rows else cl_ms
    return {"audio_ms": round(audio_ms), "segments": len(rows), "load_ms": load, "stop_to_text_ms": round(stop_to_text),
            "cleanup_ms": round(cl_ms), "tail": rows[-1] if rows else None, "segs": rows, "text": text}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cli", required=True)
    ap.add_argument("--model", action="append", default=[])
    ap.add_argument("--mid", required=True)
    ap.add_argument("--long")
    ap.add_argument("--long-ref")
    ap.add_argument("--out", required=True)
    ap.add_argument("--threads", type=int, default=4)
    ap.add_argument("--reps", type=int, default=3)
    ap.add_argument("--label", default="")
    ap.add_argument("--flags", default="", help="extra CLI speed flags, e.g. --m2 for the M2 decode path")
    a = ap.parse_args()
    FLAGS[:] = a.flags.split()
    os.makedirs(a.out, exist_ok=True)
    refs = json.load(open(os.path.join(a.mid, "refs_mid.json")))
    models = dict(m.split("=", 1) for m in a.model)
    jl = open(os.path.join(a.out, "stream.jsonl"), "a")
    summary = []
    tag = a.label or "run"
    for mn, mp in models.items():
        res = []
        with tempfile.TemporaryDirectory() as tmp:
            for n in sorted(refs):
                r = simulate(a.cli, mp, os.path.join(a.mid, n + ".wav"), a.threads, a.reps, tmp)
                r.update({"label": tag, "flags": a.flags, "model": mn, "clip": n, "expected": refs[n]["expected"]})
                r["wer"], r["errors"], r["ref_words"] = wer(r["expected"], r["text"])
                res.append(r); jl.write(json.dumps(r) + "\n"); jl.flush()
                print(json.dumps({k: r[k] for k in ("model", "clip", "segments", "stop_to_text_ms", "wer")}), flush=True)
            long_row = None
            if a.long:
                r = simulate(a.cli, mp, a.long, a.threads, 1, tmp)
                r.update({"model": mn, "clip": "long5min", "expected": open(a.long_ref).read()})
                r["wer"], r["errors"], r["ref_words"] = wer(r["expected"], r["text"])
                long_row = r; jl.write(json.dumps(r) + "\n"); jl.flush()
                print(json.dumps({k: r[k] for k in ("model", "clip", "segments", "stop_to_text_ms", "wer")}), flush=True)
        st = [r["stop_to_text_ms"] for r in res]
        tails = [r["tail"] for r in res if r["tail"]]
        med = lambda xs: statistics.median(xs) if xs else None
        s = {"label": tag, "flags": a.flags, "model": mn, "mid_stop_to_text_median_ms": med(st), "mid_stop_to_text_p90_ms": pct(st, 0.9),
             "tight_median_ms": med([r["stop_to_text_ms"] for r in res if "tight" in r["clip"]]),
             "paused_median_ms": med([r["stop_to_text_ms"] for r in res if "paused" in r["clip"]]),
             "tail_mel_enc_dec_ms": [med([t["timing"]["mel_ms"] for t in tails]), med([t["timing"]["encode_ms"] for t in tails]),
                                     med([t["timing"]["decode_ms"] for t in tails])],
             "tail_audio_ms_median": med([t["audio_ms"] for t in tails]),
             "load_ms_median": med([r["load_ms"] for r in res]),
             "mid_wer": round(sum(r["errors"] for r in res) / max(1, sum(r["ref_words"] for r in res)), 4)}
        if long_row:
            s.update({"long5min_wer": round(long_row["wer"], 4), "long5min_segments": long_row["segments"],
                      "long5min_stop_to_text_ms": long_row["stop_to_text_ms"]})
        summary.append(s)
        print(json.dumps(s), flush=True)
    old = []
    sp = os.path.join(a.out, "stream_summary.json")
    if os.path.exists(sp):
        old = json.load(open(sp))
    json.dump(old + summary, open(sp, "w"), indent=1)


if __name__ == "__main__":
    main()
