package com.humming.keyboard

/**
 * Humming: fits dictated text into the sentence around the cursor (pure Kotlin: DictationJoinTest).
 *
 * The clean-up writes every dictation as whole sentences ("The plan works."). Dropped into the middle of a sentence
 * that reads "I told him. The plan works." DictationJoin makes it "I told him the plan works":
 * - [glue]: a space only where a word would touch the text before (not after whitespace, an opening bracket or quote,
 *   "@" or "#");
 * - [adapt]: when the text before continues a sentence (ends in a letter, digit or , ; :), a capitalised common word
 *   at the start is lowercased. "I", names, acronyms and the user's dictionary terms are never lowercased, because a
 *   wrong lowercase is worse than a missed one. When the text after the cursor carries on the sentence, the final
 *   period the clean-up added is dropped.
 */
object DictationJoin {
    private const val OPENERS = "([{“‘«@#/"

    /** Words that are only capitalised because they start a sentence. Anything else (a name) keeps its capital. */
    private val COMMON = setOf(
        "the", "a", "an", "and", "but", "or", "so", "then", "if", "when", "because", "as", "to", "for", "of", "in",
        "on", "at", "by", "with", "from", "about", "that", "this", "these", "those", "it", "its", "it's", "we", "you",
        "they", "he", "she", "them", "us", "our", "your", "their", "my", "me", "him", "her", "is", "are", "was",
        "were", "be", "been", "would", "can", "could", "should", "do", "does", "did", "have", "has", "had",
        "not", "no", "yes", "maybe", "also", "just", "please", "thanks", "there", "here", "what", "which", "who",
        "how", "why", "where", "all", "some", "any", "more", "very", "really", "let's", "we're", "you're", "they're",
    )

    /** The separator before the dictated text: a space when the cursor follows a word, else nothing. */
    fun glue(before: String): String {
        if (before.isEmpty()) return ""
        val last = before.last()
        if (last.isWhitespace() || last in OPENERS) return ""
        // A straight quote opens a quotation when it starts the text or follows whitespace.
        if ((last == '"' || last == '\'') && (before.length == 1 || before[before.length - 2].isWhitespace())) return ""
        return " "
    }

    /** [text] adapted to the text [before] and [after] the cursor; [dictionary] terms keep their casing. */
    fun adapt(before: String, text: String, after: String = "", dictionary: List<String> = emptyList()): String {
        if (text.isBlank()) return text
        var t = text
        val prev = before.trimEnd(' ', '\t').lastOrNull() // a newline ends the sentence
        val continuing = prev != null && (prev.isLetterOrDigit() || prev in ",;:")
        if (continuing) t = lowercaseFirstWord(t, dictionary)
        val next = after.trimStart().firstOrNull()
        if (next != null && next.isLetterOrDigit() && t.endsWith(".") && !t.endsWith("..")) {
            t = t.dropLast(1)
            if (after.first().isLetterOrDigit()) t += " "
        }
        return t
    }

    private fun lowercaseFirstWord(text: String, dictionary: List<String>): String {
        val word = text.takeWhile { it.isLetter() || it == '\'' || it == '’' }
        if (word.length < 1 || !word[0].isUpperCase()) return text
        if (word.drop(1).any { it.isUpperCase() }) return text // NASA, McDonald
        if (word.lowercase() !in COMMON) return text // names, and "I" / "I'm"
        if (dictionary.any { it.trim() == word }) return text
        return word[0].lowercaseChar() + text.substring(1)
    }
}
