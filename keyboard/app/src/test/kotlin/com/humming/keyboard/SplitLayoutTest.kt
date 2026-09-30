package com.humming.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class SplitLayoutTest {
    private val w = 2000f
    private val gap = 400f

    @Test fun noSplitOnPortraitPhones() = assertEquals(0f, SplitLayout.gapPx(true, 1080f, 2.75f), 0f) // 393 dp
    @Test fun splitsInLandscape() = assertEquals(2400f * 0.2f, SplitLayout.gapPx(true, 2400f, 2.75f), 0.01f) // 873 dp
    @Test fun offMeansOff() = assertEquals(0f, SplitLayout.gapPx(false, 2400f, 2.75f), 0f)

    @Test
    fun leftHalfShrinksInPlace() {
        val (l, r) = SplitLayout.map(0f, 200f, w, gap)
        assertEquals(0f, l, 0.01f); assertEquals(160f, r, 0.01f)
    }

    @Test
    fun rightHalfMovesPastTheGap() {
        val (l, r) = SplitLayout.map(1800f, 2000f, w, gap)
        assertEquals(1840f, l, 0.01f); assertEquals(2000f, r, 0.01f)
    }

    @Test
    fun spaceBarSpansTheGap() {
        val (l, r) = SplitLayout.map(600f, 1400f, w, gap)
        assertEquals(480f, l, 0.01f); assertEquals(1520f, r, 0.01f)
    }

    @Test
    fun nothingChangesWithoutAGap() = assertEquals(10f to 20f, SplitLayout.map(10f, 20f, w, 0f))
}
