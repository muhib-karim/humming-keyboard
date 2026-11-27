---
name: humming-voice-rules
description: Modify text cleanup, spoken commands, list formatting, or edit-by-voice rules across core/ Rust, keyboard/ Kotlin, and ios/HummingCore Swift. Use only when touching cleanup, list parsing, or VoiceEdit behavior.
---

# Humming Cross-Platform Voice Rules

1. **Three-way parity**: keep rule behavior aligned across:
   - Rust (`core/src/cleanup.rs`, `core/src/voice_edit.rs`, `desktop/src-tauri/src/text_cleanup.rs`, `desktop/src-tauri/src/voice_edit.rs`)
   - Kotlin (`keyboard/app/src/main/kotlin/com/humming/keyboard/{TextCleanup.kt,ListFormat.kt,VoiceCommands.kt,VoiceEdit.kt}`)
   - Swift (`ios/HummingCore/Sources/HummingCore/...`)
2. **Shared test vectors**: add/update cases in `core/testdata/voice_edit_vectors.json` (and any shared cleanup vectors) so Kotlin, Swift, and Rust tests verify identical outputs.
3. **Android ICU regex pitfall**: NEVER use `java.util.regex.Pattern.UNICODE_CHARACTER_CLASS` on Android (throws `IllegalArgumentException` at runtime on Android ART). Use explicit Unicode property boundaries (`(?<![\p{L}\p{M}\p{N}_])` / `(?![\p{L}\p{M}\p{N}_])`) instead of `\b` or `UNICODE_CHARACTER_CLASS`, and cover non-ASCII Unicode targets in `ImeSmokeTest`.
4. **Multilingual guard**: keep English filler/self-correction/command rules disabled for the Multilingual model (`english_cleanup("multi") == false`).
