# Firebase and app keys: prep only

## Where things stand

Humming ships with no Firebase and no network calls besides the one-time model download (on Android
`NetworkAuditTest` enforces this; the desktop app makes no other request either). That is a selling point
("works fully offline, nothing leaves the device"), so anything below is opt-in and should stay off the voice path.

## What Firebase would be for, and what the owner supplies

| Use | Needed from the owner | Repository change when supplied |
|---|---|---|
| Crash reports (Crashlytics), opt-in in Settings | a Firebase project; an Android app registered for `com.humming.keyboard`; the downloaded `google-services.json` | add the `com.google.gms.google-services` and Crashlytics Gradle plugins and keep `google-services.json` out of git (CI writes it from a secret `GOOGLE_SERVICES_JSON_BASE64`) |
| Tester builds (App Distribution) | the same project + tester emails | a CI step with a service-account JSON secret `FIREBASE_SA_JSON` |
| Analytics | a decision first: it conflicts with the privacy promise; recommended: no | none |
| App Links / deep links | a domain he controls (Firebase Dynamic Links is shut down, so links use the domain directly) and the release key's SHA-256 | fill in `landing/.well-known/assetlinks.json` and publish `landing/` on that domain |

Also needed for Play and for any Google API key: the SHA-1 and SHA-256 of the upload key and of the Play app
signing key (Play Console > Setup > App integrity). Register both in the Firebase project.

Secrets never go into the repository: `.gitignore` already excludes `google-services.json`,
`GoogleService-Info.plist` and key files. `google-services.json` is not secret in the cryptographic sense, but it
ties builds to the owner's project, so it is supplied by CI like the keys.

## Desktop

Firebase has no desktop SDK worth using here. Crash reports for the desktop app, if wanted, would be an opt-in
minidump upload (for example Sentry, with a DSN the owner creates); nothing is prepared for it yet.
