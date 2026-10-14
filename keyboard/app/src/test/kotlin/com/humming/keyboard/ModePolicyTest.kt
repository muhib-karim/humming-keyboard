package com.humming.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

/** The mic never silently types placeholder text: test mode only on purpose, and only in a debuggable build. */
class ModePolicyTest {
    @Test
    fun userBuildsAlwaysUseTheOnDeviceEngine() {
        assertEquals(Prefs.MODE_DEVICE, Prefs.resolveMode(null, debuggable = false))
        assertEquals(Prefs.MODE_DEVICE, Prefs.resolveMode(Prefs.MODE_DEVICE, debuggable = false))
        assertEquals(Prefs.MODE_DEVICE, Prefs.resolveMode(Prefs.MODE_MOCK, debuggable = false))
        assertEquals(Prefs.MODE_DEVICE, Prefs.resolveMode("anything else", debuggable = false))
    }

    @Test
    fun nothingSavedMeansOnDeviceEvenInDebug() {
        // Before this rule an unsupported CPU fell back to test mode automatically and typed "[mock] ..." text.
        assertEquals(Prefs.MODE_DEVICE, Prefs.resolveMode(null, debuggable = true))
    }

    @Test
    fun testModeOnlyWhenSetInADebuggableBuild() {
        assertEquals(Prefs.MODE_MOCK, Prefs.resolveMode(Prefs.MODE_MOCK, debuggable = true))
    }
}
