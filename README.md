<p align="center"><img src="docs/banner.png" alt="Humming: private voice typing that never leaves your device" width="100%"></p>

<p align="center">
<a href="https://github.com/muhib-karim/humming-keyboard/releases/latest"><img alt="Release" src="https://img.shields.io/badge/release-v1.4.0-e5486b"></a>
<a href="https://github.com/muhib-karim/humming-keyboard/actions/workflows/keyboard.yml"><img alt="Android CI" src="https://github.com/muhib-karim/humming-keyboard/actions/workflows/keyboard.yml/badge.svg"></a>
<a href="https://github.com/muhib-karim/humming-keyboard/actions/workflows/desktop.yml"><img alt="Desktop CI" src="https://github.com/muhib-karim/humming-keyboard/actions/workflows/desktop.yml/badge.svg"></a>
<a href="https://github.com/muhib-karim/humming-keyboard/actions/workflows/ios.yml"><img alt="iOS CI" src="https://github.com/muhib-karim/humming-keyboard/actions/workflows/ios.yml/badge.svg"></a>
<img alt="Licence" src="https://img.shields.io/badge/licence-Apache--2.0-2a2a33">
<img alt="Platforms" src="https://img.shields.io/badge/Android%20%C2%B7%20iOS%20%C2%B7%20Windows%20%C2%B7%20macOS%20%C2%B7%20Linux-18181f">
</p>

# Humming Keyboard

**A private keyboard with offline voice typing, for Android, iPhone, Windows, macOS and Linux.** Tap the mic and
talk: the words are typed into the text field as you speak, for as long as you speak, even with the screen off. Tap
the mic again to stop. Speech recognition runs on your device, so dictation works in airplane mode and your voice
never leaves it. Free and open source.

