<p align="center"><img src="docs/banner.png" alt="Humming: private voice typing that never leaves your device" width="100%"></p>

<h1 align="center">Humming Keyboard</h1>

<p align="center"><strong>Private voice typing that works offline: a full keyboard for Android, plus iPhone, Windows, macOS and Linux apps.</strong></p>

<p align="center">
<a href="https://github.com/muhib-karim/humming-keyboard/releases/latest"><img alt="Latest release" src="https://img.shields.io/github/v/release/muhib-karim/humming-keyboard?color=e5486b"></a>
<a href="#download-and-install"><img alt="Platforms" src="https://img.shields.io/badge/platforms-Android%20%C2%B7%20iOS%20%C2%B7%20Windows%20%C2%B7%20macOS%20%C2%B7%20Linux-18181f"></a>
<a href="#privacy"><img alt="Dictation: on-device by default" src="https://img.shields.io/badge/dictation-on--device%20by%20default-2a2a33"></a>
<a href="#benchmarks"><img alt="WER 2.78% (Fast model, LibriSpeech test-clean)" src="https://img.shields.io/badge/WER%20test--clean-2.78%25%20Fast%20%C2%B7%201.68%25%20Best-2a2a33"></a>
<a href="LICENSE"><img alt="Licence: Apache-2.0" src="https://img.shields.io/badge/licence-Apache--2.0-2a2a33"></a>
</p>

<p align="center">
<a href="https://github.com/muhib-karim/humming-keyboard/releases/latest"><strong>Download</strong></a> ·
<a href="docs/README.md"><strong>Docs</strong></a> ·
<a href="CHANGELOG.md"><strong>Changelog</strong></a> ·
<a href="https://muhib-karim.github.io/humming-keyboard/privacy.html"><strong>Privacy</strong></a> ·
<a href="https://muhib-karim.github.io/humming-keyboard/"><strong>Website</strong></a>
</p>

Tap the mic and talk: your words appear in the text field as you speak, as an underlined draft that corrects itself
in place, for as long as you speak, even with the screen off. Tap the mic again to stop and the final text is typed
once (or turn off "Live text while speaking" to see nothing until you stop). In the default Offline mode speech
recognition runs on your device, so dictation works in airplane mode and your voice never leaves it. Free, no
account, no analytics, no ads.

Android keyboard built on FlorisBoard (Apache 2.0). Speech recognition by NVIDIA Parakeet on sherpa-onnx.

