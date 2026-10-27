# Static analysis and quality gates (since 1.7.1)

Every gate runs on GitHub Actions for pushes to `main` and `upgrade/**` branches and for pull requests. None of them
runs Gradle on a developer machine.

| Gate | Where | Tool | Fails when |
|---|---|---|---|
| Kotlin style | `quality.yml` → ktlint + detekt | ktlint 1.7.1 (release file, SHA-256 pinned), `intellij_idea` style from `keyboard/.editorconfig` | a finding not in `keyboard/config/ktlint-baseline.xml` |
| Kotlin code smells | `quality.yml` → ktlint + detekt | detekt 1.23.8 CLI (SHA-256 pinned), default rules plus `keyboard/config/detekt.yml` | a finding not in `keyboard/config/detekt-baseline.xml` |
| Android lint | `keyboard.yml` → Android lint | `:app:lintDebug`, rule configuration `keyboard/app/lint.xml` | an error not in `keyboard/app/lint-baseline.xml` |
| Secrets | `quality.yml` → gitleaks | gitleaks 8.21.2 (SHA-256 pinned), every commit in the history | any leak |
| Security | `codeql.yml` | CodeQL `java-kotlin`, `security-extended` queries, traced debug build, `.github/codeql/codeql-config.yml` | the analysis cannot run; alerts go to the Security tab, and the branch is expected to have **0 open** (see "CodeQL" below) |
| Website | `quality.yml` → website check | `scripts/assemble_site.py` + `scripts/check_site.py` (same as `pages.yml`) | a broken link, image, anchor or a request to another origin |
| Script tests | `quality.yml` | `unittest` for the release, coverage and emulator-metrics scripts | a failing test |
| Unit tests + coverage | `keyboard.yml` → build | JUnit 4/5, Robolectric 4.16.1, JaCoCo (`createDebugUnitTestCoverageReport`) | a failing test (coverage is reported, see [COVERAGE.md](COVERAGE.md)) |
| Emulator smoke test | `android-emulator.yml` | UiAutomator on an API 34 emulator | Humming cannot be enabled, opened or typed with |
| Dependencies | `.github/dependabot.yml` | Dependabot, weekly: Gradle (`/keyboard`) and GitHub Actions | (opens grouped pull requests) |

## Baselines: what they hold and how they were made

Humming's Android app is a fork of FlorisBoard. The upstream code has thousands of style findings that this project
did not write and does not plan to reformat (a mass reformat would make every future upstream merge conflict). The
baselines record exactly the findings that existed when the gates were switched on, so the gates stop *new* ones.

- **How they were made**: CI, not a developer machine. The first run of each gate had no baseline, wrote one to its
  artifact and failed on purpose (`keyboard/ci/static_checks.sh`; Android lint aborts the same way). The files were
  downloaded, checked, and committed:
  - `keyboard/config/ktlint-baseline.xml`: 5,816 findings in 273 files (quality.yml on commit `3b01d65`); 5,815 after
    the CodeQL fixes removed one. Entries of seven files edited in 1.7.1 were re-keyed to their new line numbers after
    checking that each file's rule counts were unchanged (no finding added). The same was done for `DictationOutput.kt`,
    `DictationOutputTest.kt`, `HummingVoice.kt` and `VoiceScreen.kt` with voice editing (rule counts per file
    unchanged; the new `FieldEdit.kt` and `FieldEditTest.kt` have no entries);
  - `keyboard/config/detekt-baseline.xml`: 1,634 findings (same run). Two entries for `ExtensionAuthorTest.kt`, an
    upstream test that is commented out in full, were replaced by an `excludes` entry in `detekt.yml` so that the
    file's sample e-mail addresses are not copied into the baseline;
  - `keyboard/app/lint-baseline.xml`: 340 Android lint issues (151 of them `UnusedResources` in upstream resources), from
    the `lint` job of keyboard.yml on the same commit. `lintVitalRelease` (part of every release build) reads the same file.
- **What was checked before committing**: no entry points at a file added in 1.7.1. The one detekt finding in new code
  (an over-long comment in `ImeSmokeTest.kt`) was fixed instead. New and changed Kotlin files were also run through
  the same ktlint and detekt releases before pushing.
- **A missing baseline is a failure**, never a silent pass.
- **Shrinking them**: fix findings in a file and regenerate (delete the file, push, take the new one from the artifact).
  Never add new findings to a baseline by hand.

## CodeQL

The first CodeQL runs on this branch used the `security-and-quality` suite and opened 110 alerts. How each was settled:

| Alert | Where | Outcome |
|---|---|---|
| `java/zipslip` (high) | `ModelDownload.kt`, model archive extraction | **Fixed.** `ModelPolicy.outputFile` refuses any entry whose normalised path leaves the model directory (on top of the existing name check); `ModelPolicyTest.outputFilesStayInsideTheModelDirectory` covers `../`, `a/../../` and a sibling directory with the same prefix. |
| `java/relative-path-command` (medium) | upstream `Devtools.kt`, logcat dump | **Fixed.** Runs `/system/bin/logcat` by absolute path. |
| `java/android/backup-enabled` (high) | `AndroidManifest.xml` | **Excluded in the CodeQL config, with this reason.** The query reads only `android:allowBackup`. The manifest's `dataExtractionRules` and `fullBackupContent` are allow-lists: cloud backup carries the keyboard settings and installed themes only; device-to-device transfer also carries learned words. Voice models, voice settings and custom words, clipboard and dictation history are never included, and `BackupRulesTest` fails if a rule ever names them. Keeping settings backup is a 1.7.0 product decision (privacy policy, "Android backup"). |
| `java/android/missing-certificate-pinning` (medium) ×3 | `AppUpdate.kt`, `GifSearch.kt`, `LlmCleanup.kt` | **Excluded in the CodeQL config, with this reason.** All three use HTTPS with the system trust store, to endpoints whose certificates Humming does not control: the update feed on GitHub/Cloudflare Pages, Openverse (GIF search, off by default), and the AI provider the user chooses with their own key (off by default). A pin would break each of them at the provider's next certificate rotation. The update path does not rely on TLS for integrity: the APK must match the feed's SHA-256 and be signed with Humming's certificate before Android is even asked to install it. None of the three is on the voice path. |
| 104 quality warnings and notes | 94 in upstream FlorisBoard code, 4 in KSP-generated code, 6 in Humming's | **Suite changed to `security-extended`** (code quality is gated by ktlint, detekt and Android lint, with baselines, instead) and `**/build/generated/**` ignored. Humming's own were fixed anyway: the voice panel's timer loop no longer tests a value that cannot change inside it (`HummingVoice.kt`), `Lexicon` flag tests are parenthesised, and `UpdateInstallReceiver` uses `IntentCompat` instead of the deprecated call. Not changed: `BluetoothMic`'s `startBluetoothSco` / `isBluetoothScoOn`, deprecated in API 31 but the only Bluetooth headset route on Android 8 to 11 (min SDK 26); they run only below API 31 or when the API 31 route fails, and carry `@Suppress("DEPRECATION")`. |

To bring a quality query back for Humming's own code, add it to the config with `paths: [keyboard/app/src/main/kotlin/com/humming]`
in a separate analysis category rather than switching the whole suite back.

ktlint's baseline is keyed by file, line and column. Editing an upstream file can move an old finding to a new line,
and the gate then reports it. Fix that finding or regenerate the baseline as above.
