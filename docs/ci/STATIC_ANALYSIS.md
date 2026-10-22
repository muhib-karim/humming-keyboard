# Static analysis and quality gates (since 1.7.1)

Every gate runs on GitHub Actions for pushes to `main` and `upgrade/**` branches and for pull requests. None of them
runs Gradle on a developer machine.

| Gate | Where | Tool | Fails when |
|---|---|---|---|
| Kotlin style | `quality.yml` → ktlint + detekt | ktlint 1.7.1 (release file, SHA-256 pinned), `intellij_idea` style from `keyboard/.editorconfig` | a finding not in `keyboard/config/ktlint-baseline.xml` |
| Kotlin code smells | `quality.yml` → ktlint + detekt | detekt 1.23.8 CLI (SHA-256 pinned), default rules plus `keyboard/config/detekt.yml` | a finding not in `keyboard/config/detekt-baseline.xml` |
| Android lint | `keyboard.yml` → Android lint | `:app:lintDebug`, rule configuration `keyboard/app/lint.xml` | an error not in `keyboard/app/lint-baseline.xml` |
| Secrets | `quality.yml` → gitleaks | gitleaks 8.21.2 (SHA-256 pinned), every commit in the history | any leak |
| Security | `codeql.yml` | CodeQL `java-kotlin`, `security-and-quality` queries, traced debug build | the analysis cannot run (alerts go to the Security tab) |
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
  - `keyboard/config/ktlint-baseline.xml`: 5,816 findings in 273 files (quality.yml on commit `3b01d65`);
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

ktlint's baseline is keyed by file, line and column. Editing an upstream file can move an old finding to a new line,
and the gate then reports it. Fix that finding or regenerate the baseline as above.
