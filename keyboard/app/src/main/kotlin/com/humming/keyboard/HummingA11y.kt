/*
 * Copyright 2026 Humming contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package com.humming.keyboard

import dev.patrickgold.florisboard.ime.text.key.KeyCode

/** Humming: what TalkBack reads for a key. Letters read as themselves; function keys get a spoken name. */
object HummingA11y {
    fun describe(code: Int, label: String?): String = when (code) {
        KeyCode.SPACE -> "Space"
        KeyCode.ENTER -> "Enter"
        KeyCode.DELETE -> "Delete"
        KeyCode.SHIFT -> "Shift"
        KeyCode.CAPS_LOCK -> "Caps lock"
        KeyCode.VIEW_SYMBOLS, KeyCode.VIEW_SYMBOLS2 -> "Symbols"
        KeyCode.VIEW_CHARACTERS -> "Letters"
        KeyCode.VIEW_NUMERIC_ADVANCED -> "Numbers"
        KeyCode.LANGUAGE_SWITCH -> "Switch language"
        KeyCode.IME_UI_MODE_MEDIA -> "Emoji"
        KeyCode.IME_UI_MODE_CLIPBOARD -> "Clipboard"
        KeyCode.IME_UI_MODE_TEXT -> "Back to keyboard"
        KeyCode.VOICE_INPUT -> "Voice typing"
        KeyCode.SETTINGS -> "Keyboard settings"
        else -> when {
            label.isNullOrBlank() -> "Key"
            label == "," -> "Comma"
            label == "." -> "Period"
            label == "?" -> "Question mark"
            label == "!" -> "Exclamation mark"
            label == "'" -> "Apostrophe"
            else -> label
        }
    }
}
