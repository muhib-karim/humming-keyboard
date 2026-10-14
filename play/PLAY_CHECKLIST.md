# Google Play release checklist: Humming Keyboard

Everything in `play/` is ready. Only the steps marked **MK** need you; they're the ones that need your identity or a payment.

## Ready in this folder

| Item | Where |
|---|---|
| Signed release AAB (upload this) | pro CI run `android-release` 37085217795 on main 80e1deff, artifact `humming-keyboard-signed-release` (kept 90 days). `humming-keyboard-release.aab`, sha256 `20acb236799474841b78c8937c01878f48b95f55b2bfeadebae680f6346150da` |
| Upload key certificate | SHA-256 `8C:8B:68:BB:E6:3A:A4:2A:85:61:59:AA:A5:B0:C9:0C:D9:54:EE:F7:8A:98:0D:39:BC:B6:69:41:75:3E:1F:C6`, SHA-1 `1F:14:D0:45:DE:1A:CA:5D:91:81:49:1D:7E:9C:24:F5:37:D3:48:59` (CN=Humming, RSA 4096) |
| App | `com.humming.keyboard`, version 1.2.0 (versionCode 1200), minSdk 26, targetSdk 36 |
| Store listing text | `listing/en-US/title.txt`, `short_description.txt` (78/80 chars), `full_description.txt` |
| App icon 512x512 | `graphics/icon-512.png` |
| Feature graphic 1024x500 | `graphics/feature-graphic-1024x500.png` |
| Phone screenshots (8, 1080x1920) | `graphics/screenshots/phone-01.png` to `phone-08.png`, framed from real emulator captures |
| Privacy policy | https://muhib-karim.github.io/humming-keyboard/privacy.html (source: `site/privacy.html`, `PRIVACY_POLICY.md`) |
| Data safety answers | `DATA_SAFETY.md` |
| Content rating + other declarations | `CONTENT_RATING.md` |

## Steps

1. **MK: create the developer account** at play.google.com/console with **your Google account email**. Choose a **Personal** account.
2. **MK: pay the one-time US$25 fee** and finish **identity verification** (government ID; Google may also ask to verify a phone number and, for personal accounts, an Android device through the Play Console app). Approval can take a few days.
3. Create the app: name "Humming Keyboard", default language English (United States), App, Free.
4. Set up **Play App Signing** (the default). Upload `humming-keyboard-release.aab`; Play keeps the app signing key and our key becomes the **upload key**. Check the upload certificate shown under Setup > App integrity matches the SHA-256 above.
5. Fill in **Store listing** from `listing/` and `graphics/`. Category: Tools (or Productivity). Contact email: a public developer contact address. Privacy policy: https://muhib-karim.github.io/humming-keyboard/privacy.html
6. Fill in **App content**: Privacy policy, Ads (none), App access (no login), Content rating (`CONTENT_RATING.md`), Target audience (13+ or 18+), Data safety (`DATA_SAFETY.md`).
7. **Closed testing**: create a closed track, upload the AAB, add a testers email list with **at least 12 people** (more is safer, since people drop out), and share the opt-in link. Testers must stay opted in for **14 days in a row**.
8. After 14 days, **apply for production access** (Dashboard > "Apply for production"). Google asks a few questions about the test; review usually takes up to 7 days.
9. Release to production: create a release on the Production track with the same AAB (or a newer versionCode) and roll out.

## Before each new upload

- Bump `projectVersionCode` in `keyboard/gradle.properties`. Play refuses a versionCode it has already seen.
- Pro CI builds and signs the AAB on every push to main that touches `keyboard/`. Take it from the run's artifact.
- The privacy page redeploys automatically when `play/site/` changes (workflow `pages`).

## Things to know

- Humming's voice models download from GitHub at first use (104 MB or 482 MB), so the AAB stays small and Play's size limits are fine.
- Keyboards get extra review attention. The listing and privacy policy say exactly what leaves the device (the model download and opt-in GIF search) and nothing else. Keep them accurate if that ever changes.
