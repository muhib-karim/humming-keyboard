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

    /** Default for new installs and anyone who never picked a setting: 30 days. */
    const val DEFAULT = MONTH

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
 * Local-only dictation history: a small JSON file plus each dictation's audio (for Retry) in app-private storage.
 * Nothing here leaves the phone.
 */
class HistoryStore(private val ctx: Context) {
    companion object {
        /**
         * One lock for the whole process: callers each create their own HistoryStore, so an instance lock would let
         * the audio sweep in prune() delete a file add() has just written, or two saves drop each other's entries.
         */
        private val LOCK = Any()
    }

    private val file = File(ctx.filesDir, "history.json")
    private val audioDir = File(ctx.filesDir, "history_audio")

    fun all(): List<HistoryEntry> = synchronized(LOCK) {
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

    fun add(text: String, engine: String, ms: Long, clip: AudioClip): HistoryEntry? = synchronized(LOCK) {
            if (Prefs.retention(ctx) == HistoryRetention.NEVER) return null
            val now = System.currentTimeMillis()
            prune(now)
            val entry = HistoryEntry(now, now, text, engine, ms, clip.durationSeconds)
            audioDir.mkdirs()
            runCatching { audioFile(entry.id).writeBytes(clip.pcm16le) }
            save(all() + entry)
            return entry
    }

    fun update(id: Long, text: String, engine: String, ms: Long) {
        synchronized(LOCK) {
            save(all().map { if (it.id == id) it.copy(text = text, engine = engine, ms = ms) else it })
        }
    }

    fun delete(id: Long) {
        synchronized(LOCK) {
            audioFile(id).delete()
            save(all().filter { it.id != id })
        }
    }

    fun audio(id: Long): AudioClip? =
        audioFile(id).takeIf { it.isFile }?.let { AudioClip(it.readBytes(), WavRecorder.SAMPLE_RATE) }

    /** Applies the retention setting; called before every new entry and when the setting changes. */
    fun prune(nowMs: Long = System.currentTimeMillis()) {
        synchronized(LOCK) {
            val setting = Prefs.retention(ctx)
            val entries = all()
            val kept = entries.filter { HistoryRetention.keep(it.timeMs, nowMs, setting) }
            if (kept.size != entries.size) save(kept)
            // Audio of entries that are gone (expired, deleted, or past the 500-entry cap) is removed too.
            val keptIds = kept.map { "${it.id}.pcm" }.toSet()
            audioDir.listFiles()?.filter { it.name !in keptIds }?.forEach { it.delete() }
        }
    }

    /** Deletes every dictation (text and audio) kept on this phone. */
    fun deleteAll() {
        synchronized(LOCK) {
            file.delete()
            File(file.path + ".tmp").delete()
            audioDir.deleteRecursively()
        }
    }

    private fun audioFile(id: Long) = File(audioDir, "$id.pcm")

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
}
