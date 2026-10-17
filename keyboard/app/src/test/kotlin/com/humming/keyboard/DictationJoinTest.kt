package com.humming.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class DictationJoinTest {
    @Test fun emptyFieldKeepsSentence() {
        assertEquals("Hello there.", DictationJoin.adapt("", "Hello there."))
        assertEquals("The plan works.", DictationJoin.adapt("   ", "The plan works."))
    }

    @Test fun continuesSentence() {
        assertEquals("the plan is fine.", DictationJoin.adapt("I told him", "The plan is fine."))
        assertEquals("and we left.", DictationJoin.adapt("Bring snacks,", "And we left."))
        assertEquals("we start at 9.", DictationJoin.adapt("Agenda:", "We start at 9."))
    }

    @Test fun afterSentenceEndKeeps() {
        assertEquals("We start.", DictationJoin.adapt("Done. ", "We start."))
        assertEquals("We start.", DictationJoin.adapt("Really?", "We start."))
        assertEquals("We start.", DictationJoin.adapt("Notes\n", "We start."))
    }

    @Test fun keepsNamesAndI() {
        assertEquals("John left.", DictationJoin.adapt("I told", "John left."))
        assertEquals("I agree.", DictationJoin.adapt("Honestly", "I agree."))
        assertEquals("I'm in.", DictationJoin.adapt("Count me", "I'm in."))
        assertEquals("NASA called.", DictationJoin.adapt("Guess what", "NASA called."))
        assertEquals("Will to call.", DictationJoin.adapt("ask", "Will to call.", dictionary = listOf("Will")))
    }

    @Test fun dropsPeriodBeforeContinuation() {
        assertEquals("the plan works", DictationJoin.adapt("I said", "The plan works.", after = " today"))
        assertEquals("the plan works ", DictationJoin.adapt("I said", "The plan works.", after = "today"))
        assertEquals("Is it ready?", DictationJoin.adapt("", "Is it ready?", after = " now"))
        assertEquals("Wait...", DictationJoin.adapt("", "Wait...", after = " then"))
        assertEquals("The end.", DictationJoin.adapt("", "The end.", after = ". More"))
    }

    @Test fun glueOnlyWhereAWordWouldTouch() {
        assertEquals("", DictationJoin.glue(""))
        assertEquals("", DictationJoin.glue("a "))
        assertEquals("", DictationJoin.glue("line\n"))
        assertEquals(" ", DictationJoin.glue("word"))
        assertEquals(" ", DictationJoin.glue("end."))
        assertEquals("", DictationJoin.glue("("))
        assertEquals("", DictationJoin.glue("say “"))
        assertEquals("", DictationJoin.glue("say \""))
        assertEquals(" ", DictationJoin.glue("said \"hi\""))
        assertEquals("", DictationJoin.glue("@"))
        assertEquals("", DictationJoin.glue("#"))
    }

    @Test fun blankTextIsUnchanged() {
        assertEquals("", DictationJoin.adapt("word", ""))
    }
}
