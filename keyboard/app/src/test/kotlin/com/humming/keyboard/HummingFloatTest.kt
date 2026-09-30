package com.humming.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class HummingFloatTest {
    @Test fun clampsIntoTheWindow() {
        assertEquals(0, HummingFloat.clampX(-40f))
        assertEquals(HummingFloat.MAX_X, HummingFloat.clampX(999f))
        assertEquals(57, HummingFloat.clampX(57.9f))
        assertEquals(0, HummingFloat.clampY(-1))
        assertEquals(HummingFloat.MAX_Y, HummingFloat.clampY(10_000f))
        assertEquals(120, HummingFloat.clampY(120))
    }
}
