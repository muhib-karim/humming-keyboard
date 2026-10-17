# Humming v1.7.0-rc1 premium pass: task list

Branch `feat/v1.7-premium`. Nothing here is built locally (low-memory machine): every build and test runs on GitHub
Actions after the owner pushes. Each item says what was RUN locally and what is only INFERRED until CI runs.
If a session ends early, the remaining work is in [HANDOFF.md](HANDOFF.md).

## 0. Setup
- [x] TASKS.md and a CLAUDE.md "continue from TASKS.md / HANDOFF.md" rule
- [ ] SKILLS_USED.md at the repo root (skills actually invoked, and evidence)
- [x] docs/ci/CODEBASE_MAP.md (Understand-Anything method, by hand: core not built here)

## 1. COMPETITORS.md
- [x] Feature + UX teardown: Gboard, SwiftKey, Fleksy, Typewise, Ridmik Keyboard, Avro/Bijoy-style Bangla input vs Humming
- [x] Gap list ranked by user impact x feasibility, marking what this pass implements

## 2. Gaps implemented in source (with unit tests)
- [x] Gap A: Bangla phonetic typing (`BanglaPhonetic`, wired in `LatinLanguageProvider`), BanglaPhoneticTest (CI)
- [x] Gap B: dictation fits the sentence around the cursor (`DictationJoin`), DictationJoinTest (CI)
- [x] Gap C: colon/semicolon/quotes/brackets/ellipsis + "delete last word" on Android, desktop, iOS (CI)
- [ ] Whole-branch code review of gaps A-C (fresh reviewer) and fixes

## 3. App UI polish
- [ ] Android: design tokens, key/candidate bar, voice button + listening state, a11y (48dp, content descriptions, AA)
- [ ] iOS / desktop: token parity
- [ ] docs/BRAND.md design tokens section

## 4. GitHub Pages site (play/site/)
- [ ] Redesigned index.html (hero, features, comparison, downloads, privacy, FAQ, footer), no CDNs/trackers
- [ ] OG/Twitter meta, docs/social-preview.png (1280x640) + SVG source, favicon set
- [ ] sitemap.xml, robots.txt, 404.html; privacy.html URL kept; pages.yml updated
- [ ] Links resolve to files that exist (checked by script)

## 5. README.md
- [ ] Premium rewrite: banner, badges, hero, highlights, comparison summary, install per platform, privacy, build, contributing, license

## 6. Release plumbing v1.7.0-rc1
- [ ] Versions: Android 1.7.0 (versionCode up), iOS 1.7.0 (build up), desktop tauri.conf/Cargo/package.json, core
- [ ] CHANGELOG "1.7.0-rc1"
- [ ] assemble_release.py: -rc tags, per-platform report, loud failures (with a local dry-run test)
- [ ] release.yml: one (pre)release with every platform, rc => prerelease
- [ ] desktop.yml reviewed for fragile steps
- [ ] All workflow YAML parses

## 7. Repo metadata
- [ ] docs/ci/REPO_METADATA.md (About <= 350 chars, website, 12-20 topics)

## Done checks
- [ ] gitleaks clean, forbidden-strings scan clean
- [ ] Final report
