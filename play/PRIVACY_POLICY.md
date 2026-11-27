# Humming Keyboard: Privacy Policy

Effective 27 November 2026. Applies to the Humming Keyboard Android app (package `com.humming.keyboard`), the Humming desktop app, and the Humming iOS app.

## The short version

Humming works on your device. By default, what you type, the words it learns, your clipboard, your meetings, your usage statistics, and your voice stay on your device. There is no account, no advertising, no analytics and no tracking. We (the developer) receive none of your data. Optional features, all off by default, only connect when you configure them: Online voice mode on Android sends audio to the phone's own speech service; AI clean-up / Command Mode / Meeting Synthesis sends only text (never audio) to local Ollama on `127.0.0.1` or to the provider you pick (OpenAI, Anthropic, Google Gemini or Groq) using your own API key; the optional desktop HTTP & MCP server binds strictly to `127.0.0.1` with a Bearer token; and GIF search sends the words typed in its search box to Openverse. The update check of the APK and desktop versions, on by default, sends no personal data. One more exception: if you have turned on Android's own device backup, Android may copy Humming's settings and personal dictionary to your Google account. Details are under “When the app uses the internet” and “Other apps and Android features” below.

## What stays on your device

- **Typing.** Keystrokes are processed on the device to show suggestions and corrections. They are not sent anywhere.
- **Voice.** In the default Offline voice mode, audio is recorded and transcribed on the device by a speech model stored on your phone or computer, and neither audio nor transcripts are uploaded. Two optional features change this when you turn them on: Online voice mode on Android sends audio to the phone's speech service, and BYOK AI clean-up sends finished text (plus, when enabled on desktop, focused app name / window title) to local Ollama (`127.0.0.1`) or the provider you pick. Voice history, usage stats (words, WPM, median latency), and Meeting Notepad sessions (`meetings.json`) stay on your device, and history can be cleared or turned off.
- **Local HTTP API & MCP server (desktop, off by default).** When turned on in Settings, Humming listens strictly on loopback (`127.0.0.1`) and requires an auto-generated Bearer token for `/v1/transcribe`, `/v1/cleanup`, `/v1/meetings`, `/v1/history`, and `POST /mcp`. Nothing listens on public interfaces.
- **Learned words and settings.** The words Humming learns from your typing, your shortcuts, themes and other settings are stored in the app's private storage (see “Android backup” below for the one way Android itself may copy some of them).
- **Clipboard history.** 100 items by default (you can set 10 to 500, or no limit), on the phone only. Automatic removal of old items is off by default; if you turn it on, items are removed after the time you choose (1 day by default, a week at most). It is off in incognito mode.
- **Incognito mode.** When an app asks for incognito, or you turn it on yourself, Humming does not learn words and keeps no clipboard or voice history. AI clean-up never runs in incognito, and GIF search does not run unless you allow it separately. Incognito does not switch off Online voice mode: if you have chosen Online, your audio still goes to the phone's speech service.

## When the app uses the internet

The app uses the internet only for the things below. A unit test in the project's source code (`NetworkAuditTest`) scans the app's source for network calls and fails if one appears outside these features; it is a check on the code as written, not a firewall while the app runs. The project's automated CI workflows (`ci.yml` and `keyboard.yml`) run this test on Android pull requests and pushes to `main`.

