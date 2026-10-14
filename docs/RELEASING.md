# Releasing Humming

How a Humming release is versioned, signed, assembled and published. Signing keys and secrets are described in
[RELEASE_SIGNING.md](RELEASE_SIGNING.md); no key, password or secret value belongs in this repository or in any
release note.

- [1. Versions](#1-versions)
- [2. Changelog](#2-changelog)
- [3. Signing](#3-signing)
- [4. Release assets](#4-release-assets)
- [5. Update feeds](#5-update-feeds)
- [6. Tag and publish](#6-tag-and-publish)
- [7. After the release](#7-after-the-release)

## 1. Versions

Each platform keeps its version in its own build file:

| Platform | File | Fields |
|---|---|---|
| Android | `keyboard/gradle.properties` | `projectVersionName` (for example `1.6.3`) and `projectVersionCode` (for example `1603`) |
| Desktop | `desktop/src-tauri/tauri.conf.json`, `desktop/src-tauri/Cargo.toml`, `desktop/package.json` | `version` |
| iOS | `ios/project.yml` | `MARKETING_VERSION` and `CURRENT_PROJECT_VERSION` (build number, for example `1600`) |
| Shared core | `core/Cargo.toml` | `version` |

Version codes follow the version: 1.4.0 is `1400`, 1.6.0 is `1600`, 1.6.3 is `1603`. The Android updater installs a
release only if its `versionCode` is higher than the installed one, so the code must go up with every Android release.

Platforms may be on different versions. At the time of writing Android is 1.6.3 on `main` (1.6.2 released), the
desktop app is 1.6.0 and the iOS project is 1.6.0 (build 1600), with the last published iOS build being 1.5.0. Each
CHANGELOG entry states which platform versions it covers.

## 2. Changelog

Add a section to [CHANGELOG.md](../CHANGELOG.md) at the top, headed `## <version>: <short title>`, for example
`## 1.6.3: voice never types placeholder text, ...`. Describe user-visible changes first, then name the platform
versions (for example "Android 1.6.3 (versionCode 1603). Desktop stays 1.6.0, iOS unchanged.").

`scripts/assemble_release.py` copies the section whose heading starts with `## <version>` into the GitHub release
notes and into the `notes` field of both update feeds (the Android feed keeps the first 2,000 characters).
While a version is only a candidate, its heading carries `(release candidate)` and a **Status** line; remove both
before tagging, so the published notes do not call the release a candidate.

## 3. Signing

| Artifact | Signed with | Checked by |
|---|---|---|
| Android APK and Play AAB | The Humming upload key (PKCS12). Gradle reads it from `HUMMING_KEYSTORE`, `HUMMING_KEYSTORE_PASSWORD`, `HUMMING_KEY_ALIAS` and `HUMMING_KEY_PASSWORD`; in CI these come from repository secrets. Without them the build falls back to the debug key. | `android-release.yml` fails unless the APK and AAB certificate SHA-256 equals the published certificate `8C:8B:68:BB:E6:3A:A4:2A:85:61:59:AA:A5:B0:C9:0C:D9:54:EE:F7:8A:98:0D:39:BC:B6:69:41:75:3E:1F:C6`. The in-app updater requires the same certificate. |
| Desktop self-update bundles (`.app.tar.gz`, setup `.exe`, `.msi`, `.AppImage`, optional `.deb`) | Humming's minisign (Ed25519) update key, through `tauri build` with the `TAURI_SIGNING_PRIVATE_KEY` secrets | `assemble_release.py` and `scripts/verify_updater.py` check every `.sig` against `plugins.updater.pubkey` in `desktop/src-tauri/tauri.conf.json` |
| macOS `.app` | Ad-hoc signature (`signingIdentity "-"`), not notarised | — |
| Windows and Linux installers, iOS `.ipa` | Not code-signed | `SHA256SUMS.txt` |

Full details, including the options for paid desktop code signing: [RELEASE_SIGNING.md](RELEASE_SIGNING.md).

Two builds come from the Android code: the sideloaded APK (with the self-updater) and the Google Play bundle, built
with `-Phumming.play=true` (no updater, no `REQUEST_INSTALL_PACKAGES`). The release builds are arm64-v8a only:

```sh
cd keyboard
./gradlew :app:assembleRelease -Phumming.abis=arm64-v8a
./gradlew :app:bundleRelease  -Phumming.abis=arm64-v8a -Phumming.play=true
```

## 4. Release assets

`scripts/assemble_release.py <artifacts dir> <out dir> <version>` builds the release folder. It expects exactly one
file for each of these, renames the Android files, and stops with an error if anything is missing or a signature
does not verify:

| File | Platform |
|---|---|
| `Humming-<version>.apk` | Android, sideloaded (self-updating) |
| `Humming-<version>.aab` | Android, Google Play bundle |
| `Humming_<version>_unsigned.ipa` | iPhone / iPad |
| `Humming_<version>_universal.dmg` and `Humming_<version>_universal.app.tar.gz` (+ `.sig`) | macOS |
| `Humming_<version>_x64-setup.exe` and `Humming_<version>_x64_en-US.msi` (+ `.sig`) | Windows |
| `Humming_<version>_amd64.AppImage` (+ `.sig`) and `Humming_<version>_amd64.deb` | Linux |
| `latest.json`, `android.json` | Update feeds (next section) |
| `SHA256SUMS.txt` | SHA-256 of every file above |

It also refuses a version that does not match both `desktop/src-tauri/tauri.conf.json` and
`keyboard/gradle.properties`.

## 5. Update feeds

| Feed | Read by | Contents |
|---|---|---|
| `latest.json` | Desktop apps (`tauri-plugin-updater`) | `version`, `notes`, `pub_date` and, per platform (`darwin-aarch64`, `darwin-x86_64`, `windows-x86_64`, `linux-x86_64` and their installer-specific variants), the download URL and minisign signature |
| `android.json` | Android APK (`AppUpdate.kt`) | `versionCode`, `versionName`, `apk` (file name), `sha256` and `notes` |

The apps read both feeds from `https://humming-keyboard.pages.dev/updates/` (`AppUpdate.FEED_BASE` and the updater
endpoint in `tauri.conf.json`). The download URLs inside `latest.json` point at the same place.

`pages.yml` copies the latest GitHub Release's feeds, `SHA256SUMS.txt`, the APK and the desktop update files to
`/updates/` on the GitHub Pages site and checks the live feed's signatures. It only mirrors a release that contains a
`latest.json`; for a release without one it publishes nothing under `/updates/`.

Check a feed by hand at any time:

```sh
python3 scripts/verify_updater.py --config desktop/src-tauri/tauri.conf.json --latest <path or URL of latest.json> --download
```

## 6. Tag and publish

With GitHub Actions available, a release is one tag:

1. Update the versions (section 1) and the changelog (section 2) on `main`, and make sure CI is green.
2. Create and push an annotated tag: `git tag -a v<version> -m "Humming <version>"` then `git push origin v<version>`.
3. `release.yml` runs `android-release.yml`, `desktop.yml` and `ios.yml`, then `assemble_release.py`, verifies the
   feed and the checksums, and creates the GitHub Release `Humming <version>` (marked latest) with every file and the
   changelog section as notes. If the release already exists, it uploads the files to it instead.
4. `release.yml` then runs `pages.yml`, which redeploys the website and the `/updates/` mirror and checks the live feed.

**Without GitHub Actions** (the case since 5 October 2026, and how 1.6.0 was built): build each platform on a
machine that has the signing material, verify every update signature with `scripts/verify_updater.py` and every
certificate as `android-release.yml` does, assemble the files with the names above and a `SHA256SUMS.txt`, create the
tag and the GitHub Release by hand, and publish the feeds and installers to the update mirror. macOS and iOS builds
need a Mac, so a release built on Linux leaves the previous macOS and iOS files current (as in 1.6.0).
`assemble_release.py` requires the Android and desktop versions to match, so an Android-only release (such as 1.6.1
to 1.6.3, with the desktop app on 1.6.0) cannot go through it unchanged; write `android.json` and `SHA256SUMS.txt`
in the same format.

## 7. After the release

- Install the APK over the previous version and check **Settings > Updates > Check for updates** on a device with the
  previous release, so the in-app update path is exercised.
- Check that `https://humming-keyboard.pages.dev/updates/android.json` (and `latest.json` for a desktop release)
  shows the new version.
- Update version references in the README's [Download and install](../README.md#download-and-install) section.
