package com.humming.keyboard

/**
 * Humming (M5): split keyboard for landscape phones and tablets. Keys left of the middle move left, keys right of
 * it move right, leaving a gap for the thumbs; a key that spans the middle (the space bar) spans the gap.
 * Pure: SplitLayoutTest.
 */
object SplitLayout {
    /** Split only where there is room: at least this wide (dp), i.e. landscape phones and tablets. */
    const val MIN_WIDTH_DP = 600f
    const val GAP_FRACTION = 0.2f

    fun gapPx(enabled: Boolean, widthPx: Float, density: Float): Float =
        if (enabled && density > 0f && widthPx / density >= MIN_WIDTH_DP) widthPx * GAP_FRACTION else 0f

    /** Maps a key's [left, right] in a keyboard of [width] to the split layout with [gap]. */
    fun map(left: Float, right: Float, width: Float, gap: Float): Pair<Float, Float> {
        if (gap <= 0f || width <= 0f) return left to right
        val s = (width - gap) / width
        val mid = width / 2f
        val center = (left + right) / 2f
        return when {
            right <= mid + 0.5f -> left * s to right * s
            left >= mid - 0.5f -> left * s + gap to right * s + gap
            center < mid && right - mid < (right - left) * 0.25f -> left * s to right * s
            center > mid && mid - left < (right - left) * 0.25f -> left * s + gap to right * s + gap
            else -> left * s to right * s + gap // spans the middle (space bar)
        }
    }
}
