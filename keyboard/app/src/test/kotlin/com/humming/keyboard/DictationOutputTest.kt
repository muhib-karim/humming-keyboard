package com.humming.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class DictationOutputTest {
    private val sr = 16_000

    private class FakeField : DictationOutput.Field {
        val committed = mutableListOf<String>()
        override fun commit(text: String) { committed.add(text) }
    }

    private fun tone(sec: Double): ShortArray =
        ShortArray((sec * sr).toInt()) { i -> (0.2 * 32767 * sin(2 * PI * 220 * i / sr)).toInt().toShort() }

    private fun silence(sec: Double): ShortArray = ShortArray((sec * sr).toInt())

    /** 90 s of talking with natural pauses, fed in 100 ms blocks like the recorder, every segment "transcribed". */
    @Test fun ninetySecondSessionCommitsNothingBeforeStop() {
        val field = FakeField()
        val previews = mutableListOf<String>()
        val out = DictationOutput(field) { previews.add(it) }
        val words = mutableListOf<String>()
        val seg = Segmenter(sr) { s ->
            words.add("phrase${words.size + 1}")
            out.onPartial(words.joinToString(" "))
        }
        var fed = 0.0
        var k = 0
        while (fed < 90.0) {
            val talk = 2.0 + (k % 4) // 2-5 s phrases
            val pause = if (k % 3 == 0) 0.9 else 0.6
            for (p in listOf(tone(talk), silence(pause))) {
                var i = 0
                while (i < p.size) {
                    val n = minOf(1600, p.size - i)
                    seg.feed(p.copyOfRange(i, i + n), n)
                    i += n
                }
            }
            fed += talk + pause
            k++
            assertEquals("no commit while talking (t=${"%.1f".format(fed)} s)", 0, field.committed.size)
        }
        assertTrue("live preview updated while talking", previews.size >= 10)
        assertEquals(0, out.commits)
        out.onStop()
        seg.flush()
        out.onPartial("late partial after stop")
        assertEquals(0, field.committed.size)
        assertTrue(out.onFinal(words.joinToString(" "), " "))
        assertEquals(1, field.committed.size)
        assertEquals(" " + words.joinToString(" "), field.committed[0])
        assertEquals("", previews.last())
        assertFalse(previews.contains("late partial after stop"))
    }

    @Test fun keyboardClosedMidDictationKeepsTextOutOfField() {
        val field = FakeField()
        val out = DictationOutput(field) {}
        out.onPartial("hello there")
        out.onStop()
        assertFalse(out.onFinal("hello there", "", toField = false))
        assertEquals(0, field.committed.size)
    }

    @Test fun finalIsCommittedOnceOnly() {
        val field = FakeField()
        val out = DictationOutput(field) {}
        out.onStop()
        assertTrue(out.onFinal("one", ""))
        assertFalse(out.onFinal("two", ""))
        assertEquals(listOf("one"), field.committed)
    }

    @Test fun blankFinalCommitsNothing() {
        val field = FakeField()
        val out = DictationOutput(field) {}
        out.onStop()
        assertFalse(out.onFinal("  ", ""))
        assertEquals(0, field.committed.size)
    }
}
