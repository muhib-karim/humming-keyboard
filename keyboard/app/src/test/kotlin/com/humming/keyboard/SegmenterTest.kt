package com.humming.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class SegmenterTest {
    private val sr = 16_000

    private fun tone(sec: Double, amp: Double = 0.2): ShortArray =
        ShortArray((sec * sr).toInt()) { i -> (amp * 32767 * sin(2 * PI * 220 * i / sr)).toInt().toShort() }

    private fun silence(sec: Double): ShortArray = ShortArray((sec * sr).toInt())

    private fun run(vararg parts: ShortArray): List<FloatArray> {
        val out = mutableListOf<FloatArray>()
        val s = Segmenter(sr) { out.add(it) }
        for (p in parts) {
            var i = 0
            while (i < p.size) { // feed in 100 ms blocks like the recorder
                val n = minOf(1600, p.size - i)
                s.feed(p.copyOfRange(i, i + n), n)
                i += n
            }
        }
        s.flush()
        return out
    }

    @Test fun silenceOnlyEmitsNothing() {
        assertEquals(0, run(silence(10.0)).size)
    }

    @Test fun cutsAtPauses() {
        val segs = run(silence(0.3), tone(2.0), silence(0.8), tone(2.5), silence(0.8), tone(1.8), silence(0.3))
        assertEquals(3, segs.size)
    }

    @Test fun shortPausesDoNotCut() {
        val segs = run(tone(0.6), silence(0.2), tone(0.6), silence(0.2), tone(0.6))
        assertEquals(1, segs.size)
    }

    @Test fun longSpeechIsCutUnderWhisperWindow() {
        val segs = run(tone(70.0))
        assertTrue(segs.size >= 3)
        segs.forEach { assertTrue(it.size <= 24 * sr + 320) }
        assertEquals(70 * sr, segs.sumOf { it.size })
    }

    @Test fun noiseFloorIsNotSpeech() {
        val noise = ShortArray(5 * sr) { i -> ((i * 7919 % 200) - 100).toShort() } // about -50 dBFS
        assertEquals(0, run(noise).size)
    }

    @Test fun keepsAllAudioAcrossSegments() {
        val parts = arrayOf(tone(3.0), silence(1.0), tone(3.0), silence(1.0))
        val segs = run(*parts)
        assertEquals(2, segs.size)
    }
}
