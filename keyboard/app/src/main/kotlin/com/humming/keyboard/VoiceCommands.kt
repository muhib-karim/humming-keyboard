package com.humming.keyboard

/**
 * Spoken editing commands, applied to the dictated text (pure Kotlin: VoiceCommandsTest).
 *
 * On-device, always:  "delete that" / "scratch that" / "undo that" said as its own sentence removes the sentence
 * before it; "new line" and "new paragraph" are handled by TextCleanup.
 * With AI clean-up (the user's own key): a closing "make it formal", "make it casual", "make it shorter",
 * "make it a list" / "bullet points", "fix the grammar", "translate (it) to <language>" becomes the instruction for
 * that pass. Without AI clean-up these closing commands are simply removed from the text.
 */
object VoiceCommands {
    data class Result(val text: String, val instruction: String?)

    private val deleteCmd = Regex("^(?:ok(?:ay)?,? )?(?:delete|scratch|undo|remove) (?:that|the last sentence)$", RegexOption.IGNORE_CASE)

    private val rewrites: List<Pair<Regex, (MatchResult) -> String>> = listOf(
        Regex("^(?:please )?make (?:it|this|that) (?:more )?formal$", RegexOption.IGNORE_CASE) to { _ -> "rewrite it in a formal, professional tone" },
        Regex("^(?:please )?make (?:it|this|that) (?:more )?(?:casual|friendly|relaxed)$", RegexOption.IGNORE_CASE) to { _ -> "rewrite it in a casual, friendly tone" },
        Regex("^(?:please )?make (?:it|this|that) (?:shorter|concise|brief)$", RegexOption.IGNORE_CASE) to { _ -> "make it shorter and more concise" },
        Regex("^(?:please )?(?:make (?:it|this|that) (?:a|into a) (?:bulleted )?list|bullet points)$", RegexOption.IGNORE_CASE) to { _ -> "format it as a bulleted list" },
        Regex("^(?:please )?fix (?:the )?(?:grammar|spelling)$", RegexOption.IGNORE_CASE) to { _ -> "fix grammar and spelling only" },
        Regex("^(?:please )?translate (?:it |this |that )?(?:in)?to ([a-z]+)$", RegexOption.IGNORE_CASE) to { m -> "translate it to ${m.groupValues[1]}" },
    )

    /** Sentences with their closing punctuation (and any newlines kept with the following sentence). */
    private fun sentences(text: String): List<String> =
        Regex("[^.!?]+(?:[.!?]+|$)").findAll(text).map { it.value }.filter { it.isNotBlank() }.toList()

    private fun bare(s: String) = s.trim().trimEnd('.', '!', '?', ',').trim()

    fun apply(text: String): Result {
        if (text.isBlank()) return Result(text, null)
        val out = ArrayList<String>()
        for (s in sentences(text)) {
            if (deleteCmd.matches(bare(s))) {
                if (out.isNotEmpty()) out.removeAt(out.size - 1)
            } else {
                out.add(s)
            }
        }
        var instruction: String? = null
        if (out.isNotEmpty()) {
            val last = bare(out.last())
            for ((re, f) in rewrites) {
                val m = re.matchEntire(last) ?: continue
                instruction = f(m)
                out.removeAt(out.size - 1)
                break
            }
        }
        val joined = out.joinToString("") { it }.trim()
        return Result(joined, instruction)
    }
}
