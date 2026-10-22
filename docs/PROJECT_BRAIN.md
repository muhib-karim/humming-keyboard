# Project brain: Humming Keyboard

Handoff note for the next session (human or agent). Short on purpose; the details live in TASKS.md and HANDOFF.md.

## What this is
Private keyboard with offline voice typing: Android (FlorisBoard fork, `keyboard/`), desktop (Tauri, `desktop/`),
iOS (`ios/`), shared Rust `core/`, website `play/site/`. Every build and test runs on GitHub Actions only.

## Status
v1.7.1 quality pass on `upgrade/v1.7.1`: CI gates, coverage, emulator smoke test and measurements being added.

## Next step
First push: collect the ktlint/detekt/lint baselines and the "before" coverage from CI, commit them.

## Session log
- 2026-10-22 02:15Z Claude Code/claude-opus-5-5: design + plan for v1.7.1, CI gates, emulator test; next: first push, baselines.
