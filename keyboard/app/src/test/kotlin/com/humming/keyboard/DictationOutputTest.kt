package com.humming.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class DictationOutputTest {
    private val sr = 16_000

    /** A text field: commits append at the cursor (the end), deletes remove before it. */
    private class FakeField : DictationOutput.Field {
        val committed = mutableListOf<String>()
        val text = StringBuilder()
        override fun commit(text: String) { committed.add(text); this.text.append(text) }
        override fun textBeforeCursor(n: Int): String = text.substring(maxOf(0, text.length - n))
        override fun deleteBefore(n: Int): Boolean { text.setLength(maxOf(0, text.length - n)); return true }
    }

    private val tone: ShortArray = ShortArray(sr / 10) { i -> (0.2 * 32767 * sin(2 * PI * 220 * i / sr)).toInt().toShort() }
    private val quiet = ShortArray(sr / 10)

    /**
     * [minutes] of talking with natural pauses, fed in 100 ms blocks like the recorder, every segment "transcribed".
     * Returns the field, the output and the words spoken.
     */
    private fun talk(minutes: Double, field: FakeField, previews: MutableList<String>): Pair<DictationOutput, List<String>> {
        val out = DictationOutput(field, { previews.add(it) }, glue = " ")
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

    @Test fun wordsAreTypedLiveWhileTalking() {
        val field = FakeField()
        val previews = mutableListOf<String>()
        val (out, words) = talk(1.5, field, previews)
        assertTrue("text typed while talking", field.committed.size >= 10)
        assertTrue("live preview updated", previews.size >= 10)
        out.onStop()
        out.onPartial("late partial after stop")
        assertTrue(out.onFinal(words.joinToString(" ")))
        assertEquals(" " + words.joinToString(" "), field.text.toString())
        assertFalse(previews.contains("late partial after stop"))
        assertEquals("", previews.last())
    }

    /** No length limit: an hour of dictation keeps every phrase, in order, exactly once. */
    @Test fun oneHourSessionLosesNothing() {
        val field = FakeField()
        val (out, words) = talk(60.0, field, mutableListOf())
        assertTrue("an hour has hundreds of phrases", words.size > 600)
        out.onStop()
        assertTrue(out.onFinal(words.joinToString(" ")))
        assertEquals(" " + words.joinToString(" "), field.text.toString())
    }

    @Test fun cleanupRewriteOfTypedWordsIsReplaced() {
        val field = FakeField()
        val out = DictationOutput(field, {}, glue = "")
        out.onPartial("so um we meet")
        out.onPartial("so um we meet at three")
        out.onPartial("so um we meet at three no wait four")
        assertEquals("so um we meet at ", field.text.toString())
        out.onStop()
        assertTrue(out.onFinal("So we meet at four."))
        assertEquals("So we meet at four.", field.text.toString())
    }

    @Test fun hiddenKeyboardHoldsWordsUntilItComesBack() {
        val field = FakeField()
        val out = DictationOutput(field, {}, glue = "")
        out.onPartial("one two")
        out.detach()
        out.onPartial("one two three")
        out.onPartial("one two three four five")
        assertEquals("", field.text.toString())
        out.attach()
        assertEquals("one two ", field.text.toString())
        out.onStop()
        assertTrue(out.onFinal("one two three four five"))
        assertEquals("one two three four five", field.text.toString())
    }

    @Test fun noFieldAtStopKeepsTheRestOut() {
        val field = FakeField()
        val out = DictationOutput(field, {}, glue = "")
        out.onStop()
        assertFalse(out.onFinal("hello there", toField = false))
        assertEquals(0, field.committed.size)
    }

    @Test fun finalIsAppliedOnceOnly() {
        val field = FakeField()
        val out = DictationOutput(field, {})
        out.onStop()
        assertTrue(out.onFinal("one"))
        assertFalse(out.onFinal("two"))
        assertEquals(listOf("one"), field.committed)
    }

    @Test fun blankFinalCommitsNothing() {
        val field = FakeField()
        val out = DictationOutput(field, {})
        out.onStop()
        assertFalse(out.onFinal("  "))
        assertEquals(0, field.committed.size)
    }

    @Test fun cancelRemovesWhatThisDictationTyped() {
        val field = FakeField()
        field.commit("Dear Sam,")
        val out = DictationOutput(field, {}, glue = " ")
        out.onPartial("thanks for")
        out.onPartial("thanks for the notes")
        // Only words two partials agree on, minus the newest one (it may still be rewritten), are typed.
        assertEquals("Dear Sam, thanks ", field.text.toString())
        assertTrue(out.discard())
        assertEquals("Dear Sam,", field.text.toString())
    }

    @Test fun notLiveMeansOneCommitOnStop() {
        val field = FakeField()
        val out = DictationOutput(field, {}, glue = "", live = false)
        out.onPartial("a b c")
        out.onPartial("a b c d e")
        assertEquals(0, field.committed.size)
        out.onStop()
        assertTrue(out.onFinal("a b c d e."))
        assertEquals(listOf("a b c d e."), field.committed)
    }
}
