# Humming documentation

Start with the [project README](../README.md) for an overview, screenshots and benchmarks.

## Using Humming

| Document | Contents |
|---|---|
| [USER_GUIDE.md](USER_GUIDE.md) | Install on every platform, enable the keyboard, voice typing, settings, updates and troubleshooting |
| [IOS_SIDELOAD.md](IOS_SIDELOAD.md) | Installing the unsigned iPhone / iPad build with Sideloadly, AltStore or Xcode |
| [Privacy policy](https://muhib-karim.github.io/humming-keyboard/privacy.html) | What stays on the device and the few things that use the network (source: [../play/PRIVACY_POLICY.md](../play/PRIVACY_POLICY.md)) |
| [../CHANGELOG.md](../CHANGELOG.md) | What changed in every release |

## How it is built

| Document | Contents |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | Repository layout, the dictation pipeline (mic, VAD, speech recognition, clean-up, text), the update channel, CI |
| [../desktop/README.md](../desktop/README.md) | The desktop app: features, models, build, source layout |
| [../core/INTERFACE.md](../core/INTERFACE.md) | The interface of the shared Rust crate `humming-core` (written for the first desktop milestone) |
| [../keyboard/NOTICE-HUMMING.md](../keyboard/NOTICE-HUMMING.md) | What Humming changed in the FlorisBoard fork |
| [BRAND.md](BRAND.md) | Colours and icon files |

## Releasing and operations

| Document | Contents |
|---|---|
| [RELEASING.md](RELEASING.md) | Versioning, changelog, signing, release assets, update feeds, tag and GitHub Release |
| [RELEASE_SIGNING.md](RELEASE_SIGNING.md) | Which signing keys exist, how CI uses them, and the options for desktop code signing |
| [FIREBASE_SETUP.md](FIREBASE_SETUP.md) | Preparation notes only: Humming ships with no Firebase |
| [../play/PLAY_CHECKLIST.md](../play/PLAY_CHECKLIST.md) | Google Play listing material and checklist |

## Quality and evidence

| Document | Contents |
|---|---|
| [../AUDIT.md](../AUDIT.md) | Full audit of the Android keyboard at 1.2.0: privacy, permissions, security, performance, accessibility, licences, tests |
| [AUDIT-1.6.2.md](AUDIT-1.6.2.md) | Production audit of Android 1.6.2, with an emulator walk of every settings screen ([audit-1.6.2/](audit-1.6.2/)) |
| [bench/](bench/) | Raw outputs of the word-error-rate benchmark quoted in the README |
| [screenshots/](screenshots/), [images/](images/) | Screenshots and walkthrough recordings used by the README and the website |

## Project

| Document | Contents |
|---|---|
| [../CONTRIBUTING.md](../CONTRIBUTING.md) | Reporting bugs, proposing features, pull requests |
| [../SECURITY.md](../SECURITY.md) | Reporting a vulnerability privately |
| [../CODE_OF_CONDUCT.md](../CODE_OF_CONDUCT.md) | Contributor Covenant 2.1 |
