#!/usr/bin/env python3
"""Word error rate and speed of Humming's two offline models on public LibriSpeech test sets.

usage: wer_bench.py <models_dir> <librispeech_root> <out.json> [n_clean] [n_other]

Takes the first N utterances (sorted by id) of test-clean and test-other, decodes each with sherpa-onnx (the same
runtime and model files the app downloads), and reports WER (jiwer, after lower-casing and stripping punctuation)
plus the real-time factor and median decode latency per utterance. CPU only, 2 threads, like a phone's big cores.
"""
import glob, json, os, re, statistics, sys, time

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


def recognizer(kind, d):
    if kind == "fast":
        return sherpa_onnx.OfflineRecognizer.from_nemo_ctc(
            model=os.path.join(d, "model.int8.onnx"), tokens=os.path.join(d, "tokens.txt"), num_threads=2)
    return sherpa_onnx.OfflineRecognizer.from_transducer(
        encoder=os.path.join(d, "encoder.int8.onnx"), decoder=os.path.join(d, "decoder.int8.onnx"),
        joiner=os.path.join(d, "joiner.int8.onnx"), tokens=os.path.join(d, "tokens.txt"),
        num_threads=2, model_type="nemo_transducer")


MODELS = {
    "fast": ("Parakeet TDT-CTC 110M int8 (Fast, default)", "sherpa-onnx-nemo-parakeet_tdt_ctc_110m-en-36000-int8"),
    "best": ("Parakeet TDT 0.6B v2 int8 (Best, optional)", "sherpa-onnx-nemo-parakeet-tdt-0.6b-v2-int8"),
}
sets = {"test-clean": utterances("test-clean", n_clean), "test-other": utterances("test-other", n_other)}
report = {"cpu": open("/proc/cpuinfo").read().split("model name")[1].split("\n")[0].strip(": ") if os.path.exists("/proc/cpuinfo") else "",
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
        refs, hyps, lat, audio = [], [], [], 0.0
        for uid, path, text in items:
            samples, sr = sf.read(path, dtype="float32")
            s = rec.create_stream()
            t1 = time.perf_counter()
            s.accept_waveform(sr, samples)
            rec.decode_stream(s)
            hyp = s.result.text
            lat.append(time.perf_counter() - t1)
            audio += len(samples) / sr
            refs.append(norm(text)); hyps.append(norm(hyp))
        res[split] = {"n": len(items), "wer_pct": round(100 * jiwer.wer(refs, hyps), 2),
                      "rtf": round(sum(lat) / audio, 4), "median_latency_ms": round(1000 * statistics.median(lat)),
                      "mean_audio_s": round(audio / len(items), 1)}
        print(key, split, res[split], flush=True)
    report["models"][key] = res
json.dump(report, open(out, "w"), indent=2)
print(json.dumps(report, indent=2))
