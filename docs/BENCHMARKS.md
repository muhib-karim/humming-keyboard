# Speech model benchmarks (Humming 1.8)

Every model Humming can download, measured **side by side in one run** on 28 October 2026, with the same runtime the
apps use (sherpa-onnx **1.13.8**, CPU, 2 threads) and the same model files the apps download (pinned by SHA-256 in
`desktop/src-tauri/src/models.rs` and `keyboard/app/src/main/kotlin/com/humming/keyboard/VoiceModels.kt`).

- Test set: LibriSpeech **test-clean**, the first 200 utterances sorted by id (fetched with
  `keyboard/ci/fetch_librispeech_sample.py`; LibriSpeech is CC BY 4.0). Read English speech, so this is the easy end;
  real dictation in a noisy room will be worse for every model.
- WER: jiwer after lower-casing and stripping punctuation (`keyboard/ci/wer_bench.py`).
- Machine: Intel(R) Xeon(R) Processor @ 2.60GHz, Linux x64 (shared 16-CPU cloud box, other jobs running). Speed numbers are for this box, not a phone; use them to compare models with
  each other.
- Peak RSS is the whole benchmark process (Python plus the model), so it slightly overstates the model alone.
- Raw output, including every reference and hypothesis: `docs/bench/local-20261028-<model>-test-clean-200.json` and
  `...-per-utterance.jsonl`.

| Model in the app | What it is | Download | WER | Real-time factor | Median latency per clip | Load | Peak RSS |
|---|---|---|---|---|---|---|---|
| Best (Android + desktop) | Parakeet TDT 0.6B v2 int8 (Best, optional) | 482 MB | **1.77 %** | 0.0949 | 671 ms | 2.38 s | 1266 MB |
| Multilingual (Android + desktop, new in 1.8) | Parakeet TDT 0.6B v3 int8 (Multilingual, optional) | 487 MB | **2.24 %** | 0.0956 | 695 ms | 2.43 s | 1129 MB |
| Fast (default, Android + desktop) | Parakeet TDT-CTC 110M int8 (Fast, default) | 104 MB | **2.72 %** | 0.0299 | 217 ms | 1.06 s | 478 MB |
| Balanced (desktop, new in 1.8) | Moonshine v2 base int8 (Balanced, 111 MB) | 111 MB | **3.11 %** | 0.0376 | 300 ms | 0.85 s | 379 MB |
| Lite (desktop) | Moonshine v2 tiny int8 (Lite, 30 MB) | 30 MB | **4.51 %** | 0.0206 | 157 ms | 0.44 s | 174 MB |

Mean clip length 9.1 s. Real-time factor = decode time / audio time (lower is faster).

## What changed in 1.8 and why

The 1.8 survey of new on-device speech models (Moonshine streaming, NVIDIA Nemotron streaming, Parakeet v3, the
sherpa-onnx Bangla Zipformer, Whisper turbo, Google's on-device recognisers) looked for models that run **fully offline
on the CPU** with the runtime Humming already ships. Two drop in today with no new engine code, so they are now
selectable:

- **Multilingual: NVIDIA Parakeet TDT 0.6B v3** (int8 export by sherpa-onnx). NVIDIA's model card: it "extends the
  parakeet-tdt-0.6b-v2 model by expanding language support from English to 25 European languages" and "automatically
  detects the language of the audio" (https://huggingface.co/nvidia/parakeet-tdt-0.6b-v3, licence CC-BY-4.0). In this
  run its English WER (2.24 %) sits between Fast and Best, at Best's speed and memory. It is the model the open-source
  desktop app Handy uses for its CPU mode (https://github.com/cjpais/Handy). Android and desktop, optional download,
  gated to phones with 6 GB of RAM like Best. Note: Humming's text clean-up and voice commands are English rules, so
  other languages get transcription without them.
- **Balanced (desktop): Moonshine v2 base** (MIT, int8 export by sherpa-onnx). More accurate than Lite (3.11 % vs
  4.51 %) at under half of Fast's memory; a middle step for older laptops.

**Default unchanged: Fast** (Parakeet TDT-CTC 110M). In this run it is still the best trade-off: 2.72 % WER at the
lowest latency of the Parakeet models. Best remains the most accurate (1.77 %).

## Not added yet (needs new engine code, and device testing)

- **Streaming models** (Nemotron Speech Streaming EN 0.6B, sherpa-onnx `nemotron-speech-streaming-*` and
  `nemotron-3.5-asr-streaming-*`; Moonshine streaming small/medium; Parakeet unified streaming). Humming's engines are
  offline (decode on pause); these need sherpa-onnx's online recognizer, a different code path that has to be built
  and measured on phones before it ships. Nemotron's licence (listed as "other" on Hugging Face) also needs to be read
  and recorded first.
- **Bangla**: `sherpa-onnx-streaming-zipformer-bn-vosk-2026-02-09` (87 MB) is also a streaming model, and its licence
  was not confirmed in the survey.

## Reproduce

```bash
python3 keyboard/ci/fetch_librispeech_sample.py /tmp/libri test-clean 200
# unpack the model archives (URLs and SHA-256 in models.rs) into /tmp/models, then:
pip install sherpa-onnx==1.13.8 jiwer soundfile
WER_PER_UTT=out.jsonl python3 keyboard/ci/wer_bench.py /tmp/models /tmp/libri/LibriSpeech out.json 200 0
```

Earlier figures (README, CI run 37204875974: Fast 2.78 %, Best 1.68 %) used the same 200 clips on a different
machine; the small difference is run-to-run and runtime-version noise.
