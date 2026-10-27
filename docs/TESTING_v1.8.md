# Testing Humming 1.8

What was run for 1.8.0, how, and what it printed. Local runs were on a Linux x86-64 machine; everything that needs an
Android SDK, Xcode or a desktop GUI toolkit ran in CI on pull request #15.

## Local

| What | Command | Result |
|---|---|---|
| Rust core, voice edit (incl. the 35 shared cases) | `cd core && cargo test --no-default-features --lib voice_edit` | 3 passed, 0 failed |
| Rust core, FFI build | `cd core && cargo check --no-default-features --features ffi --lib` | builds |
| Kotlin port against the shared cases | `kotlinc VoiceEdit.kt` + a harness that reads `core/testdata/voice_edit_vectors.json` | 35 / 35 match |
| Kotlin model catalog | `kotlinc VoiceModels.kt` + harness | catalog checks pass (3 models with size and SHA-256 checked) |
| Swift core (iOS clean-up, lists, voice edit) | `cd ios/HummingCore && swift test` (Swift 6.0.3, Linux) | 58 tests, 0 failures |
| Desktop voice edit (`desktop/src-tauri/src/voice_edit.rs`) | the file's own tests in a stand-alone crate (the Tauri crate needs GTK/WebKit) | 5 passed, 0 failed (includes all 35 shared cases) |
| Desktop model catalog (`desktop/src-tauri/src/models.rs`) | the file's own tests in a stand-alone crate | 5 passed, 0 failed |
| Kotlin style | `bash keyboard/ci/static_checks.sh ktlint` | 0 findings |
| Kotlin static analysis | `bash keyboard/ci/static_checks.sh detekt` | 0 findings |
| Word error rate, all five models | `python3 keyboard/ci/wer_bench.py` with sherpa-onnx 1.13.8 (the apps' engine), LibriSpeech test-clean, first 200 clips | Best 1.77 %, Multilingual 2.24 %, Fast 2.72 %, Balanced 3.11 %, Lite 4.51 %; full table in [BENCHMARKS.md](BENCHMARKS.md), raw output in `docs/bench/local-20261028-*` |

## CI (pull request #15)

These jobs build and test the real apps, including the code that cannot be compiled locally (Android framework, UIKit,
the Tauri desktop crate):

- Android: lint, unit tests and build, release build, signed release (APK + AAB), CodeQL (java-kotlin), emulator smoke
  test on Android 14 (`ImeSmokeTest`).
- Desktop: compile and unit tests on Linux, builds for Linux x64, macOS universal and Windows x64 (these compile the
  new `voice_edit.rs` and the model catalog), benchmark jobs on four runners.
- iOS: `swift test` on Linux, simulator tests, screenshots and the unsigned ipa.
- Quality: ktlint + detekt, gitleaks over the full history, website check.

The emulator smoke test failed once on the 1.8 branch at "Humming in Android's keyboard settings" (it could not find
Humming in Android's keyboard list; the same step passed on the previous commit and nothing in that commit touches the
keyboard settings). It was re-run; the result of the final run is on the pull request.

## Not tested

- On a real phone or a real desktop with a microphone: voice editing has only been exercised through the unit tests
  and the shared cases above, not by speaking to a device.
- The Multilingual model's accuracy on languages other than English was not measured.
