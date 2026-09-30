# Humming Keyboard: full audit (M7, version 1.2.0)

Scope: the Android keyboard in `keyboard/` (a FlorisBoard fork, Apache-2.0) at the M7 release commit `bb644b3` (code; the only later change is this audit), its CI
(`.github/workflows/keyboard.yml`, `.github/workflows/lm-build.yml`), the JVM unit tests and benchmark of the release
build of `bb644b3` (a Linux build machine running `./gradlew testDebugUnitTest :app:assembleRelease`), and the emulator evidence from CI run 36676272085 (1.2 on d137a7b, before the neural model was added;
GitHub Actions was unavailable for the later commits, see Remaining).
M6 numbers come from the M6 audit (CI run 36665067290). Severity: High = must fix before a public release,
Medium = fix now, Low = known and acceptable, listed honestly. Every High and Medium finding is fixed in M7 unless
marked otherwise.

## 0. What M7 changed, measured

| Goal | M6 (1.1.0) | M7 (1.2.0) |
|---|---|---|
| English next-word, held-out top-3 (sentences no model saw, 11,341 words) | 30.2 % (word tables) | **36.5 %** (neural model + tables) |
| English, Common Voice half only (new to both versions) | 23.2 % | **27.3 %** |
| German / Spanish / French / Italian / Portuguese next-word top-3 | 1.3–4.6 % (no next-word data) | **22.1–26.4 %** |
| Suggestion time per key press, human pace, CI emulator (median) | 147 ms | **55.6 ms** (limit 150 ms; 106 ms on a slower emulator host; measured before the model was added, which costs 8.4 ms per new word on the JVM) |
| Memory (PSS) after dictation, voice model loaded | 442 MB | 484 MB (before the model; it adds 2.7 MB) |
| Memory (PSS) after Android's low-memory signal | 282 MB | 338 MB |
| Phone release APK (arm64) | 25.4 MB | **32.8 MB** (limit 40 MB) |
| GIF / stickers | none | sticker panel, your own stickers, opt-in keyless GIF search |
| Settings with no control (hard-coded) | not checked | **0** user preferences without a control (CustomisationAuditTest) |
| JVM unit tests | 95 | 216 |

## 1. Privacy and network

What touches the network, and nothing else (enforced by `NetworkAuditTest`, which fails the build if any other
source file uses a network API):

- **The one-time voice model download** (`ModelDownload.kt`): unchanged from M6. Pinned URLs on the sherpa-onnx
  GitHub release, SHA-256 checked, Wi-Fi only by default.
- **New in 1.2, off by default: GIF search** (`GifSearch.kt`). It runs only after the user turns on "Online GIF
  search" in Settings > Stickers & GIFs, where the notice says what is sent and to whom. It sends only the words
  typed into the sticker panel's own search box, to Openverse (`api.openverse.org`, no account, no key, no cookies),
  over HTTPS only, with a fixed User-Agent and no device identifiers. In incognito it is off unless "Allow GIF search
  in incognito" is also on (off by default). Downloads are HTTPS-only and capped at 6 MB per GIF. The Privacy screen
  lists both switches.
- The neural next-word model is bundled in the APK and runs in Humming's own Kotlin code: no download, no native
  library, no network. Prediction, learning, clipboard and dictation never leave the phone.
- Finding (Medium, fixed in M7): the GIF search asked Openverse for 24 results per page; Openverse refuses more than
  20 without an account (HTTP 401), so every search showed "No openly licensed GIFs". The CI screenshot of the GIF
  panel caught it. It now asks for 20 (`StickersTest` checks), and a failed request says "Couldn't reach Openverse"
  instead of "no GIFs".
- Low: Openverse results are openly licensed (CC0, public domain, CC BY), but the content itself is whatever
  creators uploaded; `mature=false` is sent and each result shows its licence, creator and source.

## 2. Permissions

Unchanged from M6, and no new permission in 1.2: `RECORD_AUDIO` (asked on first dictation), `INTERNET` (the model
download; since 1.2 also the opt-in GIF search), `ACCESS_NETWORK_STATE` (Wi-Fi-only download), `MODIFY_AUDIO_SETTINGS`
(Bluetooth headset microphone), `VIBRATE`, `POST_NOTIFICATIONS` (upstream). Your own stickers are chosen with the
system document picker (`OpenMultipleDocuments`, no storage permission) and copied into the app's private storage.

## 3. Security

- The model file is parsed by `HummingLm` with a magic header and size checks (`require` on the vocabulary size);
  it is a bundled asset, not a downloaded file.
- GIF search: HTTPS only (`require(url.startsWith("https://"))` for the search and every download), size cap,
  and Android's `HttpURLConnection` does not follow a redirect from HTTPS to plain HTTP.
- Stickers are shared with the target app through the app's `FileProvider` (content URIs, no file paths), only
  through the editor's own image-commit API; apps that do not accept images get a toast, not a crash.
