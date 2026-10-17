# v1.7 competitor gaps: design

Status: decided autonomously on 2026-10-17. The owner's brief fixed the order and constraints and asked for no
questions, so every choice below is **the builder's call**, recorded so it can be reviewed.

## Intent

[COMPETITORS.md](../../../COMPETITORS.md) shows three gaps where Humming loses to Gboard, SwiftKey and the Bangla
keyboards, and that can be closed safely without a local build (pure logic, unit-tested in CI):

1. **Bangla phonetic typing.** Ridmik Keyboard and Avro let Bangla speakers type romanised Bangla ("ami tomake
   bhalobashi") and get Bangla script. Humming only has the fixed `bengali_bd` layout, which most Bangla users don't
   touch-type.
2. **Dictation that fits the sentence it lands in.** Humming always capitalises the first word and adds a final period,
   and glues with a space even after "(" or a quote. Dictating into the middle of a sentence leaves
   "I told him. Hello there." where Gboard gives "I told him hello there".
3. **More spoken punctuation and a "delete last word" command.** Gboard and SwiftKey take "colon", "semicolon",
   quotes and brackets; Humming takes only "comma", "full stop", "question mark", "exclamation mark", "new line",
   "new paragraph".

## Goals

- A: on Android, a Bengali subtype with a Latin (QWERTY) layout shows the Avro-style Bangla transliteration of the
  word being typed as the auto-commit suggestion, so **space or punctuation commits Bangla**; the typed Latin word stays
  available as a second suggestion. `.` maps to the dari `।` only when the user picks it, never automatically.
- B: on Android, dictated text is adapted to the text before the cursor (and just after it):
  - no leading space after whitespace, an opening bracket or quote, or at the start of the field;
  - when continuing a sentence (the text before ends in a letter, digit or `,;:`), a capitalised common word at the
    start ("The", "And", "We") is lowercased, but "I", names, acronyms and dictionary terms are kept;
  - when the text after the cursor continues the sentence (starts with a letter or digit), the final period the
    clean-up added is dropped and a trailing space is added.
- C: on Android, desktop and iOS (the same rule in Kotlin, Rust and Swift, each with tests): "colon", "semicolon",
  "open quote" / "close quote" / "end quote" / "unquote", "open bracket" / "open paren(thesis)",
  "close bracket" / "close paren(thesis)", "ellipsis" / "dot dot dot", and "delete last word" /
  "scratch the last word" (removes the word before it).

## Non-goals

- Full Avro parity (Avro's dictionary-backed suggestions and its full rule table). This pass is a rule-based
  transliterator written from scratch for Humming, not a copy of Avro's MPL rule data.
- Bangla offline voice typing (no on-device Bangla Parakeet model exists in this repo).
- Context-aware joining on desktop (it pastes into other apps and cannot read their text) and on iOS (the keyboard
  inserts finished text; left for a later pass).
- "period" and "dash" as spoken punctuation mid-sentence: too ambiguous ("a period of time", "dash to the shop").

## Anti-goals

- Any change that alters current output for text that contains none of the new spoken words (existing tests must
  pass unchanged).
- Lowercasing a proper noun at the start of a dictation (a wrong lowercase is worse than a missed one).
- Auto-converting Latin words on non-Bengali subtypes.

## Constraints

- No local builds: Kotlin, Rust and Swift are compiled and tested only by CI. Code must match existing APIs exactly
  and use no new dependencies.
- Pure logic lives in plain Kotlin objects without Android types (JVM tests), like `TextCleanup`.

## How (builder's calls)

- **A** `com.humming.keyboard.BanglaPhonetic.transliterate(latin)`: greedy longest match over a rule table; vowels
  become dependent signs (kar) after a consonant and independent letters elsewhere; `o` after a consonant is the
  inherent vowel; two joining consonants in a row get a hasanta (`্`), which also yields reph (`rk` → `র্ক`),
  ra-phala (`pr` → `প্র`), ya-phala (`by` → `ব্য`) and ba-phala (`sw` → `স্ব`); `ng` → `ং`, `Ng` → `ঙ`, `gg` → `জ্ঞ`,
  `kkh` → `ক্ষ`, digits → Bangla digits; capitals carry Avro's retroflex/long-vowel meaning (T, D, N, R, Sh, I, U, O).
  Bengali has no auto-capitalisation in the keyboard (`FlorisLocale.supportsCapitalization` is false for `bn`), so
  case-as-typed is reliable. Wiring: `LatinLanguageProvider.suggest` returns
  `[Bangla (auto-commit), "typed"]` for a `bn` subtype when the word is ASCII letters.
- **B** `com.humming.keyboard.DictationJoin.adapt(before, text, after, dictionary)` returns the glue and the adapted
  text. `DictationOutput` gets an optional `adapt: (String) -> String` (identity by default, so its invariants and
  tests are unchanged); `HummingVoice` passes `DictationJoin` with the editor's text before and after the cursor.
- **C** extend the `SPOKEN` table in all three ports in the same order, plus a `deleteLastWord` pass; extend the
  sentence-start capital rule to allow an opening quote or bracket before the letter.

## Left to the builder

Exact rule-table entries beyond the ones listed, test sentences, and wording in the user guide.
