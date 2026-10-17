# Humming v1.7.0-rc1 premium pass: task list

Branch `feat/v1.7-premium`. Nothing here is built locally (low-memory machine): every build and test runs on GitHub
Actions after the owner pushes. Each item says what was RUN locally and what is only INFERRED until CI runs.
If a session ends early, the remaining work is in [HANDOFF.md](HANDOFF.md).

## 0. Setup
- [x] TASKS.md and a CLAUDE.md "continue from TASKS.md / HANDOFF.md" rule
- [x] SKILLS_USED.md at the repo root (skills actually invoked, and evidence)
- [x] docs/ci/CODEBASE_MAP.md (Understand-Anything method, by hand: core not built here)

## 1. COMPETITORS.md
- [x] Feature + UX teardown: Gboard, SwiftKey, Fleksy, Typewise, Ridmik Keyboard, Avro/Bijoy-style Bangla input vs Humming
- [x] Gap list ranked by user impact x feasibility, marking what this pass implements

## 2. Gaps implemented in source (with unit tests)
- [x] Gap A: Bangla phonetic typing (`BanglaPhonetic`, wired in `LatinLanguageProvider`), BanglaPhoneticTest (CI)
- [x] Gap B: dictation fits the sentence around the cursor (`DictationJoin`), DictationJoinTest (CI)
- [x] Gap C: colon/semicolon/quotes/brackets/ellipsis + "delete last word" on Android, desktop, iOS (CI)
- [x] Whole-branch code review of gaps A-C (fresh reviewer) and fixes (2 Important + 2 re-graded minors fixed, 5 minors deferred)

## 2A. Task A (owner, 2:30 AM): dictation apps
- [ ] COMPETITORS.md "Dictation apps" section: Wispr Flow, Typeless, Superwhisper, Aqua Voice, Willow, MacWhisper,
      Apple / Google dictation (accuracy only with published numbers, latency, offline/privacy, cleanup, commands,
      dictionary, languages incl. Bangla, platforms, pricing) and a ranked dictation gap list
- [x] Spec + plan: `docs/superpowers/specs/2026-10-17-dictation-gaps-design.md`, `docs/superpowers/plans/2026-10-17-dictation-gaps.md`
- [x] Android: lists ("bullet point", spoken "first, second"), "spoken -> written" replacements, filler switch (ListFormatTest,
      TextCleanupTest, AppVocabularyTest; Java mirror 45/45; Kotlin pending CI)
- [x] Desktop: "bullet point" lists, "delete that" as its own sentence (Rust tests pending CI)
- [x] iOS: lists, replacements, filler switch, "delete that" (XCTest pending CI)
- [ ] Fresh code review of the three commits, fixes
- [ ] Implemented gaps marked ✅ in COMPETITORS.md, homepage comparison, README, CHANGELOG (CHANGELOG done), user guide

## 3. App UI polish
- [x] Android: voice panel a11y (48dp Clean chip, roles/state), AA contrast tokens, rec-dot recomposition fix, exported-activity URI fix; Android skills review in docs/ci/ANDROID_REVIEW.md
- [x] Android: Smartbar mic touch target 48 dp wide and the full Smartbar height (visible circle unchanged, same 50 dp
      footprint), button role and long-press label, press spring read in the draw phase (session 2)
- [ ] Android: candidate-strip restyle (needs device screenshots; handed off)
- [x] iOS / desktop: token parity (desktop CSS tokens, focus, reduced motion; iOS Brand accentStrong/accentText)
- [x] docs/BRAND.md design tokens section; docs/ci/DESIGN_AUDIT.md

## 4. GitHub Pages site (play/site/)
- [x] Redesigned index.html (hero, features, comparison, downloads, privacy, FAQ, footer), no CDNs/trackers
- [x] OG/Twitter meta, docs/social-preview.png (1280x640) + SVG source, favicon set
- [x] sitemap.xml, robots.txt, 404.html; privacy.html URL kept; pages.yml updated
- [x] Links resolve to files that exist (scripts/check_site.py: 3 pages, 0 problems)

## 5. README.md
- [x] Premium rewrite: banner, badges, hero, highlights, comparison summary, install per platform, privacy, build, contributing, license (all md links and anchors checked)

## 6. Release plumbing v1.7.0-rc1
- [x] Versions: Android 1.7.0 (versionCode 1700), iOS 1.7.0 (build 1700), desktop tauri.conf/Cargo(+lock)/package.json(+lock), core 1.7.0
- [x] CHANGELOG "1.7.0-rc1"
- [x] assemble_release.py: -rc tags, per-platform report, loud failures (scripts/test_assemble_release.py: 7/7 OK locally)
- [x] release.yml: one (pre)release with every platform, rc => prerelease, mirror only for final releases
- [x] desktop.yml reviewed for fragile steps (no failure log in docs/ci; keyless builds no longer fail; tags still require the key)
- [x] All workflow YAML parses (8/8, every run block passes bash -n)

## 7. Repo metadata
- [x] docs/ci/REPO_METADATA.md (About 273 chars, website, 20 topics)

## Done checks
- [x] gitleaks clean, forbidden-strings scan clean
- [x] Final report (handoff items in HANDOFF.md)
