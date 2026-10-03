# Humming for Windows and macOS

Hold a shortcut, speak, let go: Humming transcribes on your own computer and pastes clean text into whatever
app has focus (mail, chat, docs, the browser, the terminal). It is the desktop sibling of the Humming Android
keyboard and uses the same engine and rules: NVIDIA Parakeet on sherpa-onnx 1.13.8 (the same two models, pinned by
SHA-256) and a Rust port of the keyboard's `TextCleanup.kt`. No account and no cloud: audio never leaves the machine.

## What it does

| Feature | Status |
|---|---|
| Push-to-talk on a global shortcut (default `Ctrl+Shift+Space` / `Cmd+Shift+Space`), or press-to-toggle | yes |
| Paste into the focused app (clipboard + Ctrl/Cmd+V, clipboard restored), or type key by key | yes |
| Offline speech-to-text: Parakeet Fast (110M) / Best (0.6B) on sherpa-onnx, as on Android; downloaded on first run with a SHA-256 check | yes |
| Clean mode (Android rules): fillers, self-corrections ("3, no, 4pm" -> "4pm"), repeats, "4 pm" -> "4pm", capitals, final ? or . | yes |
| Spoken lists -> bullets ("first, eggs, second, milk") | yes (desktop extra, can be turned off) |
| Verbatim mode | yes |
| Voice commands: new line, new paragraph, period, comma, question mark... | yes |
| Personal dictionary: fixed spellings ("Okonkwo", "GitHub") and phrase replacements | yes |
| Long dictation (up to 15 min; decoded in <= 25 s windows cut at pauses) | yes |
| History on this computer: search, copy, delete, clear | yes |
| Tray / menu bar app, listening overlay with a live mic level | yes |
| Settings: shortcut (with a recorder), mode, model, language, microphone, paste method, overlay, start at login | yes |
| In-app "Try it" box (transcribe without pasting) and a cleanup tester | yes |
| Languages other than English | not yet (Parakeet models are English, as on Android) |
| Speak-to-edit ("make that shorter"), LLM rewrite | not yet |
| Code signing / notarisation, auto-update | not yet (needs the owner's certificates) |

## Models

| Model | Download | Word error rate (Android bake-off, 73 LibriSpeech clips) | RAM |
|---|---|---|---|
| Fast: Parakeet TDT-CTC 110M int8 (default) | 104 MB | 3.7 % | ~500 MB |
| Best: Parakeet TDT 0.6B v2 int8 | 482 MB | 2.6 % | ~1.3 GB |

Archives come from the sherpa-onnx GitHub release (`asr-models`), are checked against the same pinned size and
SHA-256 as the Android app (`src-tauri/src/models.rs`), and only the files the engine needs are unpacked into the
app data folder (`HUMMING_MODELS_DIR` overrides it). Weights: CC-BY-4.0 (NVIDIA); ONNX export and runtime:
sherpa-onnx (Apache-2.0), ONNX Runtime (MIT).

## Build

Needs Node 20+, Rust stable and the platform toolchain (Xcode command line tools on macOS, Visual Studio Build
Tools on Windows). The sherpa-onnx build script downloads its prebuilt static libraries for the target once.

```
cd desktop
npm ci
npm run tauri dev                                         # run it
npm run tauri build                                       # installer for this machine
npm run tauri build -- --target universal-apple-darwin    # universal macOS .dmg
```

CI (`.github/workflows/desktop.yml`) type-checks and unit-tests on Linux, then builds an unsigned universal macOS
`.dmg` and Windows `.msi` + setup `.exe`, uploaded as workflow artifacts with `SHA256SUMS.txt`.

## How to open it (the builds are unsigned)

The Windows and macOS builds are **unsigned**: they are not signed with an Apple Developer ID or a Microsoft
code-signing certificate yet, so the operating system warns on first launch. The app is the same either way; this
is a one-time step.

**macOS** (universal `.dmg`, Apple silicon and Intel)
1. Open the `.dmg` and drag Humming to Applications.
2. Open Humming. macOS blocks the first open ("cannot be opened" / "could not verify"). Click Done.
3. Go to **System Settings > Privacy & Security**, scroll down, and click **Open Anyway** next to Humming.
4. Confirm with Open (and your password). From then on it opens normally.

Apple's guide: https://support.apple.com/en-us/102445

**Windows** (`.msi` or the setup `.exe`)
1. Run the installer.
2. SmartScreen shows "Windows protected your PC" with an unknown publisher. Click **More info**.
3. Click **Run anyway** and finish the installer.

CI builds both installers on every push (`.github/workflows/desktop.yml`, artifacts `humming-desktop-macos-universal`
and `humming-desktop-windows-x64`, each with a `SHA256SUMS.txt`). Signing options are in `../docs/RELEASE_SIGNING.md`.

## First run

- macOS: allow the microphone, and allow Humming under System Settings > Privacy & Security > Accessibility so it
  can paste.
- Windows: allow desktop apps to use the microphone.

## Layout

```
desktop/
  index.html, overlay.html, src/*.ts|css   UI (Vite + TypeScript, no framework)
  src-tauri/src/lib.rs                     app state, hotkey, dictation flow, tray, commands
  src-tauri/src/audio.rs                   microphone capture (cpal), mono f32, live level
  src-tauri/src/insert.rs                  paste / type into the focused app (arboard + enigo)
  src-tauri/src/models.rs                  Parakeet models: download, SHA-256 check, unpack
  src-tauri/src/stt.rs                     sherpa-onnx recognizer, long-audio windows, resampling
  src-tauri/src/text_cleanup.rs            port of the Android TextCleanup.kt rules (+ tests)
  src-tauri/src/settings.rs, history.rs    JSON files in the app config / data folders
```
