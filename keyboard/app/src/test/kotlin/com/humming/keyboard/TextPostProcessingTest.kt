package com.humming.keyboard

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Humming 1.7.1: the single stages of the dictation clean-up that were only reached through the whole pipeline
 * (TextCleanupTest), and the audio helpers every transcript starts from.
 */
class TextPostProcessingTest {
    @Test fun compactTimesWritesTimesTheWayPeopleTypeThem() {
        assertEquals("meet at 4pm", TextCleanup.compactTimes("meet at 4 pm"))
        assertEquals("call at 10:30am, ok", TextCleanup.compactTimes("call at 10:30 AM, ok"))
        assertEquals("at 9pm.", TextCleanup.compactTimes("at 9 p.m."))
        assertEquals("room 4 amazing", TextCleanup.compactTimes("room 4 amazing"))
    }

    @Test fun removeRepeatsDropsStutters() {
        assertEquals("I think we should go", TextCleanup.removeRepeats("I I think we we should go"))
        assertEquals("we should go", TextCleanup.removeRepeats("we should we should go"))
    }

    @Test fun removeRepeatsKeepsDeliberateRepeats() {
        assertEquals("no no no", TextCleanup.removeRepeats("no no no"))
        assertEquals("he said that that was fine", TextCleanup.removeRepeats("he said that that was fine"))
        assertEquals("well, well", TextCleanup.removeRepeats("well, well"))
    }

    @Test fun applyDictionaryFixesCasingOfWholeWordsOnly() {
        val terms = listOf("Okonkwo", "Kubernetes", "New York", "  ")
        assertEquals(
            "meet Okonkwo in New York about Kubernetes",
            TextCleanup.applyDictionary("meet okonkwo in new   york about KUBERNETES", terms),
        )
        assertEquals("booking", TextCleanup.applyDictionary("booking", listOf("OK")))
        assertEquals("unchanged", TextCleanup.applyDictionary("unchanged", emptyList()))
    }

    @Test fun finishAddsCapitalsSpacingAndAFinalMark() {
        assertEquals("Hello world.", TextCleanup.finish("hello world"))
        assertEquals("What time is it?", TextCleanup.finish("what time is it"))
        assertEquals("I think so, really.", TextCleanup.finish("i think so , really"))
        assertEquals("A, b.", TextCleanup.finish("a,b"))
        assertEquals("3,000 people.", TextCleanup.finish("3,000 people"))
        assertEquals("First line\nSecond line.", TextCleanup.finish("first line\n  second line"))
        assertEquals("Done!", TextCleanup.finish("done!"))
    }

    @Test fun selfCorrectKeepsWhatWasMeant() {
        assertEquals("meet at 4pm", TextCleanup.selfCorrect("meet at 3, no, 4pm"))
        assertEquals("to Jane", TextCleanup.selfCorrect("to John, sorry, I mean Jane"))
        assertEquals("nothing to fix here", TextCleanup.selfCorrect("nothing to fix here"))
    }

    @Test fun scratchThatRemovesTheSentenceBeforeIt() {
        assertEquals("Book Saturday.", VoiceCommands.retract("Book Friday. Scratch that. Book Saturday."))
        val plain = "Book Friday. Book Saturday."
        assertEquals(plain, VoiceCommands.retract(plain))
    }

    @Test fun audioClipReadsLittleEndianSamples() {
        val clip = AudioClip(pcm(0, 16384, -32768), 16_000)
        assertEquals(3, clip.sampleCount)
        assertEquals(3 / 16_000.0, clip.durationSeconds, 1e-12)
        assertEquals(listOf(0, 16384, -32768), (0 until 3).map { clip.sample(it) })
        assertArrayEquals(floatArrayOf(0f, 0.5f, -1f), clip.toFloat32(), 0f)
    }

    @Test fun peakLevelInDbfs() {
        assertEquals(0.0, AudioClip(pcm(0, -32768), 16_000).peakDbfs(), 1e-9)
        assertEquals(-6.0206, AudioClip(pcm(16384), 16_000).peakDbfs(), 1e-4)
        assertEquals("silence", -120.0, AudioClip(pcm(0, 0), 16_000).peakDbfs(), 0.0)
    }

    @Test fun wavHeaderIsCanonical() {
        val data = pcm(1, 2, 3)
        val wav = AudioClip(data, 16_000).toWav()
        val b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(Wav.HEADER_BYTES + data.size, wav.size)
        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals(36 + data.size, b.getInt(4))
        assertEquals("WAVEfmt ", String(wav, 8, 8, Charsets.US_ASCII))
        assertEquals(1, b.getShort(20).toInt())
        assertEquals(1, b.getShort(22).toInt())
        assertEquals(16_000, b.getInt(24))
        assertEquals(32_000, b.getInt(28))
        assertEquals("data", String(wav, 36, 4, Charsets.US_ASCII))
        assertEquals(data.size, b.getInt(40))
        assertArrayEquals(data, wav.copyOfRange(Wav.HEADER_BYTES, wav.size))
    }

    private fun pcm(vararg samples: Int): ByteArray {
        val b = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (s in samples) b.putShort(s.toShort())
        return b.array()
    }
}
