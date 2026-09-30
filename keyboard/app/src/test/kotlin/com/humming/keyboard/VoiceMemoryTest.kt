package com.humming.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceMemoryTest {
    @Test fun useCountNeverGoesNegative() {
        val u = UseCount()
        assertTrue(u.idle)
        u.acquire(); u.acquire()
        assertFalse(u.idle)
        u.release()
        assertEquals(1, u.count)
        u.release(); u.release()
        assertEquals(0, u.count)
        assertTrue(u.idle)
    }

    @Test fun unloadsOnlyUnderMemoryPressure() {
        assertFalse(CoreLoader.shouldUnloadOnTrim(5)) // RUNNING_MODERATE
        assertTrue(CoreLoader.shouldUnloadOnTrim(10)) // RUNNING_LOW
        assertTrue(CoreLoader.shouldUnloadOnTrim(15)) // RUNNING_CRITICAL
        assertTrue(CoreLoader.shouldUnloadOnTrim(40)) // BACKGROUND
    }

    @Test fun nothingToUnloadWhenNoModelIsLoaded() {
        assertFalse(CoreLoader.releaseIfIdle())
    }
}
