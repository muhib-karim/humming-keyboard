/*
 * Humming (added file). Forwards the build flags the Humming voice code reads to the app's BuildConfig.
 */
package com.humming.keyboard

object BuildConfig {
    @JvmField val DEBUG: Boolean = dev.patrickgold.florisboard.BuildConfig.DEBUG
    /** Seconds of a dictation's audio kept for History's retry copy. Dictation itself has no length limit. */
    const val MAX_RECORD_SECONDS: Int = 300
}
