package com.humming.keyboard

/**
 * How a dictation reaches the text field.
 *
 * LIVE (Settings › Voice › "Live text while speaking", on by default): every partial (the cleaned transcript so far)
 * is shown in the field as composing text — the underlined draft Gboard also uses — and each new partial replaces the
 * whole draft in place. Nothing is committed while the user talks, so a later partial or the final clean-up can rewrite
 * any word without leaving the old one behind. On stop the draft is replaced by the final text and committed once.
 *
 * NOT LIVE: the field shows nothing while the user talks (the panel shows only that it is listening); the final text is
 * committed once on stop.
 *
 * While the keyboard is hidden (screen off, another app) the draft is taken out of the field and nothing is shown; it
 * comes back when the keyboard returns. The final text is committed only into a field the keyboard is attached to;
 * otherwise the caller keeps it in History.
 *
 * Invariants (DictationOutputTest): at most one commit per dictation, the committed text is exactly glue + adapt(final text),
 * and no draft is ever left in the field after the dictation ends.
 * Pure Kotlin (no Android types).
 */
class DictationOutput(
    private val field: Field,
    private val preview: (String) -> Unit,
    /** Separator put before the text (a space when the cursor follows a word). */
    private val glue: String = "",
    /** Show the words in the field while talking (default). False = nothing until the one commit on stop. */
    private val live: Boolean = true,
    /** Fits the text to the sentence around the cursor (DictationJoin.adapt); applied to the draft and the commit. */
    private val adapt: (String) -> String = { it },
    /** The field this dictation started in. Null = unknown: no binding (the words go wherever the cursor is). */
    private val target: Target? = null,
    /** The field that has input focus right now (package and field id of the current editor). */
    private val currentTarget: () -> Target? = { target },
) {
    /**
     * Identifies one text field. [fieldId] alone is weak (every field in a WebView or Compose screen shares the host
     * view id), so the input type, field name, hint and the keyboard's input [generation] (bumped on every fresh
     * onStartInput) are part of the identity too. Coming back to a field after focus left it counts as a new field:
     * the draft is then held in History rather than replayed, which is the safe failure.
     */
    data class Target(
        val packageName: String?,
        val fieldId: Int,
        val inputType: Int = 0,
        val fieldName: String? = null,
        val hint: String? = null,
        val generation: Long = 0,
    )

    /**
     * True while the focused field is the one this dictation started in. The draft and the final text only ever go
     * there; in any other field (another app, another box) they are held back and the caller keeps them in History.
     */
    fun inOriginalField(): Boolean = target == null || currentTarget() == target

    /**
     * Outcome of a voice edit ([tryFieldEdit]). NOT_APPLICABLE = the field can't be edited, NOT_FOUND = the target is
     * not before the cursor; in both cases the field is untouched and the words are typed instead.
     */
    enum class FieldEditResult { NOT_APPLICABLE, NOT_FOUND, APPLIED }

    interface Field {
        /** Shows [text] as the composing draft, replacing the previous draft in place. False when there is no field. */
        fun setComposing(text: String): Boolean
        /**
         * Commits [text] as the dictation's one commit. [replacesDraft] = a draft is in the field and [text] replaces
         * it. True when done.
         */
        fun commitFinal(text: String, replacesDraft: Boolean): Boolean
        /** Removes the draft from the field without committing anything. */
        fun clearComposing()

        /**
         * Reads up to [windowChars] before the cursor, asks [planner] for the edit and applies it in one batch edit.
         * NOT_APPLICABLE when there is no field or text is selected; NOT_FOUND when [planner] returns null. Both leave
         * the field's text untouched (the caller then types the words).
         */
        fun applyEdit(windowChars: Int, planner: (String) -> FieldEdit.Edit?): FieldEditResult
    }

    enum class State { LISTENING, FINISHING, DONE }

    companion object {
        /** Characters before the cursor a voice edit may look at. */
        const val EDIT_WINDOW = 2000
    }

    var state = State.LISTENING
        private set
    /** Number of commits made to the field (0 or 1). */
    var commits = 0
        private set
    /** Number of in-place draft updates sent to the field. */
    var draftUpdates = 0
        private set
    var lastPreview = ""
        private set
    /** The draft currently shown in the field (with [glue]), or "" when none. */
    var draft = ""
        private set
    /** False while the keyboard is hidden: nothing is shown in the field. */
    var attached = true
        private set

    private fun showDraft(text: String) {
        val d = if (text.isEmpty()) "" else glue + adapt(text)
        if (d == draft) return
        if (d.isEmpty()) { clearDraft(); return }
        if (field.setComposing(d)) { draft = d; draftUpdates++ }
    }

    private fun clearDraft() {
        if (draft.isEmpty()) return
        field.clearComposing()
        draft = ""
    }

    /** A partial (cleaned text so far). Live: shown as the draft in the field, replaced in place. Not live: nothing. */
    fun onPartial(text: String) {
        if (state != State.LISTENING) return
        if (!live) return
        lastPreview = text
        preview(text)
        // In another field the framework already finished our composing span: forget it so it is not replaced twice.
        if (attached) { if (inOriginalField()) showDraft(text) else draft = "" }
    }

    /** Keyboard hidden mid-dictation (screen off, app switch): keep listening, take the draft out of the field. */
    fun detach() {
        clearDraft()
        attached = false
    }

    /** Keyboard back: show the latest words again as the draft. */
    fun attach() {
        // Focus moved to a different field (or app) while the keyboard was hidden: stay detached so nothing spoken for
        // the first field is replayed into this one. Coming back to the original field attaches again.
        if (!inOriginalField()) { draft = ""; attached = false; return }
        attached = true
        if (state == State.LISTENING && live) showDraft(lastPreview)
    }

    /** The user tapped stop: no more partials; the draft stays until the final text replaces it. */
    fun onStop() {
        if (state == State.LISTENING) state = State.FINISHING
    }

    /**
     * The final transcript after stop: replaces the draft and is committed once. [toField] false keeps it out of the
     * field (no field to type into; the caller saves it to History). Returns true when the field got the text.
     */
    fun onFinal(text: String, toField: Boolean = true): Boolean {
        if (state == State.DONE) return false
        state = State.DONE
        preview("")
        lastPreview = ""
        if (!inOriginalField()) {
            draft = "" // forget the draft without touching the focused field: it is not ours
            return false
        }
        if (text.isBlank() || !toField || !attached) {
            clearDraft()
            return false
        }
        val hadDraft = draft.isNotEmpty()
        val ok = field.commitFinal(glue + adapt(text), replacesDraft = hadDraft)
        draft = ""
        if (ok) commits++
        return ok
    }

    /**
     * The final transcript was a voice edit ([FieldEdit]): take the draft out and edit the text before the cursor
     * instead of typing. Only in the original field while the keyboard is attached; the caller checks privacy (a
     * private field is never read). Only APPLIED ends the dictation (nothing typed). NOT_APPLICABLE and NOT_FOUND
     * leave it open so the caller types the words with [onFinal]: a dictated sentence is never lost.
     */
    fun tryFieldEdit(cmd: FieldEdit.Command, windowChars: Int = EDIT_WINDOW): FieldEditResult {
        if (state == State.DONE || !attached || !inOriginalField()) return FieldEditResult.NOT_APPLICABLE
        clearDraft()
        // One extra character so a target at the window's start is checked for a word boundary.
        val r = field.applyEdit(windowChars + 1) { before ->
            FieldEdit.plan(before, cmd, if (before.length > windowChars) 1 else 0)
        }
        if (r == FieldEditResult.APPLIED) {
            state = State.DONE
            preview("")
            lastPreview = ""
        }
        return r
    }

    /** Cancel: nothing was committed, so taking the draft out leaves the field as it was. Always true. */
    fun discard(): Boolean {
        state = State.DONE
        preview("")
        lastPreview = ""
        clearDraft()
        return true
    }
}
