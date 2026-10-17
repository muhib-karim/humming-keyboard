package com.humming.keyboard

/**
 * Humming 1.5: per-app custom words. Pure logic (AppVocabularyTest).
 *
 * A dictionary line is a term, optionally followed by " @" and a comma-separated list of app words:
 * "Kubernetes @slack, termux". The term applies only while the focused app's package name contains one of
 * the words (case-insensitive). Lines without " @" apply in every app. "name@example.com" is not a tag (no space).
 *
 * 1.7: "spoken -> written" (or "→") replaces what the recogniser heard: "cube cuddle -> kubectl @termux". The written
 * side is also a casing term. A line with an empty side is ignored.
 */
object AppVocabulary {
    /** [term] is the written form; [spoken] is what it replaces ("" = a casing term only). */
    data class Line(val term: String, val apps: List<String>, val spoken: String = "")

    private val ARROW = Regex("\\s*(?:->|→)\\s*")

    fun parse(line: String): Line? {
        val t = line.trim()
        if (t.isEmpty()) return null
        val at = t.lastIndexOf(" @")
        val term = if (at < 0) t else t.substring(0, at).trim()
        val apps = if (at < 0) emptyList<String>() else t.substring(at + 2).split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (term.isEmpty()) return null
        val arrow = ARROW.find(term) ?: return Line(term, apps)
        val spoken = term.substring(0, arrow.range.first).trim()
        val written = term.substring(arrow.range.last + 1).trim()
        if (spoken.isEmpty() || written.isEmpty()) return null
        return Line(written, apps, spoken)
    }

    fun appliesTo(l: Line, pkg: String?): Boolean {
        if (l.apps.isEmpty()) return true
        val p = pkg?.lowercase() ?: return false
        return l.apps.any { p.contains(it) }
    }

    /** The terms that apply while [pkg] has focus, de-duplicated, in order. */
    fun select(lines: List<String>, pkg: String?): List<String> =
        lines.mapNotNull { parse(it) }.filter { appliesTo(it, pkg) }.map { it.term }.distinct()

    /** The "spoken -> written" pairs that apply while [pkg] has focus, in order. */
    fun replacements(lines: List<String>, pkg: String?): List<Pair<String, String>> =
        lines.mapNotNull { parse(it) }.filter { it.spoken.isNotEmpty() && appliesTo(it, pkg) }.map { it.spoken to it.term }
}
