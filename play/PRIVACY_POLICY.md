# Humming Keyboard: Privacy Policy

Effective 14 October 2026. Applies to the Humming Keyboard Android app (package `com.humming.keyboard`) and the Humming desktop app.

## The short version

Humming works on your device. By default, what you type, the words it learns, your clipboard and your voice stay on your phone. There is no account, no advertising, no analytics and no tracking. We (the developer) receive none of your data. Three optional features, all off by default, send data to a service you choose: Online voice mode sends your audio to the phone's own speech service (on most phones Google's), also in incognito; AI clean-up sends the dictated text (never audio) to Google Gemini, OpenAI or Groq using your own key; and GIF search sends the words you type in its search box to Openverse. The update check of the APK and desktop versions, on by default, sends no personal data. One more exception: if you have turned on Android's own device backup, Android may copy Humming's settings and personal dictionary to your Google account. Details are under “When the app uses the internet” and “Other apps and Android features” below.

## What stays on your device

- **Typing.** Keystrokes are processed on the device to show suggestions and corrections. They are not sent anywhere.
- **Voice.** In the default Offline voice mode, audio is recorded and transcribed on the device by a speech model stored on the phone, and neither audio nor transcripts are uploaded. Two optional features change this when you turn them on: Online voice mode sends the audio to the phone's speech service, and AI clean-up sends the finished text to the provider you pick (both described below). Voice history is kept on the phone, and you can turn it off.
- **Learned words and settings.** The words Humming learns from your typing, your shortcuts, themes and other settings are stored in the app's private storage (see “Android backup” below for the one way Android itself may copy some of them).
- **Clipboard history.** Up to 100 items (the default limit), on the phone only. Automatic removal of old items is off by default; if you turn it on, items are removed after the time you choose (1 day by default, a week at most). It is off in incognito mode.
- **Incognito mode.** When an app asks for incognito, or you turn it on yourself, Humming does not learn words and keeps no clipboard or voice history. AI clean-up never runs in incognito, and GIF search does not run unless you allow it separately. Incognito does not switch off Online voice mode: if you have chosen Online, your audio still goes to the phone's speech service.

## When the app uses the internet

The app uses the internet only for the things below. A unit test in the project's source code (`NetworkAuditTest`) checks that no other part of the app's code can. The project's automated builds ran this test on every change to the Android app until 5 October 2026; automated builds have been paused since then, so the test does not run automatically until they resume.

1. **Speech model download.** When you choose a voice model, the app downloads it once from GitHub (github.com, the public sherpa-onnx release page). The download sends no personal data. As with any download, GitHub sees your IP address. You can limit this download to Wi-Fi.
2. **GIF search (optional, off by default).** If you turn on GIF search in Settings > Stickers & GIFs, the words you type into the sticker search box are sent to Openverse (openverse.org, run by the WordPress Foundation) to find openly licensed GIFs. Openverse sees the search words and your IP address; see openverse.org/privacy. The GIF previews and the GIF you pick are then downloaded from the websites that host them (the image addresses in Openverse's results), so those websites also see your IP address. GIF search never runs in incognito unless you allow it separately. Nothing else you type is sent.
3. **Online voice mode (optional, off by default).** If you switch Voice input from Offline to Online, your **audio** is handed to the phone's own speech service (on most phones Google's), which processes it under its own privacy policy. Humming's own code does not send it anywhere else, but in this mode your voice leaves the phone. Incognito does not change this: in incognito, Online mode still uses the phone's speech service, and Humming only skips its own voice history. Offline stays the default.
4. **AI clean-up with your own key (optional, off by default).** If you add your own API key and turn this on, the **dictated text** (never audio) is sent to the provider you pick (Google Gemini, OpenAI or Groq) to tidy it up, under that provider's terms. It never runs in incognito, the key is stored encrypted on the phone, and any failure keeps the on-device text.
5. **App updates (the APK from GitHub or the Humming download site, and the desktop app).** Android APK: at most every 6 hours, when the keyboard opens, the app reads a small file (android.json) from the Humming download site (humming-keyboard.pages.dev, hosted by Cloudflare Pages) to see whether a newer version exists. If there is one, it downloads the new APK through the same site (the file itself is served from GitHub), checks its SHA-256 checksum and signing certificate, and asks Android to install it (Android asks you to confirm). Settings > Updates turns automatic checks and downloads off. The Google Play version does not do this: Play installs its updates. Desktop app: 45 seconds after it starts, every 4 hours after that, and when you press Check for updates, it reads latest.json from the same site; it installs only files signed with Humming's update key. Settings > Updates > Automatically install updates turns automatic installation off, but the check itself still runs. A Linux .deb install only tells you that a new version is out. The update requests send no personal data; Cloudflare and GitHub see your IP address.

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
