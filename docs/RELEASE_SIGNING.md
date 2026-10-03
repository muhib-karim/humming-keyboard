# Release signing: what is prepared, what the owner supplies

Nothing secret is in this repository. Every key below is created and held by the owner; CI reads it from
repository secrets at build time.

## Android keyboard (keyboard/)

Prepared: `keyboard/app/build.gradle.kts` signs `release` builds with the owner's key when these environment
variables are set, and falls back to the debug key (today's behaviour) when they are not:

| Variable | Meaning |
|---|---|
| `HUMMING_KEYSTORE` | path to the keystore file (PKCS12 `.p12` or `.jks`) |
| `HUMMING_KEYSTORE_PASSWORD` | keystore password |
| `HUMMING_KEY_ALIAS` | key alias (default `humming`) |
| `HUMMING_KEY_PASSWORD` | key password (default: the keystore password, as PKCS12 requires) |

Status (2026-10-03): done in CI. An upload key exists (PKCS12, RSA 4096, generic DN) and its four repository
secrets are set: `HUMMING_KEYSTORE_BASE64`, `HUMMING_KEYSTORE_PASSWORD`, `HUMMING_KEY_ALIAS`, `HUMMING_KEY_PASSWORD`.
These four names are the only keystore secrets the workflows read.

`.github/workflows/android-release.yml` (push to `main` touching `keyboard/`, or run by hand):
1. decodes `HUMMING_KEYSTORE_BASE64` to a temp file under `$RUNNER_TEMP` (mode 600) and points `HUMMING_KEYSTORE` at it;
2. builds `:app:assembleRelease` and `:app:bundleRelease` (R8, arm64-v8a) with the passwords and alias passed only as
   step environment, never echoed;
3. runs `apksigner verify --print-certs` on the APK and `jarsigner -verify` + `keytool -printcert` on the AAB, prints
   both certificate SHA-256s and fails unless both equal the published upload certificate:
   `8C:8B:68:BB:E6:3A:A4:2A:85:61:59:AA:A5:B0:C9:0C:D9:54:EE:F7:8A:98:0D:39:BC:B6:69:41:75:3E:1F:C6`;
4. deletes the keystore file (`if: always()`), then uploads `humming-keyboard-signed-release`: the APK, the AAB,
   the apksigner printout and `SHA256SUMS.txt`. Nothing is released or published.

Still for the owner, if the app goes to Google Play: a Play Console developer account, the app entry
`com.humming.keyboard` enrolled in Play App Signing (Google then holds the app signing key and this key is only the
upload key), and the Play app signing key's SHA-256 added to `landing/.well-known/assetlinks.json`. Keep an offline
backup of the upload key; losing it means a key reset through Play.

## Desktop app (desktop/)

Decision (2026-10-03): zero spend, so the desktop installers ship **unsigned**. Users get a one-time warning;
the "How to open it" steps are in the README, `desktop/README.md` and the landing page (macOS: System Settings >
Privacy & Security > Open Anyway, https://support.apple.com/en-us/102445; Windows: SmartScreen > More info > Run
anyway). The paid routes below stay documented for later. To sign:

macOS (Developer ID + notarisation), the owner supplies:
- an Apple Developer Program membership (USD 99/year) and a "Developer ID Application" certificate exported as
  `.p12` with a password;
- an app-specific password for his Apple ID and his Team ID.

Repository secrets (names tauri-action reads): `APPLE_CERTIFICATE` (base64 of the .p12),
`APPLE_CERTIFICATE_PASSWORD`, `APPLE_SIGNING_IDENTITY` (e.g. `Developer ID Application: <Name> (<TEAMID>)`),
`APPLE_ID`, `APPLE_PASSWORD` (app-specific password), `APPLE_TEAM_ID`. Pass them as `env:` to the
`tauri-apps/tauri-action` step; signing and notarisation then happen inside `tauri build`.

Windows, the owner supplies one of:
- an OV/EV code-signing certificate (`.pfx` + password, or a cloud HSM such as Azure Trusted Signing or
  DigiCert KeyLocker); then set `bundle.windows.certificateThumbprint` (local cert store) or
  `bundle.windows.signCommand` (cloud signing) in `desktop/src-tauri/tauri.conf.json`.

Auto-update (later): `tauri-plugin-updater` needs an update signing key pair (`tauri signer generate`); the
private key and its password become secrets `TAURI_SIGNING_PRIVATE_KEY` / `TAURI_SIGNING_PRIVATE_KEY_PASSWORD`.

### Optional free route for Windows: SignPath Foundation

The SignPath Foundation (https://signpath.org/) offers free code signing for open-source projects: builds made in a
public CI pipeline from a public repository are signed with a certificate issued to the foundation, which removes the
"unknown publisher" SmartScreen warning over time. It requires an application and an OSI-licensed public project;
Humming's eligibility has **not** been checked and nothing has been applied for. If it is ever pursued, the
signing step would be added to `desktop.yml` after the tauri-action build, following SignPath's GitHub Actions
integration. There is no equivalent free route for macOS notarisation (it needs an Apple Developer Program membership).
