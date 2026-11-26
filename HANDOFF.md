# Handoff — Humming Keyboard v1.9.0

Updated: 2026-11-27  
Release target: `v1.9.0` (PR #30: `https://github.com/muhib-karim/humming-keyboard/pull/30`)

## Current Objective

Ship **Humming v1.9.0** with full competitor-parity & superiority upgrades across Android, desktop, homepage, launch graphics (banner, poster, social preview, Play feature graphic), verified 100% green CI across all platforms, merged to `main`, and tagged `v1.9.0` to trigger the official GitHub Release + live GitHub Pages deployment.

## Completed Work

1. **Android offline dictation & crash-log fixes (Issue #22)**
   - `VoiceEdit.kt`: removed unsupported `Pattern.UNICODE_CHARACTER_CLASS`, replaced `\b` with explicit Unicode word boundaries, added Unicode tests (`VoiceEditTest.kt`) and Android 14 ART smoke test (`ImeSmokeTest.voiceEditRunsOnAndroidArt` — verified green on `android-emulator.yml`).
   - `CrashDialogActivity.kt`: short clipboard label, 200k-char cap, `runCatching` guard.
2. **Desktop v1.9.0 capabilities (zero new crates)**
   - **Command Mode, Per-App Tone & Local Ollama / BYOK AI** (`llm.rs`, `insert::grab_selection`): select text in any app + `Ctrl/Cmd+Shift+E` to rewrite via 100% local Ollama, optional BYOK OpenAI/Anthropic/Gemini (HTTPS enforced remotely), or offline `VoiceEdit` fallback; per-app tone rules (`casual`/`bullets` offline + AI polish); CSV dictionary import/export; History `"Teach word"`.
   - **Offline Botless Meeting Notepad & Ask Across Meetings** (`meetings.rs`, `audio::Recorder::drain`): 12s chunked local Parakeet transcription (mic or OS loopback/monitor device, zero call bots), split-pane manual notes + live timestamped transcript, template Markdown synthesis (Ollama, BYOK, or deterministic offline digest), cross-meeting cited Q&A (`ask_meetings`), Markdown export, and tray shortcut.
   - **Re-paste Shortcut, GPU Execution Provider & Local HTTP API** (`local_api.rs`, `stt.rs`): global `Ctrl/Cmd+Shift+V` re-paste last dictation, optional CoreML/DirectML/CUDA provider with automatic CPU fallback, opt-in `127.0.0.1` Bearer-auth HTTP server (`/health`, `/v1/transcribe`, `/v1/cleanup`).
   - **Overlay & Desktop UX**: glassmorphic animated floating pill (`overlay.html`/`overlay.css`/`overlay.ts`) with live elapsed timer, 11-bar mirrored waveform, Listening / Command Mode / Rewriting states; brand 5-bar SVG waveform icon in sidebar; tab reveal transitions.
3. **Interactive Homepage, Competitor Binary Audit & Launch Graphics**
   - `COMPETITORS.md`, `README.md`, `play/site/index.html`: live verified binary size table (Humming 8.8 MB Win / 24.9 MB Mac / 17.5 MB `.deb` vs Hex v3.5.0 77.2 / 33.5 / 35.7 MB vs Granola v7.853.5 171.4 / 271.2 MB), full 8-competitor feature matrix, animated hero waveform, interactive 6-mode voice pipeline demo (`site.js`), and real v1.9.0 desktop Home & Meeting Notepad captures.
   - `scripts/render_social_preview.py`: renders `docs/banner.png` (1600×640), `docs/social-preview.png` (1280×640), `docs/poster.png` (1600×2000 launch poster), and `play/graphics/feature-graphic-1024x500.png` (1024×500).
4. **Version bump to `1.9.0` (`1900`)** across `keyboard/gradle.properties`, `desktop/src-tauri/tauri.conf.json`, `desktop/src-tauri/Cargo.toml`, `desktop/package.json`, `core/Cargo.toml`, `ios/project.yml`, and `CHANGELOG.md`.

## Relevant File Paths

- Desktop Rust backend: `desktop/src-tauri/src/{lib.rs,llm.rs,meetings.rs,local_api.rs,settings.rs,stt.rs,insert.rs,audio.rs}`
- Desktop frontend & overlay: `desktop/{index.html,overlay.html}`, `desktop/src/{main.ts,style.css,overlay.ts,overlay.css}`
- Android fixes: `keyboard/app/src/main/kotlin/com/humming/keyboard/VoiceEdit.kt`, `keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/lib/crashutility/CrashDialogActivity.kt`
- Website & brand assets: `play/site/{index.html,privacy.html,assets/site.css,assets/site.js}`, `scripts/render_social_preview.py`, `docs/{banner.png,poster.png,social-preview.png}`, `play/graphics/feature-graphic-1024x500.png`
- Docs: `README.md`, `COMPETITORS.md`, `CHANGELOG.md`

## Verification Summary

- Local Python tests (`scripts/test_*.py`): **27/27 passed**
- Website assembly & validation (`assemble_site.py` + `check_site.py`): **39 files, 3 pages, 0 problems**
- Headless Chromium visual QA: verified website (dark/light, desktop/tablet/mobile, interactive 6-mode demo, `0` horizontal overflow, `0` errors), Desktop UI (all 5 tabs dark/light + glassmorphic overlay states), and all 4 generated brand graphics (`banner.png`, `poster.png`, `social-preview.png`, `feature-graphic-1024x500.png`).
- GitHub Actions CI: all 21 checks passed on `15f8c3a`; final v1.9.0 commit verified on PR #30 prior to merge and `v1.9.0` tag.

## Remaining / Optional Follow-Ups

- `AGENTS.md`, `.agents/skills/`, and `scripts/` cleanup proposals from the earlier audit remain ready for approval before applying.
- Dependabot PRs (`#23`, `#28`) untouched (separate scope).
- Future v2.0 roadmap: true token-streaming partial ASR on desktop and native macOS CoreAudio process-tap loopback without virtual devices.
