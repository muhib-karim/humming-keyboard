package com.humming.keyboard

import android.content.Context
import android.content.SharedPreferences

/** Humming voice settings (Settings > Voice), read by the keyboard in the same process. */
object Prefs {
    const val KEY_MODE = "mode"
    const val KEY_DICTIONARY = "dictionary"
    const val KEY_RETENTION = "history_retention"
    const val KEY_CLEAN = "clean_mode"
    const val KEY_WIFI_ONLY = "model_wifi_only"
    const val KEY_VOICE_MODEL = "voice_model"
    const val KEY_BT_MIC = "bluetooth_mic"
    const val KEY_INCOGNITO = "voice_incognito"

    /** On-device (the only real engine) or the offline test mode used by unit tests and screenshots. */
    const val MODE_DEVICE = "device"
    const val MODE_MOCK = "mock"

    fun get(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("humming", Context.MODE_PRIVATE)

    /** The engine the mic uses: on-device when this phone's CPU can run it, else the offline test mode. */
    fun mode(ctx: Context): String {
        val saved = get(ctx).getString(KEY_MODE, null)
        if (saved == MODE_DEVICE || saved == MODE_MOCK) return saved
        return if (CoreLoader.whyUnavailable(ctx) == null) MODE_DEVICE else MODE_MOCK
    }

    fun setMode(ctx: Context, mode: String) = get(ctx).edit().putString(KEY_MODE, mode).apply()

    /** Personal dictionary: one term per line, applied with its exact casing. */
    fun dictionary(ctx: Context): List<String> =
        (get(ctx).getString(KEY_DICTIONARY, "") ?: "").lines().map { it.trim() }.filter { it.isNotEmpty() }.distinct()

    /**
     * Clean mode (default on): fillers (um, uh), stutters and self-corrections ("3, no wait, 4pm" -> "4pm")
     * removed, spoken punctuation applied. Off = verbatim.
     */
    fun cleanMode(ctx: Context): Boolean = get(ctx).getBoolean(KEY_CLEAN, true)

    fun retention(ctx: Context): String = get(ctx).getString(KEY_RETENTION, HistoryRetention.FOREVER) ?: HistoryRetention.FOREVER

    /** Download voice models on Wi-Fi only (default on). */
    fun wifiOnly(ctx: Context): Boolean = get(ctx).getBoolean(KEY_WIFI_ONLY, true)

    /** "fast" (default) or "best"; see [VoiceModels]. */
    fun voiceModel(ctx: Context): String = get(ctx).getString(KEY_VOICE_MODEL, VoiceModels.FAST.id) ?: VoiceModels.FAST.id

    /** Use a connected Bluetooth headset's microphone for dictation (default on). */
    fun bluetoothMic(ctx: Context): Boolean = get(ctx).getBoolean(KEY_BT_MIC, true)

    /** Voice incognito: nothing dictated is kept in History (the keyboard's incognito mode also turns this on). */
    fun voiceIncognito(ctx: Context): Boolean = get(ctx).getBoolean(KEY_INCOGNITO, false)

    fun set(ctx: Context, key: String, value: Boolean) = get(ctx).edit().putBoolean(key, value).apply()
    fun set(ctx: Context, key: String, value: String) = get(ctx).edit().putString(key, value).apply()
}
