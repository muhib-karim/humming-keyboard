---
name: humming-benchmarks
description: Run, update, or document speech model WER, latency, RAM, and Android emulator cold-start benchmarks. Use only when editing keyboard/ci/wer_bench.py, docs/BENCHMARKS.md, docs/bench/, or emulator metrics.
---

# Humming Benchmark Workflow

1. **Production ASR vs legacy core**:
   - Production shipping models (`sherpa-onnx 1.13.8` Parakeet & Moonshine) use `keyboard/ci/fetch_librispeech_sample.py` and `keyboard/ci/wer_bench.py` (or `.github/workflows/wer-bench.yml`).
   - Scripts under `core/ci/` (`bench.py`, `stream_bench.py`) are legacy `whisper.cpp` harnesses; do not cite their numbers as Parakeet production metrics.
2. **Pin & provenance discipline**:
   - Verify SHA-256 digests and archive byte sizes match `models.rs` and `VoiceModels.kt` whenever adding or updating a model.
   - Commit raw JSON / JSONL outputs under `docs/bench/` alongside any figure quoted in `docs/BENCHMARKS.md`, `README.md`, or `play/site/`.
3. **Android emulator measurements**:
   - `keyboard/ci/emulator_run.sh` + `scripts/emulator_metrics.py` collect cold-start (`am start -W`, `COLD` launches only) and `ModelLoadBenchTest` load timings. Clearly label emulator runs as runner baselines, never as physical phone speed.
