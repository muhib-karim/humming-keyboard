package com.humming.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The language key's next / previous rule (SubtypeManager.switchToNextSubtype / switchToPrevSubtype). */
class SubtypeCycleTest {
    private val langs = listOf("en", "bn", "de")

    @Test fun nextMovesForwardAndWraps() {
        assertEquals("bn", SubtypeCycle.next(langs, "en"))
        assertEquals("de", SubtypeCycle.next(langs, "bn"))
        assertEquals("en", SubtypeCycle.next(langs, "de"))
    }

    @Test fun previousMovesBackAndWraps() {
        assertEquals("de", SubtypeCycle.previous(langs, "en"))
        assertEquals("en", SubtypeCycle.previous(langs, "bn"))
        assertEquals("bn", SubtypeCycle.previous(langs, "de"))
    }

    @Test fun oneLanguageStaysPut() {
        assertEquals("en", SubtypeCycle.next(listOf("en"), "en"))
        assertEquals("en", SubtypeCycle.previous(listOf("en"), "en"))
    }

    @Test fun aRemovedActiveLanguageGoesToTheFirstInTheList() {
        // Before 1.7.1 this fell back to Subtype.DEFAULT, which need not be in the list at all.
        assertEquals("en", SubtypeCycle.next(langs, "fr"))
        assertEquals("en", SubtypeCycle.previous(langs, "fr"))
        assertEquals("en", SubtypeCycle.next(langs, null))
    }

    @Test fun noLanguagesGivesNull() {
        assertNull(SubtypeCycle.next(emptyList<String>(), "en"))
        assertNull(SubtypeCycle.previous(emptyList<String>(), null))
    }

    @Test fun fullCycleVisitsEveryLanguageOnce() {
        var cur = "en"
        val seen = mutableListOf(cur)
        repeat(langs.size) {
            cur = SubtypeCycle.next(langs, cur)!!
            seen += cur
        }
        assertEquals(listOf("en", "bn", "de", "en"), seen)
    }
}
