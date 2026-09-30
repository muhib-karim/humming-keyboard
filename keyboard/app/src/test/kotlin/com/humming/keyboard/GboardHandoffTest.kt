package com.humming.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GboardHandoffTest {
    private val humming = GboardHandoff.Ime("com.humming.keyboard/.Ime", "com.humming.keyboard", listOf("keyboard"))
    private val gboardId = "com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME"

    @Test fun noGboardNoOption() = assertNull(GboardHandoff.pick(listOf(humming)))

    @Test
    fun gboardVoiceSubtypeWins() {
        val g = GboardHandoff.Ime(gboardId, GboardHandoff.GBOARD, listOf("keyboard", "keyboard", "voice"))
        assertEquals(GboardHandoff.Target(gboardId, 2), GboardHandoff.pick(listOf(humming, g)))
    }

    @Test
    fun gboardWithoutVoiceSubtypeStillOffered() {
        val g = GboardHandoff.Ime(gboardId, GboardHandoff.GBOARD, listOf("keyboard"))
        assertEquals(GboardHandoff.Target(gboardId, null), GboardHandoff.pick(listOf(g, humming)))
    }

    @Test
    fun otherVoiceImesAreNotGboard() {
        val other = GboardHandoff.Ime("x/.Voice", "com.example.voice", listOf("voice"))
        assertNull(GboardHandoff.pick(listOf(humming, other)))
    }
}
