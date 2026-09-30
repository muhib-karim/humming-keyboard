package com.humming.keyboard

/**
 * Rule-based dictation cleanup that runs on every transcript (on-device and cloud), after the core's own cleanup.
 * Pure Kotlin, no Android types, so it is covered by JVM unit tests (TextCleanupTest).
 *
 * Order: drop non-speech/hallucinations → fillers → spoken punctuation → leading discourse markers →
 * self-corrections ("3, no, 4pm" → "4pm") → repeated words → dictionary casing → capitals and final punctuation.
 */
object TextCleanup {
    private const val W = "[\\p{L}\\p{N}_']"

    private val NON_SPEECH = Regex(
        "\\[[^\\]]*]|\\((?:[^)]*(?:music|applause|silence|blank|inaudible|laugh)[^)]*)\\)|\\*[^*]+\\*",
        RegexOption.IGNORE_CASE,
    )
    private val FILLER = Regex("(?<!$W)(?:um+|uh+|erm+|er|hmm+)(?!$W)[,.]?\\s*", RegexOption.IGNORE_CASE)
    private val YOU_KNOW = Regex(",?\\s*\\byou know\\b,\\s*", RegexOption.IGNORE_CASE)
    private val LEADING = Regex("^(?:(?:so|well|okay|ok|like|right)\\s*,\\s*)+", RegexOption.IGNORE_CASE)
    private val MARKER = Regex(
        ",\\s*(no wait|wait,? no|sorry,? i mean|i mean|sorry|no|scratch that|make that|or rather)\\s*,?\\s+",
        RegexOption.IGNORE_CASE,
    )
    // Speech recognition often hears the "no" in "3, no, 4pm" as "oh"/"know"/"nope", or fuses it into "3.0.4pm".
    // Both are rewritten to "3, no, 4pm" (only when the second number carries a time or unit) so the rule below applies.
    private const val UNIT = "(?:[ap]\\.?\\s?m\\.?|o'?clock|%|percent|dollars?|bucks|minutes?|mins?|hours?|days?|weeks?|k|kg|km|miles?)"
    private val NO_FUSED = Regex(
        "(?<![\\d.])(\\d{1,2})\\.0\\.(\\d{1,2}(?::\\d\\d)?\\s*(?:[ap]\\.?\\s?m\\.?|o'?clock))(?!\\w)",
        RegexOption.IGNORE_CASE,
    )
    private val NO_MISHEARD = Regex(
        "(?<![\\w.])(\\d+(?:[.:]\\d+)?)\\s*[,.]?\\s*(?:no|oh|know|nope)\\s*[,.]?\\s+(\\d+(?:[.:]\\d+)?\\s*$UNIT)(?!\\w)",
        RegexOption.IGNORE_CASE,
    )
    private val SPOKEN = listOf(
        Regex("\\s*\\bquestion mark\\b", RegexOption.IGNORE_CASE) to "?",
        Regex("\\s*\\bexclamation (?:mark|point)\\b", RegexOption.IGNORE_CASE) to "!",
        Regex("\\s*\\bfull stop\\b", RegexOption.IGNORE_CASE) to ".",
        Regex("\\s*\\bnew paragraph\\b\\s*", RegexOption.IGNORE_CASE) to "\n\n",
        Regex("\\s*\\bnew line\\b\\s*", RegexOption.IGNORE_CASE) to "\n",
        Regex("\\s*\\bcomma\\b", RegexOption.IGNORE_CASE) to ",",
    )
    private val KEEP_REPEAT = setOf(
        "had", "that", "very", "really", "so", "no", "bye", "ha", "haha", "go", "too", "more", "knock", "yeah", "yes",
    )
    private val QUESTION_START = setOf(
        "what", "why", "how", "when", "where", "who", "which", "is", "are", "do", "does", "did", "can", "could",
        "would", "will", "should", "shall", "may", "am", "was", "were", "have", "has",
    )

    /** Whisper's classic outputs on silence or noise; a transcript that is only one of these inserts nothing. */
    private val HALLUCINATIONS = setOf(
        "thank you for watching", "thanks for watching", "thank you so much for watching", "please subscribe",
        "like and subscribe", "you",
    )

    fun apply(raw: String, dictionary: List<String> = emptyList()): String {
        var t = NON_SPEECH.replace(raw, "")
        if (isHallucination(t)) return ""
        t = FILLER.replace(t, "")
        t = YOU_KNOW.replace(t, " ")
        for ((re, rep) in SPOKEN) t = re.replace(t, rep)
        t = collapseSpaces(t)
        t = LEADING.replace(t, "")
        t = selfCorrect(t)
        t = removeRepeats(t)
        t = applyDictionary(t, dictionary)
        t = compactTimes(t)
        return finish(t)
    }

    private val TIME_AMPM = Regex("(?<![\\p{L}\\p{N}])(\\d{1,2}(?::\\d{2})?)\\s?([AaPp])\\.?\\s?[Mm](?=[\\s,.;!?]|$)")

    /** "4 pm", "4 p.m.", "10:30 AM" → "4pm", "10:30am" (the way people type times). */
    fun compactTimes(input: String): String =
        TIME_AMPM.replace(input) { m -> m.groupValues[1] + m.groupValues[2].lowercase() + "m" }

