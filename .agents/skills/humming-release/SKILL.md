---
name: humming-release
description: Cut, verify, and ship a Humming multi-platform release (version bumps, changelog, tag push, signed assets, and Pages feed mirror). Use only when preparing, bumping, tagging, or troubleshooting a Humming release.
---

# Humming Release Workflow

1. **Sync all version files** to `X.Y.Z` / code `XXYY` (e.g. `2.0.0` / `2000`):
   - `keyboard/gradle.properties` (`projectVersionName`, `projectVersionCode`)
   - `desktop/src-tauri/tauri.conf.json`, `desktop/src-tauri/Cargo.toml`, `desktop/package.json`
   - `core/Cargo.toml`
   - `ios/project.yml` (`MARKETING_VERSION`, `CURRENT_PROJECT_VERSION`)
   - Update root package entries in `desktop/src-tauri/Cargo.lock`, `core/Cargo.lock`, and `desktop/package-lock.json`.
2. **Update `CHANGELOG.md`** with a top heading `## [X.Y.Z] - YYYY-MM-DD` — `scripts/assemble_release.py` extracts this exact section for release notes and updater feeds.
3. **Run release script dry-run tests** locally: `python3 -W error::ResourceWarning -m unittest discover -s scripts -p 'test_*.py' -v`.
4. **Land PR on `main` with `ci.yml` (`gate`) green**, then push an annotated tag:
   `git tag -a vX.Y.Z -m "Humming X.Y.Z" && git push origin vX.Y.Z`.
5. **Verify `.github/workflows/release.yml` finishes**: confirm all 16 signed assets (`SHA256SUMS.txt`, `latest.json`, `android.json`, Android `.apk`/`.aab`, iOS `.ipa`, macOS `.dmg`/`.app.tar.gz`+`.sig`, Windows `.exe`/`.msi`+`.sig`, Linux `.AppImage`+`.sig`/`.deb`). If Pages CDN propagation races inside `release.yml`, dispatch `pages.yml` on `main` and verify live `/updates/latest.json` and `/updates/android.json`.
