# Humming Keyboard: production audit, Android 1.6.2

Date: 6 October 2026. Scope: the Android keyboard in `keyboard/` (a FlorisBoard fork, Apache-2.0) at the 1.6.2 build
commit `46cea1f`, the signed release APK built from it, and the emulator walk of every settings screen
(`docs/audit-1.6.2/screens/`). The earlier full audit (`AUDIT.md`, M7, 1.2.0) has the last complete set of on-device
measurements; where a number below is not re-measured for 1.6.2, it says so and gives the source. Nothing here is
estimated: a cell is either measured, quoted from a vendor's own page (linked, checked 6 October 2026), or marked
"not measured".

Severity: High = wrong or broken for a user today, Medium = visible quality gap, Low = known, acceptable for now.

## 1. What 1.6.2 fixed (found in this audit)

| # | Finding | Severity | Fix in 1.6.2 |
|---|---|---|---|
| 1 | Live dictation committed words to the field while the user talked (plain `commitText` once two partials agreed). When the final clean-up rewrote words that were already typed and `getTextBeforeCursor` could not confirm them (many editors), the corrected words were appended after the old ones: duplicated words. | High | Live words are composing text (the underlined draft), replaced in place on every partial; one commit on stop; Cancel leaves the field untouched. The editor no longer re-marks the composing region while a dictation owns it (`AbstractEditorInstance.externalComposing`). `DictationOutputTest` (11 tests) pins the rules. |
| 2 | No way to get "all at the end" dictation. | Medium | Settings > Voice > **Live text while speaking** (default on). Off: nothing appears until stop, then one commit. |
| 3 | Settings > Addons & Extensions linked to the upstream FlorisBoard Addons Store and its update search, and said the app has no Internet permission (it does, for the model download). | Medium | Store button and update search removed; text rewritten. |
| 4 | Extension lists, theme manager and language-pack manager showed raw IDs (`org.florisboard.themes`), and component details showed developer fields (`stylesheetPath = stylesheets/floris_day.json`). | Medium | Built-in extensions show "Built-in"; details read "Authors", "Night theme"/"Day theme", "Language". IDs unchanged (settings carry over). |
| 5 | About > Changelog / Repository, the setup footer and the crash dialog linked to FlorisBoard's GitHub; Settings > Voice had a second FlorisBoard credit; two more upstream links (a feedback thread, a key-code table). | Medium | Links go to the public Humming site (humming-keyboard.pages.dev, with a changelog page) and the Humming privacy policy; extra credit and upstream links removed. One attribution stays in About: "Android keyboard built on FlorisBoard (Apache 2.0)". |
| 6 | 20 translations still said "FlorisBoard Addons Store". | Medium | Those strings fall back to the new English text. |
| 7 | Tapping the dictation panel while the speech model is not downloaded (a new user's first mic tap), and "+" in the sticker panel, opened the "Import extension" screen instead of Settings > Voice / Stickers & GIFs: the settings deep link was sent without `CATEGORY_BROWSABLE`, so `FlorisAppActivity` treated it as a file to import. Present since 1.6.0; found by this audit's emulator walk, whose own deep links hit the same path. | High | Both shortcuts add `CATEGORY_BROWSABLE` (commit `46cea1f`). |

Proof: a case-insensitive "floris" grep over the apktool-decoded 1.6.2 APK (res, assets, manifest) finds exactly one
user-visible string, the About attribution; every other hit is an internal ID, a licence/NOTICE line or a dictionary
word (`florist`). The emulator walk is the second check: 37 screenshots of every settings route plus the Addons & Extensions lists, an extension's details page, Voice scrolled to the **Live text while speaking** switch and About scrolled to the bottom (`docs/audit-1.6.2/screens/`, visible text in `docs/audit-1.6.2/text/`); a case-insensitive "floris" search over the visible text of all of them finds only About's attribution line (`docs/audit-1.6.2/FLORIS_HITS.md`).

## 2. Findings by area

### Code quality
- Kotlin, 274 source files in the app module; Humming's own code is in `com.humming.keyboard` (about 3,450 lines) plus
  Humming screens under `dev.patrickgold.florisboard.app.settings.humming`. Upstream packages and IDs are kept so
  FlorisBoard fixes can still be merged.
- Medium: user-facing text in Humming's own screens (Voice, Updates, Privacy) is hard-coded English in Kotlin, not
  string resources, so those screens cannot be translated. Plan: move to `strings.xml`.
- Low: the `perf/latency` branch (below) is compiled but not merged; it is based on 1.6.0 and needs a rebase.

### Crashes and ANRs
- Not re-measured on a phone for 1.6.2. Last measured: 0 crash-buffer entries and 0 ANRs across the full CI emulator
  run in M7 (run 36676272085). The 1.6.2 emulator walk records any crash dialog it meets (see the screens notes).
- GitHub Actions cannot run on the private repo (account billing), so the emulator suites (`screens.py`, `handoff.py`)
  have not run since 4 October. Builds and unit tests run on separate build machines instead.

### Performance
| Measure | Value | Source |
|---|---|---|
| Stop-speaking to text on screen, Android, Fast model, 1,000 LibriSpeech clips | 1.6.x: p50 171 ms, p90 252 ms. `perf/latency` branch: **p50 80 ms, p90 119 ms**, WER 3.74 % (was 9.32 %) | `docs/LATENCY.md` on `perf/latency`; replay on a 4-vCPU x86 machine with the real sherpa-onnx engine, not on a phone |
| Suggestion time per key press, human pace | median 55.6 ms, p90 171 ms | M7 audit, CI emulator (software rendering) |
| Keyboard cold start | 9.0 s on the CI emulator | M7 audit; not measured on a phone |
| Memory (PSS) after dictation, voice model loaded | 484 MB | M7 audit |
| Release APK (arm64) | **32.8 MB** (32,807,503 B) | measured, 1.6.2 |
- High (next): merge `perf/latency` so the 80 ms number ships; then measure on a real mid-range phone.

### Accessibility
- Every Humming theme pair meets WCAG AA 4.5:1 (`ThemeContrastTest`, since 1.6.1 text on pink uses `#C93A5B`).
- Keys are labelled for TalkBack (upstream + `HummingA11yTest`). Low: the suggestion strip does not announce "best
  guess"/"autocorrect" (unchanged since M6).

### Privacy and permissions
- Network use is limited by a build-failing test (`NetworkAuditTest`) to: the one-time voice-model download
  (SHA-256 pinned), opt-in GIF search, opt-in AI clean-up with the user's own key, and the update check.
- Permissions in the 1.6.2 APK: RECORD_AUDIO, INTERNET, ACCESS_NETWORK_STATE, MODIFY_AUDIO_SETTINGS, VIBRATE,
  POST_NOTIFICATIONS, FOREGROUND_SERVICE(+_MICROPHONE), WAKE_LOCK (dictation with the screen off) and
  REQUEST_INSTALL_PACKAGES (self-update; the Play bundle does not have it).
- Privacy policy live at https://muhib-karim.github.io/humming-keyboard/privacy.html (HTTP 200, 6 Oct 2026).

### Settings UX
- Walked every settings screen on an emulator for 1.6.2 (screenshots in `docs/audit-1.6.2/screens/`).
- Medium: the upstream "Addons & Extensions" area is power-user territory (import `.flex`, edit extensions); most
  users only need themes. Plan: fold Themes into one Humming "Look" screen and move extension tools under Advanced.

### Tests and CI
- JVM unit tests: all pass on the 1.6.2 build (`testDebugUnitTest`, 6 min 12 s on a 4-CPU build machine).
- CI: the eight workflows are in place but blocked by the account's Actions billing on the private repository.
  Making the repository public restores free Actions minutes.

### README and docs
- README carries a feature table, a comparison with Wispr Flow, Typeless and Gboard Rambler (vendor pages, 4 Oct),
  benchmarks and screenshots. 1.6.2 updates the version, the download and the live-dictation description.
- Medium: README badges point at Actions runs that cannot run while billing blocks Actions, so they show failures.

### Play readiness
- `play/` has the listing, graphics, data-safety and content-rating answers and a checklist. Waiting on the owner:
  the US$25 developer account, ID check, and a 14-day closed test with 12+ testers. The AAB is built without the
  install permission.

## 3. Feature matrix (Android)

Vendor columns quote the vendor's own pages, checked 6 October 2026 (links below). "Not stated" means the page does
not say; it does not mean the feature is missing.

| | **Humming 1.6.2** | Gboard | Microsoft SwiftKey | FlorisBoard (upstream) | Fleksy |
|---|---|---|---|---|---|
| Voice typing built in | **Yes, on-device** (Parakeet via sherpa-onnx), no length limit, works with the screen off | Yes; "advanced voice typing" (auto punctuation, voice edit commands) needs a Pixel 6 or later [1] | Yes, "voice-to-text" (microphone permission) [3] | No built-in voice input (works with a separate voice app) [4] | Not assessed: the vendor shut down its website in June 2026 [5] |
| Live text while speaking, corrected in place | **Yes** (composing draft), or off | Not stated | Not stated | — | — |
| Works offline | **Yes**, voice and typing | Not stated on the pages checked | Not stated | Yes (no network features) | — |
| Glide/swipe typing | Yes (upstream engine, English) | Yes [2] | Yes [3] | Experimental; disabled by default for performance [6] | — |
| Word suggestions / next word | Yes: on-device tables + neural model, 5 more languages (M7: 36.5 % top-3 English) | Yes, multilingual [2] | Yes, "AI-powered" [3] | "Not included in the current releases" [4] | — |
| Clipboard manager | Yes (upstream) | Not on the pages checked | Yes; clips expire after 1 hour unless pinned [7] | Yes [4] | — |
| Translate in the keyboard | Only with AI clean-up and your own key ("translate it to Spanish") | Yes, Google Translate [2] | Yes (Translator) [3] | No | — |
| Ads | **None** | None stated | "Contains ads" (Play listing) [3] | None | — |
| Open source | **Yes, Apache-2.0** (repository public from 1.6.3) | No | No | Yes, Apache-2.0 | No |

[1] https://support.google.com/gboard/answer/11197787 ·
[2] https://play.google.com/store/apps/details?id=com.google.android.inputmethod.latin ·
[3] https://play.google.com/store/apps/details?id=com.touchtype.swiftkey ·
[4] https://github.com/florisboard/florisboard (README) ·
[5] https://keyboardkit.com/blog/2026/06/08/fleksy-shuts-down-their-website ·
[6] https://github.com/florisboard/florisboard/discussions/2797 ·
[7] https://support.microsoft.com/en-us/swiftkey-keyboard/how-does-the-clipboard-work-with-microsoft-swiftkey-keyboard

Not measured yet against the others: typing latency, autocorrect accuracy and voice accuracy on the same phone. The
plan below makes those measurements part of each release.

## 4. Ranked plan: what makes Humming better, not just different

Ranked by user impact per unit of work. Each ships as a 1.7.x release with the same gates as 1.6.2 (build, unit
tests, emulator walk with screenshots, gitleaks, tag verified on the remote, release, update feed).

1. **Fastest private dictation (1.7.0).** Merge `perf/latency` (p50 80 ms stop-to-text in the replay benchmark,
   WER 9.3 % to 3.7 %), then measure on a real phone and publish the number next to the vendors' own claims.
2. **Voice editing that works offline (1.7.1).** On-device commands beyond "scratch that": "delete the last
   sentence", "replace X with Y", "capitalise that", spoken punctuation everywhere, applied to the live draft before
   it is committed.
3. **Offline in more languages (1.7.2).** Add a multilingual offline speech model option, with per-language WER on
   public test sets in the README.
4. **Snippets and smart clipboard (1.7.3).** Pinned snippets with a typed trigger ("@addr"), clipboard search, and
   no time-based expiry; all on-device.
5. **One-handed and floating polish (1.7.4).** Humming already has a floating keyboard and split layout; measure
   reachability, add a one-tap side switch and remember per-app size.
6. **Proof, not promises (ongoing).** Reproducible build notes, the network audit test in the README, an F-Droid
   listing once the repository is public, and a public crash-free/latency dashboard from the emulator suite.
7. **Settings that read like Humming (1.7.x).** One "Look" screen for themes, extension tools under Advanced,
   Humming screens translated.
