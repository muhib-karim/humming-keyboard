package com.humming.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

class BoundedReadTest {
    @Test
    fun stopsOneBytePastTheLimit() {
        assertEquals(65, BoundedRead.read(ByteArrayInputStream(ByteArray(100)), 64).size)
        assertEquals(64, BoundedRead.read(ByteArrayInputStream(ByteArray(64)), 64).size)
        assertEquals(0, BoundedRead.read(ByteArrayInputStream(ByteArray(0)), 64).size)
    }

    @Test
    fun anEndlessStreamIsCut() {
        val endless = object : InputStream() { override fun read(): Int = 0 }
        assertEquals(65, BoundedRead.read(endless, 64).size)
    }

    @Test
    fun theUpdaterUsesIt() {
        val t = File("src/main/kotlin/com/humming/keyboard/AppUpdate.kt").readText()
        assertTrue(t.contains("BoundedRead.read(it, maxBytes)"))
    }
}
