# Humming Agent Guide

Follow the existing patterns, naming, and style of the area you touch (`keyboard/`, `desktop/`, `core/`, `ios/`, `play/site/`, `scripts/`). If [HANDOFF.md](HANDOFF.md) exists, read it first for active context and update it when handing off.

## Security & Release Invariants (Never Break)
- Never commit secrets, signing keys, fake 32-hex/base64 token literals, personal emails, or phone numbers (`gitleaks` scans full git history).
- Never change Android `applicationId` (`com.humming.keyboard`), package names, or the upload signing certificate.
- Keep the offline voice path network-free (`NetworkAuditTest` enforces the allowlist in `keyboard/`).
- Every benchmark or metric in `README.md` or `play/site/` must come from committed repo data (`docs/bench/`, tests); competitor facts must cite public sources or be marked "unverified".

## Build & Verification Rules
- Do not run heavy native builds locally on low-memory machines (`gradlew`, `cargo build/test`, `xcodebuild`, full `npm ci`); open a PR and let GitHub Actions (`ci.yml`, `android-emulator.yml`) verify them. Explicitly state which native checks are pending CI.
- Lightweight Python checks (`python3 -m unittest discover -s scripts -p 'test_*.py' -v`, `assemble_site.py`, `check_site.py`) and `render_social_preview.py` are safe to run locally.
- For UI or website changes, inspect rendered dark/light desktop & mobile views in headless Chromium before reporting completion.

## On-Demand Skills
Load task-specific workflows under `.agents/skills/` only when the task matches:
- `humming-release`: cutting/verifying tagged multi-platform releases
- `humming-site-brand`: editing `play/site/`, `docs/BRAND.md`, `README.md`, or launch graphics
- `humming-voice-rules`: changing cleanup or edit-by-voice rules across Rust, Kotlin, and Swift
- `humming-benchmarks`: running or updating WER and Android emulator measurements
- `humming-competitors`: auditing competitors and updating `COMPETITORS.md` matrices
