package com.humming.keyboard

/**
 * How a dictation reaches the text field: LIVE, word by word, while the user talks.
 *
 * Partials are the cleaned transcript so far. A word is typed into the field once two partials in a row agree on it
 * (the clean-up pass may still rewrite the newest phrase, so the unstable tail stays in the keyboard's preview line
 * until the next phrase confirms it). Everything is plain commits; composing text is never used, because many editors
 * (WhatsApp, Chrome, Messages) commit composing text on their own at random moments.
 *
 * On stop the rest of the final text is typed. If the final clean-up rewrote words that were already typed, those
 * words are replaced when the cursor still sits right after them (checked against the field), else the rest is
 * appended. While the keyboard is hidden (screen off, another app) nothing is typed; the words wait and are typed
 * when the keyboard comes back on the same field, or on stop.
 * Pure Kotlin (no Android types): covered by DictationOutputTest.
 */
class DictationOutput(
    private val field: Field,
    private val preview: (String) -> Unit,
    /** Separator typed before the first word (a space when the cursor follows a word). */
    private val glue: String = "",
    /** Type words while talking (default). False = one commit on stop. */
    private val live: Boolean = true,
) {
    interface Field {
        fun commit(text: String)
        /** The [n] characters before the cursor, or null when the field can't say. */
        fun textBeforeCursor(n: Int): String? = null
        /** Deletes [n] characters before the cursor; true when done. */
        fun deleteBefore(n: Int): Boolean = false
    }

    enum class State { LISTENING, FINISHING, DONE }

    var state = State.LISTENING
        private set
    /** Number of commits made to the field so far. */
    var commits = 0
        private set
    var lastPreview = ""
        private set
    /** The transcript text typed into the field so far (without [glue]). */
    var typed = ""
        private set
    private var glueTyped = false
    private var prevPartial = ""
    /** Words the last two partials agreed on (typed unless the keyboard is hidden). */
    private var stableNow = ""
    /** False while the keyboard is hidden: words wait for the field. */
    var attached = true
        private set

    private fun type(text: String) {
        if (text.isEmpty()) return
        val out = if (!glueTyped) glue + text else text
        field.commit(out)
        glueTyped = true
        typed += text
        commits++
    }

    /** A live partial (cleaned text so far). Types the words two partials agree on; the rest is preview only. */
    fun onPartial(text: String) {
        if (state != State.LISTENING) return
        lastPreview = text
        preview(text)
        val stable = stableWords(prevPartial, text)
        prevPartial = text
        stableNow = stable
        if (!live || !attached) return
        if (stable.length > typed.length && stable.startsWith(typed)) type(stable.substring(typed.length))
    }

    /** Keyboard hidden mid-dictation (screen off, app switch): keep listening, stop typing. */
    fun detach() { attached = false }

    /** Keyboard back on the same field: type what was confirmed meanwhile. */
    fun attach() {
        attached = true
        if (state != State.LISTENING || !live) return
        val stable = stableNow
        if (stable.length > typed.length && stable.startsWith(typed)) type(stable.substring(typed.length))
    }

    /** The user tapped stop: no more live typing, wait for the final text. */
    fun onStop() {
        if (state == State.LISTENING) state = State.FINISHING
    }

    /**
     * The final transcript after stop. [toField] false keeps the rest out of the field (no field to type into);
     * the caller saves it to History. Returns true when the field got (the rest of) the text.
     */
    fun onFinal(text: String, toField: Boolean = true): Boolean {
        if (state == State.DONE) return false
        state = State.DONE
        preview("")
        lastPreview = ""
        if (text.isBlank() || !toField) return false
        if (text.startsWith(typed)) {
            val rest = text.substring(typed.length)
            if (rest.isEmpty()) return typed.isNotEmpty()
            type(rest)
            return true
        }
        // The final clean-up rewrote words already typed: replace them if the cursor still follows them.
        val keep = commonPrefix(typed, text).let { p -> val sp = p.lastIndexOf(' '); if (sp < 0) 0 else sp + 1 }
        val wrong = typed.substring(keep)
        if (wrong.isNotEmpty() && field.textBeforeCursor(wrong.length) == wrong && field.deleteBefore(wrong.length)) {
            typed = typed.substring(0, keep)
            type(text.substring(keep))
        } else {
            val rest = text.substring(keep.coerceAtMost(text.length))
            if (rest.isNotEmpty()) { field.commit(if (typed.endsWith(" ") || typed.isEmpty()) rest else " $rest"); commits++ }
        }
        return true
    }

    /** Cancel: removes the words this dictation typed, when the cursor still follows them. True when removed. */
    fun discard(): Boolean {
        state = State.DONE
        preview("")
        val all = (if (glueTyped) glue else "") + typed
        if (all.isEmpty()) return true
        if (field.textBeforeCursor(all.length) == all && field.deleteBefore(all.length)) {
            typed = ""; glueTyped = false
            return true
        }
        return false
    }

    companion object {
        fun commonPrefix(a: String, b: String): String {
            val n = minOf(a.length, b.length)
            var i = 0
            while (i < n && a[i] == b[i]) i++
            return a.substring(0, i)
        }

        /** The words (with their trailing space) two consecutive partials agree on. */
        fun stableWords(prev: String, cur: String): String {
            val p = commonPrefix(prev, cur)
            val sp = p.lastIndexOf(' ')
            return if (sp < 0) "" else p.substring(0, sp + 1)
        }
    }
}
