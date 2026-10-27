#!/usr/bin/env python3
"""Word error rate and speed of Humming's offline models on public LibriSpeech test sets.

usage: wer_bench.py <models_dir> <librispeech_root> <out.json> [n_clean] [n_other]
       (set WER_PER_UTT=<file.jsonl> to also write every reference and hypothesis)

Takes the first N utterances (sorted by id) of test-clean and test-other, decodes each with sherpa-onnx (the same
runtime and model files the app downloads), and reports WER (jiwer, after lower-casing and stripping punctuation)
plus the real-time factor and median decode latency per utterance. CPU only, 2 threads, like a phone's big cores.
"""
import glob, json, os, platform, re, statistics, subprocess, sys, time

import jiwer
import sherpa_onnx
import soundfile as sf

models, root, out = sys.argv[1], sys.argv[2], sys.argv[3]
n_clean = int(sys.argv[4]) if len(sys.argv) > 4 else 200
n_other = int(sys.argv[5]) if len(sys.argv) > 5 else 100


def utterances(split, n):
    items = []
    for trans in sorted(glob.glob(os.path.join(root, split, "*", "*", "*.trans.txt"))):
        d = os.path.dirname(trans)
        for line in open(trans):
            uid, text = line.strip().split(" ", 1)
            items.append((uid, os.path.join(d, uid + ".flac"), text))
    items.sort()
    return items[:n]


def norm(t):
    t = t.lower().replace("-", " ")
    t = re.sub(r"[^a-z' ]", " ", t)
    return re.sub(r"\s+", " ", t).strip()


def cpu_name():
    try:
        if sys.platform == "darwin":
            return subprocess.check_output(["sysctl", "-n", "machdep.cpu.brand_string"], text=True).strip()
        if sys.platform == "win32":
            out = subprocess.check_output(["powershell", "-NoProfile", "-Command",
                                           "(Get-CimInstance Win32_Processor).Name"], text=True)
            return out.strip().splitlines()[0].strip()
        for line in open("/proc/cpuinfo"):
            if line.lower().startswith(("model name", "cpu model")):
                return line.split(":", 1)[1].strip()
        return platform.processor() or platform.machine()
    except Exception:
        return platform.processor() or platform.machine()


def recognizer(kind, d):
    if kind in ("lite", "balanced"):
        return sherpa_onnx.OfflineRecognizer.from_moonshine_v2(
            encoder=os.path.join(d, "encoder_model.ort"), decoder=os.path.join(d, "decoder_model_merged.ort"),
            tokens=os.path.join(d, "tokens.txt"), num_threads=2)
    if kind == "fast":
        return sherpa_onnx.OfflineRecognizer.from_nemo_ctc(
            model=os.path.join(d, "model.int8.onnx"), tokens=os.path.join(d, "tokens.txt"), num_threads=2)
    return sherpa_onnx.OfflineRecognizer.from_transducer(
        encoder=os.path.join(d, "encoder.int8.onnx"), decoder=os.path.join(d, "decoder.int8.onnx"),
        joiner=os.path.join(d, "joiner.int8.onnx"), tokens=os.path.join(d, "tokens.txt"),
        num_threads=2, model_type="nemo_transducer")


def windows(samples, sr, max_s):
    """Cut at the quietest 50 ms frame in the last third of each window (as the desktop app does)."""
    import numpy as np
    out, start, mx, fr = [], 0, int(max_s * sr), sr // 20
    while len(samples) - start > mx:
        lo, hi = start + mx * 2 // 3, start + mx
        e = [float(np.sum(samples[i:i + fr] ** 2)) for i in range(lo, hi - fr + 1, fr)]
        cut = lo + int(np.argmin(e)) * fr + fr // 2
        out.append(samples[start:cut]); start = cut
    out.append(samples[start:])
    return out


# Moonshine v2 returns nothing for clips over ~10 s in sherpa-onnx 1.13.8, so the apps decode Lite in <= 8 s windows.
MAX_WINDOW = {"lite": 8, "balanced": 8}

MODELS = {
    "lite": ("Moonshine v2 tiny int8 (Lite, 30 MB)", "sherpa-onnx-moonshine-tiny-en-quantized-2026-02-27"),
    "fast": ("Parakeet TDT-CTC 110M int8 (Fast, default)", "sherpa-onnx-nemo-parakeet_tdt_ctc_110m-en-36000-int8"),
    "best": ("Parakeet TDT 0.6B v2 int8 (Best, optional)", "sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8"),
    # New in 1.8 (selectable, not default): see docs/BENCHMARKS.md.
    "balanced": ("Moonshine v2 base int8 (Balanced, 111 MB)", "sherpa-onnx-moonshine-base-en-quantized-2026-02-27"),
    "multi": ("Parakeet TDT 0.6B v3 int8 (Multilingual, optional)", "sherpa-onnx-nemo-parakeet-tdt-0.6b-v3-int8"),
}
sets = {"test-clean": utterances("test-clean", n_clean), "test-other": utterances("test-other", n_other)}
# Optional per-utterance reference/hypothesis lines (JSONL) so every WER figure can be re-checked by hand.
per_utt = open(os.environ["WER_PER_UTT"], "w") if os.environ.get("WER_PER_UTT") else None
report = {"device": os.environ.get("BENCH_DEVICE", f"{platform.system()} {platform.machine()}"),
          "os": f"{platform.system()} {platform.release()}", "arch": platform.machine(), "cpu": cpu_name(),
          "threads": 2, "sets": {k: len(v) for k, v in sets.items()}, "models": {}}
for key, (label, dirname) in MODELS.items():
    d = os.path.join(models, dirname)
    if not os.path.isdir(d):
        continue
    t0 = time.time()
    rec = recognizer(key, d)
    load_s = time.time() - t0
    res = {"label": label, "load_s": round(load_s, 2)}
    for split, items in sets.items():
        if not items:  # e.g. n_other=0, or a sample with only test-clean (fetch_librispeech_sample.py)
            continue
        refs, hyps, lat, audio = [], [], [], 0.0
        for uid, path, text in items:
            samples, sr = sf.read(path, dtype="float32")
            t1 = time.perf_counter()
            parts = []
            for chunk in windows(samples, sr, MAX_WINDOW.get(key, 25)):
                s = rec.create_stream()
                s.accept_waveform(sr, chunk)
                rec.decode_stream(s)
                parts.append(s.result.text.strip())
            hyp = " ".join(p for p in parts if p)
            lat.append(time.perf_counter() - t1)
            audio += len(samples) / sr
            refs.append(norm(text)); hyps.append(norm(hyp))
            if per_utt is not None:
                per_utt.write(json.dumps({"model": key, "split": split, "id": uid, "ref": refs[-1], "hyp": hyps[-1],
                                          "audio_s": round(len(samples) / sr, 2),
                                          "latency_ms": round(1000 * lat[-1])}) + "\n")
        res[split] = {"n": len(items), "wer_pct": round(100 * jiwer.wer(refs, hyps), 2),
                      "rtf": round(sum(lat) / audio, 4), "median_latency_ms": round(1000 * statistics.median(lat)),
                      "mean_audio_s": round(audio / len(items), 1)}
        print(key, split, res[split], flush=True)
    report["models"][key] = res
if per_utt is not None:
    per_utt.close()
json.dump(report, open(out, "w"), indent=2)
print(json.dumps(report, indent=2))
