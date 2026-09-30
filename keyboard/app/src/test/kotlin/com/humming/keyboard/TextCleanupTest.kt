package com.humming.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parity P0 cleanup items, checked on both raw engine text and text the core already cleaned. */
class TextCleanupTest {
    private val dict = listOf("Humming", "Okonkwo")
    private fun clean(s: String) = TextCleanup.apply(s, dict)

    @Test fun p0_3_plainSentence() = assertEquals("See you soon.", clean("see you soon"))

    @Test fun p0_4_silenceHallucinationsInsertNothing() {
        assertEquals("", clean("Thank you for watching!"))
        assertEquals("", clean(" Thanks for watching."))
        assertEquals("", clean("[BLANK_AUDIO]"))
        assertEquals("", clean("(music)"))
        assertEquals("", clean("Subtitles by the Amara.org community"))
        assertEquals("", clean("you"))
        assertEquals("Thank you.", clean("thank you"))
    }

    @Test fun timesAreCompact() {
        assertEquals("Meet at 4pm.", clean("Meet at 4 pm."))
        assertEquals("Meet at 4pm.", clean("meet at 4 p.m."))
        assertEquals("Call at 10:30am tomorrow.", clean("call at 10:30 AM tomorrow"))
        assertEquals("I am 4 feet away.", clean("I am 4 feet away"))
        assertEquals("Meet at 4pm. Then we eat.", clean("meet at 4 pm. then we eat"))
    }

    @Test fun p0_6_fillers() {
        assertEquals("I think we should ship it.", clean("um, so, uh, I think we should ship it"))
        assertEquals("I think we should ship it.", clean("Um, so, uh, I think we should ship it."))
        assertEquals("I think we should ship it.", clean("So, I think we should ship it."))
    }

    @Test fun p0_7_repeats() {
        assertEquals("I think we should go.", clean("I I think we we should go"))
        assertEquals("We should go now.", clean("we should we should go now"))
        assertEquals("It was very very good.", clean("it was very very good"))
    }

    @Test fun p0_8_selfCorrectionNumber() {
        assertEquals("Meet at 4pm.", clean("um, meet at 3, no, 4pm"))
        assertEquals("Meet at 4pm.", clean("Meet at 3, no, 4pm."))
    }

    /** What the engine actually produced for "meet at three, no, four p m" in the emulator test, and its cousins. */
    @Test fun p0_8_misheardNo() {
        assertEquals("Meet at 4pm.", clean("Meet at 3.0.4pm."))
        assertEquals("Meet at 4pm.", clean("meet at 3 oh 4pm"))
        assertEquals("Meet at 4pm.", clean("Meet at 3, know, 4 p.m."))
        assertEquals("Meet at 4pm.", clean("Meet at 3. No, 4pm."))
        assertEquals("It costs 6 dollars.", clean("it costs 5 nope 6 dollars"))
    }

    @Test fun p0_8_versionNumbersUntouched() {
        assertEquals("Version 3.0.4 is out.", clean("version 3.0.4 is out"))
        assertEquals("I have 2 oh 3 kids.", clean("I have 2 oh 3 kids"))
    }

    @Test fun p0_9_selfCorrectionName() {
        assertEquals("Send it to Jane.", clean("send it to John, sorry, I mean Jane"))
        assertEquals("Send it to Jane.", clean("Send it to John, sorry, I mean Jane."))
    }

    @Test fun p0_9_realNoIsKept() = assertEquals("Is it ready, no, not yet.", clean("Is it ready, no, not yet."))

    @Test fun p0_10_punctuation() {
        assertEquals("What time is it?", clean("what time is it question mark"))
        assertEquals("What time is it?", clean("what time is it"))
        assertEquals("Hello, world.", clean("hello comma world"))
        assertEquals("I think I can.", clean("i think i can"))
        assertEquals("First line\nSecond line.", clean("first line new line second line"))
    }

    @Test fun p0_14_dictionary() {
        assertEquals("Open Humming and ping Okonkwo.", clean("open humming and ping okonkwo"))
        assertEquals("Ask Nova Beta.", TextCleanup.apply("ask nova   beta", listOf("Nova Beta")))
    }

    @Test fun emptyStaysEmpty() {
        assertEquals("", clean(""))
        assertEquals("", clean("um, uh"))
        assertTrue(TextCleanup.isHallucination("Thanks for watching!"))
    }
}
