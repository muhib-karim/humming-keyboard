# Handoff: v1.7.0-rc1 premium pass

Branch `feat/v1.7-premium`. Every item in [TASKS.md](TASKS.md) is done except the one marked open below. Nothing was
built locally (see CLAUDE.md), so every Kotlin, Rust and Swift change is **unverified until CI runs**.

## Owner actions (need push or write access)

1. Push the branch and let CI run: `keyboard.yml` (Android unit tests, including the new `BanglaPhoneticTest`,
   `DictationJoinTest`, `SharedUriPolicyTest`, the extended `TextCleanupTest` and `DictationOutputTest`), `desktop.yml`
   (`cargo test` with the new `text_cleanup.rs` cases), `ios.yml` (`swift test` with the new `TextCleanupTests` cases;
   `ios/Shared/SharedStore.swift` now imports UIKit).
2. When CI is green on `main`: `git tag -a v1.7.0-rc1 -m "Humming 1.7.0-rc1" && git push origin v1.7.0-rc1`. It becomes
   a GitHub pre-release with every platform; a missing platform stops it with a per-platform report
   (docs/RELEASING.md section 6).
3. Repository About, website and topics: `docs/ci/REPO_METADATA.md`; upload `docs/social-preview.png` as the social
   preview.

## Session 2 (Task A: dictation apps)

Done: `COMPETITORS.md` "Dictation apps" (73 sources) and a ranked dictation gap list; D1-D4 implemented on Android,
desktop and iPhone (lists, `spoken -> written` replacements, filler switch, "scratch that" everywhere), reviewed and
fixed; site, README, CHANGELOG, user guide and About text updated; Smartbar mic tap target widened. New tests to watch in
CI: Kotlin `ListFormatTest`, new cases in `TextCleanupTest`, `AppVocabularyTest`, `VoiceCommandsTest`; Rust
`text_cleanup.rs` (`bullet_points_*`, `delete_that_*`, `bullet_review_fixes`, `decimals_do_not_end_a_sentence`),
`lib.rs` `bullet_points_become_a_list`, core `a_second_in_prose_is_not_a_list`; Swift `ListFormatTests`, new
`TextCleanupTests` cases.

## Open items (next pass)

- Dictation gaps D5-D9 (COMPETITORS.md): voice editing of text already in the field, per-app tone, a dictionary that
  learns from corrections, offline Bangla dictation, whisper mode.
- Android: grey out "Remove filler words" when Clean mode is off and "Accuracy mode" when fillers are off (iOS does);
  a list dictated with no intro right after a word starts on that word's line.

- Candidate-strip restyle needs device screenshots (TASKS.md section 3). The Smartbar mic's tap target is now 48 dp
  wide and the bar's full height (the bar itself is shorter than 48 dp).
- Release hardening the review suggested but this pass did not do: pass named secrets instead of `secrets: inherit`,
  pin third-party actions to commit SHAs, consider a `release` environment with required reviewers.
- The apps read their update feeds from humming-keyboard.pages.dev (Cloudflare Pages), while `pages.yml` deploys GitHub
  Pages; how pages.dev is kept in sync is not in this repository (docs/RELEASING.md section 6 describes a manual mirror).
- `docs/ci/ANDROID_REVIEW.md`: AGP 9 blockers (KSP version, `libraryVariants` in lib/snygg), voice-panel recomposition
  from the level and timer updates, Play Console microphone foreground-service declaration.
- Deferred review minors and the full skills/evidence table: [SKILLS_USED.md](SKILLS_USED.md).
- Competitor gaps still open (ranked in COMPETITORS.md): Bangla dictionary suggestions, Bangla phonetic on desktop and
  iOS, offline voice in more languages, store distribution, a fuller iOS keyboard.
