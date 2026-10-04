package com.humming.keyboard

/**
 * Humming 1.5: per-app custom words. Pure logic (AppVocabularyTest).
 *
 * A dictionary line is a term, optionally followed by " @" and a comma-separated list of app words:
 * "Kubernetes @slack, termux". The term applies only while the focused app's package name contains one of
 * the words (case-insensitive). Lines without " @" apply in every app. "name@example.com" is not a tag (no space).
 */
object AppVocabulary {
    data class Line(val term: String, val apps: List<String>)

    fun parse(line: String): Line? {
        val t = line.trim()
        if (t.isEmpty()) return null
        val at = t.lastIndexOf(" @")
        if (at < 0) return Line(t, emptyList())
        val term = t.substring(0, at).trim()
        val apps = t.substring(at + 2).split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (term.isEmpty()) return null
        return Line(term, apps)
    }

    fun appliesTo(l: Line, pkg: String?): Boolean {
        if (l.apps.isEmpty()) return true
        val p = pkg?.lowercase() ?: return false
        return l.apps.any { p.contains(it) }
    }

    /** The terms that apply while [pkg] has focus, de-duplicated, in order. */
    fun select(lines: List<String>, pkg: String?): List<String> =
        lines.mapNotNull { parse(it) }.filter { appliesTo(it, pkg) }.map { it.term }.distinct()
}