[Get Humming](#get-humming) · [Walkthroughs](#walkthroughs) · [Screenshots](#screenshots) · [Features](#features) ·
[Benchmarks](#benchmarks) · [Compare](#how-humming-compares) · [Privacy](#offline-by-design) · [Website](https://muhib-karim.github.io/humming-keyboard/)

## At a glance

| | Android | iPhone / iPad | Windows · macOS · Linux |
|---|:---:|:---:|:---:|
| Offline dictation, nothing sent anywhere | ✅ Parakeet | ✅ Apple on-device | ✅ Parakeet |
| One control, no length limit | ✅ | ✅ | ✅ (hold or toggle shortcut) |
| Clean mode (um, uh, stutters, "3, no, 4pm" → "4pm") | ✅ | ✅ | ✅ |
| Accuracy mode (strict clean-up, new in 1.4) | ✅ | ✅ | ✅ |
| Custom words | ✅ | ✅ | ✅ |
| Voice commands ("new line", "scratch that") | ✅ | ✅ "new line", spoken punctuation | ✅ |
| Full keyboard (prediction, glide, emoji, clipboard) | ✅ | ✅ basic layout | — |
| Keeps going with the screen off | ✅ | — | — |
| Optional AI clean-up with your own key | ✅ | — | — |
| History you can search | ✅ | — | ✅ |
| Install | APK / AAB | unsigned `.ipa` (sideload) | `.exe` `.msi` `.dmg` `.AppImage` `.deb` |

## Get Humming

Every file is on the [latest GitHub Release](https://github.com/muhib-karim/humming-keyboard/releases/latest), with a `SHA256SUMS.txt` to check them.

| Platform | Download | Install |
|---|---|---|
| **Android** 8.0+ | `Humming-1.4.0.apk` | Open the APK, allow "install unknown apps" for your browser or file manager, install. Then open Humming and follow the two steps: enable the keyboard, then switch to it. The first mic tap downloads the Fast speech model (104 MB, once). |
| **iPhone / iPad** (iOS 16+) | `Humming_1.4.0_unsigned.ipa` | Sideload with your own free Apple ID through Sideloadly, AltStore or Xcode (re-sign every 7 days). Then Settings > General > Keyboard > Keyboards > Add New Keyboard > Humming. Step by step: [docs/IOS_SIDELOAD.md](docs/IOS_SIDELOAD.md). |
| **Windows** 10/11 x64 | `Humming_1.4.0_x64-setup.exe` or `.msi` | Run the installer. SmartScreen: **More info** > **Run anyway** ([details](#desktop-windows-macos-and-linux-how-to-open-it)). |
| **macOS** 11+ (Apple silicon and Intel) | `Humming_1.4.0_universal.dmg` | Drag to Applications, open once, then **System Settings > Privacy & Security > Open Anyway**. Allow Microphone and Accessibility. |
| **Linux** x86-64 | `Humming_1.4.0_amd64.AppImage` or `.deb` | `chmod +x` the AppImage and run it, or `sudo apt install ./Humming_1.4.0_amd64.deb`. |

All five share one version number (1.4.0). On the desktop you hold a shortcut (Ctrl/Cmd + Shift + Space by default)
in any app, talk, and let go: the text is pasted where your cursor is.

## Walkthroughs

| Android: tap, talk, it types | Android: screen off, still listening | iPhone: talk, clean text | Desktop: try it, talk, get clean text |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/android_dictation.gif" width="190" alt="Android dictation"> | <img src="docs/screenshots/android_screen_off.gif" width="190" alt="Dictation with the screen off"> | <img src="docs/screenshots/ios_walkthrough.gif" width="190" alt="iPhone dictation with accuracy mode"> | <img src="docs/screenshots/desktop_dictation.gif" width="300" alt="Desktop dictation"> |

*Android frames come from a CI emulator run in airplane mode with a spoken clip fed in as the microphone. The iPhone
and desktop recordings come from CI too (the iOS simulator and the desktop interface), playing a sample sentence,
because CI machines have no microphone.*

## Screenshots

Every image is a real capture from CI on the 1.3/1.4 builds: Android on an emulator (Pixel 6, Android 15), iPhone
on the iOS simulator, desktop from the app's own interface in Chromium. Sample text is made up.

### Android

**Dictation: tap once, talk, text is typed as you go**

<p>
<img src="docs/screenshots/android_dictation_listening.png" width="200" alt="Listening: one round control, no time limit">
<img src="docs/screenshots/android_dictation_typed.png" width="200" alt="Dictated text in a chat">
<img src="docs/screenshots/android_screen_off_listening.png" width="200" alt="Still listening after the screen was turned off and on">
<img src="docs/screenshots/android_offline_committed.png" width="200" alt="Committed offline with airplane mode on">
</p>

**Keyboard**

<p>
<img src="docs/screenshots/android_keyboard_light.png" width="200" alt="Keyboard, light">
<img src="docs/screenshots/android_keyboard_dark.png" width="200" alt="Keyboard, dark">
<img src="docs/screenshots/android_swipe.gif" width="200" alt="Glide typing">
<img src="docs/screenshots/android_emoji.png" width="200" alt="Emoji panel">
<img src="docs/screenshots/android_stickers.png" width="200" alt="Stickers and GIFs">
<img src="docs/screenshots/android_clipboard.png" width="200" alt="Clipboard history">
</p>

**Themes**

<p>
<img src="docs/screenshots/android_themes.gif" width="200" alt="Light, dark, AMOLED and day themes">
<img src="docs/screenshots/android_theme_picker.png" width="200" alt="Theme picker">
<img src="docs/screenshots/android_theme_amoled.png" width="200" alt="AMOLED black theme">
<img src="docs/screenshots/android_dark_voice.png" width="200" alt="Voice panel, dark">
</p>

**Settings**

<p>
<img src="docs/screenshots/android_voice_settings.gif" width="200" alt="Voice settings walkthrough">
<img src="docs/screenshots/android_settings_voice.png" width="200" alt="Voice: model and download">
<img src="docs/screenshots/android_model_manager.png" width="200" alt="Model manager">
<img src="docs/screenshots/android_settings_online_mode.png" width="200" alt="Offline / Online voice input">
<img src="docs/screenshots/android_settings_custom_words.png" width="200" alt="Custom words">
<img src="docs/screenshots/android_settings_ai_cleanup.png" width="200" alt="AI clean-up with your own key">
<img src="docs/screenshots/android_settings_home.png" width="200" alt="Settings home">
</p>

**Gboard hand-off**

<p>
<img src="docs/screenshots/android_voice_menu.png" width="200" alt="Long-press the mic">
<img src="docs/screenshots/android_gboard_after_handoff.png" width="200" alt="Gboard takes over">
<img src="docs/screenshots/android_back_to_humming.png" width="200" alt="Back to Humming">
</p>

### iPhone

<p>
<img src="docs/screenshots/ios_dictate.png" width="200" alt="Dictate: clean text, offline badge">
<img src="docs/screenshots/ios_listening.png" width="200" alt="Listening: one control, live words">
<img src="docs/screenshots/ios_keyboard.png" width="200" alt="Keyboard setup and the Humming keyboard">
<img src="docs/screenshots/ios_settings.png" width="200" alt="Settings: clean mode, accuracy mode, custom words, privacy">
<img src="docs/screenshots/ios_dictate_dark.png" width="200" alt="Dictate, dark">
<img src="docs/screenshots/ios_keyboard_dark.png" width="200" alt="Keyboard, dark">
</p>

### Desktop (Windows, macOS, Linux)

<p>
<img src="docs/screenshots/desktop_home.png" width="420" alt="Desktop home: status, model, try it">
<img src="docs/screenshots/desktop_home_dark.png" width="420" alt="Desktop home, dark">
<img src="docs/screenshots/desktop_history.png" width="420" alt="History: every dictation, searchable">
<img src="docs/screenshots/desktop_dictionary.png" width="420" alt="Dictionary and clean-up preview">
<img src="docs/screenshots/desktop_settings.png" width="420" alt="Settings, with accuracy mode">
<img src="docs/screenshots/desktop_settings_dark.png" width="420" alt="Settings, dark">
</p>

## Features

**Voice**
- Offline dictation on NVIDIA Parakeet, run by sherpa-onnx. Two models:
  - **Fast** (default): Parakeet TDT-CTC 110M int8, a 104 MB download.
  - **Best**: Parakeet TDT 0.6B v2 int8, a 482 MB download. It is offered on phones with 6 GB+ RAM.
- **One control** (1.3): tap the round mic to start, tap it again to stop. Each phrase (cut by Silero VAD) is typed
  into the field as soon as it is recognised, so you see your words land live.
- **No length limit** (1.3): dictate for as long as you like. Audio is recognised phrase by phrase as you talk and memory use stays
  flat, so a one-hour session works like a ten-second one.
- **Keeps going with the screen off** (1.3): dictation runs in a microphone foreground service with a "Humming is
  listening" notification (tap Stop there too), so turning the screen off or switching apps does not cut it.
- **Online mode** (1.3, off by default): switch Voice input from Offline to Online to use Android's own speech
  recogniser (Google, no account needed, more languages). Offline stays the default and is unchanged.
- **AI clean-up with your own key** (1.3, off by default): an optional pass with Gemini, OpenAI or Groq that removes
  fillers, fixes punctuation and structure, and follows a closing spoken command such as "make it formal", "make it
  a list" or "translate to Spanish". Only the finished text is sent (never audio), never in incognito, and the key is
  stored encrypted on the phone (Android Keystore). Any failure keeps the on-device text.
- **Voice commands**: "scratch that" / "delete that" removes the last sentence; "new line" and "new paragraph".
- **Custom words** (Settings > Voice > Custom words): teach spellings and names ("hum ming" → Humming).
- **Accuracy mode** (1.4, off by default; Android, desktop and iOS): stricter on-device clean-up on top of Clean
  mode. It also removes hedge fillers ("like", "basically", "actually", "you know", "I mean") when they are set off by
  commas, extra hesitation sounds and a spoken "period" at the end, and never drops a "like" that carries meaning
  ("I like it" stays). The same rules and tests run on all three platforms. Pair it with the Best model for the
  lowest error rate.
- Clean mode (on by default) removes fillers and stutters. It also applies self-corrections: "ten, no, fifteen
  minutes" becomes "15 minutes", and "scratch that" drops the sentence. Punctuation and capitals come from the model.
- Model manager in Settings > Voice. You can choose Fast or Best, see the size, delete a model and turn on
  **Download on Wi-Fi only**. An **Offline** badge shows on the voice panel once a model is installed.
- Bluetooth headset microphone support.
- Long-press the mic to hand off to **Gboard voice typing** (see below).

**iPhone and iPad** (1.4, `ios/`)
- A SwiftUI app plus the Humming keyboard. Dictation runs on Apple's on-device recogniser with on-device recognition
  required: Humming refuses to run rather than fall back to a server. Punctuation from the recogniser, then the same
  Clean mode, Accuracy mode and custom words as Android.
- One control and no length limit (recognition restarts phrase by phrase). iOS keyboards can't use the microphone,
  so you dictate in the app and the keyboard's red strip inserts the result in one tap.
- Released as an unsigned `.ipa`: sideload it with your own free Apple ID ([docs/IOS_SIDELOAD.md](docs/IOS_SIDELOAD.md)).

**Typing** (built on FlorisBoard)
- **Word prediction that reads like Gboard**: a three-slot strip with the best guess in the middle and what you typed
  in quotes. A 156k-word English dictionary with frequencies, next-word prediction, keyboard-aware autocorrect
  (Off / Mild / Normal / Aggressive), apostrophe fixes (dont → don't) and learning from what you type, on the phone
  only. German, Spanish, French, Italian and Portuguese dictionaries too.
- **On-device neural next-word model** (1.2): a small word-level LSTM (16k words, int8, 2.7 MB) that reads the whole
  sentence so far, run by Humming's own Kotlin code with no native library and no network. Blended with the word
  tables, it lifts held-out next-word top-3 accuracy from 30.2 % to 36.5 % (sentences it never saw;
  measured by a unit test on every build). "Smart prediction strength" in Settings sets the blend or turns it off.
- **Next-word prediction in six languages** (1.2): German, Spanish, French, Italian and Portuguese now suggest the
  next word too (held-out top-3 22-26 %, up from 1-5 %).
- **Stickers & GIFs** (1.2, off until you turn it on): 32 built-in stickers, your own imported images, a search box,
  and optional online GIF search through Openverse (openly licensed GIFs, no account or key) that says exactly what
  it sends and stays off in incognito.
- Emoji in the strip ("pizza" offers 🍕), emoji search with `:heart`, and text shortcuts ("omw" → your phrase).
- Glide (swipe) typing, fed by the same dictionary.
- Number row, long-press symbols, key sound and vibration, one-handed mode, a **floating keyboard** you drag by its
  handle, and a split keyboard in landscape and on tablets.
- Themes: Humming light, dark and AMOLED black, a custom accent colour, and your own **background photo**.
- Clipboard history, on by default: a 2-column grid with timestamps, up to 100 items kept for up to a week.
- Edit shortcuts: long-press a (select all), c (copy), x (cut), v (paste), z (undo) and y (redo).
- Space-bar trackpad: slide to move the cursor left, right, up and down.
- Incognito mode: no learning, no clipboard history and no voice history.
- TalkBack reads every key, and every theme meets WCAG AA contrast.

All of it works at the same time: this is AMOLED + a background photo + floating + incognito + the number row + an
emoji suggestion, in one screenshot.

| | | |
|---|---|---|
| ![All features together](docs/images/m6_all_together.png) | ![Prediction](docs/images/m6_completion.png) | ![Emoji search](docs/images/m6_emoji_search.png) |
| Everything on together | "Sounds gre" → "gre" · **great** · grew | Emoji search `:heart` |
| ![Floating keyboard](docs/images/m6_floating.png) | ![German](docs/images/m6_lang_3.png) | ![Settings search](docs/images/m6_settings_search.png) |
| Floating keyboard | German layout and dictionary | Settings search |
| ![Model manager](docs/screenshots/android_model_manager.png) | ![Clipboard grid](docs/screenshots/android_clipboard.png) | ![Dark voice panel](docs/screenshots/android_dark_voice.png) |
| Settings > Voice: model manager | Clipboard history | Voice panel (dark) |

## Settings

One tree, every option searchable (Settings > Search) and exportable to a file (Settings > Other > Backup, and
Restore to import):

- **Voice**: model (Fast / Best), download on Wi-Fi only, delete, Clean mode, Accuracy mode, Bluetooth mic, voice incognito,
  history retention, personal dictionary.
- **Languages & layouts**: add languages and layouts (QWERTY, QWERTZ, AZERTY and more).
- **Theme**: light / dark / AMOLED, day and night themes, accent colour, background photo and dim, theme editor
  (colours, fonts, key shapes).
- **Keyboard**: height, key spacing, font size, number row, long-press symbols and delay, one-handed, split,
  floating, bottom offset; **Input feedback**: sound and vibration.
- **Smartbar**: toolbar buttons (add, remove, reorder), voice button size.
- **Typing**: suggestions, **Prediction** (autocorrect strength, next word, smart prediction strength, weight of
  your own typing, suggestions shown (3-5), best suggestion in the middle or first, learn from my typing, emoji),
  text shortcuts, auto-capitalisation, double-space period, personal dictionary.
- **Gestures**: glide typing and its trail, swipe gestures, space-bar trackpad.
- **Clipboard**: history and retention. **Emoji**: skin tone, history, emoji suggestions; **Stickers & GIFs**:
  the panel, stickers per row, your own stickers, online GIF search and whether it may run in incognito.
- **Privacy**: incognito, learning, clear learned words, block offensive words, what uses the network.
- **Other**: backup / restore to a file, app theme. **About**: licences.

The full audit (privacy, permissions, security, performance, accessibility, crash risk, licences, tests and a gap
list against Gboard, SwiftKey and FUTO) is in [AUDIT.md](AUDIT.md).

## Benchmarks

### Humming: accuracy and speed (measured)

Measured by CI (`.github/workflows/wer-bench.yml`, run [37204875974](https://github.com/muhib-karim/humming-keyboard/actions/runs/37204875974) on the 1.4.0 code) on
LibriSpeech, the standard read-English benchmark, with the exact models and decoder settings the app ships, on a
2-thread x86-64 CPU (AMD EPYC 9V74 80-Core Processor). Lower is better.

| Model | WER test-clean (200 clips) | WER test-other (100 clips) | Real-time factor | Median time to text per clip | Model load |
|---|---|---|---|---|---|
| **Fast** (default): Parakeet TDT-CTC 110M int8 | **2.78 %** | **4.20 %** | 0.043 (23x faster than real time) | 312 ms clean · 217 ms other | 1.16 s |
| **Best**: Parakeet TDT 0.6B v2 int8 | **1.68 %** | **3.12 %** | 0.177 (6x faster than real time) | 1314 ms clean · 937 ms other | 2.13 s |

The clips average 9.1 s (clean) and 6.3 s (other). test-other is the harder, noisier half of LibriSpeech. The
sample is a fixed subset, so treat the WER as +/- 0.5 points; the full-set figures published for these models are in
the same range. Phones are roughly 2-4x slower than this CPU, still well under real time.

### Against Wispr Flow, Typeless and Gboard Rambler

Only numbers each company publishes itself are listed. Nobody else in this table publishes a figure on a public
benchmark, so there is no like-for-like comparison to make; where nothing is published the cell says so.

| | **Humming Fast** | **Humming Best** | Wispr Flow | Typeless | Gboard Rambler |
|---|---|---|---|---|---|
| WER, LibriSpeech test-clean | **2.78 %** | **1.68 %** | Not published | Not published | Not published |
| WER, LibriSpeech test-other | **4.20 %** | **3.12 %** | Not published | Not published | Not published |
| WER on the vendor's own data | n/a | n/a | Chart only, on 10 hours of Wispr's own dictations; no figures in text [13] | Not published | Not published |
| Time to text | **312 ms** median per clip (measured above) | **1314 ms** | Not published as a measurement; states a 500 ms response goal [14] | Not published | Not published |
| Measured by | this repo's CI, open script, public data | same | vendor | — | — |
| Works offline | Yes | Yes | No [1] | No [2] | Limited [12] |

Run it yourself: Actions > wer-bench > Run workflow (inputs: number of test-clean and test-other clips).

## How Humming compares

Checked on the vendors' own pages on 4 October 2026 (links below the table). Prices are per user per month.
"Gboard Rambler" is Google's new voice input in Gboard.

| | **Humming** | Wispr Flow | Typeless | Gboard Rambler |
|---|---|---|---|---|
| Works fully offline | **Yes**, every platform | No: "Transcription always occurs on the cloud" [1] | No: uses cloud LLM providers [2] | Limited: offline keeps basic clean-up and punctuation only [12] |
| Where your voice goes | **Stays on the device** | Wispr's cloud; training use is a setting [1] | Cloud, "zero data retention" [2] | Google; "never saved, stored, or shared" [12] |
| Price | **Free, open source (Apache-2.0)** | Free tier; Pro $12-15 [5] | Free tier; Pro $12 yearly / $30 monthly [6] | Free with Gboard |
| Free-tier limit | **None** | 2,000 words/week desktop, 1,000/week mobile [5] | "Limited words" [6] | None stated |
| Longest single dictation | **No limit** | 20 min desktop [7], 5 min on Android [8] | 9 min [9] | Not stated |
| Platforms | **Android, iOS, Windows, macOS, Linux** | Mac, Windows, iOS, Android [5] | macOS, Windows, iOS, Android; Linux listed on the pricing page [6] | Pixel 11 phones only [12] |
| Languages | English offline (Parakeet; Apple's on-device English on iOS); Online mode: Android's recogniser languages | 100+ [5][10] | 100+ [6] | A listed set of major languages [12] |
| Clean-up (fillers, punctuation) | **On-device** Clean mode and Accuracy mode; optional AI pass with your own key | Yes (cloud) [11] | Yes (cloud) [6] | Yes; full style rewrites need the internet [12] |
| Custom words | Yes | Yes, dictionary [5] | Yes, personal dictionary [6] | Not stated |
| Spoken edit commands | "scratch that", "new line"; "make it formal", "translate to …" with AI clean-up | Corrections as you speak ("Backtrack") [11] | Translate, "Ask anything" [6] | Edit and rewrite by voice [12] |
| Full keyboard | **Yes** (Android, iOS) | No, dictation app | No, dictation app | Yes (Gboard) |

**Why Humming:** it is the only one of the four that is fully offline on every platform, free with no word caps,
open source, and has no session length limit. The trade-off is language coverage offline (English today) and that
the AI clean-up needs your own API key.

[1] https://wisprflow.ai/data-controls ·
[2] https://www.typeless.com/data-controls ·
[5] https://wisprflow.ai/pricing ·
[6] https://www.typeless.com/pricing ·
[7] https://docs.wisprflow.ai/articles/4841123325-longer-dictation-sessions-now-up-to-20-minutes ·
[8] https://docs.wisprflow.ai/articles/6391241694-use-flow-hands-free ·
[9] https://www.typeless.com/help/troubleshooting/dictation-limit ·
[10] https://docs.wisprflow.ai/articles/6409258247-supported-languages ·
[11] https://wisprflow.ai/features ·
[12] https://support.google.com/pixelphone/answer/17468539 ·
[13] https://wisprflow.ai/canto ·
[14] https://wisprflow.ai/post/our-operating-principles

## Offline by design

- **The voice path has no network code.** Recording, voice detection, recognition, cleanup and history run on the
  phone. A unit test (`NetworkAuditTest`) scans every Kotlin and Java source in the app and its libraries. It fails
  the build if any file other than `ModelDownload.kt` (and, since 1.2, the opt-in `GifSearch.kt`) uses a network API (sockets, HTTP, URL connections, WebView,
  DownloadManager). It also checks that no voice-path file even imports `java.net`.
- **Network use: the one-time model download, and GIF search only if you turn it on.** GIF search (off by default,
  off in incognito unless allowed) sends only the words typed into the sticker panel's search box to Openverse;
  `NetworkAuditTest` allows network APIs in exactly these two files (`ModelDownload.kt`, `GifSearch.kt`).
- **The model download** goes to the sherpa-onnx GitHub release. Each file is
  checked against a pinned SHA-256 and rejected on a mismatch. With **Wi-Fi only** on, the download waits for Wi-Fi.
  After that, dictation needs no connection at all.
- **Proven on an emulator.** The airplane-mode run behind the Android screenshots (Android 15 emulator, 1.3 build):
  1. turns on airplane mode and disables Wi-Fi, then checks that there is no default network and that ping fails;
  2. cold-starts the keyboard and dictates a real spoken clip;
  3. commits "Please send the quarterly report to the whole team by Friday afternoon." into the text field.

  Airplane mode is still on at the end. On every push, `NetworkAuditTest` (a unit test in `keyboard.yml`) keeps the
  network rule above enforced.

![Committed offline, airplane mode on](docs/screenshots/android_offline_committed.png)

On iPhone, dictation uses Apple's recogniser with on-device recognition required, so audio is processed on the
phone and Humming refuses to run rather than fall back to a server. The iOS app has no network code.

There is no cloud speech engine, no account, no analytics and no ads.

## Why Parakeet: the bake-off

Every engine got the same 73 clips: 48 LibriSpeech test-clean and 25 test-other (accents, noise, fast speech).
They were run on a desktop CPU with 2 threads. WER is the word error rate, so lower is better. RTF is decode time
divided by audio length, so lower is faster.

| Engine | WER clean | WER other | WER all | RTF | Download |
|---|---:|---:|---:|---:|---:|
| **Parakeet TDT 0.6B v2 int8 (Humming Best)** | 2.0% | 4.4% | **2.6%** | 0.092 | 482 MB |
| **Parakeet TDT-CTC 110M int8 (Humming Fast)** | 2.6% | 6.6% | **3.7%** | **0.028** | 104 MB |
| FUTO Whisper small.en ACFT q8 (FUTO's biggest) | 3.6% | 10.0% | 5.4% | 0.259 | ~260 MB |
| Whisper base.en q8 (whisper.cpp) | 4.4% | 10.7% | 6.1% | 0.228 | 82 MB |
| FUTO Whisper base.en ACFT q8 | 4.5% | 14.1% | 7.1% | 0.072 | 82 MB |
| FUTO Whisper tiny.en ACFT q8 | 7.9% | 16.3% | 10.2% | 0.032 | 44 MB |

The small Parakeet is more accurate than FUTO's biggest model and about 9x faster. Caveats: this was measured on a
desktop CPU, and phones are roughly 2-4x slower. With 73 clips, gaps under about half a point are noise. FUTO's
models were only benchmarked, never shipped.

## Gboard voice typing hand-off (Rambler)

Gboard's newer voice typing (code-named "Rambler" on Pixel phones) is part of Gboard, which is closed source.
Humming can't embed it, so it offers a switch instead. If Gboard is installed and enabled as a keyboard,
long-pressing Humming's mic shows a menu:

- **Humming**: offline, on this phone.
- **Gboard voice typing**: switches the active keyboard to Gboard through Android's input-method API. If Gboard
  declares a voice subtype, Humming picks that. Otherwise it opens Gboard's keyboard, and Gboard's mic is one tap
  away. Gboard's own keyboard-switch key brings you back to Humming.

| | | |
|---|---|---|
| ![Long-press menu](docs/screenshots/android_voice_menu.png) | ![Gboard active](docs/screenshots/android_gboard_after_handoff.png) | ![Back to Humming](docs/screenshots/android_back_to_humming.png) |
| Long-press the mic | Gboard takes over | Back to Humming |

Honest limits:
- The option is hidden when Gboard isn't enabled. A plain tap on the mic always uses Humming.
- The current Gboard declares no voice subtype, so it takes one extra tap on Gboard's mic.
- Gboard voice typing is Google's service. Whether it runs offline or sends audio to Google is set in Gboard and
  Google's settings, not by Humming.
- Humming can't read what you dictated in Gboard, so that text isn't added to Humming's voice history.
- Android lets you come back with Gboard's switch key or the system keyboard picker. Humming can't pull focus back
  by itself.
- CI checks this flow on a Google Play emulator image with Gboard preinstalled (long-press, menu tap, Gboard
  active, back to Humming).

## Desktop (Windows, macOS and Linux): how to open it

The Windows and macOS builds are **unsigned**: they are not signed with an Apple Developer ID or a Microsoft
code-signing certificate yet, so the operating system warns on first launch. The app is the same either way; this
is a one-time step.

**macOS** (universal `.dmg`, Apple silicon and Intel)
1. Open the `.dmg` and drag Humming to Applications.
2. Open Humming. macOS blocks the first open ("cannot be opened" / "could not verify"). Click Done.
3. Go to **System Settings > Privacy & Security**, scroll down, and click **Open Anyway** next to Humming.
4. Confirm with Open (and your password). From then on it opens normally.

Apple's guide: https://support.apple.com/en-us/102445

**Windows** (`.msi` or the setup `.exe`)
1. Run the installer.
2. SmartScreen shows "Windows protected your PC" with an unknown publisher. Click **More info**.
3. Click **Run anyway** and finish the installer.

**Linux** (`.AppImage` or `.deb`, x86-64; builds are not signed, and Linux shows no warning)
1. AppImage: `chmod +x Humming_*_amd64.AppImage` and run it. Debian / Ubuntu: `sudo apt install ./Humming_*_amd64.deb`,
   then open Humming from your app menu.
2. Pasting into other apps works on X11 sessions and in XWayland apps. On a pure Wayland session auto-paste may not
   land; every dictation is kept in History, one click to copy.

CI builds every installer on every push (`.github/workflows/desktop.yml`, artifacts `humming-desktop-macos-universal`,
`humming-desktop-windows-x64` and `humming-desktop-linux-x64`, each with a `SHA256SUMS.txt`). Signing options are in `docs/RELEASE_SIGNING.md`.

## Build

Requirements: JDK 17 and the Android SDK (platform 36, build-tools 35).

```sh
cd keyboard
./gradlew :app:testDebugUnitTest      # unit tests
./gradlew :app:assembleDebug          # debug APK: app/build/outputs/apk/debug/
./gradlew :app:assembleRelease        # release APK (R8): app/build/outputs/apk/release/
```

The sherpa-onnx Android library (v1.13.8) is downloaded once during the build and checked against a pinned SHA-256.
The speech models aren't part of the APK: the app downloads the one you choose. The release build is signed with
the development key. For a store build, set your own keystore in `keyboard/app/build.gradle.kts`.

CI (`.github/workflows/keyboard.yml`) runs the unit tests plus the debug APK, and the release APK.

**Download:** the APK is attached to the GitHub Release.

**iOS:** `brew install xcodegen && cd ios && xcodegen generate && open Humming.xcodeproj`. The clean-up rules are a
Swift package (`ios/HummingCore`): `cd ios/HummingCore && swift test` runs their tests on macOS or Linux. CI
(`.github/workflows/ios.yml`) runs the tests on Linux and the iOS simulator, takes the screenshots and builds the
unsigned `.ipa`.

## Licence and credits

Humming Keyboard is licensed under the **Apache License 2.0**. See `keyboard/LICENSE`.

- **FlorisBoard** (Apache-2.0): the keyboard base. Copyright (C) 2020-2026 The FlorisBoard Contributors. The
  upstream licence and file headers are kept, and our changes are listed in `keyboard/NOTICE-HUMMING.md`. Humming is
  not affiliated with the FlorisBoard project.
- **NVIDIA Parakeet** TDT-CTC 110M and TDT 0.6B v2: the speech model weights, licensed under
  **CC-BY-4.0** (https://creativecommons.org/licenses/by/4.0/). The ONNX int8 export is by the sherpa-onnx project
  and is used unmodified. The credit is also shown in Settings > Voice.
- **sherpa-onnx** (**Apache-2.0**), by the k2-fsa project: the speech runtime.
- **ONNX Runtime** (MIT), **Silero VAD** (MIT), Apache Commons Compress (Apache-2.0) and the AOSP word lists
  (Apache-2.0).
- Prediction data: **Tatoeba** sentences (CC BY 2.0 FR; only counts and trained weights ship) and **Mozilla Common
  Voice** sentence lists (CC0). The English neural model is Humming's own (Apache-2.0).
- Stickers: rendered from **Noto Emoji** images (Apache-2.0) with captions in the **Bangers** font (OFL-1.1).

No GPL code is included. The full list with each obligation is in `keyboard/LICENSES.md`. See `CHANGELOG.md` for
changes.

The earlier standalone voice app (M1-M3) lives in `android/` and `core/`, and its notes are in `BUILD.md` and
`evidence/`.
