package com.humming.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class DictationOutputTest {
    private val sr = 16_000

    /**
     * A text field like Android's: committed text, then the composing draft at the cursor (the end).
     * setComposingText replaces the draft; commitText replaces the draft (if any) with the committed text.
     */
    private class FakeField : DictationOutput.Field {
        val committed = mutableListOf<String>()
        val base = StringBuilder()
        var composing = ""
        val text: String get() = base.toString() + composing
        var composingCalls = 0
        override fun setComposing(text: String): Boolean { composing = text; composingCalls++; return true }
        override fun commitFinal(text: String, replacesDraft: Boolean): Boolean {
            composing = ""; committed.add(text); base.append(text); return true
        }
        override fun clearComposing() { composing = "" }
        override fun applyEdit(
            windowChars: Int,
            planner: (String) -> FieldEdit.Edit?,
        ): DictationOutput.FieldEditResult {
            composing = ""
            val before = base.takeLast(windowChars).toString()
            val e = planner(before) ?: return DictationOutput.FieldEditResult.NOT_FOUND
            val offset = base.length - before.length
            base.replace(offset + e.start, offset + e.end, e.replacement)
            return DictationOutput.FieldEditResult.APPLIED
        }
        fun type(s: String) { base.append(s) }
    }

    private val tone: ShortArray = ShortArray(sr / 10) { i -> (0.2 * 32767 * sin(2 * PI * 220 * i / sr)).toInt().toShort() }
    private val quiet = ShortArray(sr / 10)

    /** [minutes] of talking with natural pauses, fed in 100 ms blocks like the recorder, every segment "transcribed". */
    private fun talk(minutes: Double, field: FakeField, previews: MutableList<String>, live: Boolean = true):
        Pair<DictationOutput, List<String>> {
        val out = DictationOutput(field, { previews.add(it) }, glue = " ", live = live)
        val words = mutableListOf<String>()
        val seg = Segmenter(sr) { _ ->
            words.add("phrase${words.size + 1}")
            out.onPartial(words.joinToString(" "))
        }
        var blocks = 0
        var k = 0
        val total = (minutes * 600).toInt()
        while (blocks < total) {
            val talkBlocks = 20 + (k % 4) * 10 // 2-5 s phrases
            val pauseBlocks = if (k % 3 == 0) 9 else 6
            repeat(talkBlocks) { seg.feed(tone, tone.size); blocks++ }
            repeat(pauseBlocks) { seg.feed(quiet, quiet.size); blocks++ }
            k++
        }
        seg.flush()
        return out to words
    }

    @Test fun liveWordsAreADraftNotCommits() {
        val field = FakeField()
        val previews = mutableListOf<String>()
        val (out, words) = talk(1.5, field, previews)
        assertTrue("draft updated while talking", field.composingCalls >= 10)
        assertEquals("nothing committed while talking", 0, field.committed.size)
        assertEquals(" " + words.joinToString(" "), field.text)
        assertTrue("live preview updated", previews.size >= 10)
        out.onStop()
        out.onPartial("late partial after stop")
        assertTrue(out.onFinal(words.joinToString(" ")))
        assertEquals(listOf(" " + words.joinToString(" ")), field.committed)
        assertEquals(" " + words.joinToString(" "), field.text)
        assertEquals("", field.composing)
        assertFalse(previews.contains("late partial after stop"))
        assertEquals("", previews.last())
    }

    /** No length limit: an hour of dictation keeps every phrase, in order, exactly once, in one commit. */
    @Test fun oneHourSessionLosesNothing() {
        val field = FakeField()
        val (out, words) = talk(60.0, field, mutableListOf())
        assertTrue("an hour has hundreds of phrases", words.size > 600)
        out.onStop()
        assertTrue(out.onFinal(words.joinToString(" ")))
        assertEquals(1, field.committed.size)
        assertEquals(" " + words.joinToString(" "), field.text)
    }

    /** The bug fixed in 1.6.2: rewritten words used to stay in the field next to their replacements. */
    @Test fun rewritesHappenInPlaceWithNoDuplicates() {
        val field = FakeField()
        val out = DictationOutput(field, {}, glue = "")
        out.onPartial("so um we meet")
        out.onPartial("so um we meet at three")
        assertEquals("so um we meet at three", field.text)
        out.onPartial("So we meet at three, no wait, four")
        assertEquals("So we meet at three, no wait, four", field.text)
        out.onStop()
        assertTrue(out.onFinal("So we meet at four."))
        assertEquals("So we meet at four.", field.text)
        assertEquals(listOf("So we meet at four."), field.committed)
    }

    @Test fun existingTextIsNeverTouched() {
        val field = FakeField()
        field.type("Dear Sam,")
        val out = DictationOutput(field, {}, glue = " ")
        out.onPartial("thanks for")
        out.onPartial("Thanks for the notes")
        assertEquals("Dear Sam, Thanks for the notes", field.text)
        out.onStop()
        assertTrue(out.onFinal("thanks for the notes."))
        assertEquals("Dear Sam, thanks for the notes.", field.text)
    }

    @Test fun hiddenKeyboardShowsNothingUntilItComesBack() {
        val field = FakeField()
        val out = DictationOutput(field, {}, glue = "")
        out.onPartial("one two")
        assertEquals("one two", field.text)
        out.detach()
        assertEquals("", field.text)
        out.onPartial("one two three")
        out.onPartial("one two three four five")
        assertEquals("", field.text)
        out.attach()
        assertEquals("one two three four five", field.text)
        out.onStop()
        assertTrue(out.onFinal("One, two, three, four, five."))
        assertEquals(listOf("One, two, three, four, five."), field.committed)
        assertEquals("One, two, three, four, five.", field.text)
    }

    @Test fun noFieldAtStopKeepsTextOutAndRemovesDraft() {
        val field = FakeField()
        val out = DictationOutput(field, {}, glue = "")
        out.onPartial("hello there")
        out.onStop()
        assertFalse(out.onFinal("hello there", toField = false))
        assertEquals(0, field.committed.size)
        assertEquals("", field.text)
    }

    @Test fun finalIsAppliedOnceOnly() {
        val field = FakeField()
        val out = DictationOutput(field, {})
        out.onStop()
        assertTrue(out.onFinal("one"))
        assertFalse(out.onFinal("two"))
        assertEquals(listOf("one"), field.committed)
    }

    @Test fun blankFinalCommitsNothingAndClearsDraft() {
        val field = FakeField()
        val out = DictationOutput(field, {})
        out.onPartial("uh")
        out.onStop()
        assertFalse(out.onFinal("  "))
        assertEquals(0, field.committed.size)
        assertEquals("", field.text)
    }

    @Test fun cancelLeavesTheFieldAsItWas() {
        val field = FakeField()
        field.type("Dear Sam,")
        val out = DictationOutput(field, {}, glue = " ")
        out.onPartial("thanks for")
        out.onPartial("thanks for the notes")
        assertTrue(out.discard())
        assertEquals("Dear Sam,", field.text)
        assertEquals(0, field.committed.size)
    }

    @Test fun liveOffShowsNothingAndCommitsOnceOnStop() {
        val field = FakeField()
        val previews = mutableListOf<String>()
        val out = DictationOutput(field, { previews.add(it) }, glue = "", live = false)
        out.onPartial("a b c")
        out.onPartial("a b c d e")
        assertEquals("", field.text)
        assertEquals(0, field.composingCalls)
        assertTrue("no words in the panel either", previews.none { it.isNotEmpty() })
        out.onStop()
        assertTrue(out.onFinal("a b c d e."))
        assertEquals(listOf("a b c d e."), field.committed)
        assertEquals("a b c d e.", field.text)
    }

    @Test fun liveOffLongSessionIsOneCommit() {
        val field = FakeField()
        val (out, words) = talk(5.0, field, mutableListOf(), live = false)
        assertEquals(0, field.composingCalls)
        out.onStop()
        assertTrue(out.onFinal(words.joinToString(" ")))
        assertEquals(listOf(" " + words.joinToString(" ")), field.committed)
    }

    /** The draft and the one commit both go through adapt (DictationJoin), after the glue. */
    @Test fun adaptAppliesToDraftAndCommit() {
        val field = FakeField()
        field.type("I told him")
        val out = DictationOutput(field, {}, glue = " ", adapt = { DictationJoin.adapt("I told him", it) })
        out.onPartial("The plan")
        assertEquals("I told him the plan", field.text)
        out.onStop()
        assertTrue(out.onFinal("The plan works."))
        assertEquals(listOf(" the plan works."), field.committed)
        assertEquals("I told him the plan works.", field.text)
    }

    // --- binding to the field the dictation started in ---

    private val notes = DictationOutput.Target("com.example.notes", 7)
    private val bank = DictationOutput.Target("com.example.bank", 3)

    @Test
    fun aDraftIsNotReplayedIntoAnotherAppsField() {
        val field = FakeField()
        var focused: DictationOutput.Target? = notes
        val out = DictationOutput(field, {}, target = notes, currentTarget = { focused })
        out.onPartial("my secret plan")
        assertEquals("my secret plan", field.text)
        out.detach()
        assertEquals("", field.text)
        focused = bank
        out.attach()
        assertFalse(out.attached)
        out.onPartial("my secret plan and more")
        assertEquals("", field.text)
        out.onStop()
        assertFalse(out.onFinal("my secret plan and more"))
        assertEquals("", field.text)
        assertEquals(0, out.commits)
    }

    @Test
    fun backInTheOriginalFieldTheDraftResumes() {
        val field = FakeField()
        var focused: DictationOutput.Target? = notes
        val out = DictationOutput(field, {}, target = notes, currentTarget = { focused })
        out.onPartial("hello")
        out.detach()
        focused = bank
        out.attach()
        assertFalse(out.attached)
        focused = notes
        out.attach()
        assertTrue(out.attached)
        assertEquals("hello", field.text)
        out.onStop()
        assertTrue(out.onFinal("hello there"))
        assertEquals("hello there", field.text)
    }

    @Test
    fun focusMovingBetweenStopAndFinalKeepsTheTextOutOfTheNewField() {
        val field = FakeField()
        var focused: DictationOutput.Target? = notes
        val out = DictationOutput(field, {}, target = notes, currentTarget = { focused })
        out.onPartial("draft")
        out.onStop()
        focused = bank
        assertFalse(out.onFinal("draft final"))
        assertEquals(0, out.commits)
        assertEquals("", out.draft)
    }

    @Test
    fun noFocusedFieldCountsAsADifferentField() {
        val field = FakeField()
        var focused: DictationOutput.Target? = notes
        val out = DictationOutput(field, {}, target = notes, currentTarget = { focused })
        focused = null
        assertFalse(out.inOriginalField())
        out.onStop()
        assertFalse(out.onFinal("text"))
    }

    @Test
    fun withoutATargetBehaviourIsUnchanged() {
        val field = FakeField()
        val out = DictationOutput(field, {})
        assertTrue(out.inOriginalField())
        out.onPartial("plain")
        out.onStop()
        assertTrue(out.onFinal("plain"))
        assertEquals("plain", field.text)
    }

    @Test
    fun sameHostViewButANewInputGenerationIsADifferentField() {
        // WebView/Compose fields share one host view id; a fresh onStartInput bumps the generation.
        val field = FakeField()
        val first = DictationOutput.Target("com.example.web", 1, inputType = 1, generation = 1)
        var focused: DictationOutput.Target? = first
        val out = DictationOutput(field, {}, target = first, currentTarget = { focused })
        out.onPartial("hello")
        assertEquals("hello", field.text)
        focused = first.copy(inputType = 0x81, generation = 2) // the password box on the same page
        assertFalse(out.inOriginalField())
        out.onPartial("hello there")
        assertEquals("", out.draft)
        out.onStop()
        assertFalse(out.onFinal("hello there"))
        assertEquals(0, out.commits)
    }

    // --- voice edits of the text already in the field ---

    @Test
    fun aVoiceEditRemovesTheDraftAndEditsInsteadOfTyping() {
        val field = FakeField()
        field.type("Meet on Tuesday.")
        val out = DictationOutput(field, {}, glue = " ")
        out.onPartial("change Tuesday to Wednesday")
        assertEquals("Meet on Tuesday. change Tuesday to Wednesday", field.text)
        out.onStop()
        val cmd = FieldEdit.parse("Change Tuesday to Wednesday.")!!
        assertEquals(DictationOutput.FieldEditResult.APPLIED, out.tryFieldEdit(cmd))
        assertEquals("Meet on Wednesday.", field.text)
        assertEquals("", out.draft)
        assertEquals(0, out.commits)
        assertFalse("the dictation is over", out.onFinal("Change Tuesday to Wednesday."))
        assertEquals("Meet on Wednesday.", field.text)
    }

    @Test
    fun aVoiceEditThatFindsNothingTypesTheWordsOnce() {
        val field = FakeField()
        field.type("Hello there.")
        val out = DictationOutput(field, {}, glue = " ")
        out.onPartial("delete Tuesday")
        out.onStop()
        assertEquals(DictationOutput.FieldEditResult.NOT_FOUND, out.tryFieldEdit(FieldEdit.Command.Delete("Tuesday")))
        assertEquals("the field is untouched", "Hello there.", field.text)
        assertEquals(0, field.committed.size)
        assertEquals(DictationOutput.State.FINISHING, out.state)
        assertTrue("the dictation is still open", out.onFinal("Delete Tuesday."))
        assertEquals(listOf(" Delete Tuesday."), field.committed)
        assertEquals("Hello there. Delete Tuesday.", field.text)
        assertEquals(1, out.commits)
        assertFalse(out.onFinal("Delete Tuesday."))
    }

    @Test
    fun aVoiceEditInAnotherFieldIsNotApplicableAndTypesAsBefore() {
        val field = FakeField()
        var focused: DictationOutput.Target? = notes
        val out = DictationOutput(field, {}, target = notes, currentTarget = { focused })
        out.onStop()
        focused = bank
        assertEquals(DictationOutput.FieldEditResult.NOT_APPLICABLE, out.tryFieldEdit(FieldEdit.Command.Delete("x")))
        focused = notes
        out.detach()
        assertEquals(DictationOutput.FieldEditResult.NOT_APPLICABLE, out.tryFieldEdit(FieldEdit.Command.Delete("x")))
        assertEquals("", field.text)
    }
}
