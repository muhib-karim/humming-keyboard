# Google Play Data safety form: draft answers

> **ARCHIVED (1.2.0, 3 Oct 2026) - not current, not ready for submission.** The 1.2.0 draft at the end of this file
> says only GIF search collects data and that audio and keystrokes stay on the device. That is no longer true: 1.6.3
> also has Online voice mode, AI clean-up and the APK update check. Use the 1.6.3 draft below instead.

## Current draft: 1.6.3 (draft, not submitted)

App: Humming Keyboard (`com.humming.keyboard`) 1.6.3 (versionCode 1603, `keyboard/gradle.properties`), Play bundle
built with `-Phumming.play=true`. Written 14 Oct 2026 to match `play/PRIVACY_POLICY.md` (effective 14 October 2026)
and checked against the code in `keyboard/app/src/main/kotlin`. **Draft, not submitted:** no answer here has been
entered in the Play Console, and none is certified against Google's current Data safety guidance; the owner decides
the final answers.

### Data paths in the code

| # | Path | Default | What leaves the device, and to whom | Code |
|---|---|---|---|---|
| 1 | Offline voice | **on** (default engine) | Nothing. Audio is recorded and transcribed on the device. | `HummingVoice.kt` (`Prefs.engine` defaults to `ENGINE_OFFLINE`, `Prefs.kt`); `NetworkAuditTest` checks the voice files have no network code |
| 2 | Online voice mode | off | **Audio**, handed to the phone's own speech service (Android `SpeechRecognizer`, on most phones Google's), which makes its own connection under its own policy. Humming's code sends it nowhere else. **Also in incognito:** incognito only skips Humming's voice history. | `OnlineSession.kt`; `HummingVoice.kt` (`onlineMode` does not check incognito; history is skipped when `incognito(app)`) |
| 3 | AI clean-up (user's own API key) | off | The **dictated text, never audio**, over HTTPS to the provider the user picks: Google Gemini (`generativelanguage.googleapis.com`), OpenAI (`api.openai.com`) or Groq (`api.groq.com`), under that provider's terms. Never in incognito, never without a key, texts over 12,000 characters are not sent. The key is encrypted on the device with an Android Keystore key and is not in device backups. | `LlmCleanup.kt` (`allowed`, `post` requires `https://`); `SecretStore.kt` |
| 4 | GIF search | off | The **words typed into the sticker search box** (first 80 characters), plus the IP address, over HTTPS to Openverse (`api.openverse.org`, WordPress Foundation); the previews and the chosen GIF are then fetched from the image hosts in Openverse's results, which see the IP address. Not in incognito unless allowed separately. | `GifSearch.kt` (`allowed`, `queryUrl`); `AppPrefs.kt` (`humming__gif_search_enabled`, `humming__gif_search_in_incognito`, both off) |
| 5 | Speech model download | when the user picks a model | No user data: a fixed public file from `github.com/k2-fsa/sherpa-onnx` releases, SHA-256 pinned. GitHub sees the IP address. Can be limited to Wi-Fi. | `ModelDownload.kt`, `VoiceModels.kt` |
| 6 | APK update check | **not in the Play build** | None in the Play build: `BuildConfig.SELF_UPDATE = false` disables `AppUpdate.kt`, and `src/play/AndroidManifest.xml` removes `REQUEST_INSTALL_PACKAGES`. (The sideloaded APK reads `humming-keyboard.pages.dev/updates/android.json` and sends no personal data.) | `AppUpdate.kt` (`enabled`), `app/build.gradle.kts` (`hummingPlay`) |
| 7 | Android device backup | only if the user has turned on Android backup | Android itself may copy the keyboard settings datastore, the `ime` files and the personal dictionary to the user's Google account. Google handles it; the developer cannot see it. The AI key (`SecretStore`) is not included. | `res/xml/backup_rules.xml`, `res/xml-v31/backup_rules.xml`, `AndroidManifest.xml` (`allowBackup="true"`) |

Only `ModelDownload.kt`, `GifSearch.kt`, `LlmCleanup.kt` and `AppUpdate.kt` may use a network API (`NetworkAuditTest`).
Online voice mode (path 2) uses no network API in Humming's code: the system speech service connects.

**Collected by the developer: nothing.** There is no Humming server, account, analytics, advertising or
crash-reporting SDK. The crash screen shows the log on the device only and never sends it.

**Retention.** The developer keeps nothing. On the device: voice history (the text of the last 500 dictations, no audio since 1.7.1; off in voice incognito
and keyboard incognito, `History.kt`), clipboard
history (100 items by default, 10 to 500 or unlimited; off in incognito; age-based auto-clean is optional and off by default, `AppPrefs.kt`)
and learned words stay until the user deletes them or uninstalls the app, with two exceptions: voice history keeps only
the newest 500 dictations, and clipboard items are also removed when the size limit pushes out the oldest, or by
auto-clean when the user turns it on. Audio that builds before 1.7.1 kept with voice history is deleted when the app
starts. Data sent under paths 2 to 4 is kept by the speech service, the AI provider or Openverse under their own
policies; Humming cannot see or delete it.

**Deletion.** Uninstalling the app, or clearing its storage in Android's app settings, deletes everything Humming
stored on the device. Data held by the phone's speech service, Google Gemini, OpenAI, Groq or Openverse is deleted
through those providers' own policies and tools. Android backup copies are managed in the user's Google account.

### Data collection and security (draft answers)

| Question | Draft answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | **Yes** | Paths 2, 3 and 4 send user data off the device when the user turns them on. |
| Is all of the user data collected by your app encrypted in transit? | **Unresolved: owner to decide, do not submit as is** | Paths 3 and 4: `LlmCleanup.kt` and `GifSearch.kt` refuse anything but `https://`. Path 2 (Online voice audio) is the phone's speech service's own connection; Humming's code cannot show how that service transmits audio, so encryption in transit for path 2 is not established here. |
| Do you provide a way for users to request that their data is deleted? | Owner to choose; suggested: data is not stored by the developer | The developer holds no user data. Third-party copies follow the providers' policies (see Deletion). |

### Data types (draft answers)

Conservative answers: every optional off-device path is declared. The "Shared" column treats the transfer to a provider
the user picked as sharing; if the owner applies Google's user-initiated-transfer exemption, it would be "No".
"Ephemeral" is **No** because retention is decided by the third party, not by Humming.

| Category > type | Collected | Shared | Ephemeral | Required or optional | Purpose | Path |
|---|---|---|---|---|---|---|
| Audio > Voice or sound recordings | Yes | Yes (phone's speech service) | No | Optional (Online voice mode, off by default) | App functionality | 2 |
| App activity > Other user-generated content (dictated text) | Yes | Yes (Google Gemini, OpenAI or Groq) | No | Optional (AI clean-up, off by default, user's own key) | App functionality | 3 |
| App activity > In-app search history (GIF search words) | Yes | Yes (Openverse) | No | Optional (GIF search, off by default) | App functionality | 4 |

Not collected: typing and keystrokes (path 1 and the keyboard itself stay on the device), audio in Offline mode,
personal info, location, contacts, files, device IDs, app info and performance (crash logs), and anything from the
update check (not in the Play build). The model download (path 5) sends no user data. Android device backup (path 7)
is Android's own backup to the user's account; whether it needs declaring is for the owner to confirm against Google's
guidance.

### Open points before submission

- The services in paths 2 to 5 see the device's IP address as part of any connection. The policy says so for
  Openverse, GitHub and Cloudflare; whether IP address must be declared as a data type is for the owner to decide.
- GIF search also downloads the preview and the chosen GIF from the image URLs in Openverse's results
  (`StickerPanel.kt`, `GifSearch.download`), so the site hosting that image also sees the IP address. The privacy
  policy now says so (1.6.3).
- Clipboard history: the age-based auto-clean (`clipboard__history_auto_clean_old_enabled`) is off by default, 1 day
  when turned on, a week at most (`AppPrefs.kt:125-131`). The privacy policy and README now say this (1.6.3).
- Privacy policy URL: https://muhib-karim.github.io/humming-keyboard/privacy.html (whether it is live was not checked
  here).

## Archived 1.2.0 draft

App: Humming Keyboard (`com.humming.keyboard`) 1.2.0 (versionCode 1200). Drafted 3 Oct 2026 from the code on main 80e1deff. Checked against the code, not assumed:
- Network use: only `ModelDownload.kt` (speech model from github.com/k2-fsa/sherpa-onnx releases, sha256-pinned) `GifSearch.kt` (Openverse, opt-in, off by default, off in incognito), `LlmCleanup.kt` (opt-in, user's own key) and `AppUpdate.kt` (update check of the sideloaded APK only; compiled out of the Play bundle with `-Phumming.play=true`, which also drops REQUEST_INSTALL_PACKAGES). `NetworkAuditTest` fails the unit-test run if any other source file uses a network API.
- No analytics, ads, crash-reporting or account SDKs in the Gradle dependencies (no Firebase, Play services, Sentry, ACRA and so on).
- Voice path (`HummingVoice`, `LocalEngine`, `Transcriber`, `WavRecorder`, ...) has no network code, also enforced by `NetworkAuditTest`.

### Data collection and security

| Question | Answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | **Yes** (see note) | Only because of the optional GIF search, which sends the typed search words to Openverse. If GIF search is removed or you decide it falls under the exemptions, answer **No**. |
| Is all of the user data collected by your app encrypted in transit? | **Yes** | GifSearch enforces `https://` (`require(url.startsWith("https://"))`). |
| Do you provide a way for users to request that their data is deleted? | **No** (nothing is stored off the device) | Choose the option saying data isn't stored, or "No". Users clear on-device data in Settings or by uninstalling. |

### Data types (only if you answered Yes above)

| Category > type | Collected | Shared | Ephemeral | Required or optional | Purpose |
|---|---|---|---|---|---|
| App activity > In-app search history (GIF search words) | Yes | No* | **Yes** | **Optional** (off by default) | App functionality |

\*Sharing exemption: the transfer is started by the user (they type into the GIF search box after turning the feature on) and the settings screen says what is sent where.

Everything else: **not collected**.
- Audio (voice recordings): processed only on the device, so not collected.
- Messages and keystrokes ("Other user-generated content"): processed only on the device.
- Personal info, location, contacts, files, device IDs, app info and performance (crash logs): not collected.

### Notes

- Android Auto Backup (`allowBackup=true`, rules in `res/xml/backup_rules.xml`: settings datastore, `ime` files, user dictionary) is Android's own backup to the user's Google account. Google's guidance does not count it as developer collection.
- Speech model download: the app sends no user data; the request only fetches a public file.
- Privacy policy URL: https://muhib-karim.github.io/humming-keyboard/privacy.html
