# keyboard/ci: scripts the workflows run

| Script | Run by | What it does |
|---|---|---|
| `static_checks.sh ktlint\|detekt` | `quality.yml` | Downloads the pinned ktlint / detekt release (SHA-256 checked) and checks `keyboard/app` against the baseline in `keyboard/config/`. A missing baseline fails and writes a fresh one to `build/static/`. See [docs/ci/STATIC_ANALYSIS.md](../../docs/ci/STATIC_ANALYSIS.md). |
| `emulator_run.sh <out dir>` | `android-emulator.yml` | With the debug, test and benchmark APKs built: runs `ModelLoadBenchTest` (Fast model download + load time) and `ImeSmokeTest` (settings + typing, screenshots), then measures cold start with `am start -W`. `scripts/emulator_metrics.py` labels the numbers with the system image and date. |
| `wer_bench.py`, `fetch_librispeech_sample.py` | `wer-bench.yml` | Accuracy and speed of the voice models on LibriSpeech (README "Benchmarks"). |

Run from the repository root. Locally, `static_checks.sh` needs only Java 17; `emulator_run.sh` needs `adb` and a
running emulator or device with the APKs from
`./gradlew -Phumming.abis=x86_64 :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleBenchmark`.
`ModelLoadBenchTest` downloads the 104 MB Fast model, so it only runs when `-e humming.modelBench true` is passed.
