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

    private fun strict(s: String) = TextCleanup.apply(s, dict, strict = true)

    @Test fun accuracyModeRemovesHedgeFillers() {
        assertEquals("I was thinking we could meet at 4pm.",
            strict("So, um, I was, like, thinking we could, you know, meet at 3, no, 4pm"))
        assertEquals("The build is fine.", strict("Basically, the build is, honestly, fine"))
        assertEquals("We ship on Friday.", strict("We ship on Friday, you know."))
        assertEquals("Let's go.", strict("ah, mm, let's go"))
        assertEquals("Send the report.", strict("send the report period"))
    }

    @Test fun accuracyModeKeepsMeaningfulWords() {
        assertEquals("I like this one.", strict("I like this one"))
        assertEquals("It looks like rain.", strict("it looks like rain"))
        assertEquals("It is actually done.", strict("it is actually done"))
        assertEquals("The period ends in May.", strict("the period ends in May"))
    }

    @Test fun defaultModeKeepsHedges() {
        assertEquals("It was, like, huge.", clean("it was, like, huge"))
        assertEquals("It was huge.", strict("it was, like, huge"))
    }

    @Test fun emptyStaysEmpty() {
        assertEquals("", clean(""))
        assertEquals("", clean("um, uh"))
        assertTrue(TextCleanup.isHallucination("Thanks for watching!"))
    }

    /** 1.7: more spoken punctuation, the same on desktop (text_cleanup.rs) and iOS (TextCleanup.swift). */
    @Test fun spokenColonSemicolonQuotesBracketsEllipsis() {
        assertEquals("Note: buy milk.", clean("note colon buy milk"))
        assertEquals("First; second.", clean("first semicolon second"))
        assertEquals("He said \"hi\".", clean("he said open quote hi close quote"))
        assertEquals("\"Hello\".", clean("open quote hello end quote"))
        assertEquals("Call me (maybe) tomorrow.", clean("call me open bracket maybe close bracket tomorrow"))
        assertEquals("Wait...", clean("wait dot dot dot"))
        assertEquals("Wait...", clean("wait ellipsis"))
    }

    @Test fun spokenPunctuationNeedsWholeWords() {
        assertEquals("The colonel quotes semicolons.", clean("the colonel quotes semicolons"))
    }

    @Test fun deleteLastWord() {
        assertEquals("I love dogs.", clean("I love cats delete last word dogs"))
        assertEquals("I love dogs.", clean("I love big cats delete last word delete last word dogs"))
        assertEquals("See you.", clean("see you soon. Scratch the last word."))
    }

    @Test fun deleteLastWordAlone() {
        assertEquals("", clean("delete last word"))
        assertEquals("Hello.", clean("delete last word hello"))
    }

    /** Review fixes: a line break before an opener survives; engine commas around the commands are absorbed. */
    @Test fun openersKeepLineBreaksAndAbsorbEngineCommas() {
        assertEquals("First line\n\"Hello\".", clean("first line new line open quote hello close quote"))
        assertEquals("He said, \"hi\".", clean("He said, open quote, hi, close quote."))
        assertEquals("Call me (maybe), tomorrow.", clean("Call me, open bracket, maybe, close bracket, tomorrow."))
        assertEquals("Can you remove the last word of the title?", clean("can you remove the last word of the title"))
    }
}
