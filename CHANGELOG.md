# Changelog

## 1.6.1: Humming everywhere in the app
- **Android**: every place the app showed the name "FlorisBoard" or "Floris" now says Humming: the setup and home
  screen warnings, the crash dialog, crash notifications and the crash report header, the About screen icon
  description and licence line, the theme source label, and the notification-permission explanation, in every
  translation. The built-in themes are now called Humming Day, Humming Night, Humming Pure Night (each also Borderless
  and Material You). The app name, launcher and keyboard (IME) name were already Humming.
- **Credit**: the About screen and the README now say "Android keyboard built on FlorisBoard (Apache 2.0)".
  FlorisBoard's LICENSE, notices and copyright headers are unchanged. Internal package names, class names and IDs
  (`dev.patrickgold.florisboard`, `org.florisboard.*` extension and theme IDs) are unchanged, so settings, themes and
  dictionaries carry over and 1.6.0 updates in place.
- Android version 1.6.1 (versionCode 1601). Desktop and iOS are unchanged and stay at 1.6.0.

## 1.6.0: Humming updates itself
- **Desktop (Windows, macOS, Linux AppImage)**: built-in, signed self-update. Humming checks for a new version 45
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
  update files to a public site (`/updates/`), since the repository is private. The website deploy then checks
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

## Unreleased: Humming for Windows and macOS, signed Android release
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
- CustomisationAuditTest fails the build if any user preference has no control in Settings.

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

## 1.0.0

- Works out of the box: the keyboard downloads its speech model by itself the first time it opens, with a progress
  bar inside the keyboard (resumable, sha256-verified, retries on a dropped connection). No setup app needed; the
  Humming app is now optional settings and history.
- Dictate up to 5 minutes (was 60 s), with elapsed time, time left and a countdown for the last 30 seconds.
- Live text: speech is cut at natural pauses and each phrase is transcribed while you keep talking; it appears in
  the text field as you go (underlined until you stop). After stop only the last phrase is left to transcribe, so
  the final text lands fast. On a slower device, phrases waiting in the queue are merged into one transcription
  (up to 28 s), so a long dictation never falls behind the speaker and nothing is dropped at the end.
- x86_64 devices (emulators, Chromebooks) get the smaller whisper tiny.en model (78 MB) because the engine's
  x86_64 build is too slow for base.en to stay live; arm64 phones keep base.en (148 MB).
- The engine does a one-time warm-up pass as soon as the model is loaded, so the first dictation isn't slower
  than the rest.
- Cleanup (on-device rules): fillers, repeated words, self-corrections ("3, no, 4pm"), spoken punctuation, capitals
  and final punctuation, applied to the whole dictation; times are written the way people type them ("4pm"). No cloud or LLM polish in this build (no free route that
  keeps keys out of the APK).
- Full keyboard: number row, two symbol pages (?123 and =\<), cursor arrows, key repeat on space/arrows/backspace,
  backspace-hold that speeds up to whole words, pressed-key highlight and key haptics.
- Mic closes when the keyboard hides; whatever was said so far is still inserted.
- Release APK signed with a stable release key (CI secret); debug-only test hooks are not in the release APK.
- CI: emulator proof suite (API 35, KVM, 4 cores) and a release-APK smoke test; evidence in `evidence/`.
