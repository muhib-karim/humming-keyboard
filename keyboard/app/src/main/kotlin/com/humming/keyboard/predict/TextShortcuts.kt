package com.humming.keyboard.predict

import java.util.Locale

/**
 * Text expansion: the user's own shortcuts ("omw" -> "On my way!"). Typing a shortcut shows its phrase in the middle
 * of the strip and space expands it (undo with backspace like any correction). Stored as one pref string,
 * one `shortcut<TAB>phrase` per line, so it is part of settings export/import.
 */
object TextShortcuts {
    const val DEFAULTS = "omw\tOn my way!\ntysm\tThank you so much!"

    fun parse(raw: String): Map<String, String> = buildMap {
        for (line in raw.lines()) {
            val tab = line.indexOf('\t')
            if (tab <= 0) continue
            val key = line.substring(0, tab).trim().lowercase(Locale.ROOT)
            val phrase = line.substring(tab + 1).trim().replace("\\n", "\n")
            if (key.isNotEmpty() && phrase.isNotEmpty() && key.all { it.isLetterOrDigit() }) put(key, phrase)
        }
    }

    fun serialize(map: Map<String, String>): String =
        map.entries.joinToString("\n") { (k, v) -> k.trim().lowercase(Locale.ROOT) + "\t" + v.trim().replace("\n", "\\n") }

    fun add(raw: String, shortcut: String, phrase: String): String =
        serialize(parse(raw) + (shortcut.trim().lowercase(Locale.ROOT) to phrase.trim()))

    fun remove(raw: String, shortcut: String): String = serialize(parse(raw) - shortcut.trim().lowercase(Locale.ROOT))
}
