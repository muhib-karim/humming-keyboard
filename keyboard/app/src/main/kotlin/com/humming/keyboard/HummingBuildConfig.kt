/*
 * Humming (added file). Forwards the build flags the Humming voice code reads to the app's BuildConfig.
 */
package com.humming.keyboard

object BuildConfig {
    @JvmField val DEBUG: Boolean = dev.patrickgold.florisboard.BuildConfig.DEBUG
    const val MAX_RECORD_SECONDS: Int = 900
}
