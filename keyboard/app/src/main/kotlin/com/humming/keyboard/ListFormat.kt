package com.humming.keyboard

/**
 * Humming 1.7: lists in dictation (pure Kotlin: ListFormatTest). Runs on the finished text, after TextCleanup.finish.
 *
 * - "bullet point" / "next bullet" / "new bullet" starts an item: "Groceries bullet point eggs bullet point milk" →
 *   "Groceries:\n- Eggs\n- Milk". A marker after "the", "a", "second"... is the thing itself and stays prose.
 * - A spoken list ("first, eggs, second, milk" / "number one, ..., number two, ...") becomes bullets too; it needs an
 *   opener and a second marker, so a lone "first" in prose is left alone. Same rules as core's `format_spoken_list`.
 * Same rules on desktop (`text_cleanup.rs`) and iOS (`ListFormat.swift`).
 */
object ListFormat {
    private val BULLET = Regex(
        "[ \\t]*[,;:]?[ \\t]*\\b(?:bullet point|next bullet|new bullet)\\b[,.;:]?[ \\t]*",
        RegexOption.IGNORE_CASE,
    )
    private val NOT_AFTER = setOf(
        "a", "an", "the", "this", "that", "each", "every", "another", "my", "your", "our", "first", "second", "third",
        "last", "one",
    )
    private val LIST_MARKER = Regex(
        "(?:^|[\\s,.;:])((?:and\\s+)?(?:first(?:ly)?|second(?:ly)?|third(?:ly)?|fourth(?:ly)?|fifth(?:ly)?|finally|lastly|" +
            "number\\s+(?:one|two|three|four|five|[1-5])))\\b,\\s+",
        RegexOption.IGNORE_CASE,
    )

    fun apply(text: String): String = bullets(text) ?: spokenList(text) ?: text

    /** The text with "bullet point" items as "- " lines, or null when it has no (non-empty) item. */
    fun bullets(text: String): String? {
        val parts = mutableListOf<String>()
        var from = 0
        for (m in BULLET.findAll(text)) {
            val prev = text.substring(0, m.range.first).trim().split(Regex("\\s+")).last()
            if (prev.lowercase() in NOT_AFTER) continue
            parts.add(text.substring(from, m.range.first))
            from = m.range.last + 1
        }
        parts.add(text.substring(from))
        if (parts.size < 2) return null
        val items = mutableListOf<String>()
        var tail = ""
        for (i in 1 until parts.size) {
            var p = parts[i]
            val nl = p.indexOf('\n')
            if (nl >= 0) {
                // The last item ends at its line break and the rest follows the list; earlier items are one line.
                if (i == parts.size - 1) { tail = p.substring(nl); p = p.substring(0, nl) } else p = p.replace(Regex("\\s+"), " ")
            }
            val item = trimItem(p)
            if (item.isNotEmpty()) items.add(capitalize(item))
        }
        if (items.isEmpty()) return null
        var intro = parts[0].trim()
        if (intro.isNotEmpty()) {
            val last = intro.last()
            if (last.isLetterOrDigit()) intro += ":" else if (last == ',' || last == ';') intro = intro.dropLast(1) + ":"
        }
        val head = if (intro.isEmpty()) "" else intro + "\n"
        return head + items.joinToString("\n") { "- $it" } + tail
    }

    /** "The list is first, eggs, second, milk." → "The list is:\n- Eggs\n- Milk", or null when it is not a list. */
    fun spokenList(text: String): String? {
        val marks = LIST_MARKER.findAll(text).map { m ->
            Triple(m.groups[1]!!.range.first, m.range.last + 1, m.groupValues[1].lowercase())
        }.toList()
        val opener = marks.indexOfFirst {
            val w = it.third.removePrefix("and ").trim()
            w.startsWith("first") || w == "number one" || w == "number 1"
        }
        if (opener < 0 || marks.size - opener < 2) return null
        val second = marks[opener + 1].third.removePrefix("and ")
        if (!(second.startsWith("second") || second.endsWith("two") || second.endsWith("2") || second == "finally")) return null
        val intro = text.substring(0, marks[opener].first).replace(Regex("[\\s,.;:]+$"), "")
        val items = mutableListOf<String>()
        for (k in opener until marks.size) {
            val end = if (k + 1 < marks.size) marks[k + 1].first else text.length
            var raw = trimItem(text.substring(marks[k].second, end))
            if (raw.endsWith(" and")) raw = trimItem(raw.dropLast(4))
            if (raw.isEmpty()) return null
            items.add(capitalize(raw))
        }
        return (if (intro.isEmpty()) "" else "$intro:\n") + "- " + items.joinToString("\n- ")
    }

    private fun trimItem(s: String) = s.trim().trimEnd(',', '.', ';', ':').trim()

    private fun capitalize(s: String) = if (s.isEmpty()) s else s.substring(0, 1).uppercase() + s.substring(1)
}