> **Releases.** The newest build is always on the
> [latest GitHub Release](https://github.com/muhib-karim/humming-keyboard/releases/latest); what changed in each
> version, including 1.6.3, is in [CHANGELOG.md](CHANGELOG.md).

## Contents

- [At a glance](#at-a-glance)
- [Features](#features)
- [Download and install](#download-and-install)
- [Screenshots](#screenshots)
- [Benchmarks](#benchmarks)
- [How Humming compares](#how-humming-compares)
- [Privacy](#privacy)
- [Architecture](#architecture)
- [Tech stack](#tech-stack)
- [Build from source](#build-from-source)
- [Documentation](#documentation)
- [Changelog](#changelog)
- [Contributing and security](#contributing-and-security)
- [Licence and credits](#licence-and-credits)

## At a glance

| | Android | iPhone / iPad | Windows · macOS · Linux |
|---|:---:|:---:|:---:|
| Offline dictation (default mode), audio not sent anywhere | ✅ Parakeet | ✅ Apple on-device | ✅ Parakeet |
| One control, no length limit | ✅ | ✅ | ✅ (hold or toggle shortcut) |
| Clean mode (um, uh, stutters, "3, no, 4pm" → "4pm") | ✅ | ✅ | ✅ |
| Accuracy mode (strict clean-up, new in 1.4) | ✅ | ✅ | ✅ |
| Custom words | ✅ | ✅ | ✅ |
| Voice commands ("new line", "scratch that") | ✅ | ✅ "new line", spoken punctuation | ✅ |
| Full keyboard (prediction, glide, emoji, clipboard) | ✅ | ✅ basic layout | — |
| Keeps going with the screen off | ✅ | — | — |
| Optional AI clean-up with your own key | ✅ | — | — |
| History you can search | ✅ | — | ✅ |
| Self-updates (1.6) | ✅ APK, one-tap confirm | — | ✅ signed (not the .deb) |
| Install | APK / AAB | unsigned `.ipa` (sideload) | `.exe` `.msi` `.dmg` `.AppImage` `.deb` |

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

**Windows, macOS and Linux** (Tauri v2, `desktop/`)
- Hold a global shortcut (Ctrl/Cmd + Shift + Space by default) in any app, talk and let go: the text is pasted
  where your cursor is. Press-to-toggle works too.
- The same Parakeet Fast and Best models on sherpa-onnx 1.13.8 as Android, downloaded on first run with a SHA-256
  check, and a Rust port of the Android clean-up rules (Clean mode, Accuracy mode, verbatim mode).
- Spoken lists become bullets ("first, eggs, second, milk"; can be turned off), voice commands ("new line",
  "period", "question mark"), a personal dictionary, and dictations of up to 15 minutes.
- History on the computer (search, copy, delete), a tray / menu bar app with a listening overlay, an in-app
  "Try it" box, and signed self-updates (1.6). Details: [desktop/README.md](desktop/README.md).

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

### Android settings

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

### Gboard voice typing hand-off (Rambler)

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

## Download and install

Every file is on the [latest GitHub Release](https://github.com/muhib-karim/humming-keyboard/releases/latest), with a
`SHA256SUMS.txt` to check them. The installers and update files are also mirrored on the download site,
https://humming-keyboard.pages.dev/ (Cloudflare Pages).

| Platform | Download | Install |
|---|---|---|
| **Android** 8.0+ | `Humming-<version>.apk` | Open the APK, allow "install unknown apps" for your browser or file manager, install. Then open Humming and follow the two steps: enable the keyboard, then switch to it. The first mic tap downloads the Fast speech model (104 MB, once). |
| **iPhone / iPad** (iOS 16+) | `Humming_1.5.0_unsigned.ipa` (1.6.0 has no iOS build yet) | Sideload with your own free Apple ID through Sideloadly, AltStore or Xcode (re-sign every 7 days). Then Settings > General > Keyboard > Keyboards > Add New Keyboard > Humming. Step by step: [docs/IOS_SIDELOAD.md](docs/IOS_SIDELOAD.md). |
| **Windows** 10/11 x64 | `Humming_1.6.0_x64-setup.exe` (1.6.0 ships the setup .exe only; the last .msi is 1.5.0) | Run the installer. SmartScreen: **More info** > **Run anyway** ([details](#desktop-first-launch-unsigned-builds)). |
| **macOS** 11+ (Apple silicon and Intel) | `Humming_1.5.0_universal.dmg` (1.6.0 has no macOS build yet, see the note below the table) | Drag to Applications, open once, then **System Settings > Privacy & Security > Open Anyway**. Allow Microphone and Accessibility. |
| **Linux** x86-64 | `Humming_1.6.0_amd64.AppImage` or `.deb` | `chmod +x` the AppImage and run it, or `sudo apt install ./Humming_1.6.0_amd64.deb`. |

Versions: Android is at 1.6.3 (see [CHANGELOG.md](CHANGELOG.md)); Windows and Linux
are 1.6.0. macOS and iOS stay on 1.5.0 for now: 1.6.0 was built without GitHub Actions, on Linux build machines
(Windows cross-compiled with cargo-xwin), and Apple's tools only run on a Mac. The macOS and iOS 1.5.0 files are on
the [v1.5.0 release](https://github.com/muhib-karim/humming-keyboard/releases/tag/v1.5.0) and on the download site.

A step-by-step guide for every platform, including settings and troubleshooting, is in
[docs/USER_GUIDE.md](docs/USER_GUIDE.md).

### Desktop first launch (unsigned builds)

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

Signing options are in [docs/RELEASE_SIGNING.md](docs/RELEASE_SIGNING.md).

### Updates (1.6)

| Platform | What happens |
|---|---|
| **Windows, Linux AppImage** (and macOS from its first 1.6 build) | Humming checks for a new version 45 seconds after it starts and every 4 hours, and on **Settings > Updates > Check for updates**. With **Automatically install updates** on (the default) it downloads the update, installs it and restarts itself, never in the middle of a dictation. Off: it shows "Update available" and an **Install now** button. Every update is signed with Humming's update key (minisign, Ed25519); the app refuses a file whose signature does not match the public key it ships with. |
| **Linux .deb** | A .deb install cannot replace itself: Humming shows that a new version is out. Use the AppImage for automatic updates. |
| **Android (APK)** | Humming checks the update feed at most every 6 hours (when the keyboard opens) and on **Settings > Updates > Check for updates**. With automatic download on (the default) it fetches the new APK on Wi-Fi, checks its SHA-256 against `SHA256SUMS.txt` and its signing certificate (`8C:8B:...:1F:C6`, the release key), then hands it to Android's installer. **Android asks you to confirm with one tap**: an app installed outside the Play Store cannot install itself silently, and Humming does not pretend otherwise. On Android 12+, once Humming has installed an update itself, Android may skip the prompt for later ones (Android's decision). The first time, Android asks you to allow Humming to install apps. |
| **Android (Google Play bundle)** | The Play bundle has no self-updater and no install permission; Play installs its updates. |
| **iPhone / iPad** | No automatic updates: an unsigned sideloaded .ipa cannot update itself. Download the new .ipa and sideload it again. |

The update feeds (`latest.json` for desktop, `android.json` for Android) are published with every GitHub Release by
`release.yml` and mirrored, with the update files, to https://humming-keyboard.pages.dev/updates/, where the apps read them
(`AppUpdate.FEED_BASE` on Android, the updater endpoint in `tauri.conf.json` on desktop). `scripts/verify_updater.py` checks every
signature in the feed against the public key in `desktop/src-tauri/tauri.conf.json`. How a release is cut:
[docs/RELEASING.md](docs/RELEASING.md).

## Screenshots

### Walkthroughs

| Android: tap, talk, it types | Android: screen off, still listening | iPhone: talk, clean text | Desktop: try it, talk, get clean text |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/android_dictation.gif" width="190" alt="Android dictation"> | <img src="docs/screenshots/android_screen_off.gif" width="190" alt="Dictation with the screen off"> | <img src="docs/screenshots/ios_walkthrough.gif" width="190" alt="iPhone dictation with accuracy mode"> | <img src="docs/screenshots/desktop_dictation.gif" width="300" alt="Desktop dictation"> |

*Android frames come from a CI emulator run in airplane mode with a spoken clip fed in as the microphone. The iPhone
and desktop recordings come from CI too (the iOS simulator and the desktop interface), playing a sample sentence,
because CI machines have no microphone.*

### Captures

Every image is a real capture from CI on the 1.3/1.4 builds: Android on an emulator (Pixel 6, Android 15), iPhone
on the iOS simulator, desktop from the app's own interface in Chromium. Sample text is made up.

#### Android

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

#### iPhone

<p>
<img src="docs/screenshots/ios_dictate.png" width="200" alt="Dictate: clean text, offline badge">
<img src="docs/screenshots/ios_listening.png" width="200" alt="Listening: one control, live words">
<img src="docs/screenshots/ios_keyboard.png" width="200" alt="Keyboard setup and the Humming keyboard">
<img src="docs/screenshots/ios_settings.png" width="200" alt="Settings: clean mode, accuracy mode, custom words, privacy">
<img src="docs/screenshots/ios_dictate_dark.png" width="200" alt="Dictate, dark">
<img src="docs/screenshots/ios_keyboard_dark.png" width="200" alt="Keyboard, dark">
</p>

#### Desktop (Windows, macOS, Linux)

<p>
<img src="docs/screenshots/desktop_home.png" width="420" alt="Desktop home: status, model, try it">
<img src="docs/screenshots/desktop_home_dark.png" width="420" alt="Desktop home, dark">
<img src="docs/screenshots/desktop_history.png" width="420" alt="History: every dictation, searchable">
<img src="docs/screenshots/desktop_dictionary.png" width="420" alt="Dictionary and clean-up preview">
<img src="docs/screenshots/desktop_settings.png" width="420" alt="Settings, with accuracy mode">
<img src="docs/screenshots/desktop_settings_dark.png" width="420" alt="Settings, dark">
</p>

## Benchmarks

### Humming: accuracy and speed (measured)

Measured by CI (`.github/workflows/wer-bench.yml`, run [37204875974](https://github.com/muhib-karim/humming-keyboard/actions/runs/37204875974) on the 1.4.0 code, 4 October 2026) on
LibriSpeech, the standard read-English benchmark, with the exact models and decoder settings the app ships, on a
2-thread x86-64 CPU (AMD EPYC 9V74 80-Core Processor). Lower is better. The run's own output is committed as
[`docs/bench/ci-37204875974-wer.json`](docs/bench/ci-37204875974-wer.json) and
[`bench.log`](docs/bench/ci-37204875974-bench.log), because Actions artifacts expire and are visible only to
repository members.

| Model | WER test-clean (200 clips) | WER test-other (100 clips) | Real-time factor | Median time to text per clip | Model load |
|---|---|---|---|---|---|
| **Fast** (default): Parakeet TDT-CTC 110M int8 | **2.78 %** | **4.20 %** | 0.043 (23x faster than real time) | 312 ms clean · 217 ms other | 1.16 s |
| **Best**: Parakeet TDT 0.6B v2 int8 | **1.68 %** | **3.12 %** | 0.177 (6x faster than real time) | 1314 ms clean · 937 ms other | 2.13 s |

The clips average 9.1 s (clean) and 6.3 s (other). test-other is the harder, noisier half of LibriSpeech. The
sample is a fixed subset (the first 200 test-clean and first 100 test-other utterances by ID, out of 2,620 and 2,939),
so treat the WER as +/- 0.5 points. These are desktop-CPU figures: speed on a phone has not been benchmarked in this
repository yet, so no phone latency is claimed here.

**Re-run on 8 October 2026 (outside CI, Fast model).** Same script, same model file (SHA-256 pinned), same first 200
test-clean utterances, fetched with [`keyboard/ci/fetch_librispeech_sample.py`](keyboard/ci/fetch_librispeech_sample.py).
Every reference and hypothesis is in [`docs/bench/`](docs/bench/) so each error can be checked by hand:

| Model | WER test-clean (200 clips) | Real-time factor | Median time to text per clip | Model load | Machine |
|---|---|---|---|---|---|
| **Fast** (default) | **2.72 %** (CI run above: 2.78 %) | 0.081 | 581 ms | 1.49 s | shared 4-CPU Intel Xeon @ 2.60GHz cloud box with other jobs running, 2 threads |

Files: [`local-20261008-fast-test-clean-200.json`](docs/bench/local-20261008-fast-test-clean-200.json) (summary) and
[`local-20261008-fast-test-clean-200-per-utterance.jsonl`](docs/bench/local-20261008-fast-test-clean-200-per-utterance.jsonl)
(200 lines: reference, hypothesis, audio length, latency; 74 of the 200 clips have at least one word wrong). The WER
matches the CI run within the stated noise; speed is about half the CI runner's because the box was shared. The Best
model and test-other were not re-run.

### Against Wispr Flow, Typeless and Gboard Rambler

Only numbers each company publishes itself are listed. Nobody else in this table publishes a figure on a public
benchmark, so there is no like-for-like comparison to make; where nothing is published the cell says so.

| | **Humming Fast** | **Humming Best** | Wispr Flow | Typeless | Gboard Rambler |
|---|---|---|---|---|---|
| WER, LibriSpeech test-clean | **2.78 %** | **1.68 %** | Not published | Not published | Not published |
| WER, LibriSpeech test-other | **4.20 %** | **3.12 %** | Not published | Not published | Not published |
| WER on the vendor's own data | n/a | n/a | Chart only, on 10 hours of Wispr's own dictations; no figures in text [13] | Not published | Not published |
| Time to text | **312 ms** median per clip (measured above) | **1314 ms** | Not published as a measurement; states a 500 ms response goal [14] | Not published | Not published |
| Measured by | this repo's CI, committed script, public data | same | vendor | — | — |
| Works offline | Yes | Yes | No [1] | No [2] | Limited [12] |

Run it yourself: Actions > wer-bench > Run workflow (inputs: number of test-clean and test-other clips), or locally:
`python keyboard/ci/fetch_librispeech_sample.py bench test-clean 200` then
`python keyboard/ci/wer_bench.py <models_dir> bench/LibriSpeech wer.json 200 0` (needs `sherpa-onnx==1.13.8 jiwer soundfile`).


### Why Parakeet: the bake-off

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

The small Parakeet is more accurate than FUTO's biggest model and about 9x faster on these clips. Caveats: this was
a one-off development run on a desktop CPU; its script and raw outputs are not in this repository, so treat the table
as indicative and use the CI table above (script and output committed) for claims. With 73 clips, gaps under about
half a point are noise. FUTO's models were only benchmarked, never shipped.

## How Humming compares

Checked on the vendors' own pages on 4 October 2026 (links below the table). Prices are per user per month.
"Gboard Rambler" is Google's new voice input in Gboard.

| | **Humming** | Wispr Flow | Typeless | Gboard Rambler |
|---|---|---|---|---|
| Works fully offline | **Yes**, every platform | No: "Transcription always occurs on the cloud" [1] | No: uses cloud LLM providers [2] | Limited: offline keeps basic clean-up and punctuation only [12] |
| Where your voice goes | **Stays on the device** (default Offline mode; opt-in Online mode uses the phone's speech service) | Wispr's cloud; training use is a setting [1] | Cloud, "zero data retention" [2] | Google; "never saved, stored, or shared" [12] |
| Price | **Free and open source** (Apache-2.0) | Free tier; Pro $12-15 [5] | Free tier; Pro $12 yearly / $30 monthly [6] | Free with Gboard |
| Free-tier limit | **None** | 2,000 words/week desktop, 1,000/week mobile [5] | "Limited words" [6] | None stated |
| Longest single dictation | **No limit** | 20 min desktop [7], 5 min on Android [8] | 9 min [9] | Not stated |
| Platforms | **Android, iOS, Windows, macOS, Linux** | Mac, Windows, iOS, Android [5] | macOS, Windows, iOS, Android; Linux listed on the pricing page [6] | Pixel 11 phones only [12] |
| Languages | English offline (Parakeet; Apple's on-device English on iOS); Online mode: Android's recogniser languages | 100+ [5][10] | 100+ [6] | A listed set of major languages [12] |
| Clean-up (fillers, punctuation) | **On-device** Clean mode and Accuracy mode; optional AI pass with your own key | Yes (cloud) [11] | Yes (cloud) [6] | Yes; full style rewrites need the internet [12] |
| Custom words | Yes | Yes, dictionary [5] | Yes, personal dictionary [6] | Not stated |
| Spoken edit commands | "scratch that", "new line"; "make it formal", "translate to …" with AI clean-up | Corrections as you speak ("Backtrack") [11] | Translate, "Ask anything" [6] | Edit and rewrite by voice [12] |
| Full keyboard | **Yes** on Android; basic layout on iOS | Not checked | Calls its mobile app a "voice keyboard" [15]; typing features not checked | Yes (Gboard) |

**Why Humming:** of the four in this table it is the only one that is fully offline on every platform, free with no
word caps, and has no session length limit. It is not the only offline option overall (see below). The trade-offs:
offline language coverage (English today), and the AI clean-up needs your own API key.

**Other offline options** (vendors' own pages, checked 8 October 2026):
- **FUTO Keyboard** (Android): a full keyboard advertised as "100% Offline, 100% Private" with offline voice input
  [16]. This is Humming's closest competitor on Android; on accuracy, the bake-off above measured FUTO's Whisper models
  against Parakeet on the same clips.
- **Handy** (Windows, macOS, Linux): "a free, open source, and extensible speech-to-text application that works
  completely offline", MIT licence, Whisper or Parakeet models [17]. Desktop only, no keyboard.
- **Superwhisper** (Mac, Windows, iOS, Android): states it "works offline"; local and cloud models, paid Pro tier [18].

Humming's angle against these: one dependable offline full keyboard on Android plus the same engine on desktop, with
the benchmark script and raw outputs in this repository.

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
[14] https://wisprflow.ai/post/our-operating-principles ·
[15] https://www.typeless.com/ ·
[16] https://keyboard.futo.org/ ·
[17] https://github.com/cjpais/Handy ·
[18] https://superwhisper.com/

## Privacy

Humming has no cloud speech engine of its own (Online mode uses the phone's), no account, no analytics and no ads.
The full policy is on the website: [Privacy policy](https://muhib-karim.github.io/humming-keyboard/privacy.html)
(source: [play/PRIVACY_POLICY.md](play/PRIVACY_POLICY.md)).

- **The offline voice path has no network code.** Recording, voice detection, recognition, cleanup and history run on
  the phone. A unit test (`NetworkAuditTest`) scans every Kotlin and Java source in the app and its libraries. It fails
  the build if any file other than these four uses a network API (sockets, HTTP, URL connections, WebView,
  DownloadManager): `ModelDownload.kt`, `GifSearch.kt`, `LlmCleanup.kt` and `AppUpdate.kt`. It also checks that no
  voice-path file even imports `java.net`.
- **What does use the network, all of it listed:**
  1. The one-time voice-model download (below).
  2. The update check of the sideloaded APK (`AppUpdate.kt`, since 1.6): **on by default**. It fetches the release
     feed from humming-keyboard.pages.dev and, on unmetered networks, may download the new APK. It sends no audio and
     no typed text. Switch it off in Settings > Updates.
  3. GIF search (off by default, off in incognito unless allowed): sends only the words typed into the sticker
     panel's search box to Openverse.
  4. AI clean-up (off by default, needs your own API key): sends the **dictated text** (not audio) to the provider you
     pick (Google Gemini, OpenAI or Groq).
  5. Online mode (off by default): hands your **audio** to the phone's own speech service (on most phones Google's).
     Humming's own code does not send it anywhere, but it leaves the phone.
- **The model download** goes to the sherpa-onnx GitHub release. Each file is
  checked against a pinned SHA-256 and rejected on a mismatch. With **Wi-Fi only** on, the download waits for Wi-Fi.
  After that, dictation needs no connection at all.
- **Proven on an emulator.** The airplane-mode run behind the Android screenshots (Android 15 emulator, 1.3 build):
  1. turns on airplane mode and disables Wi-Fi, then checks that there is no default network and that ping fails;
  2. cold-starts the keyboard and dictates a real spoken clip;
  3. commits "Please send the quarterly report to the whole team by Friday afternoon." into the text field.

  Airplane mode is still on at the end. `NetworkAuditTest` runs with the unit tests (`keyboard.yml`) and keeps the
  network rule above enforced.

![Committed offline, airplane mode on](docs/screenshots/android_offline_committed.png)

On iPhone, dictation uses Apple's recogniser with on-device recognition required, so audio is processed on the
phone and Humming refuses to run rather than fall back to a server. The iOS app has no network code.

## Architecture

One repository, three apps that share the same speech models and the same clean-up rules:

| Directory | What it is |
|---|---|
| [`keyboard/`](keyboard/) | The Android keyboard: a fork of FlorisBoard v0.5.2 (Kotlin, Jetpack Compose). Humming's voice engine lives in `com.humming.keyboard`; changes to upstream are listed in [`keyboard/NOTICE-HUMMING.md`](keyboard/NOTICE-HUMMING.md). |
| [`desktop/`](desktop/) | The Windows, macOS and Linux app (Tauri v2: Rust back end, TypeScript UI). |
| [`ios/`](ios/) | The iPhone and iPad app and keyboard extension (SwiftUI), with the clean-up rules as a Swift package. |
| [`core/`](core/) | A shared Rust crate (VAD, audio helpers, clean-up; optional whisper.cpp from the first prototype). The desktop app uses it without whisper.cpp. |
| [`play/`](play/) | The website source (`play/site/`, published to GitHub Pages), the privacy policy and Google Play listing material. |
| [`landing/`](landing/) | An App Links landing page (not published yet) and the site icon. |
| [`scripts/`](scripts/) | Release assembly and update-signature verification (Python). |
| [`docs/`](docs/) | Documentation, screenshots, benchmark outputs and audits. |

```mermaid
flowchart LR
    Mic[Microphone<br/>16 kHz mono] --> VAD[Silero VAD<br/>cut at pauses]
    VAD --> ASR[Parakeet on sherpa-onnx<br/>on-device]
    ASR --> Clean[Clean-up rules<br/>Clean / Accuracy mode,<br/>custom words, voice commands]
    Clean --> Draft[Underlined draft<br/>while speaking]
    Draft --> Commit[Final text<br/>committed once on stop]
```

The full picture (every module, the dictation pipeline step by step, the update channel and CI) is in
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Tech stack

| Area | Technology |
|---|---|
| Android | Kotlin, Jetpack Compose, FlorisBoard v0.5.2 base, sherpa-onnx 1.13.8 (ONNX Runtime), Silero VAD v4, min SDK 26, target SDK 36 |
| Speech models | NVIDIA Parakeet TDT-CTC 110M int8 (Fast) and Parakeet TDT 0.6B v2 int8 (Best), sherpa-onnx ONNX exports |
| Prediction | Dictionaries and next-word tables for six languages, plus an on-device int8 word-level LSTM run by Humming's own Kotlin code |
| Desktop | Tauri v2, Rust (sherpa-onnx, cpal, arboard, enigo, tauri-plugin-updater with minisign), TypeScript + Vite |
| iOS | SwiftUI, Apple Speech with on-device recognition required, XcodeGen, Swift package `HummingCore` |
| Shared core | Rust crate `humming-core` (optional whisper.cpp through whisper-rs, UniFFI bindings) |
| Release and CI | GitHub Actions workflows, Python release scripts, GitHub Pages and Cloudflare Pages for the website and update feeds |

## Build from source

Short version below; requirements and every platform in detail are in the per-app READMEs and
[docs/RELEASING.md](docs/RELEASING.md).

**Android** (JDK 17 and the Android SDK, platform 36, build-tools 35):

```sh
cd keyboard
./gradlew :app:testDebugUnitTest      # unit tests
./gradlew :app:assembleDebug          # debug APK: app/build/outputs/apk/debug/
./gradlew :app:assembleRelease        # release APK (R8): app/build/outputs/apk/release/
```

The sherpa-onnx Android library (v1.13.8) is downloaded once during the build and checked against a pinned SHA-256.
The speech models aren't part of the APK: the app downloads the one you choose. Without the release-signing
environment variables the release build is signed with the development key
([docs/RELEASE_SIGNING.md](docs/RELEASE_SIGNING.md)).

**Desktop** (Node 20+, Rust stable, the platform toolchain):

```sh
cd desktop
npm ci
npm run tauri dev       # run it
npm run tauri build     # installer for this machine
```

**iOS:** `brew install xcodegen && cd ios && xcodegen generate && open Humming.xcodeproj`. The clean-up rules are a
Swift package (`ios/HummingCore`): `cd ios/HummingCore && swift test` runs their tests on macOS or Linux.

**CI** (`.github/workflows/`): `keyboard.yml` (Android unit tests, debug and release APK), `android-release.yml`
(signed APK and AAB), `desktop.yml` (installers for every desktop platform), `ios.yml` (tests, simulator screenshots,
unsigned `.ipa`), `screens.yml` (desktop screenshots), `wer-bench.yml` (the benchmark above), `release.yml` (a tagged
release end to end) and `pages.yml` (the website and update mirror). See the repository status note at the top.

## Documentation

| Document | Contents |
|---|---|
| [docs/README.md](docs/README.md) | Index of all documentation |
| [docs/USER_GUIDE.md](docs/USER_GUIDE.md) | Install, enable the keyboard, voice typing, settings, updates, troubleshooting |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Modules, the dictation pipeline, the update channel, CI |
| [docs/RELEASING.md](docs/RELEASING.md) | Versioning, signing, release assets, update feeds, tagging |
| [docs/IOS_SIDELOAD.md](docs/IOS_SIDELOAD.md) | Installing the unsigned iOS build |
| [AUDIT.md](AUDIT.md), [docs/AUDIT-1.6.2.md](docs/AUDIT-1.6.2.md) | Full audit (1.2.0) and production audit (1.6.2) |

## Changelog

Every release is described in [CHANGELOG.md](CHANGELOG.md); `release.yml` takes the GitHub release notes for a tag from it (`scripts/assemble_release.py`).

## Contributing and security

- [CONTRIBUTING.md](CONTRIBUTING.md): how to report bugs, propose features and open pull requests.
- [SECURITY.md](SECURITY.md): report vulnerabilities privately through GitHub Security Advisories.
- [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md): Contributor Covenant 2.1.

## Licence and credits

Humming is licensed under the **Apache License 2.0**: see [`LICENSE`](LICENSE) and [`NOTICE`](NOTICE) at the
repository root. This covers the whole repository: the Android keyboard (`keyboard/`, which also keeps the
FlorisBoard copy in [`keyboard/LICENSE`](keyboard/LICENSE)), the desktop app (`desktop/`), the shared core (`core/`)
and the iOS app (`ios/`). Android keyboard built on FlorisBoard (Apache 2.0).

Third-party components:

- **FlorisBoard** (Apache-2.0): the keyboard base. Copyright (C) 2020-2026 The FlorisBoard Contributors. The
  upstream licence and file headers are kept, and our changes are listed in
  [`keyboard/NOTICE-HUMMING.md`](keyboard/NOTICE-HUMMING.md). Humming is not affiliated with the FlorisBoard project.
- **NVIDIA Parakeet** TDT-CTC 110M and TDT 0.6B v2: the speech model weights, licensed under
  **CC-BY-4.0** (https://creativecommons.org/licenses/by/4.0/). The ONNX int8 export is by the sherpa-onnx project
  and is used unmodified.
- **sherpa-onnx** (**Apache-2.0**), by the k2-fsa project: the speech runtime.
- **ONNX Runtime** (MIT), **Silero VAD** (MIT), Apache Commons Compress (Apache-2.0) and the AOSP word lists
  (Apache-2.0).
- Prediction data: **Tatoeba** sentences (CC BY 2.0 FR; only counts and trained weights ship) and **Mozilla Common
  Voice** sentence lists (CC0). The English neural model is Humming's own (Apache-2.0).
- Stickers: rendered from **Noto Emoji** images (Apache-2.0) with captions in the **Bangers** font (OFL-1.1).

No GPL code is included. The full list with each obligation is in [`keyboard/LICENSES.md`](keyboard/LICENSES.md).
