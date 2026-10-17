# Skills used in the v1.7.0-rc1 pass

Branch `feat/v1.7-premium`, 17 October 2026. Only skills that were actually invoked are listed. "Main session" means
the skill was loaded with the Skill tool in the working session; "subagent" means a delegated agent was told to invoke
and apply it, and its written output is the evidence (its own skill calls were not independently observed).

Nothing was built locally (no gradle, cargo, swift, xcode): Kotlin, Rust and Swift changes are **pending CI**. Logic was
exercised locally through single-file Java 17 mirrors (Java runs the same `java.util.regex` engine as Kotlin on the
JVM); the mirrors live outside the repository, and their rule tables were checked to be identical to the Kotlin source.

| Change / commit | Skill(s) | Evidence |
|---|---|---|
| `3c6b56d` codebase map | **understand** (main session) | Its scripted pipeline could not run (`@understand-anything/core` not built; building needs a pnpm install the low-memory rules forbid), so its phases were followed by hand: scan, layers, tour. `docs/ci/CODEBASE_MAP.md` records this and includes the scan script. |
| `4ec9189` Bangla phonetic, `f643e79` dictation joining, `551fd6f` spoken punctuation | **brainstorming** → **writing-plans** → **test-driven-development** → **executing-plans** (main session) | Spec `docs/superpowers/specs/2026-10-17-v17-gaps-design.md` and plan `docs/superpowers/plans/2026-10-17-v17-gaps.md`. Owner had opted out of questions, so the decisions are recorded as the builder's calls. TDD on the mirrors: BanglaPhonetic RED 30 failing → GREEN 35/35; DictationJoin RED 8 → GREEN 30/30 (31/31 after the fix pass); TextCleanup RED 12 → GREEN 16/16 including 3 existing-behaviour cases (20/20 after the fix pass). Kotlin `BanglaPhoneticTest`, `DictationJoinTest`, `DictationOutputTest.adaptAppliesToDraftAndCommit`, `TextCleanupTest`, Rust `text_cleanup.rs` tests and Swift `TextCleanupTests` are **pending CI**. |
| `6f08370` review fixes for the gaps | **code-review-and-quality** + requesting-code-review's `code-reviewer.md` (subagent, opus) | Whole-branch review of `3c6b56d..551fd6f`: 0 Critical, 2 Important (line break lost before openers; engine commas around commands), 8 Minor. Both Important fixed test-first; two Minors re-graded and fixed ("remove the last word" deleted words in ordinary speech; "Will" lowercased as a name). Five Minors deferred (below). |
| `8a86148` COMPETITORS.md | (no skill: a general-purpose research subagent using web search on public pages only) | 40 numbered public sources checked 17 October 2026; unsourced facts marked "unverified". |
| `95b8cf6` UI contrast, touch-target and semantics pass | **ui-ux-pro-max**, **impeccable** (`audit`, `detect`, craft floor) (main session) | UI/UX Pro Max searches: *App Store Style Landing*, *Focus States*, *48 dp / 44 pt / 24 px* targets; its palette suggestion (green accent, Google Fonts) was rejected as off-brand and against the no-CDN rule. Impeccable `detect` on desktop: 4 contrast findings before, **0 after**. WCAG ratios computed by script (`docs/ci/DESIGN_AUDIT.md`), e.g. white on `#C93A5B` 4.95:1, Clean chip text 2.84 → 4.59:1, Offline pill 2.64 → 4.80:1. |
| `2f4f24b` design tokens | **design-system** (main session) | Three-layer token tables (primitive → semantic → component) across Snygg, Compose, desktop CSS, SwiftUI and the site in `docs/BRAND.md`; audit score 13/20 → 15/20 in `docs/ci/DESIGN_AUDIT.md`. |
| `c8b636e`, `e10e50a` website | **frontend-ui-engineering**, **impeccable**, **ui-ux-pro-max** (main session) | `scripts/check_site.py`: 3 pages, 0 problems (links, anchors, tag balance, alt and size on every image, no other-origin loads). Impeccable `detect`: footer heading-scale finding fixed; `.table-wrap` cramped-padding judged a false positive. Headless system Chromium (`/usr/bin/chromium`, already installed) render at 360 / 768 / 1280 / 1440 px, light and dark: three layout bugs found and fixed; captures in `docs/ci/site/`. |
| `c8b636e` social preview | **banner-design** (main session) | `docs/social-preview.png` 1280×640 rendered with Pillow from real captures by `scripts/render_social_preview.py`; SVG source `docs/social-preview.svg`. One art direction, because there was no browser capture step and real screenshots were required. |
| `142a705` Android fixes and review | **jetpack-compose-performance**, **edge-to-edge**, **styles**, **adaptive**, **agp-9-upgrade** (readiness only, no upgrade), **android-permissions-security**, **android-intent-security**, **play-policy-insights** (subagent) | `docs/ci/ANDROID_REVIEW.md`, one section per skill (checked: 266 lines, 8 sections). Acted on: the recording dot read its animation in composition (now `graphicsLayer`); the exported `FlorisCopyToClipboardActivity` copied any shared URI (now `SharedUriPolicy`, `SharedUriPolicyTest`: mirror RED 7 failing → GREEN 10/10; Kotlin pending CI). play-policy's orchestrator script was not run because it writes into the repository. |
| `f357a41`, `e74ffc4` release plumbing | **test-driven-development** (main session); **code-review-and-quality**, **security-and-hardening**, **ci-cd-and-automation** (subagent, opus) | `scripts/test_assemble_release.py` written first: 6 of 7 failing → 7/7 OK (re-run in the final check). The review found nothing that breaks an rc1 tag and four Important issues for later releases. Fixed: pages gate after an allowed-missing release, the feed mirror's all-or-nothing delete, least-privilege permissions, rc testers' desktop update path (documented), and Minors (pipefail, curl retry, no feeds in pre-releases). Not done: `secrets: inherit` → named secrets and SHA-pinned actions (see HANDOFF.md). All 8 workflows parse, and every `run:` block passes `bash -n`. |
| `1ea9ce9` release-pipeline diagram | **archify** (main session) | `docs/release-pipeline.html`, a workflow diagram with source links pinned to a commit. `finalize` gates validate, deliver, strict check and browser-check all **pass** (`docs/release-pipeline.finalize-summary.json`). It took two layout repairs (an edge crossing, then sublabel width). Automated checks only; not visually reviewed. |
| Final checks | **verification-before-completion** (main session) | Fresh run: release tests OK; mirrors 35/35, 31/31, 20/20, 10/10; Kotlin/mirror rule-table parity true; site check 0 problems; 8 workflows parse; JSON files parse; versions 1.7.0 / 1700 everywhere; `gitleaks detect` no leaks found; forbidden-strings scan clean. |

## Not used

**playwright-skill**: no Playwright browser cache exists on this machine and none was downloaded; the system Chromium
was driven directly instead. **brand** and **canvas-design** were not invoked. The brand rules already in
`docs/BRAND.md` were extended by hand, and the banner-design skill covered the social image.

## Deferred review minors

- The final period is kept inside closing marks (`."`, `.)`) before text that continues the sentence.
- `BanglaPhonetic.Consonant.joins` is never false (a dead parameter).
- A standalone `w` before a vowel writes ওআ (Avro writes ওয়া).
- `text_cleanup.rs` uses `\x{2019}` inside a character class; a literal ’ would remove any doubt.
- Review Focus item 1 is tested at the transliterate level, not through the suggestion provider.
