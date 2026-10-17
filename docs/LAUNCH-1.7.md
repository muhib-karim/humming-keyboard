# Humming 1.7: an offline voice keyboard that publishes its accuracy

*Launch notes for Humming 1.7.0, released 17 October 2026.*
[Release](https://github.com/muhib-karim/humming-keyboard/releases/tag/v1.7.0) ·
[Website](https://muhib-karim.github.io/humming-keyboard/) ·
[Changelog](../CHANGELOG.md)

Voice typing is the fastest way to write on a phone, yet the usual choices force a trade. The dictation
apps that turn speech into clean text run in the cloud, so your voice leaves the device, and their free
tiers cap how many words you can dictate. Gboard's offline voice typing needs a Pixel 6 or later. And
Gboard, SwiftKey and Apple publish no accuracy figure at all.

Humming is my answer: a free, open-source keyboard and dictation app that types what you say on the
device, on Android, iPhone, Windows, macOS and Linux, and that measures its own accuracy in public.

## What 1.7 adds

**Bangla phonetic typing on Android.** Add Bengali with a QWERTY layout and type romanised Bangla the
way Avro and Ridmik users already do: `ami bangla likhi` becomes আমি বাংলা লিখি. The Bangla word is the
suggestion that space or punctuation commits, and the Latin word stays one tap away. The transliterator
is rule-based and written for Humming. It handles reph, ra-, ya- and ba-phala, ং and ঙ, জ্ঞ and ক্ষ,
Avro-style capitals and Bangla digits, and every rule has a unit test.

**Dictation that fits the sentence.** Dictating into the middle of a line lowercases a common first word,
adds no space after an opening bracket, quote, `@` or `#`, and drops the final period when the text after
the cursor carries on. Names, "I", acronyms and custom words keep their capitals.

**Spoken lists and replacements everywhere.** Say "bullet point" to start a list item, or "first, ...
second, ..." and the result becomes bullets. A custom-words line written `spoken -> written` replaces what
the recogniser heard, so "cube cuddle" types `kubectl`. "Scratch that" removes the previous sentence. The
same clean-up rules and the same test sentences run in Kotlin on Android, Rust on desktop and Swift on iOS.

**A finished look.** Accessible touch targets and screen-reader labels on the voice panel, light-theme text
that meets WCAG AA contrast, and a rebuilt website with real captures and no third-party requests.

## The numbers

Word error rate (WER, the share of words the recogniser gets wrong) on LibriSpeech, using the exact model
files, SHA-256 pins and decoder settings the apps ship. Measured in CI on the release commit, on four
machines (Linux x64 and arm64, macOS, Windows), 2 CPU threads each, over a fixed sample of 200 test-clean
and 100 test-other utterances. Treat each figure as ±0.5 points.

| Model | Size | WER test-clean | WER test-other | Median time to text (test-clean) |
|---|---:|---:|---:|---:|
| Fast (default), Parakeet TDT-CTC 110M int8 | 104 MB | 2.74–2.91 % | 4.07–4.26 % | 99–193 ms |
| Best, Parakeet TDT 0.6B v2 int8 | 482 MB | 1.66–1.79 % | 3.06–3.25 % | 337–671 ms |
| Lite (desktop only), Moonshine tiny int8 | 30 MB | 4.49–4.57 % | 11.78–12.03 % | 85–134 ms |

Ranges span the four machines; per-machine results and logs are in CI run
[39053406494](https://github.com/muhib-karim/humming-keyboard/actions/runs/39053406494). Anyone can reproduce the run on their own fork with one command:

```sh
gh workflow run wer-bench.yml -f n_clean=200 -f n_other=100
```

Two things these numbers do not say. Speed is desktop-CPU speed; phone latency has not been benchmarked,
so none is claimed. And the WER figures some commercial dictation apps publish come from their own test
sets, so they cannot be compared with this table; [COMPETITORS.md](../COMPETITORS.md) lists them
with their sources.

## How it works

Audio is captured at 16 kHz, cut at pauses by Silero VAD and recognised by NVIDIA Parakeet or Moonshine
models running on sherpa-onnx (on iPhone, Apple's recogniser with on-device recognition required). The
text then passes through the clean-up rules (fillers, self-corrections, lists, commands, custom words), is
fitted to the sentence around the cursor, and is shown as an underlined draft while you speak and typed
once when you stop. Models are downloaded once, from pinned URLs with SHA-256 checks. Nothing about your
speech leaves the device unless you switch on the optional AI clean-up, which sends text only, with your
own key.

The Android keyboard is a fork of FlorisBoard, so it is a full keyboard and not only a microphone: glide
typing, prediction in six languages, emoji search, clipboard history, themes, and one-handed, floating
and split layouts. The desktop app is built with Tauri; the iPhone app and keyboard extension are SwiftUI.

## Where Humming trails

- Offline voice recognition is English only. Bangla is typed phonetically, not yet dictated offline.
- It cannot yet edit text already in the field by voice, or adapt tone per app.
- Gboard and SwiftKey type in far more languages and install from the app stores; Humming is sideloaded,
  and the iPhone build must be re-signed every 7 days.

## Try it

Android, Windows, macOS and Linux builds are on the
[1.7.0 release](https://github.com/muhib-karim/humming-keyboard/releases/tag/v1.7.0), with
`SHA256SUMS.txt` and signing details so you can verify the APK before installing. Humming is
Apache-2.0, with no ads, no analytics and no account. Issues and pull requests are welcome.