1. **Speech model download.** When you choose a voice model, the app downloads it once from GitHub (github.com, the public sherpa-onnx release page). The download sends no personal data. As with any download, GitHub sees your IP address. You can limit this download to Wi-Fi.
2. **GIF search (optional, off by default).** If you turn on GIF search in Settings > Stickers & GIFs, the words you type into the sticker search box are sent to Openverse (openverse.org, run by the WordPress Foundation) to find openly licensed GIFs. Openverse sees the search words and your IP address; see openverse.org/privacy. The GIF previews and the GIF you pick are then downloaded from the websites that host them (the image addresses in Openverse's results), so those websites also see your IP address. GIF search never runs in incognito unless you allow it separately. Nothing else you type is sent.
3. **Online voice mode (optional, off by default).** If you switch Voice input from Offline to Online, your **audio** is handed to the phone's own speech service (on most phones Google's), which processes it under its own privacy policy. Humming's own code does not send it anywhere else, but in this mode your voice leaves the phone. Incognito does not change this: in incognito, Online mode still uses the phone's speech service, and Humming only skips its own voice history. Offline stays the default.
4. **AI clean-up, Command Mode & Meeting Synthesis (optional, off by default).** Local Ollama runs completely offline on `127.0.0.1` with no external upload. If you add your own API key and pick a remote provider (OpenAI, Anthropic, Google Gemini or Groq), only text (dictated words, selected text in Command Mode, or meeting notes/transcript during Synthesis, plus optional focused app name / window title on desktop — never raw audio) is sent over HTTPS to that provider under its terms. On Android it never runs in incognito, the key is stored encrypted on the phone, and any failure keeps the on-device text.
5. **App updates (the APK from GitHub or the Humming download site, and the desktop app).** Android APK: at most every 6 hours, when the keyboard opens, the app reads a small file (android.json) from the Humming download site (muhib-karim.github.io/humming-keyboard, hosted by GitHub Pages; versions 1.6 to 1.8.0 use humming-keyboard.pages.dev, hosted by Cloudflare Pages) to see whether a newer version exists. If there is one, it downloads the new APK through the same site (the file itself is served from GitHub), checks its SHA-256 checksum and signing certificate, and asks Android to install it (Android asks you to confirm). Settings > Updates turns automatic checks and downloads off. The Google Play version does not do this: Play installs its updates. Desktop app: 45 seconds after it starts, every 4 hours after that, and when you press Check for updates, it reads latest.json from the same site; it installs only files signed with Humming's update key. Settings > Updates > Automatically install updates turns automatic installation off, but the check itself still runs. A Linux .deb install only tells you that a new version is out. The update requests send no personal data; GitHub sees your IP address, and so does Cloudflare for versions 1.6 to 1.8.0.

## Other apps and Android features

- **Gboard voice typing hand-off.** If you long-press the mic and choose Gboard voice typing, Android switches to Google's keyboard. Google's privacy policy covers that.
- **Android backup.** If you have turned on Android's device backup, Android may include Humming's settings and personal dictionary in your backup to your Google account. Google handles that backup; we cannot see it. Humming's own Backup & restore feature writes a file that you choose where to save.
- **Crash screen.** If the app crashes, it can show the error log on screen. It is never sent automatically. You can copy it yourself if you want to report a problem.

## Permissions

- **Microphone:** voice typing in Offline and Online mode, only while you are dictating.
- **Foreground service and wake lock:** keep dictation running while the screen is off, with a “Humming is listening” notification.
- **Internet / network state:** the model download, optional GIF search, optional AI clean-up and the update check described above. Online voice mode does not go through Humming's network code: the phone's speech service makes its own connection.
- **Install apps (APK version only):** to install Humming updates. Android asks you to confirm. Not requested by the Google Play version.
- **Notifications:** to let you know if the keyboard crashed. The log stays on the device.
- **Vibrate:** key-press feedback.
- **Audio settings:** routing dictation to a Bluetooth headset microphone.

## Children

Humming is a general-purpose keyboard and is not directed at children. It collects no personal data from anyone.

## Changes

If this policy changes, the new version will be posted at this address with a new effective date.

## Contact

- Privacy requests and anything you do not want to post publicly: report it privately through GitHub's private vulnerability reporting (the repository's Security tab > “Report a vulnerability”), https://github.com/muhib-karim/humming-keyboard/security/advisories/new. Only you and the maintainers can see it.
- Security reports: the same private route.
- Everything else (questions, bug reports): GitHub issues, https://github.com/muhib-karim/humming-keyboard/issues. Issues are public, so please do not put personal data in them.
