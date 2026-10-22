# Project brain: Humming Keyboard

Handoff note for the next session (human or agent). Short on purpose; the details live in TASKS.md and HANDOFF.md.

## What this is
Private keyboard with offline voice typing: Android (FlorisBoard fork, `keyboard/`), desktop (Tauri, `desktop/`),
iOS (`ios/`), shared Rust `core/`, website `play/site/`. Every build and test runs on GitHub Actions only.

## Status
v1.7.1 quality pass done on `upgrade/v1.7.1`: CI gates with baselines, coverage 42.2 % -> 48.2 %, emulator smoke test
and measurements, README/site refresh, version 1.7.1 everywhere. All workflows green on the branch head.

## Next step
Coordinator: review and merge, then tag `v1.7.1` so release.yml attaches the signed APK (HANDOFF.md).

## Session log
- 2026-10-22 02:15Z Claude Code/claude-opus-5-5: design + plan for v1.7.1, CI gates, emulator test; next: first push, baselines.
- 2026-10-22 03:20Z Claude Code/claude-opus-5-5: gates, tests, emulator run, docs, review fixes pushed; next: merge + tag v1.7.1.
