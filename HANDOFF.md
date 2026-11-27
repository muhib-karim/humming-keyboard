# Handoff — Humming Keyboard v2.0.0

Updated: 2026-11-27  
Release target: `v2.0.0` (branch `feat/humming-v2`)

## Current Objective

Ship **Humming v2.0.0** with dual-track botless Meeting Notepad (native Windows WASAPI loopback + Unix/Mac monitor), built-in local MCP JSON-RPC 2.0 server, live rolling overlay draft preview, Whisper Mode, local usage stats, focused window-title AI context, repo hygiene (`AGENTS.md`, `.agents/skills/`, clean `scripts/`), updated interactive homepage, v2.0 launch graphics, full green CI, and official GitHub Release `v2.0.0` + GitHub Pages deploy.

## Completed Work

1. **Desktop v2.0.0 Backend (zero new crates)**
   - **Dual-track botless Meeting Notepad (`audio.rs`, `meetings.rs`, `lib.rs`)**: `loopback_devices()` + `start_loopback()` captures system audio (`[Remote]`, using Windows `cpal` WASAPI `default_output_config()` + `build_input_stream()` or Unix/Mac monitor/BlackHole) simultaneously with the mic (`[You]`), adds speaker tags (`format_seg_tag`) across transcripts, digests, and `ask_meetings`, plus live level metering and `.md` download.
   - **Built-in Local MCP Server & Expanded HTTP API (`mcp.rs`, `local_api.rs`, `lib.rs`)**: `POST /mcp` JSON-RPC 2.0 endpoint on `127.0.0.1` with Bearer auth exposing `humming_list_meetings`, `humming_get_meeting`, `humming_search_meetings`, `humming_list_history`, and `humming_cleanup_text`; plus `GET /v1/meetings`, `GET /v1/history`, and one-click `Copy MCP config` in Settings.
   - **Live Overlay Drafts & Whisper Mode (`stt.rs`, `audio.rs`, `overlay.*`)**: `SherpaEngine` `decode_lock` + non-blocking `try_transcribe` streams 1.4 s rolling draft words into the `360x88` frosted overlay pill (`partial-transcript`); `whisper_mode` applies `apply_whisper_gain` and `-54 dB` VAD threshold.
   - **Local Usage Stats & Window Context (`history.rs`, `focus.rs`, `llm.rs`)**: `History::stats()` powers 4 Home stat tiles (`Words dictated`, `Avg WPM`, `Time saved`, `Median speed`) alongside the active accelerator badge; `focused_window_title()` adds optional window-title context for local Ollama / BYOK Command Mode & AI polish.
2. **Repo Hygiene, Skills & Script Cleanups**
   - Consolidated `AGENTS.md` (single source of truth; `CLAUDE.md` points to it) and created 5 on-demand workflow skills under `.agents/skills/humming-*/SKILL.md`.
   - Fixed unclosed file handles (`ResourceWarning`) across `scripts/{assemble_release,verify_updater,check_site,test_assemble_release}.py`, added `argparse` to site scripts, added `scripts/test_site_scripts.py`, switched `.github/workflows/quality.yml` to `unittest discover`, and reduced `gitleaks` / `emulator_run.sh` log verbosity.
3. **Interactive Homepage, Brand Graphics & Version `2.0.0`**
   - Updated `play/site/{index.html,privacy.html,assets/site.css,assets/site.js}`, `play/PRIVACY_POLICY.md`, `COMPETITORS.md`, `README.md`, `CHANGELOG.md`, `docs/ci/desktop_screens.mjs`, and `scripts/render_social_preview.py` (`docs/{banner,poster,social-preview}.png` + `play/graphics/feature-graphic-1024x500.png`).
   - Bumped versions to `2.0.0` (`2000`) across `keyboard/gradle.properties`, `desktop/src-tauri/{tauri.conf.json,Cargo.toml,Cargo.lock}`, `desktop/package*.json`, `core/{Cargo.toml,Cargo.lock}`, and `ios/project.yml`.

## Relevant File Paths

- Desktop Rust backend: `desktop/src-tauri/src/{audio.rs,focus.rs,history.rs,lib.rs,llm.rs,local_api.rs,mcp.rs,meetings.rs,settings.rs,stt.rs}`
- Desktop frontend & overlay: `desktop/{index.html,overlay.html}`, `desktop/src/{main.ts,style.css,overlay.ts,overlay.css}`
- Agent guide & skills: `AGENTS.md`, `CLAUDE.md`, `.agents/skills/humming-*/SKILL.md`
- Scripts & CI: `scripts/*.py`, `.github/workflows/quality.yml`, `keyboard/ci/emulator_run.sh`
- Website & brand assets: `play/site/`, `play/PRIVACY_POLICY.md`, `docs/{banner,poster,social-preview}.png`, `docs/screenshots/`, `play/graphics/feature-graphic-1024x500.png`
