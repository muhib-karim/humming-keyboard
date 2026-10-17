# Humming vs other keyboards: feature and UX teardown

Checked on **17 October 2026** against public sources only: vendors' help centres and store listings, official
repositories, Wikipedia and named press. Every competitor fact has a numbered source at the end. **"unverified"**
means no public primary source was found; the expected answer is given in brackets and must not be quoted as fact.
Humming facts come from this repository (file paths given). Google Play pages could not be fetched in full, so facts
taken from Play search snippets are marked *(Play snippet)*.

Compared: **Gboard** (Google), **Microsoft SwiftKey**, **Fleksy**, **Typewise**, **Ridmik Keyboard** (the most-used
Bangla keyboard), and for Bangla input on computers **Avro Keyboard** (phonetic), **Bijoy** (fixed layout) and
**OpenBangla Keyboard** (Linux, Avro-compatible).

- [Summary table](#summary-table)
- [Column by column](#column-by-column)
- [Bangla input in detail](#bangla-input-in-detail)
- [UX teardown](#ux-teardown)
- [Gap list (ranked)](#gap-list-ranked)
- [Sources](#sources)

## Summary table

| | **Humming** | Gboard | SwiftKey | Fleksy | Typewise | Ridmik | Avro / OpenBangla | Bijoy |
|---|---|---|---|---|---|---|---|---|
| Offline voice typing | **Yes**, default mode, every platform (English) | Pixel 6+ only, 6 languages [2]; Rambler limited offline [3] | Stable: no own offline engine documented [12]; on-device English "AI voice" in beta only, press reports [14] | No own voice [24] | No own voice [27] | No, uses Google speech over the internet [30][31] | No voice [33][40] | No voice [38] |
| Published ASR accuracy | **WER 2.78 % (Fast) / 1.68 % (Best)**, LibriSpeech test-clean, first 200 clips, ±0.5 [H1] | Not published | Not published | n/a | n/a | Not published | n/a | n/a |
| Telemetry | **None**: no analytics, no account; every network use listed [H2] | Usage statistics and voice sent to Google; federated learning on by default [6][7] | Opt-in typing data; anonymised voice clips with opt-out [12][15] | SDK: licence counts only [25] | iOS label lists tracking data [27] | "never sent anywhere" [31], but the Play label says it may collect personal info and IDs *(Play snippet)* | None documented (local IME) | Activation needs a machine ID [38] |
| Voice length / word limits | **None** (desktop: none by default, optional cap) [H3] | Rambler "subject to usage limits", no numbers [3] | None documented | n/a | n/a | None documented | n/a | n/a |
| Bangla phonetic typing | **Yes, new in 1.7** (Avro-style, on a Latin layout) [H4] | Own transliteration layout; Avro compatibility unverified [9] | Android only, own scheme [19] | No | No [29] | **Yes**, Avro-style [31] | **Yes** (defines the scheme) [33][40] | No, fixed layout [30] |
| Prediction / autocorrect | Yes, 6 languages, on-device neural model for English [H5] | Yes [4] | Yes [17] | Yes [24] | Yes [27] | Yes, Bangla [31] | Dictionary + autocorrect [33][40] | None documented |
| Glide typing | Yes [H5] | Yes [4] | Yes [17][20] | Swipe in SDK [24] | Gestures, not continuous glide (unverified) | unverified [no] | n/a | n/a |
| Clipboard | History, 100 items by default (10-500 or no limit), auto-delete off by default [H5] | 1 hour unless pinned [10] | 1 hour unless pinned; iOS 30 clips [21] | unverified | unverified | Recent clips *(Play snippet)* | n/a | n/a |
| Emoji / GIF search | Emoji search and emoji in suggestions; GIF search opt-in (Openverse) [H5] | Yes, Emoji Kitchen, GIFs [4][5] | Emoji, GIFs, stickers [17] | GIF branding, search unverified | No emoji search [27] | Stickers and emoji [31] | n/a | n/a |
| Themes | Light, dark, AMOLED, accent colour, photo background, theme editor [H5] | Yes, photo themes [4] | 100+ *(Play snippet)* | unverified | 24 + 16 PRO [27] | Theme builder and store [31] | n/a | n/a |
| One-handed / floating / split | **All three** [H5] | One-handed and floating [5][11]; split unverified | All on Android; not Thumb/Float on iOS [23] | unverified | Tablet mode (PRO) [27] | Height and number row only [31] | n/a | n/a |
| Desktop | **Windows, macOS, Linux** dictation app [H6] | No | No (removed from Windows in 2020) [20] | SDK only [24] | No | No | Yes, Windows/macOS/Linux [33][35][40] | Yes, paid [38] |
| iOS | Yes, sideloaded unsigned `.ipa` [H7] | Listed, last update May 2022 [4] | Yes [17] | Consumer app removed (unverified date) [24] | Listed; vendor says consumer line closed in 2022 [26] | Yes [32] | No | No |
| Price / ads | **Free, no ads** | Free | Free, ads on Android *(Play snippet)* | B2B licensing | Free + PRO from $1.99/month [27] | Free, ads and IAP on Android *(Play snippet)* | Free | Paid licence [38] |
| Open source | **Yes, Apache-2.0** [H8] | No | No | No | No (no repo found) | No | Avro MPL-2.0 (archived 2026) [36]; OpenBangla GPL-3.0 [40] | No [30] |

**Where Humming leads:** the only keyboard here whose voice typing works offline by default on every platform it ships
on, with a published, reproducible accuracy figure, no telemetry, no length limit, desktop apps, and an open licence.
**Where it trails:** offline voice is English only; Gboard and SwiftKey cover hundreds of typing languages and ship
through the stores; Ridmik has Bangla dictionary suggestions and a much larger Bangla user base.

## Column by column

**Offline voice typing.** Gboard's on-device "Advanced voice typing" needs a Pixel 6 or later and covers English,
French, German, Italian, Japanese and Spanish [2]; its Pixel 11 "Rambler" mode needs "an active internet connection for
full features" and keeps "basic cleanup, punctuation, and capitalization" offline [3]. On iOS, Gboard voice input "will
be sent to Google for transcription" [1]. SwiftKey's stable Android build uses its multimodal mode or Google's voice
input [12] (whether multimodal works offline: unverified); an on-device English "AI voice" mode is reported in a beta
(v9.13.16.4) by the press, not announced by Microsoft [14]. SwiftKey on iOS sends voice to Microsoft's speech service
[12]. Ridmik asks for the internet permission "for voice input" *(Play snippet)* and uses Google's speech backend [30].
Humming runs NVIDIA Parakeet on sherpa-onnx on Android and desktop and requires Apple's on-device recogniser on iOS
[H1][H2].

**On-device ASR quality.** No competitor publishes a word error rate. Humming's figures come from a CI run with the
script and raw output committed [H1].

**Privacy and telemetry.** Gboard sends usage statistics and talk-to-text audio to Google; federated learning is "turned
on by default" and can be switched off; donated voice snippets are kept up to 18 months and may be reviewed by people
[6][7]. A Trinity College Dublin study found telemetry in both Gboard and SwiftKey, including the Google Advertising ID
in SwiftKey at the time [8]. SwiftKey's sharing for product improvement is opt-in [15]. Humming has no analytics and no
account; every network use is listed and a unit test (`NetworkAuditTest`, a source scan) fails the build if any other
file uses a network API [H2].

**Limits.** Gboard's Rambler "is subject to usage limits" without published numbers [3]. No competitor documents a
session limit for standard voice typing. Humming has none on Android and iOS; on desktop none is set by default [H3].

**Typing features.** Gboard, SwiftKey and Humming all have prediction, glide typing, clipboard history, emoji search,
themes and one-handed modes. Clipboard history in Gboard and SwiftKey expires after one hour unless pinned [10][21];
Humming keeps 100 items by default and deletes nothing automatically unless you turn that on [H5]. SwiftKey's Thumb and
Float modes are Android only [23]; Humming has one-handed, floating and split [H5].

**Platforms and price.** Gboard for iOS was last updated in May 2022 [4]. SwiftKey left Windows in 2020 [20]. Fleksy's
consumer app is reported removed from the stores (Wikipedia, uncited) [24]; Typewise says its consumer chapter "closed in
2022" although listings remain [26][27]. Humming is free with no ads and runs on Android, iOS (sideload), Windows, macOS
and Linux [H6][H7].

## Bangla input in detail

| | Humming 1.7 | Ridmik | Avro (desktop) | OpenBangla | Gboard | SwiftKey | Bijoy |
|---|---|---|---|---|---|---|---|
| Phonetic (romanised → Bangla) | Yes, rule-based, Avro-style conventions [H4] | Yes, "like your favorite Avro keyboard" [31] | Yes, defines Avro Phonetic [33] | "100% compatibility" with Avro Phonetic [40] | Own transliteration [9] | Android only [19] | No |
| Fixed Bangla layout | Yes (`bengali_bd`, from FlorisBoard) | Jatiyo, Probhat [31] | Probhat, Munir Optima, Bornona, National [33] | Same set [40] | Yes [9] | Yes [18] | Bijoy (proprietary) [30] |
| Bangla word suggestions | No (gap 5) | Yes [31] | ~150,000-word phonetic dictionary [33] | Yes [40] | unverified [yes] | unverified [yes] | None documented |
| Bangla voice typing | Online mode only (phone's speech service) | Online (Google) [30] | No | No | unverified offline [no] | unverified | No |

How Humming's phonetic typing works: add the Bengali language with a QWERTY layout (Settings > Languages & layouts),
type "ami tomake bhalobashi" and each word is offered in Bangla as the auto-commit suggestion, so space or punctuation
types it; the Latin word stays one tap away. Capitals carry Avro's meaning (T, D, N, R, Sh, I, U, O), consonants in a
row are joined (reph, ra-phala, ya-phala, ba-phala), "ng" becomes ং or ঙ before a vowel. The rules were written for
Humming from the common conventions, not copied from Avro's MPL data, and do not yet cover Avro's dictionary-backed
choices (gap 5).

## UX teardown

| Moment | Gboard | SwiftKey | Ridmik | Humming (1.7) |
|---|---|---|---|---|
| Start dictating | Tap mic; on Pixel offline, elsewhere online | Tap mic; Google or multimodal | Tap mic, needs internet | Tap mic; first use downloads the 104 MB model once, then offline |
| While talking | Live words | Live words | Live words | Underlined draft that corrects itself in place; keeps listening with the screen off |
| Dictating into the middle of a sentence | unverified | unverified | unverified | **New:** lowercases a common first word, no stray space after "(", drops the final period when the sentence carries on [H9] |
| Spoken punctuation | unverified [yes; no list in the sources above] | unverified | unverified | comma, full stop, question/exclamation mark, new line/paragraph, **new:** colon, semicolon, quotes, brackets, ellipsis [H9] |
| Fix a mistake by voice | "Fix it" edits go to Google servers [2] | unverified | unverified | "scratch that", **new:** "delete last word"; AI rewrite only with your own key [H9] |
| Privacy prompt | Settings in Google account | Opt-in screens | None | Nothing to opt out of; network uses listed in Settings > Privacy |

## Gap list (ranked)

Ranked by **user impact × feasibility** (each 1-5; feasibility means safe to build and verify in this repository without
new paid services). ✅ = implemented in this pass (1.7.0-rc1, unit tests run in CI); the rest are open.

| # | Gap | Who has it | Impact | Feasibility | Score | Status |
|---|---|---|---:|---:|---:|---|
| 1 | Bangla phonetic typing on a Latin layout | Ridmik, Avro, OpenBangla, Gboard (own scheme) | 5 | 5 | 25 | ✅ `BanglaPhonetic.kt`, `BanglaPhoneticTest` |
| 2 | Dictation fits the sentence around the cursor (case, spacing, final period) | unverified [Gboard likely] | 4 | 5 | 20 | ✅ `DictationJoin.kt`, `DictationJoinTest` (Android) |
| 3 | More spoken punctuation and "delete last word" | unverified [Gboard, SwiftKey likely; no public list] | 3 | 5 | 15 | ✅ Android, desktop and iOS, same tests in all three |
| 4 | Visual polish: tokens, voice button, candidate strip, contrast | Gboard, SwiftKey | 3 | 4 | 12 | Partly (see `docs/BRAND.md`); full pass needs device screenshots from CI |
| 5 | Bangla word suggestions (dictionary-backed phonetic choices) | Ridmik, Avro | 4 | 3 | 12 | Open: needs a Bangla word list with a compatible licence |
| 6 | Bangla phonetic on desktop and iOS | Avro, OpenBangla (desktop) | 3 | 3 | 9 | Open: port `BanglaPhonetic` to Rust and Swift |
| 7 | Offline voice in more languages (Bangla, Hindi, Spanish...) | Gboard (6 languages, Pixel only) | 5 | 1 | 5 | Open: needs on-device models per language and benchmarks |
| 8 | Store distribution (Play, App Store, signed desktop installers) | Everyone except Avro/OpenBangla | 5 | 1 | 5 | Open: owner accounts and certificates |
| 9 | Full iOS keyboard (prediction, glide) | Gboard, SwiftKey, Ridmik | 3 | 2 | 6 | Open |
| 10 | Cloud clipboard / cross-device sync | SwiftKey [21] | 2 | 1 | 2 | Won't do: conflicts with no-account, no-cloud privacy |

## Sources

Humming (this repository):

- [H1] `docs/bench/ci-37204875974-wer.json` and the README "Benchmarks" section (CI run 37204875974, 4 October 2026).
- [H2] `play/PRIVACY_POLICY.md`, `keyboard/app/src/test/kotlin/com/humming/keyboard/NetworkAuditTest.kt`.
- [H3] README "At a glance"; `desktop/src-tauri/src/settings.rs` (`max_seconds`: no cap unless the user sets one).
- [H4] `keyboard/app/src/main/kotlin/com/humming/keyboard/BanglaPhonetic.kt` and its test (1.7.0-rc1, verified in CI).
- [H5] README "Typing"; `keyboard/app/src/main/kotlin/com/humming/keyboard/` and `assets/predict/`.
- [H6] `desktop/`, `desktop/README.md`. [H7] `ios/`, `docs/IOS_SIDELOAD.md`. [H8] `LICENSE`, `NOTICE`.
- [H9] `DictationJoin.kt`, `TextCleanup.kt` (+ `desktop/src-tauri/src/text_cleanup.rs`, `ios/HummingCore/.../TextCleanup.swift`).

Competitors (all checked 17 October 2026):

1. Gboard Help, Type with your voice: https://support.google.com/gboard/answer/2781851
2. Gboard Help, Advanced voice typing: https://support.google.com/gboard/answer/11197787
3. Gboard Help, Rambler voice input: https://support.google.com/gboard/answer/17468539
4. App Store, Gboard: https://apps.apple.com/us/app/gboard-the-google-keyboard/id1091700242
5. Wikipedia, Gboard: https://en.wikipedia.org/wiki/Gboard
6. Gboard Help, what Gboard sends to Google: https://support.google.com/gboard/answer/9058584
7. Gboard Help, how Gboard learns: https://support.google.com/gboard/answer/12373137
8. Trinity College Dublin, "How Private Are Android Keyboards?": https://www.scss.tcd.ie/Doug.Leith/pubs/gboard_kamil.pdf
9. Gboard Help, languages: https://support.google.com/gboard/answer/6380730
10. Gboard Community guide, clipboard retention: https://support.google.com/gboard/community-guide/446950596
11. Android Central, Gboard one-handed and floating: https://www.androidcentral.com/how-get-out-one-handed-mode-or-floating-mode-gboard
12. Microsoft Support, SwiftKey voice to text: https://support.microsoft.com/en-us/swiftkey-keyboard/how-do-i-use-voice-to-text-with-microsoft-swiftkey-keyboard
13. Microsoft Support, disable multimodal voice: https://support.microsoft.com/en-us/topic/how-to-disable-multimodal-voice-dictation-in-swiftkey-f2f0aae5-3444-43a3-87d0-3089bebd79a2
14. Android Headlines, SwiftKey AI voice beta (11 Sept 2026): https://www.androidheadlines.com/2026/09/swiftkeys-new-ai-voice-mode-takes-aim-at-gboards-rambler.html
15. Microsoft Support, SwiftKey typing data FAQ: https://support.microsoft.com/en-us/topic/microsoft-swiftkey-keyboard-sharing-your-typing-data-faq-d737059d-8810-448e-b376-9af56171a37d
16. Microsoft Privacy Statement: https://www.microsoft.com/en-us/privacy/privacystatement
17. App Store, Microsoft SwiftKey: https://apps.apple.com/us/app/microsoft-swiftkey-ai-keyboard/id911813648
18. Microsoft Support, SwiftKey languages: https://support.microsoft.com/en-us/topic/what-languages-are-currently-supported-for-microsoft-swiftkey-keyboard-661bce6a-8446-435d-a6aa-9ea006ee8353
19. Microsoft Support, SwiftKey transliteration: https://support.microsoft.com/en-au/topic/which-languages-support-transliteration-and-how-does-it-work-in-microsoft-swiftkey-for-android-00819751-f597-4f17-b38c-3d2e1a8c946e
20. Wikipedia, Microsoft SwiftKey: https://en.wikipedia.org/wiki/Microsoft_SwiftKey
21. Microsoft Support, SwiftKey clipboard: https://support.microsoft.com/en-us/swiftkey-keyboard/how-does-the-clipboard-work-with-microsoft-swiftkey-keyboard
22. GSMArena, SwiftKey themes free (2016): https://m.gsmarena.com/newscomm-21953.php
23. Microsoft Support, SwiftKey keyboard modes: https://support.microsoft.com/en-us/swiftkey-keyboard/how-to-change-your-keyboard-mode-on-microsoft-swiftkey-keyboard
24. Wikipedia, Fleksy: https://en.wikipedia.org/wiki/Fleksy
25. Fleksy solutions and FAQ: https://www.fleksy.com/solutions/
26. Typewise: https://www.typewise.app/
27. App Store, Typewise: https://apps.apple.com/us/app/typewise-custom-keyboard/id1470215025
28. Typewise support: https://www.typewise.app/support
29. Typewise suggestions, "Add Bangla": https://suggestions.typewise.app/suggestions/79413/add-bangla
30. Wikipedia, Bengali input methods: https://en.wikipedia.org/wiki/Bengali_input_methods
31. Ridmik Keyboard: https://ridmik.com/keyboard ; Play: https://play.google.com/store/apps/details?id=ridmik.keyboard
32. App Store, Ridmik Keyboard: https://apps.apple.com/us/app/ridmik-keyboard/id1475531051
33. OmicronLab, Avro Keyboard: https://www.omicronlab.com/avro-keyboard.html
34. Wikipedia, Avro Keyboard: https://en.wikipedia.org/wiki/Avro_Keyboard
35. OmicronLab, ibus-avro: https://linux.omicronlab.com/
36. GitHub, mugli/Avro-Keyboard: https://github.com/mugli/Avro-Keyboard
37. GitHub, OmicronLab: https://github.com/omicronlab
38. Bijoy Ekushe software page: https://bijoyekushe.net.bd/index.php?action=software
39. Ryans, Bijoy price list: https://www.ryans.com/category/bangla-typing-application-bijoy
40. GitHub, OpenBangla Keyboard: https://github.com/OpenBangla/OpenBangla-Keyboard
