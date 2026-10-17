# Dictation-app gaps (Task A): design

17 October 2026, branch `feat/v1.7-premium`. The owner asked (2:30 AM) for the comparison to cover dictation apps
(Wispr Flow, Typeless, Superwhisper, Aqua Voice, Willow, MacWhisper, Apple and Google dictation) and for the top
feasible gaps to be built on every platform where the dictation pipeline lives. The owner opted out of questions for
this overnight run, so every decision below is **the builder's call**, recorded so the owner can check it.

## Intent

Dictation apps win on what happens *after* recognition: fillers gone, commands obeyed, the user's own words spelled
their way, lists that look like lists. Humming already has most of this on one platform or another; the gaps are uneven
parity and two missing behaviours. Close them with deterministic, on-device rules (no new services, no models).

## What exists (read from source)

| Behaviour | Android (`TextCleanup.kt`, `VoiceCommands.kt`) | Desktop (`text_cleanup.rs`, `lib.rs`, core) | iOS (`TextCleanup.swift`) |
|---|---|---|---|
| Filler removal (um, uh, er, hmm, ", you know,") | yes, only as part of Clean mode | yes, own switch (`remove_fillers`) | yes, only as part of Clean mode |
| "new line" / "new paragraph" | yes | yes | yes |
| "scratch that" between commas (self-correction) | yes | yes | yes |
| "delete that" / "scratch that" as its own sentence | yes (`VoiceCommands`) | **no** (core's `RETRACT_SENTENCE` is not on the desktop path) | **no** |
| Personal dictionary, casing ("okonkwo" → "Okonkwo") | yes | yes | yes |
| Personal dictionary, replacement ("cube cuddle" → "kubectl") | **no** | yes (`spoken` → `written`) | **no** |
| Spoken lists ("first, eggs, second, milk") | **no** (`LocalEngine.cleanup` is a pass-through) | yes (`format_spoken_list`) | **no** |
| "bullet point" command | **no** | **no** | **no** |

## Decisions (builder's calls)

1. **Replacements on Android and iOS** use one line syntax: `spoken -> written` (also `→`), optionally followed by the
   existing ` @app` tag on Android: `cube cuddle -> kubectl @termux`. Replacements run first, on the raw transcript
   (as desktop does), case-insensitive, whole words, literal output. The written side also joins the casing list.
   A line without an arrow keeps today's meaning. A line with an empty side is ignored.
2. **Filler switch** on Android and iOS: "Remove filler words", default on, inside Clean mode. Off keeps um/uh/er/hmm,
   ", you know," and leading "so, / well, / okay,". Accuracy mode's strict filler rules need it on (desktop's rule:
   `strict = strict && remove_fillers`). The filler list is not widened: it stays conservative.
3. **"bullet point" command** on all three: "bullet point" / "next bullet" / "new bullet" starts a list item.
   Done as one pure post-pass on the finished text (`ListFormat` on Android and iOS, `bullet_list` in
   `text_cleanup.rs`), so the existing cleanup order is untouched. Rules: text before the first marker is the intro
   (a trailing word gets ":"; a trailing sentence mark is kept); each item is trimmed of trailing `,.;:`, capitalised,
   and ends at the first line break, after which the rest of the text follows the list; an empty item is dropped.
   "bullet points" (plural, the AI-instruction phrase on Android) is not a marker.
4. **Spoken lists** on Android and iOS: a port of core's `format_spoken_list` (needs an opener *and* a second marker,
   so a lone "first" in prose is left alone). Runs after the bullet pass, only when it found no bullets.
5. **Sentence-level "delete that"** on desktop and iOS: a port of Android's `VoiceCommands` delete rule ("delete that",
   "scratch that", "undo that", "remove that", "delete the last sentence", optional "okay," before it) said as its own
   sentence removes the sentence before it. Desktop: under the existing "voice commands" switch. iOS: always (iOS has no
   commands switch, like Android).
6. Settings copy: Android Voice screen and iOS Settings gain the filler switch, the arrow syntax in the custom-words
   help, and "bullet point" in the voice-commands help. Desktop help gains "bullet point" and "scratch that".

## Non-goals

- Select/replace by voice in text already in the field ("replace X with Y", "select the last sentence"): needs
  `InputConnection` editing on Android and accessibility APIs on desktop; ranked, left open.
- Per-app tone, AI command mode, whisper mode: need a model or a service; left open.
- Widening the filler list ("like", "basically" outside commas): stays in Accuracy mode only, to avoid eating meaning.

## Anti-goals

- Any rule that deletes words a user meant (hence: "bullet point" only as an exact phrase, lists need two markers).
- Changing existing outputs: every existing test must still pass unchanged.

## Verification

Tests first on each platform (Kotlin JVM tests, Rust `#[test]`, Swift XCTest), pending CI because nothing builds here.
Locally: a single-file Java mirror of the new pure functions (same `java.util.regex` engine as Kotlin on the JVM),
RED before GREEN, kept outside the repository.
