# Humming on iPhone and iPad: sideloading the unsigned build

Humming for iOS is released as an **unsigned `.ipa`** (`Humming_<version>_unsigned.ipa` on the GitHub Release, also
built by `.github/workflows/ios.yml` on every change under `ios/`). It is not on the App Store: that needs a paid
Apple Developer Program membership, and Humming is free. Any of the three routes below signs it on your side with
your own free Apple ID.

Requirements: iOS / iPadOS 16 or later. For offline dictation, the on-device English model must be installed:
**Settings > General > Keyboard > Dictation Languages > English** (most iPhones already have it).

## Route 1: Sideloadly (Windows or macOS, easiest)
1. Install Sideloadly from https://sideloadly.io and connect the iPhone with a cable (trust the computer).
2. Drag `Humming_<version>_unsigned.ipa` into Sideloadly, enter your Apple ID, click **Start**.
3. On the iPhone: **Settings > General > VPN & Device Management**, tap your Apple ID, **Trust**.
4. iOS 16+: turn on **Settings > Privacy & Security > Developer Mode** and restart when asked.

## Route 2: AltStore (refreshes over Wi-Fi)
1. Install AltServer on your computer and AltStore on the iPhone (https://altstore.io).
2. Open the `.ipa` from the Files app with AltStore (**My Apps > +**). AltStore signs it with your Apple ID.
3. AltStore re-signs the app in the background every few days while AltServer is reachable.

## Route 3: Xcode with a free personal team (build from source)
1. On a Mac with Xcode 16+: `brew install xcodegen`, then `cd ios && xcodegen generate && open Humming.xcodeproj`.
2. Select the **Humming** target > Signing & Capabilities > Team: your Apple ID (**Personal Team**). Change the
   bundle identifiers (`com.humming.ios` and `com.humming.ios.keyboard`) to something unique to you, keeping the
   keyboard's as a suffix of the app's. Do the same for the **HummingKeyboard** target.
3. Plug in the iPhone, pick it as the run destination, press Run.

## The 7-day limit
Apps signed with a free Apple ID expire after **7 days** and a free ID can have **3 sideloaded apps** at a time.
When Humming stops opening, re-sign it (Sideloadly: run it again; AltStore: refresh; Xcode: Run again). Your
settings and custom words are kept.

## Turning on the keyboard
1. **Settings > General > Keyboard > Keyboards > Add New Keyboard > Humming**.
2. Optional: tap **Humming > Allow Full Access**. It lets the keyboard paste what you copied. Humming has no
   network code either way, and nothing you type is stored or sent.
3. In any app, hold the globe key and choose Humming.

**Why dictation happens in the app:** iOS does not let keyboard extensions use the microphone. Open Humming, tap
the mic, talk, tap again to stop. The text appears in the red strip at the top of the Humming keyboard, and one tap
inserts it wherever you are typing. With a sideloaded build the App Group that shares this between app and keyboard
may not carry over; use **Copy & use in keyboard** and the keyboard's paste button (Full Access) instead.

## Privacy
Dictation uses Apple's speech recogniser with `requiresOnDeviceRecognition` on: audio is processed on the device
and Humming refuses to run rather than fall back to Apple's servers. Humming has no analytics, no account and no
network code on iOS.

## Checking the download
Each release lists the `.ipa` in `SHA256SUMS.txt`. On a Mac: `shasum -a 256 Humming_*_unsigned.ipa`; on Windows:
`certutil -hashfile Humming_<version>_unsigned.ipa SHA256`.

## Updates

The iOS app cannot update itself. An unsigned .ipa that you sideload has no update channel (Apple allows in-app updates only through the App Store or TestFlight), so to update, download the new `.ipa` from the latest release and sideload it again the same way. The Android and desktop apps update themselves (see the README's Updates section).