- gitleaks is clean on every push; the release APK is still signed with a development key (see Remaining).

## 4. Performance (CI emulator, x86_64 with software rendering; a real phone is faster)

Measured by `screens.py` on the CI emulator (Android 15, x86_64, software GPU) in run 36676272085 (commit d137a7b: all of 1.2
except the neural model; the model adds one LSTM step per new word, 8.4 ms on the JVM). Emulator hosts vary: run
36678145109 of the next commit measured a paced median of 106 ms and a 29 s voice-model load against 11 s here.

| Measure | M6 | M7 |
|---|---|---|
| Suggestion time per key press, typed at a human pace | median 147 ms, p90 264 ms | median **55.6 ms**, p90 171 ms, max 214 ms |
| Suggestion time per key press, adb bursts (overlapping) | median 183 ms | median 84 ms |
| Neural model per new word on the JVM (build machine, benchmark) | — | 8.4 ms per next-word guess (19.5 ms on a busier run) |
| Whole predictor per key press on the JVM (`PredictorTest.fastEnough`, best of 5 passes) | — | 23.1 ms (limit 40 ms) |
| First suggestion after install | 11.3 s | 4.3 s |
| Keyboard cold start | 16.5 s | 9.0 s |
| Settings cold start | 4.8 s | 4.6 s |
| PSS after dictation (voice model loaded) | 442 MB | 484 MB |
| PSS after the low-memory signal | 282 MB | 338 MB |

The neural model costs 2.7 MB of heap (int8 weights kept as bytes) and one LSTM step per new word; each
sentence prefix's state is cached, so typing letters inside a word costs nothing extra.

APK size: phone release APK (arm64) **32.8 MB** (M6 25.4 MB). New in 1.2: the five next-word tables
(about 12 MB uncompressed), the neural model (2.7 MB) and 32 stickers.

## 5. Accessibility

Unchanged from M6 (keys labelled for TalkBack, every Humming theme pair ≥ 4.5:1). New in 1.2: the sticker panel's
stickers carry content descriptions (their caption), the GIF results carry their titles, and the search box has its
own letter keys (the main keyboard is replaced while the panel is open). Low, remaining: as in M6, the strip does not
announce "best guess" or "autocorrect".

## 6. Crash and ANR risk

- CI emulator run 36676272085: every feature including the sticker panel, sticker search, GIF search and the five new
  languages' next-word, with **0 entries** in the crash buffer and **0 ANRs** from the keyboard.
- The model loads with the predictor, once per process, off the main thread; if it is missing or corrupt the
  predictor falls back to the tables (`runCatching`).
- GIF search runs on `Dispatchers.IO` with 8 s timeouts; a failure shows a message, never an exception.

## 7. Licences

All code is Apache-2.0 and no GPL code is included; no FUTO code or model is used. New in 1.2 (all recorded in
`keyboard/LICENSES.md`, `NOTICE-HUMMING.md`, `assets/predict/NOTICE.txt` and `assets/stickers/NOTICE.txt`):

