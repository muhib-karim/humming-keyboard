package com.humming.keyboard

/**
 * Humming 1.2: key timings that used to be fixed system constants are settings (Settings > Keyboard):
 * the repeat speed while Delete is held, and the double-tap window used by double-space period and by
 * double-tap Shift for caps lock. The long-press delay was already a setting.
 */
object KeyTiming {
    /** Android's own key repeat interval (ViewConfiguration.getKeyRepeatDelay), the default. */
    const val DEFAULT_REPEAT_MS = 50
    const val MIN_REPEAT_MS = 20
    const val MAX_REPEAT_MS = 200

    /** Android's own double-tap timeout (ViewConfiguration.getDoubleTapTimeout), the default. */
    const val DEFAULT_DOUBLE_TAP_MS = 300
    const val MIN_DOUBLE_TAP_MS = 150
    const val MAX_DOUBLE_TAP_MS = 800

    /** Milliseconds between repeats for a key whose repeat is [factor] times slower (word delete, undo). */
    fun repeatDelay(settingMs: Int, factor: Float = 1f): Long =
        (settingMs.coerceIn(MIN_REPEAT_MS, MAX_REPEAT_MS) * factor).toLong()

    /** Whether a second tap [elapsedMs] after the first counts as a double tap. */
    fun isDoubleTap(elapsedMs: Long, settingMs: Int): Boolean =
        elapsedMs >= 0 && elapsedMs < settingMs.coerceIn(MIN_DOUBLE_TAP_MS, MAX_DOUBLE_TAP_MS)
}
