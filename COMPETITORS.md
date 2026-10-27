# Humming vs other keyboards: feature and UX teardown

Checked on **17 October 2026** against public sources only: vendors' help centres and store listings, official
repositories, Wikipedia and named press. Every competitor fact has a numbered source at the end. **"unverified"**
means no public primary source was found; the expected answer is given in brackets and must not be quoted as fact.
Humming facts come from this repository (file paths given). Google Play pages could not be fetched in full, so facts
taken from Play search snippets are marked *(Play snippet)*.

Compared: **Gboard** (Google), **Microsoft SwiftKey**, **Fleksy**, **Typewise**, **Ridmik Keyboard** (the most-used
Bangla keyboard), and for Bangla input on computers **Avro Keyboard** (phonetic), **Bijoy** (fixed layout) and
**OpenBangla Keyboard** (Linux, Avro-compatible). A second part compares voice-dictation apps: **Wispr Flow**,
**Typeless**, **Superwhisper**, **Aqua Voice**, **Willow**, **MacWhisper**, and Apple's and Google's built-in
dictation.

- [Summary table](#summary-table)
- [Column by column](#column-by-column)
- [Bangla input in detail](#bangla-input-in-detail)
- [UX teardown](#ux-teardown)
- [Gap list (ranked)](#gap-list-ranked)
- [Dictation apps](#dictation-apps)
- [Dictation gap list (ranked)](#dictation-gap-list-ranked)
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
| Spoken punctuation | unverified [yes; no list in the sources above] | unverified | unverified | comma, full stop, question/exclamation mark, new line/paragraph, **new:** colon, semicolon, quotes, brackets, ellipsis, "bullet point" lists [H9] |
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

## Dictation apps

Checked on **17 October 2026** against the vendors' own pages (sources [D1]-[D73] below). Rules as above, plus:
**accuracy only where a number is published**, with who measured it; a slogan without a method is quoted as a
"vendor claim". Word error rates measured on different test sets are **not comparable** with each other: Humming's
figure is LibriSpeech test-clean (read speech, the easier end), Superwhisper's and Aqua's are averages over harder,
mixed sets.

### Accuracy, speed, privacy, platforms, price

| App | Published accuracy | Latency | Offline / privacy | Platforms | Price (per month) | Open source |
|---|---|---|---|---|---|---|
| **Humming** | **WER 2.78 % (Fast) / 1.68 % (Best)**, LibriSpeech test-clean, first 200 clips, CI run with raw output committed [H1] | Median 312 ms (Fast) / 1314 ms (Best) from clip to text, desktop CPU, 2 threads [H1]; Android shows a live draft while you talk | **Offline by default on every platform**; no account, no analytics [H2] | Android, iPhone (sideload), Windows, macOS, Linux | **Free, no ads, no word cap** | **Yes**, Apache-2.0 [H8] |
| Wispr Flow | No figure in text (charts only) [D16]; 8.7 % WER "raw" measured by Superwhisper, a competitor, over 8 English sets [D18] | Not published; 1.28 s median measured by Superwhisper [D18] | Cloud only [D3]; audio deleted after transcription unless cloud storage is on [D3]; usage statistics collected "regardless of your data controls" [D4]; SOC 2 Type II, HIPAA BAA [D1] | Mac, Windows, iOS, Android 13+; "no native Linux" [D1][D8][D14] | Free 2,000 words/week desktop, 1,000 mobile; Pro $15 ($12 yearly) [D1] | No |
| Typeless | Not published [D20] | Not published ("instantly") [D19] | Cloud, "zero data retention policy" [D21]; no offline mode documented | macOS, Windows, **Linux**, iOS, Android [D22] | Free "limited words" (number not stated); Pro $30 ($12 yearly) [D20] | No |
| Superwhisper | **6.6 % WER** (S1-Voice, cloud), vendor benchmark over 8 English sets [D38][D40]; local Cohere model 8.4 % [D33] | 0.41 s median, end of speech to text (vendor) [D39]; streaming on Pro with some models [D37] | **Offline with local models** [D30]; cloud under zero-data-retention terms [D35] | macOS, Windows, iOS, Android; Linux "coming soon" [D41] | Free unlimited with local models; Pro $8.49, $84.99/year or $249.99 once [D31] | No |
| Aqua Voice | 6.24 % average WER on the Hugging Face Open ASR Leaderboard (Oct 2025, Aqua's recap, not re-checked); 5.55 % for Avalon 1.5, test set not stated [D45] | Under 50 ms to start, ~450 ms after you stop (vendor) [D45] | Cloud ("needs an internet connection") [D47]; Privacy Mode off by default [D45][D46] | macOS, Windows, iOS, Android (Oct 2026); no Linux [D45][D47] | Free 1,000 words; Pro $10 ($8 yearly) [D44] | No |
| Willow | Vendor claim: "3x more accurate than built-in dictation", no method [D48] | Vendor claim: "as little as 200ms" [D48] | Conflicting: homepage offers an offline mode [D48], help centre says cloud models [D51] | Mac, Windows, iPhone; Android "coming soon" [D56] | Free (weaker model); Pro $15 ($12 yearly) [D49] | No |
| MacWhisper | Not published [D57] | Not published | **Local models**, optional cloud AI with your own keys [D57] | macOS; iPhone/iPad as "Whisper Transcription" [D59] | Free; Pro €64 once [D57] | No (built on open models) |
| Apple Dictation | Not published | Not published | On-device "in many languages" [D60]; server transcripts may be kept up to two years if used [D65] | iPhone, iPad, Mac | Free with the OS | No |
| Google (Gboard voice, Pixel, Rambler) | Not published | Not published | Pixel 6+ advanced voice typing on-device [D68]; Rambler needs internet for full features [D69] | Android, iOS (Gboard), web (Docs) | Free; Rambler has unpublished usage limits [D69] | No |

### Clean-up, commands, dictionary, languages

| App | Fillers removed | Lists | Voice commands | Personal dictionary / replacements / snippets | Languages, Bangla |
|---|---|---|---|---|---|
| **Humming 1.7** | **Yes**, switch, on by default ✅ | **Yes**: "bullet point", "first, ... second, ..." ✅ | "new line", "new paragraph", spoken punctuation, "scratch that" / "delete that" ✅, "delete last word", self-corrections ("3, no, 4pm"); on Android "change … to …", "delete …", "insert … before/after …" edit text already in the field (not on desktop or iPhone) | Custom words (casing, recogniser hints on iPhone) and **"spoken -> written" replacements** ✅, per app on Android and desktop | Offline dictation: English. Bangla: phonetic **typing** (Android); Android's opt-in Online mode uses the phone's speech service, Bangla there unverified |
| Wispr Flow | Yes, three levels [D9] | Yes ("one… two…") [D6] | New line / paragraph, Backtrack ("scratch that") [D6]; Command Mode on selected text, Pro, desktop [D5] | Dictionary with auto-learn, misspelling fixes, snippets on every plan [D10][D11] | "100+"; Bengali unverified [D7] |
| Typeless | Yes [D19] | Yes [D19] | Speak-to-edit on selected text [D24]; "new line" / "scratch that" not documented | Dictionary, auto-add, CSV import; replacements not documented [D25] | "100+"; no list, Bengali unverified [D19] |
| Superwhisper | Yes, Message mode [D34] | Yes, Note mode [D34] | Commands on selected text (Super mode) [D34]; "new line" / "scratch that" not documented | Vocabulary hints and **replacements**, e.g. "my work email" → address [D32] | "100+"; Bengali not named, expected via Whisper models [D33][D42] |
| Aqua Voice | Not documented | Via custom instructions | Edit Mode on selected text; "undo that" not supported [D45] | Dictionary and replacements ("my calendar link" → URL) [D44][D45] | 49, **Bengali listed** [D45] |
| Willow | Yes [D48] | Yes ("Bullet, …") [D50] | New line / paragraph, spoken lists [D50]; "scratch that" not documented | Terms and voice "shortcuts" [D53] | "100+"; Bengali unverified [D52] |
| MacWhisper | Yes [D57] | Not documented | Not documented | Not documented | "100"; Bengali not named, expected via Whisper [D42][D57] |
| Apple Dictation | Not documented | Not documented | New line / paragraph, caps; on iPhone in US English: "change … to …", "select", "delete", "undo" [D61] | None for dictation documented [D65] | 63 language/region entries; **Bangla not listed** [D64] |
| Google | Rambler only [D69] | Not documented | New line / paragraph [D67]; Pixel: "delete last word", "clear", "undo", detailed edits [D68]; Docs: "select", "delete" [D72] | Unverified | Bengali in Gboard voice typing (2017 post) [D70] and Docs [D72]; not in Pixel advanced voice typing [D68] |

**Where Humming leads:** the only app here that is free with no word cap, open source, and offline by default on
Android, iPhone, Windows, macOS **and** Linux (Typeless is the only other one with a Linux app, and it is cloud-only).
It publishes a reproducible accuracy figure with the raw output. With 1.7 it has the shared core of the dictation apps:
filler removal with a switch, lists, "scratch that", and spoken → written replacements that double as snippets.
**Where it trails:** voice editing of text already in the field is Android only (exact phrases, no AI edit mode) and
there is no per-app tone (that needs an AI model); offline dictation is English only, while Aqua and Google list Bengali (online).

## Dictation gap list (ranked)

Ranked like the keyboard gaps (user impact × feasibility, 1-5 each). ✅ = implemented in 1.7.0-rc1 with unit tests that
run in CI (Kotlin `ListFormatTest`, `TextCleanupTest`, `AppVocabularyTest`; Rust `text_cleanup.rs` and `lib.rs`;
Swift `ListFormatTests`, `TextCleanupTests`).

| # | Gap | Who has it | Impact | Feasibility | Score | Status |
|---|---|---|---:|---:|---:|---|
| D1 | Spoken → written replacements (and snippets) on every platform | Wispr, Superwhisper, Aqua, Willow | 5 | 5 | 25 | ✅ Android and iPhone (`spoken -> written` lines); desktop had them since 1.5 |
| D2 | Smart lists ("bullet point", "first, ... second, ...") | Wispr, Typeless, Superwhisper, Willow | 4 | 5 | 20 | ✅ Android, desktop, iPhone (`ListFormat`, `bullet_list`) |
| D3 | "Scratch that" / "delete that" as its own sentence on every platform | Wispr, Typeless, Willow | 4 | 5 | 20 | ✅ desktop and iPhone; Android had it |
| D4 | A switch for filler-word removal (default on) | Wispr (clean-up levels) | 3 | 5 | 15 | ✅ Android and iPhone; desktop had it |
| D5 | Voice editing of text already in the field ("change X to Y", "select", "delete X") | Apple (US English), Pixel, Docs; AI edit modes in Wispr, Typeless, Superwhisper, Aqua | 4 | 2 | 8 | ✅ Android (`FieldEdit`: "change X to Y", "delete X", "insert X before/after Y"); desktop (accessibility APIs) and iPhone open |
| D6 | Per-app tone and style | Wispr, Typeless, Superwhisper, Aqua, Willow, MacWhisper | 3 | 2 | 6 | Open: needs a language model; Android's own-key AI clean-up covers "make it formal" |
| D7 | Dictionary that learns from your corrections | Wispr (desktop), Typeless | 3 | 2 | 6 | Open |
| D8 | Offline dictation in Bangla and more languages | Aqua (cloud, Bengali listed), Superwhisper / MacWhisper via Whisper (untested) | 5 | 1 | 5 | Open (keyboard gap 7) |
| D9 | Whisper / quiet-speech mode | Typeless | 2 | 2 | 4 | Open |
| D10 | Team-shared dictionary | Wispr, Aqua, Willow | 1 | 2 | 2 | Won't do: needs accounts and a server |

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


Dictation apps (all checked 17 October 2026; [D18] is a competitor's measurement, [D73] a press snippet):

Wispr Flow:

- [D1] https://wisprflow.ai/pricing
- [D2] https://wisprflow.ai/
- [D3] https://wisprflow.ai/privacy
- [D4] https://wisprflow.ai/data-controls
- [D5] https://docs.wisprflow.ai/articles/4816967992-how-to-use-command-mode
- [D6] https://docs.wisprflow.ai/articles/5373093536-how-do-i-use-smart-formatting-and-backtrack
- [D7] https://docs.wisprflow.ai/articles/3191899797-use-flow-with-multiple-languages
- [D8] https://docs.wisprflow.ai/articles/6478598909-using-flow-with-linux-wsl-and-terminal-applications
- [D9] https://docs.wisprflow.ai/articles/4283510616-auto-cleanup-control-how-much-flow-edits-your-dictation-beta
- [D10] https://docs.wisprflow.ai/articles/5784437944-create-and-use-snippets
- [D11] https://docs.wisprflow.ai/articles/4052411709-teach-flow-your-words-with-the-dictionary
- [D12] https://docs.wisprflow.ai/articles/2368263928-how-to-setup-flow-styles
- [D13] https://wisprflow.ai/india
- [D14] https://wisprflow.ai/android
- [D15] https://wisprflow.ai/lab
- [D16] https://wisprflow.ai/canto
- [D17] https://wisprflow.ai/whats-new (redirect target of roadmap.wisprflow.ai/changelog/...)
- [D18] https://superwhisper.com/benchmarks/wispr-flow-raw (competitor's measurement)

Typeless:

- [D19] https://www.typeless.com/
- [D20] https://www.typeless.com/pricing
- [D21] https://www.typeless.com/privacy
- [D22] https://www.typeless.com/downloads
- [D23] https://www.typeless.com/help/billing
- [D24] https://www.typeless.com/help/quickstart/speak-to-edit
- [D25] https://www.typeless.com/help/quickstart/history-and-dictionary
- [D26] https://www.typeless.com/help/quickstart/personalization
- [D27] https://www.typeless.com/help/quickstart/dictate
- [D28] https://apps.apple.com/us/app/typeless-ai-voice-keyboard/id6749257650
- [D29] https://www.typeless.com/help/faqs

Superwhisper:

- [D30] https://superwhisper.com/
- [D31] https://superwhisper.com/docs/billing/plans
- [D32] https://superwhisper.com/docs/get-started/interface-vocabulary
- [D33] https://superwhisper.com/docs/models/voice
- [D34] https://superwhisper.com/docs/modes/built-in
- [D35] https://superwhisper.com/docs/security/sensitive-data
- [D36] https://superwhisper.com/docs/security/compliance
- [D37] https://superwhisper.com/docs/common-issues/realtime
- [D38] https://superwhisper.com/benchmarks
- [D39] https://superwhisper.com/benchmarks/superwhisper-s1-voice
- [D40] https://superwhisper.com/docs/models/benchmarks
- [D41] https://superwhisper.com/docs (and https://superwhisper.com/docs/llms.txt)
- [D42] https://github.com/openai/whisper/blob/main/whisper/tokenizer.py (Whisper language table; `"bn": "bengali"`)

Aqua Voice:

- [D43] https://aquavoice.com/
- [D44] https://aquavoice.com/pricing
- [D45] https://aquavoice.com/llms.txt (vendor's machine-readable overview, "as of October 2026")
- [D46] https://aquavoice.com/privacy (privacy policy, effective 29 Jul 2026)
- [D47] https://aquavoice.com/info/faq

Willow:

- [D48] https://willowvoice.com/
- [D49] https://willowvoice.com/pricing
- [D50] https://help.willowvoice.com/en/articles/13183983-voice-commands-and-automatic-formatting-guide
- [D51] https://help.willowvoice.com/en/articles/12854269-how-willow-protects-your-data-and-privacy
- [D52] https://help.willowvoice.com/en/articles/10878926-how-do-i-dictate-in-another-language
- [D53] https://help.willowvoice.com/en/articles/13183918-using-personal-dictionary-and-shortcuts
- [D54] https://help.willowvoice.com/en/articles/13668493-compliance-certifications-and-security-standards
- [D55] https://willowvoice.com/blog/best-offline-speech-to-text-tools (vendor blog; byline 21 Apr 2026, metadata 16 Oct 2026)
- [D56] https://willowvoice.com/download

MacWhisper:

- [D57] https://macwhisper.com/
- [D58] https://goodsnooze.gumroad.com/l/macwhisper (page body did not render for my fetcher; content taken from a search-result snippet)
- [D59] https://apps.apple.com/us/app/whisper-transcription/id1668083311

Apple:

- [D60] https://support.apple.com/guide/iphone/dictate-text-iph2c0651d2/ios
- [D61] https://support.apple.com/guide/iphone/commands-for-dictating-text-iph3bf19d7b9/ios
- [D62] https://support.apple.com/guide/mac-help/use-dictation-mh40584/mac
- [D63] https://support.apple.com/guide/mac-help/commands-for-dictating-text-mh40695/mac
- [D64] https://www.apple.com/ios/feature-availability/ (iOS/iPadOS 27; only the first ~100k characters were read, which covered all the Dictation sections)
- [D65] https://www.apple.com/legal/privacy/data/en/ask-siri-dictation/
- [D66] https://9to5mac.com/2025/08/15/aqua-voice-shows-just-how-good-mac-dictation-could-be-if-apple-just-tried/ (not fetched; cited as reported by Aqua [45])

Google:

- [D67] https://support.google.com/gboard/answer/2781851?hl=en (Android)
- [D68] https://support.google.com/gboard/answer/11197787?hl=en (advanced voice typing, Pixel)
- [D69] https://support.google.com/gboard/answer/17468539?hl=en (Rambler)
- [D70] https://blog.google/products-and-platforms/products/search/type-less-talk-more/ (14 Aug 2017)
- [D71] https://support.google.com/gboard/answer/2781851?hl=en&co=GENIE.Platform%3DiOS
- [D72] https://support.google.com/docs/answer/4492226?hl=en (Google Docs voice typing)
- [D73] https://techcrunch.com/2026/05/12/google-adds-gemini-powered-dictation-to-gboard-which-could-be-bad-news-for-dictation-startups/ (named press; search-result snippet only, not fetched)

Pages that could not be fetched: superwhisper.com/pricing (404), typeless.com/download (404; /downloads works), willowvoice.com/privacy (404), goodsnooze.com (DNS failure).
