# Google Play Data safety form: draft answers

App: Humming Keyboard (`com.humming.keyboard`) 1.2.0 (versionCode 1200). Drafted 3 Oct 2026 from the code on main 80e1deff. Checked against the code, not assumed:
- Network use: only `ModelDownload.kt` (speech model from github.com/k2-fsa/sherpa-onnx releases, sha256-pinned) and `GifSearch.kt` (Openverse, opt-in, off by default, off in incognito). `NetworkAuditTest` fails the build if any other source file uses a network API.
- No analytics, ads, crash-reporting or account SDKs in the Gradle dependencies (no Firebase, Play services, Sentry, ACRA and so on).
- Voice path (`HummingVoice`, `LocalEngine`, `Transcriber`, `WavRecorder`, ...) has no network code, also enforced by `NetworkAuditTest`.

## Data collection and security

| Question | Answer | Why |
|---|---|---|
| Does your app collect or share any of the required user data types? | **Yes** (see note) | Only because of the optional GIF search, which sends the typed search words to Openverse. If GIF search is removed or you decide it falls under the exemptions, answer **No**. |
| Is all of the user data collected by your app encrypted in transit? | **Yes** | GifSearch enforces `https://` (`require(url.startsWith("https://"))`). |
| Do you provide a way for users to request that their data is deleted? | **No** (nothing is stored off the device) | Choose the option saying data isn't stored, or "No". Users clear on-device data in Settings or by uninstalling. |

## Data types (only if you answered Yes above)

| Category > type | Collected | Shared | Ephemeral | Required or optional | Purpose |
|---|---|---|---|---|---|
| App activity > In-app search history (GIF search words) | Yes | No* | **Yes** | **Optional** (off by default) | App functionality |

\*Sharing exemption: the transfer is started by the user (they type into the GIF search box after turning the feature on) and the settings screen says what is sent where.

Everything else: **not collected**.
- Audio (voice recordings): processed only on the device, so not collected.
- Messages and keystrokes ("Other user-generated content"): processed only on the device.
- Personal info, location, contacts, files, device IDs, app info and performance (crash logs): not collected.

## Notes

- Android Auto Backup (`allowBackup=true`, rules in `res/xml/backup_rules.xml`: settings datastore, `ime` files, user dictionary) is Android's own backup to the user's Google account. Google's guidance does not count it as developer collection.
- Speech model download: the app sends no user data; the request only fetches a public file.
- Privacy policy URL: https://muhib-karim.github.io/humming-keyboard/privacy.html
