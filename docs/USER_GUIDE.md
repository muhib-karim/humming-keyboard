# Humming user guide

How to install Humming, turn on the keyboard, dictate, adjust the settings, keep it up to date, and fix common
problems. Everything here applies to the current release; version-specific notes say which version they refer to.

- [1. Install](#1-install)
- [2. Turn on the keyboard (Android)](#2-turn-on-the-keyboard-android)
- [3. Voice typing](#3-voice-typing)
- [4. Settings](#4-settings)
- [5. Updates](#5-updates)
- [6. Troubleshooting](#6-troubleshooting)

## 1. Install

All installers are attached to the [latest GitHub Release](https://github.com/muhib-karim/humming-keyboard/releases/latest)
together with `SHA256SUMS.txt`, and mirrored on https://humming-keyboard.pages.dev/. Which file belongs to which
platform, and which version each platform is on, is listed in the README under
[Download and install](../README.md#download-and-install).

**Checking a download.** Compare the file's SHA-256 with its line in `SHA256SUMS.txt`:
`sha256sum <file>` (Linux), `shasum -a 256 <file>` (macOS) or `certutil -hashfile <file> SHA256` (Windows).

### Android (8.0 or later)

1. Download `Humming-<version>.apk` on the phone.
2. Open it. Android asks you to allow "install unknown apps" for your browser or file manager; allow it and install.
3. Open Humming and follow the setup (next section).

The voice engine runs on 64-bit ARM phones (`arm64-v8a`) and on x86_64 emulators and Chromebooks.

### iPhone and iPad (iOS 16 or later)

Humming for iOS is an unsigned `.ipa` that you sign with your own free Apple ID through Sideloadly, AltStore or
Xcode. Full steps, the 7-day re-signing limit and how to turn on the keyboard are in
[IOS_SIDELOAD.md](IOS_SIDELOAD.md). For offline dictation the on-device English model must be installed:
**Settings > General > Keyboard > Dictation Languages > English**.

### Windows, macOS and Linux

| System | Install | First launch |
|---|---|---|
| Windows 10/11 x64 | Run the setup `.exe` (or `.msi`) | SmartScreen: **More info** > **Run anyway**. Allow desktop apps to use the microphone. |
| macOS 11+ | Open the `.dmg`, drag Humming to Applications | Open once, then **System Settings > Privacy & Security > Open Anyway**. Allow the microphone, and allow Humming under **Privacy & Security > Accessibility** so it can paste. |
| Linux x86-64 | `chmod +x Humming_*_amd64.AppImage` and run it, or `sudo apt install ./Humming_*_amd64.deb` | No warning. |

The Windows and macOS builds are not signed with a paid certificate, which is why the system warns once. See
[Desktop first launch](../README.md#desktop-first-launch-unsigned-builds) in the README for details.

## 2. Turn on the keyboard (Android)

Open the Humming app. The setup walks you through:

1. **Enable Humming**: tap **Open System Settings** and enable "Humming" in Android's *Language & Input* settings.
   Android requires every custom keyboard to be enabled separately.
2. **Select Humming**: tap **Switch Keyboard** and pick Humming in the input selector.
3. On Android 13 and later, Humming asks for permission to show crash-report notifications. This is optional.

Then tap **Start Customizing** to open Settings. You can switch keyboards at any time with the keyboard-switch key
or Android's keyboard picker.

The first time you tap the mic, Humming asks for the microphone permission (a keyboard cannot show permission
dialogs itself, so a small screen opens for it) and then downloads the Fast speech model, 104 MB, once.

## 3. Voice typing

### Android

1. Tap the mic in the keyboard's toolbar. The voice panel opens.
2. Talk. With **Live text while speaking** on (the default), your words appear in the text field as an underlined
   draft that is corrected in place as Humming hears more.
3. Tap the mic again to stop. The final, cleaned-up text is typed once. **Cancel** leaves the field exactly as it was.

There is no length limit. While you dictate, a "Humming is listening" notification keeps the microphone running
even if you turn the screen off or switch apps; you can tap **Stop** there too.

**Voice commands** (always on-device):
- "scratch that", "delete that" or "undo that", said as its own sentence, removes the sentence before it.
- "new line" and "new paragraph".

**Clean mode** (on by default) removes fillers ("um", "uh") and stutters and applies self-corrections: "ten, no,
fifteen minutes" becomes "15 minutes". **Accuracy mode** (off by default) is stricter: it also removes hedge
fillers such as "like", "basically" or "you know" when they are set off by commas, and never removes a "like" that
carries meaning ("I like it" stays).

**Gboard hand-off.** If Gboard is installed and enabled, long-pressing the mic offers **Gboard voice typing**, which
switches to Gboard. Gboard's keyboard-switch key brings you back. A plain tap on the mic always uses Humming.

### iPhone and iPad

iOS does not let keyboards use the microphone, so you dictate in the Humming app: tap the mic, talk, tap again to
stop. The text appears in the red strip at the top of the Humming keyboard, and one tap inserts it wherever you are
typing. If a sideloaded build cannot share text between the app and the keyboard, use **Copy & use in keyboard** and
the keyboard's paste button (needs **Allow Full Access**).

### Windows, macOS and Linux

Hold the shortcut (**Ctrl+Shift+Space**, or **Cmd+Shift+Space** on macOS) in any app, talk, and let go: the text is
pasted where your cursor is. In Settings you can switch to **Press to start, press again to stop**, record a
different shortcut, or choose **Type it out key by key** instead of pasting. The **Try it here** box on the home
screen transcribes without pasting anywhere, and **History** keeps every dictation on your computer, with search,
copy and delete.

## 4. Settings

### Android: Settings > Voice

| Setting | What it does |
|---|---|
| Voice model | **Fast** (Parakeet TDT-CTC 110M, 104 MB, default) or **Best** (Parakeet TDT 0.6B v2, 482 MB, offered on phones with 6 GB+ RAM). Shows the size; download or delete a model. |
| Download on Wi-Fi only | The model download waits for Wi-Fi. |
| Live text while speaking | On (default): an underlined draft while you talk. Off: nothing appears until you stop. |
| Clean up speech | Clean mode, described above. On by default. |
| Accuracy mode | Stricter clean-up on top of Clean mode. Off by default. |
| Bluetooth headset mic | Records from a connected Bluetooth headset. |
| Voice incognito | Keeps no voice history. The keyboard's incognito mode turns this on too. |
| Speech engine | **Offline (recommended)**: runs on the phone and works in airplane mode. **Online**: uses the phone's own speech service (usually Google), which sends audio off the phone. |
| Custom words | Names, jargon and brands Humming should always spell your way, one per line. |
| AI clean-up (optional, your own key) | After you stop, the finished text (never audio) is sent to Gemini, OpenAI or Groq with your own API key. Off by default, never in incognito; the key is stored encrypted on the phone. |

### Android: other settings

Every option is searchable (**Settings > Search**) and can be exported to a file (**Settings > Other > Backup**, and
**Restore** to import). The main groups are Languages & layouts, Theme, Keyboard, Smartbar, Typing (including
**Prediction**), Gestures, Clipboard, Emoji and Stickers & GIFs, Privacy, Updates, Other and About. The README's
[Android settings](../README.md#android-settings) section lists what each group contains.

### Desktop settings

Shortcut (with a recorder), hold-to-talk or press-to-toggle, speech model (Fast or Best), clean-up mode, language,
microphone, paste method, listening overlay, start at login, and **Updates**. The **Dictionary** tab holds fixed
spellings and phrase replacements, and has a clean-up tester.

## 5. Updates

| Platform | How to update |
|---|---|
| Android (APK) | **Settings > Updates**. "Check for updates automatically" and "Download and install updates automatically" are on by default; Humming checks at most every 6 hours and downloads on Wi-Fi. It verifies the APK's SHA-256 and signing certificate, then Android asks you to confirm with one tap. The first time, allow Humming to install apps. **Check for updates** checks now. |
| Android (Google Play) | Play installs updates. |
| Windows, Linux AppImage, macOS (from its first 1.6 build) | **Settings > Updates**. With **Automatically install updates** on (default), Humming installs signed updates and restarts, never in the middle of a dictation. Off: **Install now** appears when an update is available. |
| Linux .deb | Humming tells you a new version is out; install the new `.deb` yourself, or use the AppImage for automatic updates. |
| iPhone / iPad | No automatic updates. Download the new `.ipa` and sideload it again. |

Moving from 1.5.0 to 1.6.0 needs one manual install, because 1.5.0 has no updater.

## 6. Troubleshooting

| Problem | What to do |
|---|---|
| "Voice isn't available on this device" (Android) | The on-device engine is built for 64-bit ARM (`arm64-v8a`) and x86_64 only; this phone's CPU is not one of them. Since 1.6.3 Humming says so instead of typing placeholder text. |
| The speech model does not download | With **Download on Wi-Fi only** on, the download waits for Wi-Fi. Check the connection, or turn the option off in Settings > Voice. |
| The mic does nothing / no permission prompt | Allow the microphone for Humming in Android's app settings, then tap the mic again. |
| The Gboard option is missing on long-press | It only appears when Gboard is installed and enabled as a keyboard. |
| An update is downloaded but not installed (Android) | Android needs you to allow Humming to install apps and to confirm the update with one tap. |
| The keyboard crashed | The crash screen shows the error log; it is never sent automatically. You can copy it to include in a bug report. |
| macOS says Humming "cannot be opened" or is from an unidentified developer | **System Settings > Privacy & Security > Open Anyway** (one time). |
| macOS: the text is not pasted | Allow Humming under **System Settings > Privacy & Security > Accessibility**. |
| Windows: "Windows protected your PC" | **More info** > **Run anyway** (one time). |
| Linux: text does not paste on Wayland | Auto-paste works on X11 and in XWayland apps. On a pure Wayland session, copy the dictation from History. |
| iPhone: Humming stopped opening | Apps signed with a free Apple ID expire after 7 days. Re-sign it with Sideloadly, AltStore or Xcode; your settings are kept. |
| iPhone: dictation refuses to start | Install the on-device English model: **Settings > General > Keyboard > Dictation Languages > English**. Humming does not fall back to Apple's servers. |

Still stuck? See [CONTRIBUTING.md](../CONTRIBUTING.md) for how to report a bug.
