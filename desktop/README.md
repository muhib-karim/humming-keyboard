# Humming for Windows, macOS and Linux

Hold a shortcut, speak, let go: Humming transcribes on your own computer and pastes clean text into whatever
app has focus (mail, chat, docs, the browser, the terminal). It is the desktop sibling of the Humming Android
keyboard and uses the same engine and rules: NVIDIA Parakeet on sherpa-onnx 1.13.8 (the same two models, pinned by
SHA-256), plus a small desktop-only Lite model (Moonshine tiny, MIT) that a first run fetches first so dictation works within seconds and a Rust port of the keyboard's `TextCleanup.kt`. No account, no cloud, and no Online or AI mode on desktop: audio never leaves the machine. The only
network use is the one-time model download from GitHub and the updater, which checks for updates and, with
Settings > Updates > "Automatically install updates" on (the default), downloads and installs them (signed builds only;
the Linux .deb only reports a new version).

## What it does

| Feature | Status |
|---|---|
| Push-to-talk on a global shortcut (default `Ctrl+Shift+Space` / `Cmd+Shift+Space`), or press-to-toggle | yes |
| Paste into the focused app (clipboard + Ctrl/Cmd+V, clipboard restored), or type key by key | yes |
| Offline speech-to-text: Parakeet Fast (110M) / Multilingual (0.6B v3) / Best (0.6B v2) on sherpa-onnx, as on Android, plus Lite and Balanced (Moonshine tiny 30 MB / base 111 MB, desktop only); downloaded on first run with a SHA-256 check, Lite first by default (quick start) | yes |
| Clean mode (Android rules): fillers, self-corrections ("3, no, 4pm" -> "4pm"), repeats, "4 pm" -> "4pm", capitals, final ? or . | yes |
| Spoken lists -> bullets ("first, eggs, second, milk") | yes (desktop extra, can be turned off) |
| Verbatim mode | yes |
| Voice commands: new line, new paragraph, period, comma, question mark... | yes |
| Personal dictionary: fixed spellings ("Okonkwo", "GitHub") and phrase replacements | yes |
| Long dictation (no limit by default; an optional cap in Settings; decoded after you stop, in <= 25 s windows cut at pauses, <= 8 s with Lite) | yes |
| History on this computer: search, copy, delete, clear | yes |
| Tray / menu bar app, listening overlay with a live mic level | yes |
| Settings: shortcut (with a recorder), mode, model, language, microphone, paste method, overlay, start at login | yes |
| In-app "Try it" box (transcribe without pasting) and a cleanup tester | yes |
| Languages other than English | yes, with the optional Multilingual model (Parakeet v3: 25 European languages, detected automatically); clean-up rules stay English |
| Speak-to-edit ("make that shorter"), LLM rewrite | not yet |
| Auto-update | yes (1.6): signed Tauri updater, Settings > Updates; checks 45 s after launch and every 4 hours. Exceptions: the Linux .deb only shows that a new version is out, and macOS updates itself from its first 1.6 build on. See the main README |
| Code signing / notarisation | not yet (needs the owner's certificates) |

## Models

| Model | Download | Word error rate (73-clip LibriSpeech bake-off, one-off run on a desktop CPU) | RAM |
|---|---|---|---|
| Lite: Moonshine tiny English, quantized (first-run quick start) | 30 MB | 4.5 % | ~250 MB |
| Balanced: Moonshine base English, quantized (new in 1.8) | 111 MB | 3.1 % | ~400 MB |
| Fast: Parakeet TDT-CTC 110M int8 (default) | 104 MB | 2.7 % | ~500 MB |
| Multilingual: Parakeet TDT 0.6B v3 int8, 25 European languages (new in 1.8) | 487 MB | 2.2 % (English) | ~1.2 GB |
| Best: Parakeet TDT 0.6B v2 int8 | 482 MB | 1.8 % | ~1.3 GB |

1.8 numbers: every model measured in one run on LibriSpeech test-clean (first 200 clips), sherpa-onnx 1.13.8; see
[docs/BENCHMARKS.md](../docs/BENCHMARKS.md).

The bake-off's script and raw outputs are not in the repository (see the main README's
[bake-off, under Benchmarks](../README.md#benchmarks)). The committed CI benchmark (the first 200 LibriSpeech test-clean
clips, 4 October 2026) measured 2.78 % for Fast and 1.68 % for Best (treat as +/- 0.5 points): [Benchmarks](../README.md#benchmarks).
Lite is in neither run, so its word error rate is not measured and the app shows "not measured" for it.

**Quick start (on by default).** On a first run, when no model is on disk and you pick Fast or Best, Humming downloads
Lite first, dictates with it while the picked model downloads, then switches over (`quick_start` in the settings file,
`download_model` in `src-tauri/src/lib.rs`). Lite is `sherpa-onnx-moonshine-tiny-en-quantized-2026-02-27` from the same
sherpa-onnx release, pinned by size and SHA-256 in `src-tauri/src/models.rs`. Moonshine is made by Moonshine AI
(formerly Useful Sensors); its models are released under the MIT License (upstream LICENSE in
github.com/moonshine-ai/moonshine, read 14 October 2026). The NVIDIA CC-BY-4.0 attribution covers Parakeet only.

**Old 5-minute caps (deliberate).** A settings file written by desktop 1.6.0 or earlier has no `schema` field. If it
holds `max_seconds = 300`, it is read once as "no limit" (`Settings::sanitized` in `src-tauri/src/settings.rs`): before
1.3.0, 300 was the default and cannot be told apart from a chosen 300, and 1.3.0 to 1.6.0 already rewrote 300 to "no
limit" on every load and save, so no such file can hold a 5-minute cap chosen there. If you want a 5-minute cap, set it
again in Settings; from the first desktop release after 1.6.0, a chosen cap (300 included) is kept.

Archives come from the sherpa-onnx GitHub release (`asr-models`), are checked against the same pinned size and
SHA-256 as the Android app (`src-tauri/src/models.rs`), and only the files the engine needs are unpacked into the
app data folder (`HUMMING_MODELS_DIR` overrides it). Weights: Parakeet CC-BY-4.0 (NVIDIA), Moonshine MIT (Moonshine AI); ONNX export and runtime:
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

CI (`.github/workflows/desktop.yml`) is configured to type-check and unit-test on Linux, then build an unsigned
universal macOS `.dmg` and Windows `.msi` + setup `.exe`, uploaded as workflow artifacts with `SHA256SUMS.txt`.
GitHub Actions has not run on this repository since 5 October 2026; desktop 1.6.0 was built outside Actions (see the
CHANGELOG).

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

When GitHub Actions runs, `desktop.yml` builds both installers on every push that touches `desktop/` or `core/`
(artifacts `humming-desktop-macos-universal` and `humming-desktop-windows-x64`, each with a `SHA256SUMS.txt`). Signing options are in `../docs/RELEASE_SIGNING.md`.

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
