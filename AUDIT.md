# Humming Keyboard: full audit (M6)

Scope: the Android keyboard in `keyboard/` (a FlorisBoard fork, Apache-2.0) at the M6 release commit, its CI
(`.github/workflows/keyboard.yml`) and the emulator evidence from CI run 36665067290. Severity: High = must fix before a
public release, Medium = fix now, Low = known and acceptable, listed honestly. Every High and Medium finding below
is fixed in M6 unless marked otherwise.

## 1. Privacy and network

What touches the network: **only the one-time voice model download** (`ModelDownload.kt`), from a pinned URL on
the sherpa-onnx GitHub release, checked against a pinned SHA-256 and size, on Wi-Fi only by default. Typing,
prediction, learning, emoji, clipboard, dictation and settings never use the network.

Evidence:
- `NetworkAuditTest` scans every Kotlin/Java source of the app and its libraries for network APIs
  (`HttpURLConnection`, `URLConnection`, sockets, OkHttp, Ktor, WebView, `DownloadManager`, `java.net.URL`, ...).
  Only `ModelDownload.kt` may use one, and the voice path has none. It runs on every CI build and passes.
- The M5 offline proof still holds: an emulator in airplane mode with Wi-Fi and data off dictated and committed
  "Please send the quarterly report to the whole team by Friday afternoon." (`offline_committed.png`).
