package com.humming.keyboard

/**
 * Voice edits to the text already in the field, said as the whole utterance (pure Kotlin: FieldEditTest).
 *
 * "change X to Y" / "replace X with Y", "delete X" / "remove X", "insert X before Y" / "insert X after Y" act on the
 * last whole-word, case-insensitive occurrence of X (or Y) before the cursor. The commands the dictation already has
 * ("delete that", "scratch that", "delete the last word", "delete all", ...) are never field edits.
 * On-device and deterministic: no language model, no network.
 */
object FieldEdit {
    /** Longest utterance read as a command; anything longer is dictation. */
    const val MAX_UTTERANCE = 200

    /** Longest text to find, replace or insert. */
    const val MAX_PART = 60

    private const val REPLACE_TARGET = 1
    private const val CHANGE_TARGET = 2
    private const val SECOND_PART = 3

    sealed class Command {
        /** The text to look for before the cursor. */
        val find: String
            get() =
                when (this) {
                    is Replace -> target
                    is Delete -> target
                    is Insert -> anchor
                }

        data class Replace(val target: String, val replacement: String) : Command()

        data class Delete(val target: String) : Command()

        data class Insert(val text: String, val anchor: String, val before: Boolean) : Command()
    }

    /** Replace [start]..[end] of the text before the cursor with [replacement]. */
    data class Edit(val start: Int, val end: Int, val replacement: String) {
        fun applyTo(before: String): String = before.replaceRange(start, end, replacement)
    }

    private val leadingOk = Regex("^(?:ok|okay)\\b[,.]?\\s*", RegexOption.IGNORE_CASE)
    private val replaceCmd = Regex("^(?:replace (.+?) with|change (.+?) to) (.+)$", RegexOption.IGNORE_CASE)
    private val deleteCmd = Regex("^(?:delete|remove) (.+)$", RegexOption.IGNORE_CASE)
    private val insertCmd = Regex("^insert (.+?) (before|after) (.+)$", RegexOption.IGNORE_CASE)

    /** Targets that belong to the existing dictation commands (or are pronouns with nothing to find). */
    private val reservedTarget = Regex(
        "^(?:that|this|it|all|everything|(?:the )?last (?:word|sentence|line|paragraph)|all of it|all text)$",
        RegexOption.IGNORE_CASE,
    )

    /** A usable target or text: trimmed, not empty, not too long. */
    private fun part(s: String): String? = s.trim().trim(',').trim().takeIf { it.isNotEmpty() && it.length <= MAX_PART }

    /** A part that is something to find in the field (not "that", "the last word", ...). */
    private fun findable(s: String): String? = part(s)?.takeUnless { reservedTarget.matches(it) }

    private fun normalise(utterance: String): String =
        utterance.trim().replace(Regex("\\s+"), " ").replace(leadingOk, "")
            .trimEnd('.', '!', '?', ',', ' ').trim(',', ' ')

    private fun parseReplace(t: String): Command? = replaceCmd.matchEntire(t)?.let { m ->
        val target = findable(m.groupValues[REPLACE_TARGET].ifEmpty { m.groupValues[CHANGE_TARGET] })
        val replacement = part(m.groupValues[SECOND_PART])
        if (target != null && replacement != null) Command.Replace(target, replacement) else null
    }

    private fun parseInsert(t: String): Command? = insertCmd.matchEntire(t)?.let { m ->
        val text = part(m.groupValues[1])
        val anchor = findable(m.groupValues[SECOND_PART])
        val before = m.groupValues[2].equals("before", ignoreCase = true)
        if (text != null && anchor != null) Command.Insert(text, anchor, before) else null
    }

    private fun parseDelete(t: String): Command? =
        deleteCmd.matchEntire(t)?.let { m -> findable(m.groupValues[1])?.let { Command.Delete(it) } }

    /** The command said in [utterance], or null when it is ordinary dictation (or an existing dictation command). */
    fun parse(utterance: String): Command? {
        val t = if (utterance.length > MAX_UTTERANCE) "" else normalise(utterance)
        return when {
            t.isEmpty() -> null
            replaceCmd.matches(t) -> parseReplace(t)
            insertCmd.matches(t) -> parseInsert(t)
            else -> parseDelete(t)
        }
    }

    private fun deletion(before: String, start: Int, end: Int): Edit = when {
        start > 0 && before[start - 1] == ' ' -> Edit(start - 1, end, "")
        end < before.length && before[end] == ' ' -> Edit(start, end + 1, "")
        else -> Edit(start, end, "")
    }

    private fun replacement(before: String, start: Int, end: Int, r: String): Edit {
        val capital = before[start].isUpperCase() && r.first().isLowerCase()
        return Edit(start, end, if (capital) r.replaceFirstChar { it.uppercaseChar() } else r)
    }

    /**
     * The edit [cmd] makes to [before] (the text before the cursor), or null when its target is not there. The first
     * [contextChars] characters are only word-boundary context (the slice may start mid-word): no match starts there.
     */
    fun plan(before: String, cmd: Command, contextChars: Int = 0): Edit? {
        val find = cmd.find
        val start = if (find.isEmpty()) -1 else Words.lastIndex(before, find, contextChars)
        val end = start + find.length
        return if (start < 0) {
            null
        } else {
            when (cmd) {
                is Command.Replace -> replacement(before, start, end, cmd.replacement)
                is Command.Delete -> deletion(before, start, end)
                is Command.Insert ->
                    if (cmd.before) Edit(start, start, cmd.text + " ") else Edit(end, end, " " + cmd.text)
            }
        }
    }
}

/** Whole-word, case-insensitive matching for [FieldEdit]. */
private object Words {
    fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '\'' || c == '\u2019'

    /** True when [text] has [target] at [i] as a whole word (case-insensitive). */
    fun wholeWordAt(text: String, target: String, i: Int): Boolean {
        val end = i + target.length
        val startsWord = i == 0 || !isWordChar(text[i - 1])
        val endsWord = end == text.length || !isWordChar(text[end])
        return startsWord && endsWord && text.regionMatches(i, target, 0, target.length, ignoreCase = true)
    }

    /** Start (>= [from]) of the last whole-word, case-insensitive occurrence of [target] in [text], or -1. */
    fun lastIndex(text: String, target: String, from: Int = 0): Int =
        (text.length - target.length downTo from).firstOrNull { wholeWordAt(text, target, it) } ?: -1
}
