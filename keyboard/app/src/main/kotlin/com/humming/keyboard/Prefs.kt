package com.humming.keyboard

import android.content.Context
import android.content.SharedPreferences

/** Humming voice settings (Settings > Voice), read by the keyboard in the same process. */
object Prefs {
    const val KEY_MODE = "mode"
    const val KEY_DICTIONARY = "dictionary"
    const val KEY_RETENTION = "history_retention"
    const val KEY_CLEAN = "clean_mode"
    const val KEY_ACCURACY = "accuracy_mode"
    const val KEY_FILLERS = "remove_fillers"
    const val KEY_WIFI_ONLY = "model_wifi_only"
    const val KEY_VOICE_MODEL = "voice_model"
    const val KEY_BT_MIC = "bluetooth_mic"
    const val KEY_INCOGNITO = "voice_incognito"
    const val KEY_LIVE_TYPING = "live_typing"
    const val KEY_ENGINE = "speech_engine"
    const val KEY_LLM_PROVIDER = "llm_provider"
    const val KEY_LLM_MODEL = "llm_model"

    /** Speech engine: on this phone, no network (default), or the phone's online speech service (opt-in). */
    const val ENGINE_OFFLINE = "offline"
    const val ENGINE_ONLINE = "online"

    /** On-device (the only real engine) or the offline test mode used by unit tests and screenshots. */
    const val MODE_DEVICE = "device"
    const val MODE_MOCK = "mock"

    fun get(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences("humming", Context.MODE_PRIVATE)

    /**
     * The engine the mic uses. Always the on-device engine, except in a debuggable build where test mode was set on
     * purpose (unit tests, screenshots). A phone whose CPU the engine is not built for gets a clear "voice isn't
     * available on this device" error from the on-device path, never placeholder "[mock] ..." text typed into the
     * field (before this, such a phone silently fell back to test mode).
     */
    fun mode(ctx: Context): String {
        val debuggable = (ctx.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        return resolveMode(get(ctx).getString(KEY_MODE, null), debuggable)
    }

    /** Pure rule behind [mode]: test mode only when saved explicitly and only in a debuggable build. */
    fun resolveMode(saved: String?, debuggable: Boolean): String =
        if (saved == MODE_MOCK && debuggable) MODE_MOCK else MODE_DEVICE

    fun setMode(ctx: Context, mode: String) = get(ctx).edit().putString(KEY_MODE, mode).apply()

    /** Package of the app the keyboard is typing into (set by the IME on every onStartInput; per-app words, 1.5). */
    @Volatile var currentPackage: String? = null

    /**
     * Personal dictionary: one term per line, applied with its exact casing. A line ending in " @app1, app2" is
     * per-app (1.5): it applies only while the keyboard types into an app whose package name contains one of those
     * words ("Kubernetes @slack" -> com.Slack only).
     */
    fun dictionary(ctx: Context): List<String> =
        AppVocabulary.select((get(ctx).getString(KEY_DICTIONARY, "") ?: "").lines(), currentPackage)

    /** Personal dictionary lines written "spoken -> written" (1.7): replacements applied before the clean-up. */
    fun replacements(ctx: Context): List<Pair<String, String>> =
        AppVocabulary.replacements((get(ctx).getString(KEY_DICTIONARY, "") ?: "").lines(), currentPackage)

    /** Remove filler words (default on, 1.7): um, uh, er, hmm and ", you know,". Part of Clean mode. */
    fun removeFillers(ctx: Context): Boolean = get(ctx).getBoolean(KEY_FILLERS, true)

    /**
     * Clean mode (default on): fillers (um, uh), stutters and self-corrections ("3, no wait, 4pm" -> "4pm")
     * removed, spoken punctuation applied. Off = verbatim.
     */
    fun cleanMode(ctx: Context): Boolean = get(ctx).getBoolean(KEY_CLEAN, true)

    /**
     * Accuracy mode (default off, 1.4): the strict clean-up rules on top of Clean mode. Hedge fillers ("like",
     * "basically", "you know") set off by commas, extra hesitation sounds and a spoken "period" at the end are removed.
     */
    fun accuracyMode(ctx: Context): Boolean = get(ctx).getBoolean(KEY_ACCURACY, false)

    fun retention(ctx: Context): String = get(ctx).getString(KEY_RETENTION, HistoryRetention.FOREVER) ?: HistoryRetention.FOREVER

    /** Download voice models on Wi-Fi only (default on). */
    fun wifiOnly(ctx: Context): Boolean = get(ctx).getBoolean(KEY_WIFI_ONLY, true)

    /** "fast" (default) or "best"; see [VoiceModels]. */
    fun voiceModel(ctx: Context): String = get(ctx).getString(KEY_VOICE_MODEL, VoiceModels.FAST.id) ?: VoiceModels.FAST.id

    /** Use a connected Bluetooth headset's microphone for dictation (default on). */
    fun bluetoothMic(ctx: Context): Boolean = get(ctx).getBoolean(KEY_BT_MIC, true)

    /** Voice incognito: nothing dictated is kept in History (the keyboard's incognito mode also turns this on). */
    fun voiceIncognito(ctx: Context): Boolean = get(ctx).getBoolean(KEY_INCOGNITO, false)

    /** Offline (default) unless the user switched to Online in Settings › Voice. */
    fun engine(ctx: Context): String =
        if (get(ctx).getString(KEY_ENGINE, ENGINE_OFFLINE) == ENGINE_ONLINE) ENGINE_ONLINE else ENGINE_OFFLINE

    /** Personal dictionary as one text block (Settings › Voice › Custom words). */
    fun dictionaryText(ctx: Context): String = get(ctx).getString(KEY_DICTIONARY, "") ?: ""

    /** Type the words into the field while talking (default on). Off = everything is typed once, on stop. */
    fun liveTyping(ctx: Context): Boolean = get(ctx).getBoolean(KEY_LIVE_TYPING, true)

    fun set(ctx: Context, key: String, value: Boolean) = get(ctx).edit().putBoolean(key, value).apply()
    fun set(ctx: Context, key: String, value: String) = get(ctx).edit().putString(key, value).apply()
}