- The prediction data (dictionaries, next-word tables, emoji names) ships inside the APK. Learned words live in
  the app's private files (`predict_user*.tsv`), are skipped in incognito and when "Learn from my typing" is off,
  and are cleared by Privacy > Clear learned words. Nothing is uploaded; there is no analytics or crash reporting
  SDK (FlorisBoard's crash screen writes a local log only).
- `INTERNET` and `ACCESS_NETWORK_STATE` are declared only for the model download and the Wi-Fi-only check.

## 2. Permissions

| Permission | Why | Asked when |
|---|---|---|
| `RECORD_AUDIO` | dictation | first tap on the mic (runtime prompt) |
| `INTERNET`, `ACCESS_NETWORK_STATE` | one-time model download, Wi-Fi-only check | install time (normal) |
| `MODIFY_AUDIO_SETTINGS` | route the Bluetooth headset microphone | install time (normal) |
| `VIBRATE` | key-press vibration | install time (normal) |
| `POST_NOTIFICATIONS` | model download progress | Android 13+ runtime prompt, only when downloading |

No contacts, location, storage or accessibility-service permission. The background photo is picked with the
system photo picker (`GetContent`) and copied into private storage, so no storage permission is needed.

## 3. Security

- gitleaks over the whole working tree and the M6 commits: **no leaks** (log in the pro copy's `SCAN.md`).
- No API keys, tokens or signing secrets in the repo. The release APK is signed with a **development key** kept
  for CI; a real upload key is still needed for the store (see Remaining).
- Settings import (Settings > Other > Restore) unzips with FlorisBoard's canonical-path check (zip-slip safe), and
  Humming's part copies only a fixed list of file names, so a crafted backup cannot write outside the app's files.
- The background photo is decoded with the platform decoder into a bitmap; a bad image is rejected with a toast.
- Model archives are verified by SHA-256 **before** unpacking, and only the model's own listed files are extracted
  (`ModelPolicy.extractTarget`, which rejects `..` paths).

## 4. Performance (CI emulator, x86_64 with software rendering; a real phone is faster)

Measured by `screens.py` on the CI emulator (Android 15, x86_64, software GPU) in run 36665067290 (WIP 9, the same
prediction and voice code as the release):

| Measure | Result |
|---|---|
| Suggestion time per key press, typed at a human pace (33 key presses) | median **147 ms**, p90 264 ms, max 623 ms |
| Suggestion time per key press, adb bursts (93, overlapping) | median 183 ms, p90 794 ms |
| Same engine on the JVM (`PredictorTest.fastEnough`, CI runner, typo-heavy words) | **14.5 ms** mean per key press (test limit 40 ms) |
| First suggestion after install (dictionary load while the emulator is busy) | 11.3 s, once |
| Keyboard cold start (process killed, field tapped, keys drawn) | 16.5 s on the emulator (9.9 s in the WIP 6 run; the emulator is heavily loaded at this point of the run) |
| Settings app cold start (`am start -W`) | 4.8 s |
| Memory (PSS) after dictation, voice model loaded | 442 MB |
| Memory (PSS) after Android's low-memory signal (model unloaded, `HUMMING_ENGINE unloaded` in the log) | **282 MB** |
| Frame times while typing (software rendering) | 88% of frames over 16 ms, median 150 ms |

These are emulator numbers with no GPU; a real arm64 phone is several times faster, but **no real-phone numbers
exist yet** (see Remaining). The frame-time line reflects the emulator's software renderer, not the keyboard.

APK size: phone release APK (arm64) **25.4 MB**. The biggest parts are ONNX Runtime (22 MB uncompressed, needed
for on-device dictation), the Chinese shape-based language pack from FlorisBoard (7.4 MB), and the English
next-word table (4.9 MB, 1.9 MB compressed). M6 finding (Medium, fixed): the release APK carried x86_64 libraries
too (13 MB compressed); the phone APK is now arm64-only.

Other fixes in M6:
- Medium, fixed: once dictation had been used, the voice model stayed loaded for the life of the keyboard process
  (about 200 MB of native memory, which makes Android more likely to kill the keyboard in the background). It is
  now unloaded when Android reports low memory and no dictation is using it, and reloads on the next dictation
  (about a second). Proof: in CI run 36665067290 the keyboard's PSS fell from 442 MB to 282 MB after `am send-trim-memory ... RUNNING_LOW`.
- Medium, fixed: typo correction compared the typed word with every dictionary word of a similar length (up to
  about 15k distance checks per key press); it now scans only the first-letter buckets a typo can reach (same
  results, about 6x less work).
- Medium, fixed: settings clicks used a blocking call on the UI thread for preference writes (ANR risk); they now
  write on a background scope.
- Medium, fixed: the floating keyboard wrote its position to storage on every drag event; it now saves once when
  the drag ends.
- Low: the dictionary loads once per language per process in the background (about 1 s for English on the
  emulator); the first suggestion after a cold start can wait for it.

## 5. Accessibility

- High, fixed: keys had **no accessibility semantics** (upstream FlorisBoard issue), so TalkBack could not read or
  type them. Every key now has a spoken label ("Shift", "Delete", "Space", "Emoji", letters) and a double-tap
  types it (`HummingA11y`, `HummingA11yTest`).
- Medium, fixed: contrast. White on the dark themes' purple enter key was 3.5:1 and the light theme's auto-commit
  suggestion was 3.9:1. Every text/background pair in the Humming light, dark and AMOLED themes now meets WCAG AA
  4.5:1 (key labels 10.9:1 to 18.1:1), enforced by `ThemeContrastTest`.
- Settings screens are standard Material 3 components with labels; icon buttons added by Humming have content
  descriptions (delete shortcut, move keyboard, voice controls).
- Low, remaining: the suggestion strip reads each suggestion as plain text, without "best guess" or "autocorrect"
  hints; glide typing has no TalkBack equivalent (same as other keyboards).

## 6. Crash and ANR risk

- CI emulator run 36665067290: the keyboard went through every feature (typing in five scripts of use, emoji, clipboard,
  one-handed, floating, themes, photo, languages, settings, voice) with **no crash** in the crash buffer and
  **no ANR** from the keyboard (the only ANR event on the emulator image was Google Play services).
- Fixed in M6: a blocking preference write in click handlers (above); settings writes from the IME process are
  coroutine-based.
- Fixed in M6: a compile break in the release build (a type error in the new typo search) was caught by CI before
  anything shipped.
- Low, remaining: FlorisBoard's editor layer still uses `runBlocking` for its own content queue (upstream design,
  short critical sections).

## 7. Licences

All code is Apache-2.0 (FlorisBoard base, Humming changes) and no GPL code is included. Every third-party component
and data set, with its licence and what we must do, is in `keyboard/LICENSES.md`; data attributions are also in
`keyboard/app/src/main/assets/predict/NOTICE.txt` and in the app (Typing > Dictionaries, Settings > Voice).

| Component | Licence |
|---|---|
| FlorisBoard (base) | Apache-2.0 (+ `NOTICE-HUMMING.md` change notice) |
| sherpa-onnx 1.13.8 | Apache-2.0 |
| ONNX Runtime (in the sherpa-onnx AAR) | MIT |
| NVIDIA Parakeet models (downloaded, not bundled) | CC-BY-4.0, attributed |
| Silero VAD | MIT |
| Apache Commons Compress | Apache-2.0 |
| AOSP LatinIME word lists (en, de, es, fr, it, pt_BR) | Apache-2.0 |
| Tatoeba sentences (only aggregated counts ship) | CC BY 2.0 FR, attributed |
| Unicode CLDR emoji annotations | Unicode License v3 |
| AndroidX, Kotlin, kotlinx, Room, cache4k, AboutLibraries, JetPref, compose-tooltip | Apache-2.0 |

Low, remaining: FlorisBoard's comment in `FlorisImeSizing.kt` says the key-size fractions were "inspired by"
OpenBoard (GPL-3.0); it credits numbers, not code, and no OpenBoard code is present. The bundled Chinese
shape-based tables come from FlorisBoard, which ships them as Apache-2.0; their upstream provenance is FlorisBoard's
statement.

## 8. Test coverage

95 JVM unit tests, all green in CI run 36665067290, plus the emulator suites (`screens.py`: every feature,
the offline proof, and `handoff.py`: the Gboard hand-off on a Play image). New in M6: `PredictorTest`
(dictionary, ranking, the hero-shot case, autocorrect, apostrophes, learning, latency), `MultilingualTest`,
`SettingsTest` (strip order, backup round-trip, settings search), `HummingFloatTest`, `HummingA11yTest`,
`ThemeContrastTest`, `VoiceMemoryTest`. Not covered by unit tests: Compose UI layout (checked by emulator screenshots instead).

## 9. Gap list vs Gboard, SwiftKey and FUTO

| Area | Humming M6 | Gboard | SwiftKey | FUTO Keyboard |
|---|---|---|---|---|
| Dictation | Parakeet on-device, 3.7% WER (Fast), fully offline | cloud + on-device, best-in-class | cloud (Microsoft) | Whisper on-device, 5.4% WER (our bench) |
| Prediction quality | dictionary + bigram/trigram + learning | large neural LM, best | strong personal LM | transformer LM on-device, very good |
| Next-word languages | English next-word; 6 languages of completion/correction | 900+ languages | 700+ | many, downloadable |
| Glide typing | yes (statistical, dictionary-fed) | best | very good | good |
| Emoji search | ":word" in the strip, emoji suggestions | search box in panel | search box | search box |
| GIF / stickers | **no** (needs online services; skipped to stay offline) | yes (online) | yes (online) | no |
| Clipboard | grid, timestamps, retention | yes | yes (cloud sync) | yes |
| Themes | light/dark/AMOLED/custom colour/photo | yes | yes | yes |
| Privacy | offline by design, no telemetry | cloud features, telemetry | cloud account | offline, no telemetry |
| Licence | Apache-2.0 | proprietary | proprietary | source-first, non-commercial |

What would close the biggest gaps: a small on-device neural language model for next-word (the one area where
Gboard, SwiftKey and FUTO are clearly ahead), next-word tables for the other languages, and a search box inside the
emoji panel.

## Findings summary

Fixed in M6: 
- High: word prediction. Autocorrect picked "grew" for "Sounds gre", next-word came from a 270-pair seed list,
  glide typing used FlorisBoard's tiny test dictionary (swipes committed junk or nothing), and the strip put the
  typed word first with no clear best guess. Replaced by a real engine: AOSP dictionaries (English 156k words, five
  more languages 60k each), Tatoeba next-word statistics, keyboard-aware typo correction with a strength setting,
  learning on the phone only, and a Gboard-style strip with the best guess in the middle.
- High: TalkBack could not read or type keys (now labelled, double-tap types).
- Medium: text contrast below WCAG AA on the dark enter key and one light-theme suggestion (now all pairs ≥ 4.5:1).
- Medium: blocking preference writes on the UI thread (ANR risk); floating keyboard writing storage on every drag
  event.
- Medium: voice model held in memory forever after the first dictation (now unloaded on low memory when idle).
- Medium: typo search scanned every word of a length per key press (now bucketed, about 6x less work).
- Medium: phone release APK carried x86_64 libraries (13 MB); now arm64-only.
- Medium: adding a first language silently removed English; the app still called itself FlorisBoard in the
  settings home and toasts; the incognito indicator was a faint watermark, and the toolbar icon that replaced it never appeared because the toolbar read the incognito state without observing it (upstream bug); Gestures showed a stale "glide typing
  not available" card; settings search did not open ready to type.


Remaining (Low, or needs a person): 
- Needs a person: the release APK is signed with a development key; a real upload key (kept outside the repo) is
  needed before any store release.
- Low: next-word prediction is English only; German, Spanish, French, Italian and Portuguese have completion and
  correction but no next-word tables yet.
- Low: no GIF or sticker panel, because every GIF source needs the network (skipped to keep the keyboard offline).
- Low: emoji search is ":word" in the suggestion strip, not a search box inside the emoji panel.
- Low: prediction quality is a dictionary plus word-pair statistics, clearly below the neural models in Gboard,
  SwiftKey and FUTO for next-word guesses.
- Low: the suggestion strip does not announce "best guess" or "autocorrect" to TalkBack; glide typing has no
  TalkBack equivalent.
- Low: FlorisBoard's own `runBlocking` content queue (upstream design) and the key-size comment crediting OpenBoard
  numbers (no OpenBoard code is present).
- Low: the first suggestion after a cold start waits for the dictionary load (about 1 s on the emulator).
- Not measured: latency and memory on a real phone; all numbers here are from the CI emulator (software rendering),
  which is slower than a phone.