| Component | Licence | Obligation |
|---|---|---|
| Humming neural next-word model `hlm_en.bin` (trained by `keyboard/lm/train.py`) | Apache-2.0 (Humming's own weights) | keep the notice |
| Tatoeba German, Spanish, French, Italian, Portuguese (and English) sentences, only counts and weights ship | CC BY 2.0 FR | attribution (done) |
| Mozilla Common Voice sentence lists | CC0 1.0 | none |
| Noto Emoji images (the 32 built-in stickers) | Apache-2.0 | credit (done) |
| Bangers font (sticker captions drawn with it; the font is not shipped) | SIL OFL 1.1 | credit (done) |
| Openverse (optional online search; nothing from it ships) | service; results CC0 / PD / CC BY | each result shows licence, creator, source |

Everything from M6 is unchanged (FlorisBoard Apache-2.0, sherpa-onnx Apache-2.0, ONNX Runtime MIT, Parakeet
CC-BY-4.0 downloaded not bundled, Silero VAD MIT, AOSP word lists Apache-2.0, Unicode CLDR).

## 8. Test coverage

216 JVM unit tests, all passing, in the release build of `bb644b3` (a Linux build machine running
`./gradlew testDebugUnitTest :app:assembleRelease`), plus the emulator suites (`screens.py`, `handoff.py`).
New in M7: `PredictionBenchmarkTest` (held-out top-3 for English before/after and for the five new languages, the
neural model's Kotlin inference against the trainer's reference probabilities, sorted tables),
`CustomisationAuditTest` (every user preference has a Settings control; key timings), `StickersTest` (sticker index,
GIF permission rules, the Openverse request and parser).

## 9. Customisation audit (goal 4)

Method: every preference declared in `AppPrefs.kt` was checked for a control in a Settings screen, and the keyboard
code was read for fixed values a user would want to change. The check now runs on every build
(`CustomisationAuditTest`): 164 preferences, all with a control except 21 internal ones (developer
overlays, version stamps, the floating keyboard's saved position, the toolbar's expanded state).

The owner's list, and where each lives:

| Asked for | Setting |
|---|---|
| Prediction strength | Typing > Prediction: autocorrect strength (Off/Mild/Normal/Aggressive), smart prediction strength (0-100, 0 = neural model off), weight of your own typing |
| Suggestion count | Typing > Prediction: suggestions shown (3-5); best suggestion in the middle or first |
| Key height | Keyboard: height (portrait and landscape), key spacing, font size, bottom offset, one-handed scale |
| Themes and colours | Theme: light/dark/AMOLED, day/night schedule, accent colour, background photo and dim, theme editor (every colour, font, key shape) |
| Sounds | Keyboard > Input feedback: sound on/off, volume, per event (press, long press, repeat, swipe) |
| Haptics | Keyboard > Input feedback: vibration on/off, mode, duration, strength, per event |
| Long-press delay | Keyboard: long-press delay (100-700 ms) |
| Layouts | Languages & layouts: any number of languages, each with its layout (QWERTY, QWERTZ, AZERTY, …) |

New in 1.2 (were fixed in code): **key repeat speed** (20-200 ms between repeats while Delete is held; was the
system's 50 ms) and **double-tap speed** (150-800 ms for double-space period and Shift caps lock; was the system's
300 ms), plus voice button size, toolbar animation, stickers per row, and the four prediction settings above. All
are found by Settings search and carried by Backup & restore.

Remaining fixed values, judged not worth a setting: the space-bar swipe-up distance for its action (tied to the swipe
distance threshold, which is a setting), the GIF request timeout, and the voice pipeline's silence threshold.

## 10. Gap list vs Gboard, SwiftKey and FUTO

| Area | Humming M7 | Gboard | SwiftKey | FUTO Keyboard |
|---|---|---|---|---|
| Dictation | Parakeet on-device, 3.7% WER (Fast), fully offline | cloud + on-device, best-in-class | cloud (Microsoft) | Whisper on-device, 5.4% WER (our bench) |
| Prediction quality | small on-device LSTM + word tables + learning (36.5 % top-3 held-out) | large neural LM, best | strong personal LM | transformer LM on-device, very good |
| Next-word languages | 6 (en neural; de/es/fr/it/pt tables) | 900+ | 700+ | many, downloadable |
| Glide typing | yes (statistical, dictionary-fed) | best | very good | good |
| Emoji search | ":word" in the strip, emoji suggestions | search box in panel | search box | search box |
| GIF / stickers | built-in + your own stickers; opt-in keyless GIF search (Openverse, openly licensed only) | yes (Tenor, online) | yes (online) | no |
| Clipboard | grid, timestamps, retention | yes | yes (cloud sync) | yes |
| Themes | light/dark/AMOLED/custom colour/photo/editor | yes | yes | yes |
| Privacy | offline by design, no telemetry; GIF search opt-in | cloud features, telemetry | cloud account | offline, no telemetry |
| Licence | Apache-2.0 | proprietary | proprietary | source-first, non-commercial |

What would close the next gaps: a bigger neural model (or a small transformer) trained on more conversational text,
neural models for the other five languages, the GIF catalogue (Openverse has far fewer reaction GIFs than Tenor or
Giphy, which need API keys), and an emoji search box inside the emoji panel.

## Findings summary

Fixed in M7:
- Portuguese (and the other new languages) offered English words ("a | for | to") after an unseen word: the fallback list was English for every language; it is now the language's own most frequent words (ForeignStripTest).
- English words leaking into the German to Portuguese word lists (Tatoeba sentences quote English): demoted in the lexicons with `keyboard/lm/demote_english.py`.
- The table builder now filters banned words itself and refills each context's list after filtering, so no context is left short.
- Online GIF search returned nothing ("No openly licensed GIFs for cat"): Openverse refuses anonymous requests for more than 20 results; page size is now 20, and a failed request says it couldn't reach the service instead of claiming there are no GIFs.
- Key repeat speed and double-tap speed were fixed system values; both are settings now.
- CI language screenshots were one word behind (adb typed in a burst); they now type word by word at a typing pace.

Remaining (Low, or needs a person):
- Low: the neural model covers English only; German to Portuguese use word tables (held-out top-3 above), not a neural model.
- Low: Openverse has few reaction GIFs compared with Tenor or Giphy (both need API keys), so GIF search is thin.
- Low: the emoji panel has no search box of its own (":word" search in the suggestion strip works).
- Needs a person: install and type on a real phone (all numbers here are from the CI emulator).
- Medium, blocked: GitHub Actions on the repo's account stopped starting jobs at 06:59Z (billing), so commits from 9edbf51 on were built and unit-tested on a separate Linux build machine, not in CI, and the emulator suites (`screens.py`, `handoff.py`) have not run with the neural model. Re-run the keyboard workflow on the release commit once Actions is back.
- Low: the release APK is signed with a development key; a real release needs the owner's signing key.
