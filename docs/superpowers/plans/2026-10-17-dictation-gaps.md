# Dictation gaps (Task A) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the dictation-app parity gaps: replacements and a filler switch on Android and iOS, a "bullet point"
command on all three platforms, spoken lists on Android and iOS, sentence-level "delete that" on desktop and iOS.

**Architecture:** New behaviour lives in pure functions next to the existing cleanup (`ListFormat` objects on Android and
iOS, functions in `text_cleanup.rs` on desktop) and runs as a post-pass on finished text, so the existing rule order and
outputs are unchanged. Replacements run first on the raw transcript, as desktop already does.

**Tech Stack:** Kotlin (JUnit4 JVM tests), Rust (`fancy_regex`, `#[test]`), Swift (`NSRegularExpression`, XCTest).

**Spec:** `docs/superpowers/specs/2026-10-17-dictation-gaps-design.md`

## Global Constraints

- No local builds (no gradle, cargo, swift, xcode). Kotlin/Rust/Swift tests are pending CI; a Java mirror outside the
  repo gives a local RED/GREEN.
- Every existing test keeps passing unchanged; new parameters have defaults that keep today's behaviour.
- Marker phrases: "bullet point", "next bullet", "new bullet" (case-insensitive, whole words). Arrow: `->` or `→`.
- Delete rule (verbatim from `VoiceCommands.kt`): `^(?:ok(?:ay)?,? )?(?:delete|scratch|undo|remove) (?:that|the last sentence)$`.
- Filler switch default **on**; Android key `remove_fillers`; iOS key `remove_fillers`.

## Review Focus

- "bullet points" (plural) must not start a list (Android AI instruction phrase) → test `pluralBulletPointsIsNotAMarker`.
- A "bullet point" with nothing after it must not leave an empty "- " item → test `emptyBulletIsDropped`.
- Text after the list ("… milk new paragraph thanks") must stay after the list, not inside the last item → test
  `textAfterAListFollowsIt`.
- A replacement must not fire inside a longer word ("cat -> dog" leaves "concatenate") → test `replacementIsWholeWord`.
- A lone "first" in prose stays prose ("first, we eat") → test `loneFirstIsNotAList`.

---

### Task 1: Android `ListFormat` (bullets + spoken lists)

**Files:** Create `keyboard/app/src/main/kotlin/com/humming/keyboard/ListFormat.kt`; Test
`keyboard/app/src/test/kotlin/com/humming/keyboard/ListFormatTest.kt`; Modify `TextCleanup.kt` (`apply` returns
`ListFormat.apply(finish(t))`).

**Interfaces:** Produces `ListFormat.bullets(text: String): String?`, `ListFormat.spokenList(text: String): String?`,
`ListFormat.apply(text: String): String` (= bullets ?: spokenList ?: text).

- [ ] Step 1: tests: `"Groceries bullet point eggs bullet point milk."` → `"Groceries:\n- Eggs\n- Milk"`;
  `"Bullet point, eggs. Bullet point, milk."` → `"- Eggs\n- Milk"`; `"Here is the plan. Bullet point call Sam next bullet book the room."`
  → `"Here is the plan.\n- Call Sam\n- Book the room"`; Review Focus tests; spoken list
  `"The list is first, eggs, second, milk, third, bread."` → `"The list is:\n- Eggs\n- Milk\n- Bread"`; through
  `TextCleanup.apply`: `"groceries bullet point eggs bullet point milk"` → `"Groceries:\n- Eggs\n- Milk"`.
- [ ] Step 2: run the Java mirror: RED.
- [ ] Step 3: implement; spoken list is a line-for-line port of core `format_spoken_list` (`core/src/cleanup.rs`).
- [ ] Step 4: mirror GREEN, plus the existing TextCleanup mirror cases unchanged.
- [ ] Step 5: commit.

### Task 2: Android replacements and filler switch

**Files:** Modify `AppVocabulary.kt` (`Line(term, apps, spoken = "")`, arrow parsing, `replacements(lines, pkg)`),
`TextCleanup.kt` (`apply(raw, dictionary, strict, fillers = true, replacements = emptyList())`,
`replacePhrases(input, pairs)`), `Prefs.kt` (`KEY_FILLERS`, `removeFillers`, `replacements`), call sites
`StreamingSession.kt`, `HummingVoice.kt` (`cleanOnline`), `DictationPipeline.kt`; settings copy in `VoiceScreen.kt`;
Tests in `AppVocabularyTest.kt`, `TextCleanupTest.kt`.

**Interfaces:** Produces `AppVocabulary.replacements(lines: List<String>, pkg: String?): List<Pair<String, String>>`,
`TextCleanup.replacePhrases(input: String, pairs: List<Pair<String, String>>): String`.

- [ ] Step 1: tests: `parse("cube cuddle -> kubectl @termux")` == `Line("kubectl", listOf("termux"), "cube cuddle")`;
  `select` returns `kubectl` in termux only; `"a → b"` parses; `"-> x"` and `"x ->"` are ignored as replacements;
  `apply("run cube cuddle now", replacements = listOf("cube cuddle" to "kubectl"))` == `"Run kubectl now."`;
  `replacementIsWholeWord`; `apply("um I think so", fillers = false)` keeps "Um"; default still removes it.
- [ ] Step 2-4: mirror RED → implement → GREEN.
- [ ] Step 5: commit.

### Task 3: Desktop bullets and sentence-level delete

**Files:** Modify `desktop/src-tauri/src/text_cleanup.rs` (`pub fn bullet_list(text: &str) -> Option<String>`,
`pub fn retract_sentences(text: &str) -> String`; `apply` ends with `retract_sentences(finish)` when
`o.voice_commands`), `desktop/src-tauri/src/lib.rs` (`finalize_text_for`: bullets when `s.voice_commands`, before the
spoken-list step); help copy in `desktop/index.html`.

- [ ] Step 1: Rust tests mirroring Task 1's bullet cases and `"Book Friday. Scratch that. Book Saturday."` →
  `"Book Saturday."`; `"Delete that."` alone → `""`; `voice_commands: false` keeps the sentence.
- [ ] Step 2-4: logic checked through the Java mirror (same patterns); Rust pending CI.
- [ ] Step 5: commit.

### Task 4: iOS parity

**Files:** Create `ios/HummingCore/Sources/HummingCore/ListFormat.swift`; Modify `TextCleanup.swift`
(`apply(_:dictionary:strict:fillers:replacements:)`, `replacePhrases`, `vocabulary(_ lines:)`, `retractSentences`),
`ios/App/AppSettings.swift` (`removeFillers`), `ios/App/Dictation.swift`, `ios/App/SettingsView.swift`; Tests in
`TextCleanupTests.swift`.

- [ ] Step 1: same cases as Tasks 1-3 in XCTest.
- [ ] Step 2-4: pending CI (no Swift here); patterns identical to the mirrored Kotlin ones.
- [ ] Step 5: commit.

### Task 5: Docs

COMPETITORS.md "Dictation apps" section and ranked dictation gap list (✅ for Tasks 1-4), site comparison, README,
CHANGELOG 1.7.0-rc1, user guide, SKILLS_USED.md, TASKS.md.
