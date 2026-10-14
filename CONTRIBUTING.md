# Contributing to Humming

Thank you for helping improve Humming. This guide covers bug reports, feature ideas and pull requests.

Issues and pull requests are open to everyone. By taking part you agree to follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Reporting a bug

Open an issue with the **Bug report** template. The most useful reports include:

- the platform and Humming version (Android: **Settings > About**; desktop: the app's Settings; iOS: the app);
- the device or operating system version;
- what you did, what you expected and what happened;
- whether it happens in Offline or Online voice mode, and with the Fast or Best model (for voice problems);
- for a crash on Android, the log from the crash screen (it is never sent automatically; copy it yourself).

Please do not paste API keys, personal text you dictated, or anything else private into an issue.

**Security problems** must not be reported in a public issue. Follow [SECURITY.md](SECURITY.md) instead.

## Suggesting a feature

Open an issue with the **Feature request** template and describe the problem you want solved, not only the solution.
Humming's core promise is private, on-device dictation, so proposals that send typed text or audio off the device
must be opt-in, off by default and clearly labelled, like the existing Online mode, GIF search and AI clean-up.

## Development setup

Build instructions per platform are in the README under [Build from source](README.md#build-from-source), with more
detail in [desktop/README.md](desktop/README.md), [docs/IOS_SIDELOAD.md](docs/IOS_SIDELOAD.md) (Xcode route) and
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) (how the code is organised).

Run the tests for the part you changed before opening a pull request:

| Area | Command |
|---|---|
| Android keyboard | `cd keyboard && ./gradlew :app:testDebugUnitTest` |
| Desktop app | `cd desktop && npm ci && npm run build`, then `cd src-tauri && cargo test` |
| Shared core | `cd core && cargo test --no-default-features --lib` |
| iOS clean-up rules | `cd ios/HummingCore && swift test` |

## Pull requests

1. Branch from `main` and keep each pull request to one topic.
2. Fill in the pull request template: what changed, why, how you tested it, and any screenshots for UI changes.
3. Add an entry to [CHANGELOG.md](CHANGELOG.md) for user-visible changes.
4. Keep the documentation in step with the code (README, `docs/`, in-app text).

### Project rules

- **The offline voice path stays offline.** On Android, `NetworkAuditTest` fails the build if any file other than
  `ModelDownload.kt`, `GifSearch.kt`, `LlmCleanup.kt` and `AppUpdate.kt` uses a network API. Do not widen that list
  without a clear reason, a default-off setting and an update to the privacy policy
  ([play/PRIVACY_POLICY.md](play/PRIVACY_POLICY.md) and `play/site/privacy.html`).
- **One set of clean-up rules.** Clean mode and Accuracy mode exist three times: `TextCleanup.kt` (Android),
  `text_cleanup.rs` (desktop) and `TextCleanup.swift` (iOS). A change to the rules should be made, and tested, on
  every platform it applies to.
- **Respect the FlorisBoard licence.** `keyboard/` is a fork of FlorisBoard (Apache-2.0). Keep upstream licence
  headers on modified files, mark new files as added by Humming, and list notable changes in
  [keyboard/NOTICE-HUMMING.md](keyboard/NOTICE-HUMMING.md). New third-party code, data or models need a licence
  compatible with that and an entry in [keyboard/LICENSES.md](keyboard/LICENSES.md); no GPL code is included.
- **Pin what you download.** Models and native libraries fetched at build or run time are checked against a pinned
  SHA-256; keep it that way for anything new.
- **No secrets in the repository.** Signing keys and API keys live in repository secrets or on the user's device
  only (see [docs/RELEASE_SIGNING.md](docs/RELEASE_SIGNING.md)).
- **Measured claims only.** Benchmarks and comparisons in the documentation cite a committed script and output, or a
  vendor's own page with the date it was checked.

## Licensing

The whole repository is licensed under Apache-2.0 (root `LICENSE` and `NOTICE`); contributions are accepted under the
same licence. See [Licence and credits](README.md#licence-and-credits) before contributing.
