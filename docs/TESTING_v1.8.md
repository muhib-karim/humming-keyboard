# Testing Humming 1.8

What was run for 1.8.0, how, and what it printed. Local runs were on a Linux x86-64 machine; everything that needs an
Android SDK, Xcode or a desktop GUI toolkit ran in CI on pull request #15.

## Local

| What | Command | Result |
|---|---|---|
| Rust core, voice edit (incl. the 35 shared cases) | `cd core && cargo test --no-default-features --lib voice_edit` | 3 passed, 0 failed |
| Rust core, FFI build | `cd core && cargo check --no-default-features --features ffi --lib` | builds |
| Kotlin port against the shared cases | `kotlinc VoiceEdit.kt` + a harness that reads `core/testdata/voice_edit_vectors.json` | 35 / 35 match, plus the cut-window case ("Monday" cut to "day": no command) |
| Kotlin model catalog | `kotlinc VoiceModels.kt` + harness | catalog checks pass (3 models with size and SHA-256 checked; English clean-up off only for Multilingual) |
| Swift core (iOS clean-up, lists, voice edit) | `cd ios/HummingCore && swift test` (Swift 6.0.3, Linux) | 59 tests, 0 failures |
| Desktop voice edit (`desktop/src-tauri/src/voice_edit.rs`) | the file's own tests in a stand-alone crate (the Tauri crate needs GTK/WebKit) | 8 passed, 0 failed (includes all 35 shared cases; unknown app never edits; 30 s window and one edit per insertion; Backspace counts grapheme clusters, family emoji = 1) |
| Desktop model catalog (`desktop/src-tauri/src/models.rs`) | the file's own tests in a stand-alone crate | 6 passed, 0 failed (incl. English clean-up off only for Multilingual) |
| Desktop clean-up (`desktop/src-tauri/src/text_cleanup.rs`) | the file's own tests in a stand-alone crate, plus a check that the Multilingual options keep "Er sagt, er kommt morgen" | 26 passed, 0 failed |
| Kotlin clean-up for Multilingual | `kotlinc TextCleanup.kt` + harness | `neutral` keeps "Er sagt, er kommt morgen"; English clean-up still removes "er" |
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

The emulator smoke test failed at "Humming in Android's keyboard settings" on the 1.8 release commit (it could not find
Humming in Android's keyboard list; the same step passed on an earlier 1.8 commit and on the parallel voice-edit branch,
and nothing in the release commit touches the keyboard settings). The result on the final commit is on the pull
request.

## Review fixes (Codex review of the 1.8 diff)

| Finding | Fix | Test |
|---|---|---|
| Desktop: unknown app counted as "same app" | the focused app is always read; an edit needs a known, identical app at insertion and now, re-checked right before any Backspace | `unknown_app_never_edits` |
| Desktop: stale last insertion | edits only within 30 s (`EDIT_WINDOW_MS`) and once per insertion | `edits_expire_and_happen_once` |
| Desktop: Backspace counted Unicode scalars | counts grapheme clusters (`unicode-segmentation`); a cut inside a cluster is refused | `backspaces_count_grapheme_clusters` |
| Android: "undo that" never saw the previous recording | the last dictated commit and its field are kept across recordings, cleared for another field; a whole-utterance "undo that" now reaches it | covered by `VoiceEdit.undoCount` tests; device behaviour not tested |
| Android/iOS: commands read from cleaned or accumulated text | commands read from the current recording's raw words (Android `spokenRaw`, iOS `latestUtterance`) | Kotlin and Swift shared cases |
| Cut window made a false word boundary | `voiceCommand(..., truncated)` refuses a match at index 0 of a cut window (Android when 1,000 chars, iOS always) | `matchAtTheStartOfATruncatedWindowIsNotACommand`, `testTruncatedWindowRefusesAMatchAtItsStart` |
| Multilingual output went through English clean-up | English rules off for Multilingual on desktop and Android (spacing and dictionary only) | `multilingual_model_skips_english_cleanup`, `neutralKeepsNonEnglishWords`, `english_cleanup_is_off_only_for_multilingual` |
| Balanced described as between Fast and Best | reworded everywhere: less memory than Fast, slightly less accurate and slower | docs only |

## Not tested

- On a real phone or a real desktop with a microphone: voice editing has only been exercised through the unit tests
  and the shared cases above, not by speaking to a device.
- The Multilingual model's accuracy on languages other than English was not measured.
