@file:Suppress("ReturnCount", "ComplexCondition")

package com.humming.keyboard

import java.util.regex.Pattern

/**
 * D5 voice-command editing: a whole dictated utterance such as "change Tuesday to Wednesday" edits the text
 * the user already has instead of being inserted. Pure-Kotlin port of core/src/voice_edit.rs with the same
 * rules, checked against the same shared vectors (core/testdata/voice_edit_vectors.json) by VoiceEditTest.
 *
 * Safety rules: whole utterance only (a leading "um"/"okay" and a trailing full stop are ignored); at most
 * 14 words; the target must exist in the text before the cursor, otherwise the utterance stays dictation.
 * The match closest to the cursor (last, whole-word, case-insensitive) is the one edited.
 *
 * Offsets here are UTF-16 indices (what InputConnection counts); the Rust core reports Unicode scalars.
 */
object VoiceEdit {
    /** How much text before the cursor a voice edit may look at. */
    const val CONTEXT_CHARS = 1000

    sealed class Command {
        data class Replace(val target: String, val replacement: String) : Command()
        data class Delete(val target: String) : Command()
        data class Capitalize(val target: String) : Command()

        /** "undo that" / "scratch that": the host removes its own last insertion. */
        object Undo : Command()
    }

    /** [text] is the whole edited text; the span [start, start+oldLen) of the original now reads [inserted]. */
    data class Outcome(val text: String, val start: Int, val oldLen: Int, val inserted: String)

    data class Result(val command: Command, val outcome: Outcome?)

    private const val MAX_WORDS = 14
    private const val F = Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE or Pattern.UNICODE_CHARACTER_CLASS
    private val LEAD = Pattern.compile("^(?:(?:um+|uh+|okay|ok|so|please|hey humming|humming)[,\\s]+)+", F)
    private val TRAIL = Pattern.compile("(?:[,\\s]+please)?[\\s.!?,]*$", F)
    private val RE_REPLACE = Pattern.compile(
        "^(?:change|replace|switch)\\s+[\"\u201C]?(.+?)[\"\u201D]?\\s+(?:to|with|into)\\s+[\"\u201C]?(.+?)[\"\u201D]?$",
        F,
    )
    private val RE_DELETE = Pattern.compile(
        "^(?:delete|remove|erase)\\s+(?:the\\s+word\\s+)?[\"\u201C]?(.+?)[\"\u201D]?$",
        F,
    )
    private val RE_CAP = Pattern.compile(
        "^(?:capitalize|capitalise)\\s+(?:the\\s+word\\s+)?[\"\u201C]?(.+?)[\"\u201D]?$",
        F,
    )
    private val RE_UNDO = Pattern.compile(
        "^(?:undo(?:\\s+that)?|scratch\\s+that|delete\\s+that|undo\\s+the\\s+last\\s+(?:bit|part|sentence))$",
        F,
    )
    private val WS = Regex("\\s+")

    private fun normalise(utterance: String): String? {
        var t = utterance.trim()
        t = LEAD.matcher(t).replaceFirst("")
        t = TRAIL.matcher(t).replaceFirst("")
        t = t.trim()
        if (t.isEmpty() || t.split(WS).size > MAX_WORDS) return null
        return t
    }

    /** Parse an utterance as a command without looking at the text (cheap: no editor read needed). */
    fun parse(utterance: String): Command? {
        val t = normalise(utterance) ?: return null
        if (RE_UNDO.matcher(t).matches()) return Command.Undo
        RE_REPLACE.matcher(t).let { m ->
            if (m.matches()) {
                val a = m.group(1)!!.trim()
                val b = m.group(2)!!.trim()
                return if (a.isNotEmpty() && b.isNotEmpty() &&
                    !a.equals(b, ignoreCase = true)
                ) {
                    Command.Replace(a, b)
                } else {
                    null
                }
            }
        }
        RE_CAP.matcher(t).let { m -> if (m.matches()) return Command.Capitalize(m.group(1)!!.trim()) }
        RE_DELETE.matcher(t).let { m -> if (m.matches()) return Command.Delete(m.group(1)!!.trim()) }
        return null
    }

