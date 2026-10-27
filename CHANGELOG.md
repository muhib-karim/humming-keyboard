# Changelog

## [1.8.0] - 2026-10-28

Edit by voice on every platform, two more speech models, every model measured side by side, and the open-source
desktop apps Handy and Amical in the comparison. Versions: Android 1.8.0 (versionCode 1800), desktop and core 1.8.0,
iOS 1.8.0 (build 1800).

- **Edit by voice (gap D5)**: a whole utterance such as "change Tuesday to Wednesday", "replace X with Y", "delete the
  word X", "capitalize X" or "undo that" edits the text already there instead of being typed. Safety rules: the
  command must be the whole utterance (a leading "um" / "okay" or a trailing "please" is ignored), at most 14 words,
  and the named word must exist in the text (the closest whole-word match before the cursor is edited); otherwise the
  words are typed as ordinary dictation. One rule set: `core/src/voice_edit.rs` (Rust, also exposed through the FFI),
  ported to Kotlin (`VoiceEdit.kt`) and Swift (`VoiceEdit.swift`); all three run the same 35 shared test cases in
  `core/testdata/voice_edit_vectors.json`. Each platform has an "Edit by voice" switch, on by default.
  - Android: edits up to a bounded window of text before the cursor through the input connection.
    Known limit: a whole-utterance "undo that" / "scratch that" is handled earlier by the existing voice commands, so
    there it removes the last dictation rather than going through voice edit; "undo" inside a longer utterance works.
  - iPhone: the keyboard extension edits the text before the cursor.
  - Desktop: edits only Humming's own last insertion and only while the same app is focused (Backspace over the
    changed tail, then types the new tail); if typing fails, the memory of the last insertion is dropped.
- **Multilingual model**: NVIDIA Parakeet TDT 0.6B v3 (int8, sherpa-onnx), 25 European languages, on Android and
  desktop, offered on devices with at least 6 GB of memory, with a SHA-256 check on download. Transcription only for
  non-English speech: clean-up and voice-command rules stay English. Licence attribution in `keyboard/LICENSES.md`.
- **Balanced model (desktop)**: Moonshine base (int8), between Fast and Best in accuracy and speed. Fast stays the
  default everywhere.
- **Benchmarks**: every model measured in one run with the apps' own engine (sherpa-onnx 1.13.8) on LibriSpeech
  test-clean (first 200 clips): word error rate Best 1.77 %, Multilingual 2.24 %, Fast 2.72 %, Balanced 3.11 %,
  Lite 4.51 %, with real-time factor, median latency and peak memory, in `docs/BENCHMARKS.md` (raw output in
  `docs/bench/local-20261028-*`). `keyboard/ci/wer_bench.py` knows the two new models. Streaming models were looked at
  and not added yet; the reasons are in `docs/BENCHMARKS.md`.
- **Comparison**: `COMPETITORS.md` adds the open-source desktop apps Handy and Amical, and marks voice editing as
  built and tested, citing the test files.
- **Tests and checks for this release**: `docs/TESTING_v1.8.md` lists every command and its result.

## [1.7.1] - 2026-10-22

Checked on a real Android emulator, measured, and gated. No new features; nothing users do changes except one fix in
language switching.

- **Status**: on branch `upgrade/v1.7.1`, every workflow green in CI. It becomes the 1.7.1 release when `v1.7.1` is
  tagged after review; `release.yml` then builds the signed APK and attaches it to the GitHub Release.
- **Version**: one version everywhere: Android 1.7.1 (versionCode 1701), desktop and core 1.7.1, iOS 1.7.1 (build 1701).
- **Fix: language key after removing a language (Android)**: if the active language had just been removed from the
  list, the language key switched to the built-in default, which might not be in the list at all. It now goes to the
  first language in the list. The next/previous rule is a pure, unit-tested `SubtypeCycle` (`SubtypeCycleTest`).
- **Security (CodeQL findings)**: unpacking a voice-model archive now also refuses any entry whose normalised path
  would leave the model directory (zip slip; the name check before it already rejected `..`), with
  `ModelPolicyTest` cases for `../`, `a/../../` and a same-prefix sibling folder. The developer tools' logcat dump runs
  `/system/bin/logcat` by absolute path. Backup rules are unchanged (keyboard settings and themes only) and now tested
  to never name models, voice settings, clipboard or history. Certificate pinning was not added, and the reasons are
  in `docs/ci/STATIC_ANALYSIS.md`. No CodeQL alerts are open on the branch.
