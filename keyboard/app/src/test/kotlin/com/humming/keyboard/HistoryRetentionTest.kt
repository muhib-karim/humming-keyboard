package com.humming.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** P0-16: retention deletes rows older than the chosen window on the next launch. */
class HistoryRetentionTest {
    private val now = 1_800_000_000_000L
    private val hour = 60L * 60 * 1000

    @Test fun foreverKeepsEverything() {
        assertNull(HistoryRetention.cutoff(now, HistoryRetention.FOREVER))
        assertTrue(HistoryRetention.keep(now - 400 * 24 * hour, now, HistoryRetention.FOREVER))
    }

    @Test fun windows() {
        assertTrue(HistoryRetention.keep(now - 23 * hour, now, HistoryRetention.DAY))
        assertFalse(HistoryRetention.keep(now - 25 * hour, now, HistoryRetention.DAY))
        assertTrue(HistoryRetention.keep(now - 6 * 24 * hour, now, HistoryRetention.WEEK))
        assertFalse(HistoryRetention.keep(now - 8 * 24 * hour, now, HistoryRetention.WEEK))
        assertTrue(HistoryRetention.keep(now - 29 * 24 * hour, now, HistoryRetention.MONTH))
        assertFalse(HistoryRetention.keep(now - 31 * 24 * hour, now, HistoryRetention.MONTH))
    }

    @Test fun neverKeepsNothing() = assertFalse(HistoryRetention.keep(now, now, HistoryRetention.NEVER))

    @Test
    fun defaultIsThirtyDays() {
        assertEquals(HistoryRetention.MONTH, HistoryRetention.DEFAULT)
        val t = now
        assertFalse(HistoryRetention.keep(t - 31 * 24 * hour, t, HistoryRetention.DEFAULT))
        assertTrue(HistoryRetention.keep(t - 29 * 24 * hour, t, HistoryRetention.DEFAULT))
    }
}