    /** Last whole-word, case-insensitive occurrence of [target] in [text] as [start, end). */
    private fun findLast(text: String, target: String): Pair<Int, Int>? {
        val re = Pattern.compile("(?:^|\\b|\\s)" + Pattern.quote(target) + "(?:\\b|$)", F)
        val m = re.matcher(text)
        var last: Pair<Int, Int>? = null
        var pos = 0
        while (pos <= text.length) {
            if (!m.find(pos)) break
            val s = m.group()
            val lead = s.length - s.trimStart().length
            last = Pair(m.start() + lead, m.end())
            pos = if (m.end() > m.start()) m.start() + Character.charCount(text.codePointAt(m.start())) else m.end() + 1
        }
        return last
    }

    private fun matchCase(original: String, replacement: String): String {
        val firstUpper = original.firstOrNull()?.isUpperCase() ?: false
        val letters = original.filter { it.isLetter() }
        if (letters.length > 1 && letters.all { it.isUpperCase() }) return replacement.uppercase()
        if (firstUpper && replacement.none { it.isUpperCase() }) return replacement.replaceFirstChar { it.uppercase() }
        return replacement
    }

    /** Apply [cmd] to [text] (the text before the cursor); null when it can't apply (target absent, or Undo). */
    fun apply(text: String, cmd: Command): Outcome? = when (cmd) {
        Command.Undo -> null
        is Command.Replace -> findLast(text, cmd.target)?.let { (s, e) ->
            val ins = matchCase(text.substring(s, e), cmd.replacement)
            Outcome(text.substring(0, s) + ins + text.substring(e), s, e - s, ins)
        }
        is Command.Capitalize -> findLast(text, cmd.target)?.let { (s, e) ->
            val ins = text.substring(s, e).split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
            Outcome(text.substring(0, s) + ins + text.substring(e), s, e - s, ins)
        }
        is Command.Delete -> findLast(text, cmd.target)?.let { (s, e) ->
            val before = text.substring(0, s)
            val after = text.substring(e)
            if (before.endsWith(' ') && (after.isEmpty() || after[0] == ' ' || after[0] in ",.!?;:")) {
                Outcome(text.substring(0, s - 1) + after, s - 1, e - s + 1, "")
            } else if (after.startsWith(' ')) {
                Outcome(before + after.substring(1), s, e + 1 - s, "")
            } else {
                Outcome(before + after, s, e - s, "")
            }
        }
    }

    /** The one call hosts make. Undo comes back with a null outcome; anything else that isn't a command
     *  which applies to [text] returns null and the host inserts the utterance as dictation. [truncated]: [text]
     *  is a window cut from a longer field (e.g. exactly [CONTEXT_CHARS] long), so it may start mid-word. */
    fun voiceCommand(text: String, utterance: String, truncated: Boolean = false): Result? {
        val cmd = parse(utterance) ?: return null
        if (cmd == Command.Undo) return Result(cmd, null)
        val out = apply(text, cmd) ?: return null
        // [text] cut from a longer field: its first char may sit mid-word ("Mon|day"), so a match there is not
        // a real word boundary and the utterance stays dictation.
        if (truncated && out.start == 0) return null
        return Result(cmd, out)
    }

    /** How a field applies an outcome computed on [before] (the text before the cursor): delete this many
     *  chars before the cursor, then commit this text. Leaves the field reading exactly [Outcome.text]. */
    fun fieldEdit(before: String, outcome: Outcome): Pair<Int, String> =
        Pair(before.length - outcome.start, outcome.text.substring(outcome.start))

    /** Chars to delete for "undo that": the keyboard's own last dictated commit, only when it still sits
     *  right before the cursor (never anything the user typed or another app wrote). */
    fun undoCount(before: String, lastCommit: String?): Int =
        if (!lastCommit.isNullOrEmpty() && before.endsWith(lastCommit)) lastCommit.length else 0
}
