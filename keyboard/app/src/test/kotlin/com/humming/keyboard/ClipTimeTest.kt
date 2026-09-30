package com.humming.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class ClipTimeTest {
    private val now = 1_790_000_000_000L
    private fun ago(sec: Long) = ClipTime.ago(now - sec * 1000, now, Locale.US)

    @Test
    fun relative() {
        assertEquals("Just now", ago(5))
        assertEquals("2 min ago", ago(125))
        assertEquals("3 h ago", ago(3 * 3600 + 60))
        assertEquals("Yesterday", ago(30 * 3600))
        assertEquals("4 days ago", ago(4 * 86_400))
        assertEquals("Just now", ClipTime.ago(now + 5000, now)) // clock skew
    }

    @Test
    fun durations() {
        assertEquals("30 min", ClipTime.duration(30))
        assertEquals("2 h", ClipTime.duration(120))
        assertEquals("1 h 30 min", ClipTime.duration(90))
        assertEquals("1 day", ClipTime.duration(1440))
        assertEquals("3 days", ClipTime.duration(3 * 1440))
        assertEquals("1 week", ClipTime.duration(10_080))
    }
}