- **Emulator smoke test** (`android-emulator.yml`, `ImeSmokeTest`): on an Android 14 emulator, Humming is switched on
  as the keyboard, Android's keyboard settings and Humming's settings are opened, the first-run setup is finished and
  "hello" is typed by tapping Humming's own keys; screenshots of every step are saved as artifacts.
- **Measured on the emulator** (run 39713465586, image `system-images;android-34;google_apis;x86_64`, 22 October
  2026): cold start to first frame 605 ms (median of 10, release build settings), Fast voice model first load
  1,017 ms and reload 867 ms (`ModelLoadBenchTest`, through the app's own download, SHA-256 check and engine). Raw
  output in `docs/bench/emulator-run39713465586/`. Phone timings are not measured.
- **More unit tests** (34 new, all passing in CI): model download, resume, verification and unpacking against a local
  HTTP server (`ModelStoreTest`), every voice setting and the history-retention migration on real SharedPreferences
  (`PrefsTest`, Robolectric 4.16.1), language switching (`SubtypeCycleTest`), each clean-up stage and the audio
  helpers (`TextPostProcessingTest`). Line coverage of `com.humming.keyboard` 42.2 % → 48.2 % (JaCoCo, now reported by
  `keyboard.yml`; `docs/ci/COVERAGE.md`).
- **Quality gates in CI**: ktlint 1.7.1 and detekt 1.23.8 (`quality.yml`) and Android lint (`keyboard.yml`), each with
  a committed baseline holding only the findings that existed before 1.7.1 (a missing baseline fails); gitleaks over
  the whole history; CodeQL for Java/Kotlin with the `security-extended` suite (`codeql.yml`,
  `.github/codeql/codeql-config.yml`); Dependabot for Gradle and GitHub Actions. How each baseline
  was made: `docs/ci/STATIC_ANALYSIS.md`.
- **CI on upgrade branches**: `keyboard.yml`, `android-release.yml` and the new workflows also run on `upgrade/**`
  pushes, so a branch is checked before review.
- **Docs and website**: README with the emulator screenshots, numbers, quality badges and a code-backed architecture
  diagram (`docs/android-architecture.html`); the website's "Measured" section shows the same screenshots and numbers,
  and no longer calls 1.7 a release candidate.

## 1.7.0: Bangla phonetic typing, dictation lists and replacements, a premium look
- **Status**: final release, tagged `v1.7.0` and published as the latest GitHub release; its update files are mirrored to
  the website's update feeds. Everything below is unit-tested in CI; it was not built on the development machine.
- **Version**: one version everywhere: Android 1.7.0 (versionCode 1700), desktop and core 1.7.0, iOS 1.7.0 (build 1700).
- **Bangla phonetic typing (Android)**: add Bengali with a QWERTY layout and type romanised Bangla the Avro / Ridmik way
  (`ami bangla likhi` → আমি বাংলা লিখি). The Bangla word is the auto-commit suggestion (space or punctuation types it)
  and the Latin word stays one tap away. Rule-based, written for Humming: reph, ra-, ya- and ba-phala, ং / ঙ, জ্ঞ, ক্ষ,
  Avro capitals, Bangla digits (`BanglaPhonetic`, `BanglaPhoneticTest`).
- **Dictation fits the sentence (Android)**: dictating into the middle of a line lowercases a common first word, adds
  no space after an opening bracket, quote, `@` or `#`, and drops the final period when the text after the cursor
  carries on; names, "I", acronyms and custom words keep their capitals (`DictationJoin`, `DictationJoinTest`).
- **Dictation lists (Android, desktop, iOS)**: say "bullet point" (or "next bullet") to start a list item:
  "groceries bullet point eggs bullet point milk" → "Groceries:" with "- Eggs" and "- Milk" on their own lines. A
  spoken "first, ... second, ..." or "number one, ... number two, ..." list becomes bullets too (desktop had this;
  Android and iOS now match). "the second bullet point" in a sentence stays prose (`ListFormat.kt`, `ListFormat.swift`,
  `text_cleanup::bullet_list`; `ListFormatTest`, `ListFormatTests`).
- **Replacements in custom words (Android, iOS)**: a custom-words line written `spoken -> written` (or `→`) replaces
  what the recogniser heard ("cube cuddle -> kubectl", "my sig -> Best, Sam"), whole words, any case; Android's
  ` @app` tags work on these lines too. Desktop already had spoken → written entries.
- **Remove filler words switch (Android, iOS)**: um, uh, er, hmm and ", you know," are still removed by default; the
  new switch keeps them. Desktop already had it.
- **"Scratch that" / "delete that" as its own sentence (desktop, iOS)**: removes the sentence before it, as on
  Android ("Book Friday. Scratch that. Book Saturday." → "Book Saturday.").
- **More spoken punctuation (Android, desktop, iOS)**: "colon", "semicolon", "open/close quote", "open/close bracket",
  "ellipsis" / "dot dot dot", and "delete last word" / "scratch the last word". Commas a punctuating engine puts around
  these commands are absorbed, and a line break before an opening quote or bracket is kept. Same rules and test
  sentences in `TextCleanup.kt`, `text_cleanup.rs` and `TextCleanup.swift`.
- **Accessibility**: the voice panel's Clean / Verbatim chip has a 48 dp touch target and is announced as a switch with
  its state; the orb and cancel are announced as buttons; light-theme pill text and accent text now meet WCAG AA
  (4.5:1) on Android, desktop (`--accent-strong`, `--accent-ink`, per-theme pill colours, focus ring, reduced motion)
  and iOS (`Brand.accentStrong`, `Brand.accentText`). Tokens documented in `docs/BRAND.md`.
- **Security**: the exported "copy image to clipboard" share target only opens other apps' `content://` URIs, never
  `file://` or Humming's own providers (`SharedUriPolicy`, `SharedUriPolicyTest`).
- **Performance**: the recording dot blinks in the draw phase instead of recomposing the voice panel every frame.
- **Website**: rebuilt GitHub Pages home page with real captures, an honest comparison table, per-platform downloads,
  APK verification, FAQ, social image, favicons, sitemap, robots.txt and a 404 page; no third-party requests.
  `pages.yml` checks every link before deploying (`scripts/check_site.py`).
- **Docs**: `COMPETITORS.md` (teardown of Gboard, SwiftKey, Fleksy, Typewise, Ridmik, Avro, Bijoy, OpenBangla, and
  of the dictation apps Wispr Flow, Typeless, Superwhisper, Aqua Voice, Willow, MacWhisper and Apple / Google dictation,
  with sources and ranked gap lists), README rewrite, user guide for the new features, design tokens, design audit,
  Android review, codebase map.
- **Release plumbing**: `assemble_release.py` accepts `-rcN` tags, names files with the full version, reports every
  platform and fails naming each missing file (with an explicit `RELEASE_ALLOW_MISSING` opt-out), and writes
  `release.env`; dry-run tests in `scripts/test_assemble_release.py`. `release.yml` publishes one release with every
  platform, marks `-rc` tags as pre-releases and only mirrors final releases. `desktop.yml` builds unsigned installers
  instead of failing when the updater key is unavailable (fork pull requests), and still requires it for tags.

## 1.6.3 (release candidate): voice never types placeholder text, accurate README, reproducible WER bench
- **Status**: release candidate on `main`; it becomes the 1.6.3 release when its GitHub Release is published.
- **Version**: Android 1.6.3 (versionCode 1603). Desktop stays 1.6.0, iOS unchanged.
- **Open source**: the repository is licensed Apache-2.0 and is made public with this release (root LICENSE and NOTICE; desktop and core manifests now say Apache-2.0); docs and site updated to match; the privacy page lists GitHub contact routes instead of an email address.
- **Desktop models shown honestly**: Lite (Moonshine tiny, MIT, fetched first on a first run) is documented, and the
  model list says "word error rate not measured" for Lite instead of 0 % (`models.rs` `wer_pct: None`). A settings
  file from desktop 1.6.0 or earlier holding a 300 s cap is read as "no limit" (deliberate; see desktop/README.md).
- **Voice never types placeholder text**: a phone whose CPU the on-device engine is not built for used to fall back
  silently to the internal test mode, which typed "[mock] Humming heard ... of audio" into the field. The mic now
  always uses the on-device engine and shows "Voice isn't available on this device" instead; test mode only runs when
  set on purpose in a debuggable build (`Prefs.resolveMode`, `ModePolicyTest`).
- **README made accurate**: every network use listed,
  including the update check that is on by default; FUTO Keyboard, Handy and Superwhisper added as offline
  alternatives; unverified "no keyboard" claims about Wispr Flow and Typeless removed; the CI WER run's output is
  committed under `docs/bench/`, plus a re-run with every reference and hypothesis.
- **Docs, accurate to the code**: privacy policy dated 14 October 2026 (Online voice mode also runs in incognito;
  Android backup in the short version; AI clean-up in the internet permission; desktop update checks 45 seconds after
  launch and every 4 hours, which Settings cannot switch off; GitHub private vulnerability reporting as the private
  route for privacy requests). Desktop dictation is documented as having no length limit by default (an optional cap
  in Settings), as in the code, instead of "up to 15 minutes". CI claims say what is configured, what ran before
  5 October 2026 and what runs now. `keyboard/LICENSES.md` says it covers the Android keyboard only and that the
  English neural model's trained weights ship.
- **Benchmark tooling**: `keyboard/ci/fetch_librispeech_sample.py` streams a fixed LibriSpeech sample (about 30 MB
  instead of 350 MB); `wer_bench.py` skips empty sets and can write per-utterance results (`WER_PER_UTT`).

## 1.6.2: clean live dictation, a "Live text while speaking" switch, no FlorisBoard store left in Settings
- **Dictation, fixed**: while you talk, your words now appear in the text field as an underlined draft (composing
  text, the way Gboard shows voice typing) that is corrected in place as Humming hears more, and the final text is
  typed once when you stop. Before 1.6.2, words were committed to the field as you spoke; when the final clean-up
  changed words that were already typed and the app could not confirm them in the field, the corrected words were
  added after the old ones, so some words showed up twice. Now nothing is committed until you stop, so that cannot
  happen. Cancel leaves the field exactly as it was.
- **New switch: Settings > Voice > Live text while speaking** (on by default, replaces "Type as you talk"). Off:
  nothing appears while you talk, and the whole text is typed once when you stop.
- **Settings > Addons & Extensions**: no more link to the FlorisBoard Addons Store or its update search (Humming's
  themes, layouts and language packs are built in and update with the app; you can still import an extension file).
  Built-in extensions show "Built-in" instead of their internal `org.florisboard.*` ID, and theme and language-pack
  details are readable ("Authors", "Night theme", "Language") instead of developer fields. Internal IDs are unchanged,
  so settings and themes carry over.
- **About, setup, crash screen**: the Changelog, Website (was "Repository"), Privacy policy and crash-report links now
  go to Humming's own public site (humming-keyboard.pages.dev, with a changelog page) and privacy policy instead of
  FlorisBoard's. The one credit line stays in About:
  "Android keyboard built on FlorisBoard (Apache 2.0)". The extra credit in Settings > Voice was removed, as were two
  links to FlorisBoard's GitHub (a feedback thread and an internal key-code table in the theme editor).
- **Voice and sticker settings shortcuts, fixed**: tapping the dictation panel when the speech model is not downloaded
  yet, or "+" in the sticker panel, opened an "Import extension" screen instead of Settings > Voice or Settings >
  Stickers & GIFs. They now open the right settings page. (This was in 1.6.0 and 1.6.1 too; found by the 1.6.2 audit.)
- Android version 1.6.2 (versionCode 1602). Desktop and iOS are unchanged.

## 1.6.1: Humming everywhere in the app, new icon, pink everywhere
- **Android**: every place the app showed the name "FlorisBoard" or "Floris" now says Humming: the setup and home
  screen warnings, the crash dialog, crash notifications and the crash report header, the About screen icon
  description and licence line, the theme source label, and the notification-permission explanation, in every
  translation. The built-in themes are now called Humming Day, Humming Night, Humming Pure Night (each also Borderless
  and Material You). The app name, launcher and keyboard (IME) name were already Humming.
- **New Android app icon**: the Humming icon (white sound-wave bars on the pink gradient) replaces the FlorisBoard
  icon on the launcher, the round and themed (monochrome) icons, the splash screen, Settings and About, for the
  stable, beta and debug builds.
- **Pink everywhere**: Android now uses the Humming pink (`#E5486B`) like desktop, iOS, the site and the Play
  graphics: the default accent of Settings and the keyboard (it no longer falls back to FlorisBoard green or the
  wallpaper colour; you can still pick another in Settings), the Humming Light/Dark/AMOLED keyboard themes, the mic
  button and dictation panel, the crash screen and the notification tint. `docs/BRAND.md` lists the colours and
  icon files.
- **Credit**: the About screen and the README now say "Android keyboard built on FlorisBoard (Apache 2.0)".
  FlorisBoard's LICENSE, notices and copyright headers are unchanged. Internal package names, class names and IDs
  (`dev.patrickgold.florisboard`, `org.florisboard.*` extension and theme IDs) are unchanged, so settings, themes and
  dictionaries carry over and 1.6.0 updates in place.
- Android version 1.6.1 (versionCode 1601). Desktop and iOS are unchanged and stay at 1.6.0.

## 1.6.0: Humming updates itself
- **Desktop (Windows, macOS, Linux AppImage)**: built-in, signed self-update (not the Linux .deb, which only shows that a new version is out; macOS updates itself from its first 1.6 build on). Humming checks for a new version 45
  seconds after it starts, every 4 hours and on **Settings > Updates > Check for updates**. With **Automatically
  install updates** on (the default) it downloads the update, installs it and restarts, and it waits if you are in the
  middle of a dictation or a model download. Off: it shows "Update available" and an **Install now** button. Updates
  are verified against the minisign (Ed25519) public key built into the app before they are installed. A Linux .deb
  install cannot replace itself, so there Humming only says that a new version is out.
- **Android (sideloaded APK)**: **Settings > Updates**, with "Check for updates automatically" and "Download and
  install updates automatically" (both on by default) and a **Check for updates** button. Humming checks at most every
  6 hours, downloads a newer APK on Wi-Fi, checks its SHA-256 against `SHA256SUMS.txt` and its signing certificate
  (the release key, `8C:8B:...:1F:C6`), then hands it to Android's PackageInstaller. Android asks you to confirm the
  update with one tap: an app installed outside the Play Store cannot install itself silently. On Android 12+, once
  Humming has installed an update itself, Android may install later ones without asking; that is Android's call. The
  Google Play bundle has no updater and no REQUEST_INSTALL_PACKAGES permission (Play updates it).
- **iOS**: no automatic updates. An unsigned, sideloaded .ipa cannot update itself; sideload the new one.
- **Release pipeline**: pushing a `v*` tag runs `release.yml`, which builds every platform, verifies every updater
  signature, writes `latest.json`, `android.json` and `SHA256SUMS.txt`, creates the GitHub Release, and mirrors the
  update files to a public site (`/updates/`), so the apps need no GitHub login. The website deploy then checks
  the live feed's signatures (`scripts/verify_updater.py`). The website gets a Download section.
- **Built without GitHub Actions**: Actions can't run on this private repository right now, so 1.6.0 was built on
  Linux build machines outside GitHub: the Android APK and Play AAB (signed with the release key, certificate
  `8C:8B:...:1F:C6`), the Linux AppImage and .deb, and the Windows NSIS setup .exe (cross-compiled from Linux with
  cargo-xwin). Every updater signature was checked against the app's public key before upload. Not in 1.6.0: the
  macOS .dmg and the iOS .ipa (Apple's tools need a Mac) and the Windows .msi (WiX needs Windows); their 1.5.0 files
  stay the current ones. `release.yml` still builds everything when Actions are available.
- **Update mirror on Cloudflare Pages**: the update feeds (`latest.json`, `android.json`, `SHA256SUMS.txt`, the `.sig`
  files) and every installer are served from https://humming-keyboard.pages.dev/updates/ (free, no GitHub login).
  Installers are bigger than a Pages file may be, so the site redirects each one to a short-lived download link for
  the release file; the app still checks every signature or SHA-256 itself. Moving from 1.5.0 to 1.6.0 is one manual
  install (1.5.0 has no updater); from 1.6.0 on, updates arrive by themselves.
- Versions: 1.6.0 (Android versionCode 1600; desktop 1.6.0; iOS build 1600 in the source).

## 1.4.0: iOS, accuracy mode, one version everywhere
- **iOS (new, `ios/`)**: a SwiftUI app plus the Humming keyboard extension, for iPhone and iPad (iOS 16+). Dictation
  runs on Apple's on-device recogniser with on-device recognition required (no server fallback), with the same
  clean-up rules as Android and desktop (a Swift port, tested on Linux and on the iOS simulator). No length limit:
  recognition restarts phrase by phrase. Released as an unsigned `.ipa` for sideloading (docs/IOS_SIDELOAD.md);
  CI (`ios.yml`) runs the tests, takes simulator screenshots and builds the `.ipa`.
- **Accuracy mode** (Android, desktop, iOS; off by default): stricter on-device clean-up on top of Clean mode. It
  also removes hedge fillers ("like", "basically", "actually", "you know", "I mean") when they are set off by commas,
  extra hesitation sounds (ah, mm) and a spoken "period" at the end, and never touches a "like" that carries meaning.
  Same rules and the same tests on all three platforms.
- **One version**: Android 1.4.0 (versionCode 1400), desktop 1.4.0 and iOS 1.4.0 (build 1400).
- **README and website**: banner, screenshots of every platform from CI (desktop ones now generated by `screens.yml`,
  replacing six links that pointed at missing images), walkthrough GIFs, an at-a-glance feature table, a measured
  WER/latency table next to what Wispr Flow, Typeless and Gboard Rambler publish, and a home page on GitHub Pages.

## M8: Humming for Windows and macOS, signed Android release (desktop 0.1.0; merged after 1.2.0, included in 1.3.0)
Desktop (new, `desktop/`)
- Humming for Windows and macOS (Tauri v2): push-to-talk dictation into any app, on-device Parakeet speech
  recognition (sherpa-onnx, the same engine as Android) and the shared text clean-up from `core/`.
- The installers are unsigned for now: a universal macOS `.dmg` and Windows `.msi` and setup `.exe`. README.md
  explains the one-time "how to open it" step on each system. CI builds both on every push (`desktop.yml`).

Android
- Release signing: when the upload key is supplied through repository secrets, `android-release.yml` builds a signed
  release APK and AAB and checks the certificate SHA-256 against the published fingerprint (docs/RELEASE_SIGNING.md).
  Without the key, builds stay signed with the development key exactly as before.
- Word lists: one personal name removed from the German, English, French and Italian frequency lists.

Docs and web
- docs/RELEASE_SIGNING.md, docs/FIREBASE_SETUP.md (prep only, no keys), and `landing/`, an app-link page with
  `.well-known/assetlinks.json` for the upload certificate.

## 1.2.0: Humming Keyboard M7
Prediction
- An on-device neural next-word model for English: a small word-level LSTM (16,000 words, 128-dim embeddings, 256 hidden units, int8 weights, a 2.7 MB file) that reads the whole
  sentence so far. Humming's own Kotlin code runs it (no native library, no network). It is blended with the word
  tables and with what you type. Held-out next-word top-3 accuracy (sentences no model saw): 30.2 % in 1.1,
  36.5 % now. "Smart prediction strength" sets the blend (0 turns the model off).
- Next-word prediction in German, Spanish, French, Italian and Portuguese, from Tatoeba (CC BY 2.0 FR) and
  Common Voice (CC0) sentences: held-out top-3 German 22.1 %, Spanish 26.4 %, French 25.9 %, Italian 24.8 %, Portuguese 25.3 % (1.1 had no next-word data for them: 1-5 %).
- The benchmark runs as a unit test on every build (PredictionBenchmarkTest), and a second test checks the Kotlin
  model gives the trainer's probabilities.
- Settings: smart prediction strength, weight of your own typing, suggestions shown (3 to 5), best suggestion in the
  middle or first.

Stickers & GIFs (off until you turn it on)
- A sticker panel with 32 built-in stickers (from Noto Emoji images, Apache-2.0), your own imported images, and a
  search box with its own letter keys.
- Optional online GIF search through Openverse: openly licensed GIFs, no account or key. It says exactly what it
  sends (only the words in its search box), shows each GIF's licence and creator, and stays off in incognito unless
  you allow it. Settings > Stickers & GIFs; also listed on the Privacy screen.

Everything customisable
- New settings: key repeat speed and double-tap speed (both were fixed system values), voice button size, toolbar
  animation, stickers per row. All are found by Settings search and saved by Backup.
- CustomisationAuditTest fails the unit-test run if any user preference has no control in Settings.

Build and data
- `keyboard/lm/` builds the tables, the benchmark sentences and the neural model from the corpora (the lm-build
  workflow); only counts and trained weights ship, never sentences.
- Speed: a next-word prediction with the neural model takes 19.5 ms in the JVM benchmark; the model adds 2.7 MB to the APK.
- The upstream theme-engine tests (lib:snygg) compile and pass again: they imported two colour schemes the upstream
  tree lacks, now test fixtures, so a plain `./gradlew testDebugUnitTest` runs every module's tests (216).

Tests: PredictionBenchmarkTest, CustomisationAuditTest, StickersTest.

## 1.1.0: Humming Keyboard M6
Prediction (rewritten)
- A real word-prediction engine replaces the upstream stub and the M4/M5 test list (`com.humming.keyboard.predict`):
  - English: 156k words with blended frequencies (AOSP LatinIME word list, Apache-2.0, plus Tatoeba counts);
    next-word statistics from Tatoeba English sentences (CC BY 2.0 FR), boosted for everyday chat phrases.
  - German, Spanish, French, Italian and Portuguese: 60k-word AOSP dictionaries. A language without a dictionary
    gets no suggestions instead of English ones.
  - One scorer for completions, keyboard-aware typo correction and context; autocorrect with Off / Mild / Normal /
    Aggressive strength and a runner-up margin; apostrophe fixes (dont -> don't).
  - Learns the words and word pairs you type, per language, on the phone only (never in incognito); long-press a
    suggestion to forget it; "Clear learned words" in Privacy.
- A Gboard-style three-slot strip: the best guess in the middle, what you typed in quotes, no duplicates.
  "Sounds gre" used to show gre | grew | great with grew as the autocorrect; it now shows "gre" | great | grew.
- Emoji in the strip ("pizza" offers 🍕), emoji search with ":heart", text shortcuts ("omw" -> your phrase).
- Glide typing uses the real dictionary; its settings are back in Gestures.

Everything on together
- Number row on by default, one-handed mode, a floating keyboard you drag by its handle, split in landscape,
  AMOLED black theme, a background photo with dimming, sound and vibration, long-press symbols, clipboard grid,
  edit shortcuts, trackpad, incognito, voice, and multiple languages: all work at the same time, not as modes.
- The emoji panel opens on smileys when nothing has been used yet.

Settings
- Settings search (every setting by name or keyword) that opens ready to type.
- A Prediction group (autocorrect strength, next word, learning, emoji), a Privacy screen, Text shortcuts,
  Background image, Floating keyboard.
- Backup and restore to a file now carry the voice settings, learned words (all languages) and the background photo.

Accessibility and audit (see AUDIT.md)
- TalkBack reads every key ("Shift", "Delete", "Space", letters) and a double-tap types it.
- Every text/background pair in the Humming themes meets WCAG AA 4.5:1 (checked by ThemeContrastTest).
- Settings writes no longer block the UI thread; the floating keyboard saves its position once per drag.
- The phone release APK is arm64-only (13 MB smaller); debug builds keep x86_64 for the emulator.
- The voice model (about 200 MB of native memory once loaded) is unloaded when Android reports low memory and no
  dictation is using it; the next dictation reloads it in about a second.
- Typo search scans only the first-letter buckets a typo can reach (same results, about 6x less work).
- The app is called Humming everywhere (the settings home and toasts said FlorisBoard); adding a first language keeps
  English; incognito shows a clear icon in the toolbar.

Tests: PredictorTest, MultilingualTest, SettingsTest, HummingFloatTest, HummingA11yTest, ThemeContrastTest,
VoiceMemoryTest.

## 1.0.0: Humming Keyboard M5
Voice
- The whisper.cpp engine is replaced with NVIDIA Parakeet on sherpa-onnx 1.13.8, chosen by a 73-clip bake-off.
  - Fast (default): Parakeet TDT-CTC 110M int8, 3.7% WER.
  - Best: Parakeet TDT 0.6B v2 int8, 2.6% WER.
  - For comparison: FUTO small.en ACFT 5.4%, whisper base.en 6.1%.
- Silero VAD cuts phrases for the live preview. Text is committed once, on Done.
- Model manager (Settings > Voice):
  - choose Fast or Best, with each model's size on disk;
  - delete a model;
  - "Download on Wi-Fi only";
  - downloads pinned to SHA-256;
  - an Offline badge on the voice panel.
- Bluetooth headset microphone support.
- Long-press the mic to switch to Gboard voice typing. The option only shows when Gboard is enabled, and it uses
  Gboard's voice subtype when Gboard declares one (`GboardHandoff`, `GboardHandoffTest`).
- NetworkAuditTest: only `ModelDownload.kt` may use a network API, and the voice path has none.

Typing
- Clipboard history is on by default, shown as a 2-column grid with timestamps, and keeps 100 items for up to a week.
- Edit shortcuts on long-press: a select all, c copy, x cut, v paste, z undo, y redo. An edit toolbar sits in the
  suggestion bar.
- The space-bar trackpad also moves the cursor up and down.
- Split keyboard in landscape and on tablets.
- Incognito mode also pauses voice history and clipboard history.

CI
- Offline proof on an emulator: airplane mode on, Wi-Fi off, no default network, ping failing. Cold start, a real
  dictation committed, and the screen recorded.
- Gboard hand-off run on a Google Play emulator image.
- Release APK job (R8, development-key signed).
- 70 unit tests.

## M3.1 (unreleased)
- Dictation no longer inserts text while you talk. Root cause: each live partial was written into the app's field as
  composing text, which WhatsApp/Chrome/Messages draw like typed text and commit on any selection or focus change.
  Live text now shows only in the keyboard's own grey preview line; the field gets one commit when you tap Done
  (`DictationOutput`, `DictationOutputTest`: a 90 s session makes zero commits before stop). The mic cap (now 15 min)
  only stops the microphone; closing the keyboard mid-dictation saves the text to History instead of inserting it.
- Material 3 look: Material You dynamic colour (Android 12+), light/dark, rounded keys with ripple press states,
  tonal function keys, accent enter key, a listening panel (pulsing dot, timer, live waveform, Done button) in place
  of the suggestion strip while dictating. Robolectric snapshot test (`KeyboardSnapshotTest`, run with
  HUMMING_SNAPSHOT_DIR) renders the keyboard to PNGs.
- Clean mode (Rambler-style, on by default; Settings switch for word-for-word): retracted sentences ("scratch that",
  "never mind"), "actually, no" restarts, amount restarts ("ten minutes, uh, fifteen minutes"), fillers between
  commas, stutters with apostrophes, "$500, no, $600", spoken lists ("first, ... second, ...") become bullets.
  CLEAN_SET.md: 18/20 exact (was 5/20), 40 character edits (was 293).
- FEATURES.md: parity matrix vs FUTO Keyboard, Gboard, Gboard Rambler, SwiftKey.

## M3 (unreleased)
Latency first, then typing basics and the head-to-head kit. All numbers below are x86_64 host-box numbers from
`core/ci/stream_bench.py` and `core/ci/bench.py` (M3_BENCH.md), not phone numbers.

- Stop->text on 10-15 s dictations, keyboard pipeline simulated on the host with the real engine and models:
  base.en-q8_0 (the arm64 phone model) median 0.73 s, p90 2.6 s; M2 decode path (base.en) median 3.7 s, p90 4.8 s.
- Cleanup: "three, no, four pm" that base.en writes as "3.0.4 pm" is now read as a self-correction ("4pm").
  Host 20-clip set with base.en-q8_0 goes from 6/95 to 3/95 (3.2%); 5-min clip unchanged at 0.1447.
- Typing: next-word strip starts from a bundled seed list (`assets/next_words_seed.tsv`, hand-written, a few hundred
  common pairs); learned pairs rank above it. Autocorrect cost model: a dropped vowel, swapped vowels and a letter
  moved two places cost less. The 20 fixed misspellings and 10 next-word prefixes of the head-to-head are unit tests
  (20/20, 10/10 on the logic).
- Native library variant choice (`CoreLoader.pickVariant`) is pure and unit-tested; the choice is logged as
  `HUMMING_CORE_LIB` before the first engine call.
- `core/ci/stream_bench.py` (new) and `bench.py --resume`.

## 1.0.1 - 2026-09-28
Patch release: release hygiene only, no functional changes to the keyboard.

- versionName 1.0.1, versionCode 101.
- The MANIFEST generator (`android/ci/manifest.py`) reads versionName, versionCode, minSdk and targetSdk from
  `android/app/build.gradle.kts` instead of having 1.0.0/100 hardcoded.
- README install line points at `humming-v1.0.1.apk` from the v1.0.1 GitHub Release.
- The release APK is signed by CI with the same release key as 1.0.0, so it installs over 1.0.0.
- The release APK is now byte-reproducible (two clean builds give the same sha256): the Android Gradle plugin's
  encrypted dependency-info block is no longer embedded; it was the only difference between two builds.
- The emulator proof (CI job `emulator-proof`, items 1-6) is re-run by CI on this tag; `evidence/` still holds the
  v1.0.0 run 36350013869.
