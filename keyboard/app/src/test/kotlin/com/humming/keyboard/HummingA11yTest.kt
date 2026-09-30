package com.humming.keyboard

import dev.patrickgold.florisboard.ime.text.key.KeyCode
import org.junit.Assert.assertEquals
import org.junit.Test

class HummingA11yTest {
    @Test fun functionKeysHaveSpokenNames() {
        assertEquals("Space", HummingA11y.describe(KeyCode.SPACE, "English"))
        assertEquals("Delete", HummingA11y.describe(KeyCode.DELETE, null))
        assertEquals("Shift", HummingA11y.describe(KeyCode.SHIFT, null))
        assertEquals("Emoji", HummingA11y.describe(KeyCode.IME_UI_MODE_MEDIA, null))
    }

    @Test fun lettersReadAsThemselves() {
        assertEquals("q", HummingA11y.describe('q'.code, "q"))
        assertEquals("Comma", HummingA11y.describe(','.code, ","))
        assertEquals("Key", HummingA11y.describe(0, null))
    }
}
