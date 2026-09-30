package com.humming.keyboard.predict

import java.util.Locale

/**
 * Lays the ranked candidates out on the three-slot strip the way Gboard does: the best guess in the middle.
 * - Nothing typed (next word): second | best | third.
 * - Autocorrect pending: "typed" (quoted, commits exactly what was typed) | correction (bold, space commits it) | next.
 * - The typed word is a real word and the best guess: next | typed | third.
 * - Otherwise: typed | best | second — so what was typed is always one tap away.
 */
object StripOrder {
    enum class Kind { WORD, LITERAL, AUTOCORRECT }
    data class Slot(val text: String, val kind: Kind)

    fun arrange(r: Predictor.Result, slots: Int = 3): List<Slot> {
        val words = r.ranked.map { it.word }.distinctBy { it.lowercase(Locale.ROOT) }
        if (r.typed.isEmpty()) return middleFirst(words.take(slots).map { Slot(it, Kind.WORD) })
        val typed = r.typed
        val others = words.filter { !it.equals(typed, ignoreCase = true) }
        val out = ArrayList<Slot>(slots)
        when {
            r.autocorrect != null -> {
                out.add(Slot(typed, Kind.LITERAL))
                out.add(Slot(r.autocorrect, Kind.AUTOCORRECT))
                others.filter { !it.equals(r.autocorrect, ignoreCase = true) }.take(slots - 2).forEach { out.add(Slot(it, Kind.WORD)) }
            }
            r.typedIsWord && words.firstOrNull()?.equals(typed, ignoreCase = true) == true -> {
                val rest = others.take(slots - 1)
                rest.getOrNull(0)?.let { out.add(Slot(it, Kind.WORD)) }
                out.add(Slot(typed, Kind.WORD))
                rest.drop(1).forEach { out.add(Slot(it, Kind.WORD)) }
            }
            else -> {
                out.add(Slot(typed, if (r.typedIsWord) Kind.WORD else Kind.LITERAL))
                others.take(slots - 1).forEach { out.add(Slot(it, Kind.WORD)) }
            }
        }
        return out.take(slots)
    }

    /** [a, b, c] best-first becomes [b, a, c]: the best in the middle. */
    private fun middleFirst(list: List<Slot>): List<Slot> =
        if (list.size < 2) list else listOf(list[1], list[0]) + list.drop(2)
}
