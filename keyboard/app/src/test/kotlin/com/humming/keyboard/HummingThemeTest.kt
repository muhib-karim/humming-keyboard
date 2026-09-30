/*
 * Humming (added): the Humming light/dark stylesheets must parse exactly as the keyboard loads them
 * (ThemeManager uses SnyggStylesheet.fromJson with the default configuration); a parse failure silently falls back
 * to the built-in default look.
 */
package com.humming.keyboard

import org.florisboard.lib.snygg.SnyggStylesheet
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HummingThemeTest {
    private fun load(name: String): String {
        val candidates = listOf(
            File("src/main/assets/ime/theme/org.florisboard.themes/stylesheets/$name.json"),
            File("app/src/main/assets/ime/theme/org.florisboard.themes/stylesheets/$name.json"),
        )
        return candidates.first { it.isFile }.readText()
    }

    private fun check(name: String) {
        val r = SnyggStylesheet.fromJson(load(name))
        assertTrue("$name failed to parse: ${r.exceptionOrNull()}", r.isSuccess)
    }

    @Test fun lightParses() = check("humming_light")
    @Test fun darkParses() = check("humming_dark")
    @Test fun upstreamDayParses() = check("floris_day")
}
