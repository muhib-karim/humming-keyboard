package com.humming.keyboard

/**
 * What a dictation is allowed to do to the text field: NOTHING until the user taps stop.
 *
 * M3 wrote each live partial into the field with setComposingText. Many editors (WhatsApp, Chrome, Messages)
 * draw composing text exactly like typed text and commit it themselves on a selection change, a focus change or
 * a re-layout, so a long dictation looked (and often was) inserted while the user was still talking.
 * Now partials only go to the keyboard's own preview line; the field gets one commit, on stop.
 * Pure Kotlin (no Android types): covered by DictationOutputTest.
 */
class DictationOutput(
    private val field: Field,
    private val preview: (String) -> Unit,
) {
    interface Field {
        fun commit(text: String)
    }

    enum class State { LISTENING, FINISHING, DONE }

    var state = State.LISTENING
        private set
    /** Commits made to the field so far (at most one per dictation). */
    var commits = 0
        private set
    var lastPreview = ""
        private set

    /** A live partial (cleaned text so far): preview only, never the field. */
    fun onPartial(text: String) {
        if (state != State.LISTENING) return
        lastPreview = text
        preview(text)
    }

    /** The user tapped stop (or the mic hit its cap): stop showing partials, keep waiting for the final text. */
    fun onStop() {
        if (state == State.LISTENING) state = State.FINISHING
    }

    /**
     * The final transcript after stop. [toField] false (keyboard closed mid-dictation) keeps the text out of the
     * field; the caller saves it to History instead. Returns true when the field got the text.
     */
    fun onFinal(text: String, glue: String, toField: Boolean = true): Boolean {
        if (state == State.DONE) return false
        state = State.DONE
        preview("")
        lastPreview = ""
        if (text.isBlank() || !toField) return false
        field.commit(glue + text)
        commits++
        return true
    }
}
