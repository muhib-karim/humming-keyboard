# Handoff: v1.7.1 quality pass

Branch `upgrade/v1.7.1` (from `main` `6ac587d`). Every item in [TASKS.md](TASKS.md) is done except the GitHub Release,
which needs a tag. Everything was built and tested by GitHub Actions, nothing locally.

## Coordinator actions

1. Review and merge `upgrade/v1.7.1` into `main` (not done here by rule). On `main`, `pages.yml` deploys the refreshed
   site; check `https://muhib-karim.github.io/humming-keyboard/` shows the "On an Android emulator" block.
2. Tag the merge commit: `git tag -a v1.7.1 -m "Humming 1.7.1" && git push origin v1.7.1`. `release.yml` builds every
   platform (versions are already 1.7.1 / 1701 everywhere) and attaches the signed APK, AAB, desktop installers and
   the unsigned `.ipa` to the release; its notes come from CHANGELOG `[1.7.1]`. Before tagging, change that entry's
   "Status" bullet from branch state to "final release" (as the 1.7.0 entry says).
3. Optional: enable Dependabot security updates in the repository settings (the version-update config is committed).

## What CI proved on this branch

- `keyboard.yml`: unit tests (34 new, all passing), JaCoCo coverage 42.2 % → 48.2 % of `com.humming.keyboard`
  ([docs/ci/COVERAGE.md](docs/ci/COVERAGE.md)), Android lint gate, release APK.
- `android-emulator.yml`: ImeSmokeTest and ModelLoadBenchTest pass on `system-images;android-34;google_apis;x86_64`;
  the README and site numbers come from run 39713465586 (`docs/bench/emulator-run39713465586/`).
- `quality.yml` (ktlint, detekt, gitleaks full history, site check, script tests), `codeql.yml`, `android-release.yml`
  (signed APK + AAB, certificate matches), `desktop.yml` and `ios.yml` (version bump) all green.

## Review (requesting-code-review, one general-purpose subagent)

0 Critical. Important: (1) upgrade branches uploaded production-signed artifacts: fixed, they still sign and verify
but upload nothing (`bcc86cb`); (2) Dependabot PRs would fail the signed job without secrets: fixed, skipped for
Dependabot (`bcc86cb`); (3) handoff files stale: fixed here; (4) the emulator job depends on a 104 MB download:
mitigated with three resumable attempts (`8faa41a`). Minors fixed: test server survives client errors, wording of the
emulator trigger, class-level coverage evidence. Minors left open:

- Robolectric downloads its `android-all` SDK 34 jar from Maven Central at test time (build infrastructure, like any
  Gradle dependency; app code under test makes no network calls). Pre-fetching it would make the unit tests work offline.
- `SubtypeCycle` finds the active language by object equality, as the old loop did; matching by `id` would survive an
  edit of the active language's layout in the same moment.
- The site links to `tree/main/docs/bench/emulator-run39713465586`, which exists once the branch is merged.
- If the emulator download still proves flaky: cache the model archive with `actions/cache` and `adb push` it.

## Open items (next pass)

- Unit tests for `HistoryStore` (0 of 62 lines covered) and a TalkBack-style accessibility pass in the emulator test.
- Shrink the lint/ktlint/detekt baselines file by file (docs/ci/STATIC_ANALYSIS.md says how).
- Still open from 1.7.0: candidate-strip restyle, dictation gaps D5-D9, pinning third-party actions to SHAs, named
  secrets instead of `secrets: inherit` (see below).

# Earlier handoff: v1.7.0-rc1 premium pass

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
