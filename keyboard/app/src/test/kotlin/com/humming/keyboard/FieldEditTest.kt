package com.humming.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldEditTest {
    private fun edit(before: String, words: String): String? =
        FieldEdit.parse(words)?.let { FieldEdit.plan(before, it)?.applyTo(before) }

    @Test fun parsesAllVerbsAndRecognizerNoise() {
        assertEquals(FieldEdit.Command.Replace("Tuesday", "Wednesday"), FieldEdit.parse("Change Tuesday to Wednesday."))
        assertEquals(
            FieldEdit.Command.Replace("new date", "next Friday"),
            FieldEdit.parse("Okay, REPLACE new date WITH next Friday!"),
        )
        assertEquals(FieldEdit.Command.Delete("the old date"), FieldEdit.parse("ok, delete the old date?"))
        assertEquals(FieldEdit.Command.Delete("old date"), FieldEdit.parse("REMOVE old date,"))
        assertEquals(
            FieldEdit.Command.Insert("very", "good work", true),
            FieldEdit.parse("insert very before good work."),
        )
        assertEquals(
            FieldEdit.Command.Insert("next week", "Tuesday", false),
            FieldEdit.parse("INSERT next week AFTER Tuesday!"),
        )
        assertEquals(
            FieldEdit.Command.Replace("Monday morning", "Tuesday afternoon"),
            FieldEdit.parse("change Monday morning to Tuesday afternoon"),
        )
    }

    @Test fun doesNotParseExistingCommandsOrPartialUtterances() {
        listOf(
            "delete that", "scratch that", "undo that", "remove that", "delete the last sentence",
            "delete last word", "delete the last word", "scratch the last word", "delete all",
            "delete everything", "new line", "new paragraph", "okay, delete that.",
            "Please change Monday to Tuesday",
        ).forEach { phrase -> assertNull(phrase, FieldEdit.parse(phrase)) }
        assertNull(FieldEdit.parse(""))
        assertNull(FieldEdit.parse("change to Tuesday"))
        assertNull(FieldEdit.parse("change Monday to"))
        assertNull(FieldEdit.parse("insert after Tuesday"))
        assertNull(FieldEdit.parse("delete "))
        assertNull(FieldEdit.parse("delete ${"x".repeat(61)}"))
        assertNull(FieldEdit.parse("change Tuesday to ${"x".repeat(61)}"))
        assertNull(FieldEdit.parse("insert ${"x".repeat(61)} before Monday"))
        assertNull(FieldEdit.parse("delete ${"x".repeat(195)}"))
    }

    @Test fun ambiguousSeparatorIsDictation() {
        assertNull(FieldEdit.parse("change go to school to work"))
        assertNull(FieldEdit.parse("replace this with that with care"))
        assertNull(FieldEdit.parse("insert very before good before"))
        assertEquals(FieldEdit.Command.Replace("Tuesday", "Wednesday"), FieldEdit.parse("change Tuesday to Wednesday"))
        assertEquals(FieldEdit.Command.Insert("today", "Tuesday", false), FieldEdit.parse("insert today after Tuesday"))
    }

    @Test fun replacesLastWholeWordAndCarriesCapital() {
        assertEquals(
            "Meet Tuesday, then Wednesday.",
            edit("Meet Tuesday, then Tuesday.", "change Tuesday to Wednesday"),
        )
        assertEquals("Meet Wednesday at noon.", edit("Meet Tuesday at noon.", "change tuesday to wednesday"))
        assertEquals("concatenate cat!", edit("concatenate cat!", "replace cat with cat"))
        assertEquals("concatenate dog!", edit("concatenate cat!", "replace cat with dog"))
        assertEquals("CAT and dog", edit("CAT and cat", "change cat to dog"))
        assertNull("an apostrophe is part of the word", edit("I'm ready", "change I to We"))
        assertNull(FieldEdit.plan("concatenate", FieldEdit.Command.Replace("cat", "dog")))
        assertNull(FieldEdit.plan("don't", FieldEdit.Command.Replace("don", "do")))
        assertNull(FieldEdit.plan("hello", FieldEdit.Command.Delete("goodbye")))
        assertEquals(
            "Next Tuesday afternoon",
            edit("Next Monday morning", "change Monday morning to Tuesday afternoon"),
        )
    }

    @Test fun deletesOneAdjacentSpace() {
        assertEquals("hello world", edit("hello old world", "delete old"))
        assertEquals("world", edit("old world", "remove old"))
        assertEquals("hello", edit("hello old", "delete old"))
        assertEquals("hello,", edit("hello, old", "delete old"))
        assertEquals("hello, world", edit("hello, old world", "delete old"))
        assertEquals("cat dog", edit("cat cat dog", "delete cat"))
        assertEquals("", edit("cat", "delete cat"))
    }

    @Test fun insertsAroundLastAnchor() {
        assertEquals("a new day", edit("a day", "insert new before day"))
        assertEquals("a day today", edit("a day", "insert today after day"))
        assertEquals("day then a new day", edit("day then a day", "insert new before day"))
        assertEquals("day then day today", edit("day then day", "insert today after day"))
        assertEquals("a very good day", edit("a good day", "insert very before good day"))
        assertEquals("Tuesday next week,", edit("Tuesday,", "insert next week after Tuesday"))
        assertNull(FieldEdit.plan("day", FieldEdit.Command.Insert("new", "night", true)))
        assertTrue(FieldEdit.plan("cat", FieldEdit.Command.Insert("big", "cat", true))!!.start == 0)
    }

    @Test fun windowContextCharIsNeverMatched() {
        assertNull(FieldEdit.plan("rTuesday", FieldEdit.Command.Delete("Tuesday"), 1))
        assertEquals(
            FieldEdit.Edit(1, 8, "Monday"),
            FieldEdit.plan(" Tuesday", FieldEdit.Command.Replace("Tuesday", "Monday"), 1),
        )
        assertEquals(
            FieldEdit.Edit(0, 7, "Monday"),
            FieldEdit.plan("Tuesday", FieldEdit.Command.Replace("Tuesday", "Monday")),
        )
    }

    @Test fun matchEndingAtTheCursorNeedsAWordBoundaryAfterIt() {
        assertNull(FieldEdit.plan("cat", FieldEdit.Command.Delete("cat"), next = 'f'))
        assertEquals(
            FieldEdit.Edit(0, 4, ""),
            FieldEdit.plan("cat and cat", FieldEdit.Command.Delete("cat"), next = 'f'),
        )
        assertEquals(FieldEdit.Edit(0, 3, ""), FieldEdit.plan("cat", FieldEdit.Command.Delete("cat"), next = ' '))
        assertEquals(FieldEdit.Edit(0, 3, ""), FieldEdit.plan("cat", FieldEdit.Command.Delete("cat"), next = '.'))
    }
}
