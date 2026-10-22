# Unit-test coverage of the Android keyboard (1.7.1)

Line coverage of the JVM unit tests (`:app:createDebugUnitTestCoverageReport`, JaCoCo through AGP), summarised by
[`scripts/coverage_summary.py`](../../scripts/coverage_summary.py) in `keyboard.yml`. Both runs are on GitHub Actions;
the JSON each run wrote is committed next to this file.

| Scope | Before (commit `3b01d65`, run 39710080284) | After (commit `33a14bb`, run 39713465530) |
|---|---:|---:|
| `com.humming.keyboard` (Humming's own logic) | 42.2 % (917 of 2,175 lines) | **48.2 %** (1,052 of 2,182 lines) |
| `com.humming.keyboard` with `predict` | 53.3 % (1,490 of 2,795) | **58.0 %** (1,625 of 2,802) |
| Whole app module (FlorisBoard base included) | 10.5 % (2,844 of 27,196) | 11.0 % (2,979 of 27,181) |

Files: [`coverage/before-3b01d65-run39710080284.json`](coverage/before-3b01d65-run39710080284.json),
[`coverage/after-33a14bb-run39713465530.json`](coverage/after-33a14bb-run39713465530.json); the per-class line counts quoted
below are in [`coverage/after-33a14bb-run39713465530-classes.json`](coverage/after-33a14bb-run39713465530-classes.json)
(taken from that run's JaCoCo XML report).

## What the new tests cover

| Area (DoD) | Test | Covers |
|---|---|---|
| Model management | `ModelStoreTest` (Robolectric, 9 tests) | download from a local HTTP server, SHA-256 check, HTTP Range resume, a server that ignores Range, an oversized partial file, unpacking only the engine's files (no `../` escapes), missing files, `ready()`, delete, legacy cleanup. `ModelStore`: 66 of 73 lines. |
| Settings | `PrefsTest` (Robolectric, 9 tests) | every default of a fresh install, stored switches, engine and mode fallbacks, the history-retention migration (fresh install, existing history, user's choice wins), per-app custom words and replacements. `Prefs`: 26 of 26 lines. |
| Language switching | `SubtypeCycleTest` (6 tests) | next / previous with wrap-around, one language, an active language that was removed, an empty list. `SubtypeCycle`: 7 of 7 lines. |
| Text post-processing | `TextPostProcessingTest` (10 tests) | each clean-up stage on its own (`compactTimes`, `removeRepeats`, `applyDictionary`, `finish`, `selfCorrect`, `retract`) and the audio helpers (`AudioClip`, WAV header). |

All 34 new tests passed in CI (keyboard.yml runs 39711763314 and 39713465530). Before CI, the regex expectations in
`TextPostProcessingTest` were also checked on the development machine with a throwaway `java.util.regex` mirror (18 of
18 matched); that mirror is not in the repository, so CI is the record.

## Reading the numbers

- The whole-module figure is low because FlorisBoard's UI and IME code (most of the 27,000 lines) is exercised by the
  emulator smoke test (`android-emulator.yml`), not by JVM unit tests. JaCoCo here measures unit tests only.
- Robolectric loads classes through its own class loader; the build sets JaCoCo's `includeNoLocationClasses` so those
  classes are counted (the first CI run after adding the Robolectric tests showed `ModelStore` at 0 lines because of
  this, and was fixed in `501741d`).
- Still uncovered and worth a test next: `HistoryStore` (0 of 62 lines), `WavRecorder` (needs the microphone; the
  emulator has none).
