# Handoff — Humming Keyboard

Updated: 2026-11-27  
Branch: `feat/complete-humming-and-homepage` (PR #30: `https://github.com/muhib-karim/humming-keyboard/pull/30`)

## Current Objective

Complete the Humming v1.8.x pass across bug fixes, competitor teardowns (Wispr Flow, Typeless, Granola, Hex by Anomaly), production-grade README/banner/GitHub Pages homepage, browser visual QA, and repo hygiene audits (`AGENTS.md`, on-demand skills structure, and scripts/tools audit).

## Completed Work

1. **Android offline dictation crash fix (Issue #22)**
   - Removed `Pattern.UNICODE_CHARACTER_CLASS` from `VoiceEdit.kt` (Android ICU throws `IllegalArgumentException`) and replaced `\b` with explicit Unicode word boundaries (`(?<![\p{L}\p{M}\p{N}_])` / `(?![\p{L}\p{M}\p{N}_])`).
   - Added Unicode word-boundary unit tests in `VoiceEditTest.kt` and an Android ART smoke check (`ImeSmokeTest.voiceEditRunsOnAndroidArt`).
2. **Android crash dialog clipboard fix (Issue #22)**
   - Updated `CrashDialogActivity.kt` to use a short label (`"Humming crash report"`), truncate oversized logs at 200,000 chars, and catch clipboard exceptions via `runCatching`.
3. **Desktop tray quick action**
   - Added `"Copy last dictation"` menu item in `desktop/src-tauri/src/lib.rs` (reads newest `AppState.history` entry, copies via `insert::copy`).
4. **Competitor teardowns (`COMPETITORS.md`)**
   - Added primary-source teardowns and matrix rows for **Hex by Anomaly** and **Granola** alongside refreshed **Wispr Flow** and **Typeless** entries, including explicit architectural takeaways and where Humming trails.
5. **Brand banner, social preview, README, and GitHub Pages homepage**
   - Updated `scripts/render_social_preview.py` (with system font fallback) and regenerated `docs/banner.png` (1600×640) and `docs/social-preview.png` (1280×640) with brand-pink captures and v1.8 benchmarks.
   - Refreshed `README.md`, `play/site/index.html`, `play/site/privacy.html`, `play/PRIVACY_POLICY.md`, and `CHANGELOG.md` to reflect all 5 v1.8 models, 25-language Multilingual support, edit-by-voice, and active CI gates.
6. **Headless Chromium visual QA & UI fixes (`play/site/assets/site.css`, `play/site/index.html`)**
   - Verified rendered output at 1440×900 (dark & light), 768×1024 (dark), 390×844 (dark & light), and `privacy.html`.
   - Caught and fixed three real layout bugs:
     - Scoped `.nav a:not(.btn)` so the header Download button keeps its `999px` pill radius and `--accent-strong-hover` hover color instead of turning boxy/grey.
     - Scoped `#bench .proof` separately from top-level `.proof`, removing a 64 px dead gap below the hero and a double bottom border under `#bench`.
     - Tightened hero `h1` fluid scale (`clamp(2.25rem, 1.3rem + 3.4vw, 3.65rem)`) and copy so headline, CTAs, and checkmark facts fit above the fold on desktop and mobile.

## Verified Decisions

- **No local native builds** (`gradle`, `cargo`, `xcodebuild`): environment has no JDK/Rust/Xcode and follows `CLAUDE.md` — CI on GitHub Actions verifies Android, desktop, and static analysis.
- **No edits applied yet to `AGENTS.md`, `CLAUDE.md`, or `.agents/skills/`**: proposed diffs and skill structure are awaiting Operator approval before committing.
- **Android invariants preserved**: `applicationId` (`com.humming.keyboard`), package names, and upload signing key untouched.
- **Zero fabricated numbers**: every WER/latency/RAM figure in `README.md`, banner, and `play/site/index.html` matches `docs/BENCHMARKS.md` and `docs/bench/`.

## Relevant File Paths

- Android fixes: `keyboard/app/src/main/kotlin/com/humming/keyboard/VoiceEdit.kt`, `keyboard/app/src/test/kotlin/com/humming/keyboard/VoiceEditTest.kt`, `keyboard/app/src/androidTest/kotlin/com/humming/keyboard/ImeSmokeTest.kt`, `keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/lib/crashutility/CrashDialogActivity.kt`
- Desktop tray: `desktop/src-tauri/src/lib.rs`
- Competitor research & docs: `COMPETITORS.md`, `README.md`, `CHANGELOG.md`, `play/PRIVACY_POLICY.md`
- Website & brand assets: `play/site/index.html`, `play/site/assets/site.css`, `play/site/privacy.html`, `scripts/render_social_preview.py`, `docs/banner.png`, `docs/social-preview.png`
- Agent config (audited, not yet modified): `AGENTS.md`, `CLAUDE.md`

## Test & Verification Results

- `python3 -m unittest discover -s scripts -p 'test_*.py'`: **27/27 passed** (note: pre-existing `ResourceWarning: unclosed file` in `test_assemble_release.py`).
- `python3 scripts/assemble_site.py /tmp/humming-site && python3 scripts/check_site.py /tmp/humming-site`: **37 files assembled, 3 pages checked, 0 problems**.
- Python regex mirror of `VoiceEdit.kt` against `core/testdata/voice_edit_vectors.json` + Unicode targets: **39/39 passed**.
- Playwright headless Chromium QA (`1440×900`, `768×1024`, `390×844`, dark + light): **0 horizontal overflow**, **0 page errors**, pill nav button hover verified (`999px`, `rgb(176, 48, 80)`), `proofTopGap = 0`.
- **GitHub Actions CI on PR #30 (verified so far)**:
  - `check (Linux compile + unit tests)` (desktop Rust): **passed**
  - `quality / gitleaks (full history)`: **passed**
  - `quality / website check + script tests`: **passed**
  - `detekt 1.23.8`: **passed** (0 findings outside baseline)
  - `ktlint`: fixed single blank-line finding (`VoiceEdit.kt:37`, `spacing-between-declarations-with-comments`) and re-pushed; re-verify `quality / ktlint + detekt`, `keyboard`, `android-emulator`, and `ci / gate`.

## Unresolved Issues

- **Issue #21** (`Offline model checksum mismatch` on v1.8.0): already addressed by archive re-pinning in v1.8.1 (`71f42cb`); confirm with reporter before closing.
- **GitHub Pages live deploy**: `pages.yml` only triggers on push to `main` (or `workflow_dispatch`), so the updated homepage goes live once the PR is merged into `main`.
- **`AGENTS.md` / `CLAUDE.md` / skills / scripts refactors**: proposals prepared; awaiting Operator approval before applying.
- **Pending Dependabot PRs** on repo (`#23`, `#28`) untouched — out of scope.

## Exact Next Steps

1. Wait for GitHub Actions `ci` (`quality`, `keyboard`, `desktop`, `gate`) and `android-emulator` checks on the open PR for `feat/complete-humming-and-homepage`; fix any `ktlint`/`detekt`/compile findings if reported.
2. Merge PR into `main` so `pages.yml` deploys the refreshed site and social preview to `https://muhib-karim.github.io/humming-keyboard/`.
3. Once Operator approves the `AGENTS.md` rewrite and on-demand skills layout, apply them and remove broken references (`TASKS.md`, `docs/PROJECT_BRAIN.md`).
4. Optionally apply the scripts/tools audit cleanups (shared CLI flags, log-to-file defaults, fixing `ResourceWarning` file handles in `scripts/assemble_release.py` and `scripts/test_assemble_release.py`).
