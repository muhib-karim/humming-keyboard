package com.humming.keyboard

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Retention rule for local history, kept pure so it is unit-tested (HistoryRetentionTest). */
object HistoryRetention {
    const val FOREVER = "forever"
    const val MONTH = "month"
    const val WEEK = "week"
    const val DAY = "day"
    const val NEVER = "never"

    val ALL = listOf(FOREVER to "Forever", MONTH to "1 month", WEEK to "1 week", DAY to "24 hours", NEVER to "Never save")

    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** Entries created before the returned time are deleted; null keeps everything. */
    fun cutoff(nowMs: Long, setting: String): Long? = when (setting) {
        MONTH -> nowMs - 30 * DAY_MS
        WEEK -> nowMs - 7 * DAY_MS
        DAY -> nowMs - DAY_MS
        NEVER -> Long.MAX_VALUE
        else -> null
    }

    fun keep(createdMs: Long, nowMs: Long, setting: String): Boolean {
        val c = cutoff(nowMs, setting) ?: return true
        return createdMs >= c
    }
}

data class HistoryEntry(
    val id: Long,
    val timeMs: Long,
    val text: String,
    val engine: String,
    val ms: Long,
    val audioSeconds: Double,
)

/**
 * Local-only dictation history: a small JSON file of the last 500 dictations' text in app-private storage. No audio
 * is kept (1.7.1). Nothing here leaves the phone.
 */
class HistoryStore(private val ctx: Context) {
    private val file = File(ctx.filesDir, "history.json")

    @Synchronized
    fun all(): List<HistoryEntry> {
        if (!file.isFile) return emptyList()
        return try {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                HistoryEntry(
                    o.getLong("id"), o.getLong("time"), o.getString("text"), o.optString("engine"),
                    o.optLong("ms"), o.optDouble("audio_s", 0.0),
                )
            }.sortedByDescending { it.timeMs }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @Synchronized
    fun add(text: String, engine: String, ms: Long, audioSeconds: Double): HistoryEntry? {
        if (Prefs.retention(ctx) == HistoryRetention.NEVER) return null
        val now = System.currentTimeMillis()
        val entry = HistoryEntry(now, now, text, engine, ms, audioSeconds)
        save(all() + entry)
        return entry
    }

    private fun save(entries: List<HistoryEntry>) {
        val arr = JSONArray()
        entries.sortedByDescending { it.timeMs }.take(500).forEach { e ->
            arr.put(
                JSONObject().put("id", e.id).put("time", e.timeMs).put("text", e.text).put("engine", e.engine)
                    .put("ms", e.ms).put("audio_s", e.audioSeconds),
            )
        }
        val tmp = File(file.path + ".tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file)
    }

    companion object {
        /**
         * Builds before 1.7.1 saved each dictation's audio in history_audio/, which nothing could play or delete and
         * the 500-entry cap orphaned. Removed at app start. True when the folder is gone.
         */
        fun dropLegacyAudio(filesDir: File): Boolean = File(filesDir, "history_audio").let { !it.exists() || it.deleteRecursively() }
    }
}
