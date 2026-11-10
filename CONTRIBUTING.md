# Contributing to Humming

Thank you for helping improve Humming. This guide covers bug reports, feature ideas and pull requests.

The repository is private for now and is planned to be made public with the 1.6.3 release. Once it is public,
issues and pull requests will be open to everyone. By taking part you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Reporting a bug

Open an issue with the **Bug report** template. The most useful reports include:

- the platform and Humming version (Android: **Settings > About**; desktop: the app's Settings; iOS: the app);
- the device or operating system version;
- what you did, what you expected and what happened;
- whether it happens in Offline or Online voice mode, and with the Fast or Best model (for voice problems);
- for a crash on Android, the log from the crash screen (it is never sent automatically; copy it yourself).

Please do not paste API keys, personal text you dictated, or anything else private into an issue: issues are public.
For a privacy request, use the private route in [SECURITY.md](SECURITY.md) (GitHub private vulnerability reporting).

**Security problems** must not be reported in a public issue. Follow [SECURITY.md](SECURITY.md) instead.

## Suggesting a feature

Open an issue with the **Feature request** template and describe the problem you want solved, not only the solution.
Humming's core promise is private, on-device dictation, so proposals that send typed text or audio off the device
must be opt-in, off by default and clearly labelled, like the existing Online mode, GIF search and AI clean-up.

## Development setup

Build instructions per platform are in the README under [Build from source](README.md#build-from-source), with more
detail in [desktop/README.md](desktop/README.md), [docs/IOS_SIDELOAD.md](docs/IOS_SIDELOAD.md) (Xcode route) and
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) (how the code is organised).

Run the tests for the part you changed before opening a pull request. CI runs them again on the pull request
([what CI checks](#what-ci-checks-on-a-pull-request)):

| Area | Command |
|---|---|
| Android keyboard | `cd keyboard && ./gradlew :app:testDebugUnitTest` |
| Desktop app | `cd desktop && npm ci && npm run build`, then `cd src-tauri && cargo test` |
| Shared core | `cd core && cargo test --no-default-features --lib` |
| iOS clean-up rules | `cd ios/HummingCore && swift test` |
| Kotlin style (Java 17 only, no Gradle) | `bash keyboard/ci/static_checks.sh ktlint`, then `bash keyboard/ci/static_checks.sh detekt` |
| Release, site and CI scripts | `python3 -m unittest discover -s scripts -p 'test_*.py'` |

## Pull requests

1. Branch from `main` and keep each pull request to one topic.
2. Fill in the pull request template: what changed, why, how you tested it, and any screenshots for UI changes.
3. Add an entry to [CHANGELOG.md](CHANGELOG.md) for user-visible changes.
4. Keep the documentation in step with the code (README, `docs/`, in-app text).

### What CI checks on a pull request

[`ci.yml`](.github/workflows/ci.yml) runs on every pull request:

- always: ktlint and detekt (new findings only; older ones are in the baselines under `keyboard/config/`), gitleaks
  over the whole history, the website check and the script tests;
- when the pull request touches `keyboard/`: the Android unit tests, Android lint and the APK builds;
- when it touches `desktop/` or `core/`: the desktop and core tests and the installers;
- when it touches `ios/`: the Swift tests and the simulator build;
- a change to the shared test vectors in `core/testdata/` runs all three platforms.

Its last job, `gate`, passes only when all of those passed. `gate` is the status check that `main` requires, so a pull
request cannot be merged while it is red. CodeQL and the emulator smoke test also run on pull requests that touch
`keyboard/`; they are not part of the gate. Details: [docs/ci/STATIC_ANALYSIS.md](docs/ci/STATIC_ANALYSIS.md).

### Project rules

- **The offline voice path stays offline.** On Android, `NetworkAuditTest` fails the unit tests if any file other than
  `ModelDownload.kt`, `GifSearch.kt`, `LlmCleanup.kt` and `AppUpdate.kt` uses a network API. Do not widen that list
  without a clear reason, a default-off setting and an update to the privacy policy
  ([play/PRIVACY_POLICY.md](play/PRIVACY_POLICY.md) and `play/site/privacy.html`).
- **One set of clean-up rules.** Clean mode and Accuracy mode exist three times: `TextCleanup.kt` (Android),
  `text_cleanup.rs` (desktop) and `TextCleanup.swift` (iOS). A change to the rules should be made, and tested, on
  every platform it applies to.
- **Respect the FlorisBoard licence.** `keyboard/` is a fork of FlorisBoard (Apache-2.0). Keep upstream licence
  headers on modified files, mark new files as added by Humming, and list notable changes in
  [keyboard/NOTICE-HUMMING.md](keyboard/NOTICE-HUMMING.md). New third-party code, data or models need a licence
  compatible with that and an entry in [keyboard/LICENSES.md](keyboard/LICENSES.md) (the Android inventory); no GPL code
  is included. For the desktop app, core or iOS there is no inventory file yet: name the new dependency and its
  licence in the pull request.
- **Pin what you download.** Models and native libraries fetched at build or run time are checked against a pinned
  SHA-256; keep it that way for anything new.
- **No secrets in the repository.** Signing keys and API keys live in repository secrets or on the user's device
  only (see [docs/RELEASE_SIGNING.md](docs/RELEASE_SIGNING.md)).
- **Measured claims only.** Benchmarks and comparisons in the documentation cite a committed script and output, or a
  vendor's own page with the date it was checked.

## Licensing

The whole repository is licensed under Apache-2.0 (root `LICENSE` and `NOTICE`); contributions are accepted under the
same licence. See [Licence and credits](README.md#licence-and-credits) before contributing.