    fun isHallucination(text: String): Boolean {
        val n = normalize(text)
        return n in HALLUCINATIONS || n.startsWith("subtitles by") || n.startsWith("transcribed by")
    }

    private fun normalize(s: String) = s.lowercase().replace(Regex("[^\\p{L}\\p{N}\\s]"), "").trim().replace(Regex("\\s+"), " ")

    private fun bare(token: String) = token.replace(Regex("[^\\p{L}\\p{N}_']"), "").lowercase()

    private fun collapseSpaces(s: String) = s.replace(Regex("[ \\t]+"), " ").trim()

    /** "meet at 3, no, 4pm" → "meet at 4pm"; "to John, sorry, I mean Jane" → "to Jane". */
    fun selfCorrect(input: String): String {
        var text = NO_MISHEARD.replace(NO_FUSED.replace(input, "$1, no, $2"), "$1, no, $2")
        repeat(5) {
            val m = MARKER.find(text) ?: return text
            val marker = m.groupValues[1].lowercase()
            val pre = text.substring(0, m.range.first)
            val boundary = maxOf(pre.lastIndexOf(". "), pre.lastIndexOf("? "), pre.lastIndexOf("! "), pre.lastIndexOf('\n'))
            val head = when {
                boundary < 0 -> ""
                pre[boundary] == '\n' -> pre.substring(0, boundary + 1)
                else -> pre.substring(0, boundary + 2)
            }
            val seg = pre.substring(head.length)
            val post = text.substring(m.range.last + 1)
            val end = Regex("[.?!;\\n]").find(post)
            val y = if (end != null) post.substring(0, end.range.first) else post
            val tail = if (end != null) post.substring(end.range.first) else ""
            val bw = seg.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            val yw = y.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (bw.isEmpty() || yw.isEmpty()) return text

            val y0 = bare(yw[0])
            var cut = bw.indexOfLast { bare(it) == y0 }.takeIf { it >= 0 }
            if (cut == null && yw[0].any { it.isDigit() }) {
                cut = bw.indexOfLast { w -> w.any { it.isDigit() } }.takeIf { it >= 0 }
            }
            if (cut == null && yw[0].first().isUpperCase()) {
                cut = (bw.size - 1 downTo 1).firstOrNull { bw[it].first().isUpperCase() }
            }
            if (cut == null) {
                // A bare "no" with no clear slot to replace is probably a real "no": leave the sentence alone.
                if (marker == "no") return text
                val n = if (yw.size == 1) 1 else minOf(yw.size, bw.size - 1)
                cut = if (bw.size > 1) maxOf(bw.size - n, 0) else 0
            }
            text = head + (bw.subList(0, cut) + yw).joinToString(" ") + tail
        }
        return text
    }

    /** "I I think we we should go" → "I think we should go"; "we should we should go" → "we should go". */
    fun removeRepeats(input: String): String {
        val out = mutableListOf<String>()
        for (tok in input.split(" ")) {
            if (out.isNotEmpty()) {
                val prev = out.last()
                val a = bare(prev)
                val b = bare(tok)
                val prevEndsClause = prev.isNotEmpty() && prev.last() in ",.?!;:"
                if (a.isNotEmpty() && a == b && a !in KEEP_REPEAT && !prevEndsClause) {
                    val trailing = tok.substring(tok.indexOfLast { it.isLetterOrDigit() || it == '\'' } + 1)
                    out[out.size - 1] = prev.trimEnd { !(it.isLetterOrDigit() || it == '\'') } + trailing
                    continue
                }
            }
            out.add(tok)
        }
        return Regex("(?i)\\b(\\p{L}+ \\p{L}+) \\1\\b").replace(out.joinToString(" "), "$1")
    }

    /** Dictionary terms win over whatever casing the engine produced ("okonkwo" → "Okonkwo"). */
    fun applyDictionary(input: String, terms: List<String>): String {
        var t = input
        for (raw in terms) {
            val term = raw.trim()
            if (term.isEmpty()) continue
            val body = term.split(Regex("\\s+")).joinToString("\\s+") { Regex.escape(it) }
            t = Regex("(?<!$W)$body(?!$W)", RegexOption.IGNORE_CASE).replace(t) { term }
        }
        return t
    }

    /** Spacing around punctuation, standalone "i", sentence capitals, and a final mark when one is missing. */
    fun finish(input: String): String {
        var t = collapseSpaces(input)
        t = t.replace(Regex("\\s+([,.?!;:])"), "$1")
        t = t.replace(Regex("([,;:])(?=[^\\s\\d])"), "$1 ")
        t = t.replace(Regex("(?<!$W)i(?!$W)"), "I")
        t = Regex("(^|[.?!]\\s+|\\n)(\\p{Ll})").replace(t) { it.groupValues[1] + it.groupValues[2].uppercase() }
        if (t.isNotEmpty() && (t.last().isLetterOrDigit() || t.last() == '\'' || t.last() == '"')) {
            val lastSentence = t.split(Regex("(?<=[.?!])\\s+")).last()
            val first = lastSentence.split(" ").firstOrNull()?.let { bare(it) } ?: ""
            t += if (first in QUESTION_START) "?" else "."
        }
        return t
    }
}
