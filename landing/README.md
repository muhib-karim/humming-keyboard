# Humming landing page (app links)

A static page: `index.html` (no build step, no trackers) plus `.well-known/assetlinks.json` for Android App Links.

Status: no domain exists yet, so `<domain>` below is a placeholder and nothing here is published.

`.well-known/assetlinks.json` already carries the SHA-256 of the Humming upload certificate
(`8C:8B:68:BB:E6:3A:A4:2A:85:61:59:AA:A5:B0:C9:0C:D9:54:EE:F7:8A:98:0D:39:BC:B6:69:41:75:3E:1F:C6`), the same value the `android-release` CI workflow checks with
`apksigner verify --print-certs` on every signed build.

To go live, the owner supplies:
1. A domain (or subdomain) he controls, served over HTTPS; it replaces `<domain>` everywhere below.
2. If the app is distributed through Google Play with Play App Signing, add the Play app signing key's SHA-256
   (Play Console > Setup > App integrity) as a second entry in `sha256_cert_fingerprints`; installs from Play are
   signed with that key, not the upload key. The file must be served as `application/json` at
   `https://<domain>/.well-known/assetlinks.json` with no redirect.
3. The download links: the Play listing (or the signed APK release), and the Windows and macOS installers. Fill
   `LINKS` at the bottom of `index.html`.

Then, in the keyboard's manifest, an `https://<domain>/open` intent filter with `android:autoVerify="true"` makes
links under `/open/` open the app directly; anyone without the app lands on this page.

Hosting: any static host works (GitHub Pages, Cloudflare Pages, Netlify). Copy `desktop/src-tauri/icons/icon.png`
next to `index.html` as `icon.png` when publishing.

The desktop builds are unsigned; the page's "How to open it" section (and the repository README) tells Mac and
Windows users how to get past the first-run warning.
