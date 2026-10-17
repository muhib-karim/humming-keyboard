package com.humming.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class AppVocabularyTest {
    private val lines = listOf("GitHub", "Kubernetes @slack, termux", "kubectl @termux", "me@example.com", "", "GitHub")

    @Test fun globalWordsApplyEverywhere() {
        assertEquals(listOf("GitHub", "me@example.com"), AppVocabulary.select(lines, "com.android.chrome"))
        assertEquals(listOf("GitHub", "me@example.com"), AppVocabulary.select(lines, null))
    }

    @Test fun perAppWordsApplyOnlyInTheirApps() {
        assertEquals(listOf("GitHub", "Kubernetes", "me@example.com"), AppVocabulary.select(lines, "com.Slack"))
        assertEquals(listOf("GitHub", "Kubernetes", "kubectl", "me@example.com"), AppVocabulary.select(lines, "com.termux"))
    }

    @Test fun parsesTags() {
        assertEquals(AppVocabulary.Line("Kubernetes", listOf("slack", "termux")), AppVocabulary.parse(" Kubernetes @Slack, termux "))
        assertEquals(AppVocabulary.Line("me@example.com", emptyList()), AppVocabulary.parse("me@example.com"))
    }

    // 1.7: "spoken -> written" replacements (also "→"), with the same " @app" tags.
    @Test fun parsesReplacements() {
        assertEquals(AppVocabulary.Line("kubectl", listOf("termux"), "cube cuddle"), AppVocabulary.parse("cube cuddle -> kubectl @termux"))
        assertEquals(AppVocabulary.Line("Humming", emptyList(), "hum ing"), AppVocabulary.parse("hum ing → Humming"))
        assertEquals(null, AppVocabulary.parse("-> kubectl"))
        assertEquals(null, AppVocabulary.parse("cube cuddle ->"))
    }

    @Test fun replacementsApplyOnlyInTheirApps() {
        val l = listOf("GitHub", "cube cuddle -> kubectl @termux", "my sig -> Best, Sam")
        assertEquals(listOf("my sig" to "Best, Sam"), AppVocabulary.replacements(l, "com.android.chrome"))
        assertEquals(listOf("cube cuddle" to "kubectl", "my sig" to "Best, Sam"), AppVocabulary.replacements(l, "com.termux"))
        // The written side is also a casing term.
        assertEquals(listOf("GitHub", "kubectl", "Best, Sam"), AppVocabulary.select(l, "com.termux"))
    }
}
