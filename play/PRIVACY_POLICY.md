# Humming Keyboard: Privacy Policy

Effective 3 October 2026. Applies to the Humming Keyboard Android app (package `com.humming.keyboard`) and the Humming desktop app.

## The short version

Humming works on your device. By default, what you type, the words it learns, your clipboard and your voice stay on your phone. There is no account, no advertising, no analytics and no tracking. We (the developer) receive none of your data. Two optional features, both off by default, do send data to a service you choose: Online voice mode sends your audio to the phone's own speech service (on most phones Google's), and AI clean-up sends the dictated text (never audio) to Google Gemini, OpenAI or Groq using your own key. Details are under “When the app uses the internet” below.

## What stays on your device

- **Typing.** Keystrokes are processed on the device to show suggestions and corrections. They are not sent anywhere.
- **Voice.** When you use the microphone, audio is recorded and transcribed on the device by a speech model stored on the phone. Audio and transcripts are not uploaded. Voice history is kept on the phone, and you can turn it off.
- **Learned words and settings.** The words Humming learns from your typing, your shortcuts, themes and other settings are stored in the app's private storage.
- **Clipboard history.** Up to 100 items, kept for up to a week, on the phone only. It is off in incognito mode.
- **Incognito mode.** When an app asks for incognito, or you turn it on yourself, Humming does not learn words and keeps no clipboard or voice history.

## When the app uses the internet

The app uses the internet only for the things below. An automated test on every build checks that no other part of the app can.

1. **Speech model download.** When you choose a voice model, the app downloads it once from GitHub (github.com, the public sherpa-onnx release page). The download sends no personal data. As with any download, GitHub sees your IP address. You can limit this download to Wi-Fi.
2. **GIF search (optional, off by default).** If you turn on GIF search in Settings > Stickers & GIFs, the words you type into the sticker search box are sent to Openverse (openverse.org, run by the WordPress Foundation) to find openly licensed GIFs. Openverse sees the search words and your IP address; see openverse.org/privacy. GIF search never runs in incognito unless you allow it separately. Nothing else you type is sent.
2. **Online voice mode (optional, off by default).** If you switch Voice input from Offline to Online, your **audio** is handed to the phone's own speech service (on most phones Google's), under its own privacy policy. Humming's own code does not send it anywhere else, but in this mode your voice leaves the phone. Offline stays the default.
2. **AI clean-up with your own key (optional, off by default).** If you add your own API key and turn this on, the **dictated text** (never audio) is sent to the provider you pick (Google Gemini, OpenAI or Groq), under that provider's terms. It never runs in incognito, the key is stored encrypted on the phone, and any failure keeps the on-device text.
3. **App updates (the APK from GitHub or the Humming download site; can be turned off).** About every 6 hours the app reads a small file (android.json) from the Humming download site (humming-keyboard.pages.dev, hosted by Cloudflare Pages) to see whether a newer version exists. If there is one, it downloads the new APK through the same site (the file itself is served from GitHub), checks its SHA-256 checksum and signing certificate, and asks Android to install it (Android asks you to confirm). The request sends no personal data; Cloudflare and GitHub see your IP address. Settings > Updates turns automatic checks and downloads off. The Google Play version does not do this: Play installs its updates. The desktop app checks for updates the same way and installs only files signed with Humming's update key.

## Other apps and Android features

- **Gboard voice typing hand-off.** If you long-press the mic and choose Gboard voice typing, Android switches to Google's keyboard. Google's privacy policy covers that.
- **Android backup.** If you have turned on Android's device backup, Android may include Humming's settings and personal dictionary in your backup to your Google account. Google handles that backup; we cannot see it. Humming's own Backup & restore feature writes a file that you choose where to save.
- **Crash screen.** If the app crashes, it can show the error log on screen. It is never sent automatically. You can copy it yourself if you want to report a problem.

## Permissions

- **Microphone:** voice typing, only while you are dictating.
- **Internet / network state:** the model download, optional GIF search and the update check described above.
- **Install apps (APK version only):** to install Humming updates. Android asks you to confirm. Not requested by the Google Play version.
- **Notifications:** to let you know if the keyboard crashed. The log stays on the device.
- **Vibrate:** key-press feedback.
- **Audio settings:** routing dictation to a Bluetooth headset microphone.

## Children

Humming is a general-purpose keyboard and is not directed at children. It collects no personal data from anyone.

## Changes

If this policy changes, the new version will be posted at this address with a new effective date.

## Contact

- Questions or privacy requests: GitHub issues, https://github.com/muhib-karim/humming-keyboard/issues
- Security reports: https://github.com/muhib-karim/humming-keyboard/security/advisories/new
