package com.humming.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import kotlin.math.pow

/** Accessibility: every text/background pair in the Humming themes meets WCAG AA (4.5:1). */
class ThemeContrastTest {
    private fun lum(hex: String): Double {
        val c = hex.removePrefix("#")
        fun ch(i: Int): Double {
            val v = c.substring(i, i + 2).toInt(16) / 255.0
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * ch(0) + 0.7152 * ch(2) + 0.0722 * ch(4)
    }

    private fun ratio(a: String, b: String): Double {
        val (hi, lo) = listOf(lum(a), lum(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test fun hummingThemesMeetAA() {
        val varRe = Regex("\"(--[\\w-]+)\"\\s*:\\s*\"(#[0-9A-Fa-f]{6})\"")
        val ruleRe = Regex("\"([^\"]+)\"\\s*:\\s*\\{([^{}]*)\\}")
        val failures = mutableListOf<String>()
        for (name in listOf("humming_light", "humming_dark", "humming_amoled")) {
            val text = File("src/main/assets/ime/theme/org.florisboard.themes/stylesheets/$name.json").readText()
            val vars = varRe.findAll(text).associate { it.groupValues[1] to it.groupValues[2] }
            for (m in ruleRe.findAll(text)) {
                val body = m.groupValues[2]
                val fg = Regex("\"foreground\"\\s*:\\s*\"var\\((--[\\w-]+)\\)\"").find(body)?.groupValues?.get(1)?.let { vars[it] } ?: continue
                val bg = Regex("\"background\"\\s*:\\s*\"var\\((--[\\w-]+)\\)\"").find(body)?.groupValues?.get(1)?.let { vars[it] } ?: continue
                val r = ratio(fg, bg)
                if (r < 4.5) failures.add("$name ${m.groupValues[1]} %.2f".format(r))
            }
        }
        assertEquals(emptyList<String>(), failures)
    }
}
