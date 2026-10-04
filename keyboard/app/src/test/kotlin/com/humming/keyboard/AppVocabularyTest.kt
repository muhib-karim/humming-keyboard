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
}
