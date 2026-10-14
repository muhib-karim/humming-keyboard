# Security policy

## Supported versions

Security fixes go into the latest release of each platform. Older versions are not patched. The sideloaded Android
APK and the Windows, Linux AppImage and macOS desktop apps update themselves (see the README's
[Updates](README.md#updates-16) section), with these exceptions: a Linux `.deb` install only says that a new version is
out, a macOS install older than 1.6 needs the first 1.6 build installed by hand, the Google Play version is updated
by Play, and the iOS build is updated by sideloading the new `.ipa`.

| Platform | Supported |
|---|---|
| Android | Latest release |
| Windows, macOS, Linux | Latest release |
| iPhone / iPad | Latest release |

## Reporting a vulnerability

Please **do not** open a public issue, pull request or discussion for a security problem.

Report it privately through GitHub Security Advisories for this repository:

1. Go to the repository's **Security** tab.
2. Choose **Report a vulnerability** (or open
   https://github.com/muhib-karim/humming-keyboard/security/advisories/new).
3. Describe the problem, the affected platform and version, the steps to reproduce it, and its impact.

You can expect an acknowledgement through the advisory. Once a fix is released, the advisory can be published with
credit to you if you want it.

**Privacy requests** (anything about your data that you do not want to post in a public issue) use the same private
route. Everything else goes in a public issue.

## Scope

Areas where a report is especially valuable:

- anything that sends audio, typed text, dictated text, clipboard contents or learned words off the device when the
  user has not turned on a feature that says so (Online mode, GIF search, AI clean-up);
- network access from any Android source file other than the four allowed by `NetworkAuditTest`
  (`ModelDownload.kt`, `GifSearch.kt`, `LlmCleanup.kt`, `AppUpdate.kt`);
- the update channel: a way to make the Android updater install an APK that is not signed with the release
  certificate or does not match `SHA256SUMS.txt`, or to make the desktop updater install a file without a valid
  minisign signature;
- the speech model download: a way to make the app use a model file that does not match its pinned SHA-256;
- storage of the user's AI clean-up API key (kept encrypted with the Android Keystore);
- incognito mode keeping history, learned words or clipboard items it should not keep.

Out of scope: the warnings shown on first launch of the unsigned Windows and macOS builds (they are documented),
the 7-day expiry of iOS builds signed with a free Apple ID, and the privacy of third-party services the user opts
into (Google's speech service in Online mode, Openverse, Gemini, OpenAI, Groq, Gboard).

## Handling secrets

Release signing keys and the desktop update key are held by the owner. The CI workflows read them from repository
secrets ([docs/RELEASE_SIGNING.md](docs/RELEASE_SIGNING.md)); releases since 1.6.0 were built and signed outside GitHub
Actions with the same keys, because Actions has not run on this repository since 5 October 2026. If you find a key, password or token committed
anywhere in this repository or its history, report it privately as above.
