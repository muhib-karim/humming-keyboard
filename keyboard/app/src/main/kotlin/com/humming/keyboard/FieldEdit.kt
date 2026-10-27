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

    sealed class Command {
        data class Replace(val target: String, val replacement: String) : Command()
        data class Delete(val target: String) : Command()
        data class Insert(val text: String, val anchor: String, val before: Boolean) : Command()
    }

    /** Replace [start]..[end] of the text before the cursor with [replacement]. */
    data class Edit(val start: Int, val end: Int, val replacement: String) {
        fun applyTo(before: String): String = before.replaceRange(start, end, replacement)
    }

    private val leadingOk = Regex("^(?:ok|okay)\\b[,.]?\\s*", RegexOption.IGNORE_CASE)
    private val replaceCmd = Regex("^(?:change (.+?) to|replace (.+?) with) (.+)$", RegexOption.IGNORE_CASE)
    private val deleteCmd = Regex("^(?:delete|remove) (.+)$", RegexOption.IGNORE_CASE)
    private val insertCmd = Regex("^insert (.+?) (before|after) (.+)$", RegexOption.IGNORE_CASE)

    /** Targets that belong to the existing dictation commands (or are pronouns with nothing to find). */
    private val reservedTarget = Regex(
        "^(?:that|this|it|all|everything|(?:the )?last (?:word|sentence|line|paragraph)|all of it|all text)$",
        RegexOption.IGNORE_CASE,
    )

    private fun part(s: String): String? {
        val p = s.trim().trim(',').trim()
        return p.takeIf { it.isNotEmpty() && it.length <= MAX_PART }
    }

    /** The command said in [utterance], or null when it is ordinary dictation (or an existing dictation command). */
    fun parse(utterance: String): Command? {
        if (utterance.length > MAX_UTTERANCE) return null
        val t = utterance.trim().replace(Regex("\\s+"), " ").replace(leadingOk, "")
            .trimEnd('.', '!', '?', ',', ' ').trim(',', ' ')
        if (t.isEmpty()) return null
        replaceCmd.matchEntire(t)?.let { m ->
            val target = part(m.groupValues[1].ifEmpty { m.groupValues[2] }) ?: return null
            val replacement = part(m.groupValues[3]) ?: return null
            if (reservedTarget.matches(target)) return null
            return Command.Replace(target, replacement)
        }
        insertCmd.matchEntire(t)?.let { m ->
            val text = part(m.groupValues[1]) ?: return null
            val anchor = part(m.groupValues[3]) ?: return null
            if (reservedTarget.matches(anchor)) return null
            return Command.Insert(text, anchor, before = m.groupValues[2].equals("before", ignoreCase = true))
        }
        deleteCmd.matchEntire(t)?.let { m ->
            val target = part(m.groupValues[1]) ?: return null
            if (reservedTarget.matches(target)) return null
            return Command.Delete(target)
        }
        return null
    }

    private fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '\'' || c == '’'

    /** Start of the last whole-word, case-insensitive occurrence of [target] in [text], or -1. */
    private fun lastIndex(text: String, target: String): Int {
        var i = text.length - target.length
        while (i >= 0) {
            val end = i + target.length
            if (text.regionMatches(i, target, 0, target.length, ignoreCase = true) &&
                (i == 0 || !isWordChar(text[i - 1])) &&
                (end == text.length || !isWordChar(text[end]))
            ) return i
            i--
        }
        return -1
    }

    /** The edit [cmd] makes to [before] (the text before the cursor), or null when its target is not there. */
    fun plan(before: String, cmd: Command): Edit? {
        val find = when (cmd) {
            is Command.Replace -> cmd.target
            is Command.Delete -> cmd.target
            is Command.Insert -> cmd.anchor
        }
        if (find.isEmpty()) return null
        val start = lastIndex(before, find)
        if (start < 0) return null
        val end = start + find.length
        return when (cmd) {
            is Command.Replace -> {
                val r = cmd.replacement
                val capital = before[start].isUpperCase() && r.first().isLowerCase()
                Edit(start, end, if (capital) r.replaceFirstChar { it.uppercaseChar() } else r)
            }
            is Command.Delete -> when {
                start > 0 && before[start - 1] == ' ' -> Edit(start - 1, end, "")
                end < before.length && before[end] == ' ' -> Edit(start, end + 1, "")
                else -> Edit(start, end, "")
            }
            is Command.Insert ->
                if (cmd.before) Edit(start, start, cmd.text + " ") else Edit(end, end, " " + cmd.text)
        }
    }
}
