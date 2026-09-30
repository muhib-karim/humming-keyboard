package com.humming.keyboard

import android.content.Context

/** One place that turns audio into the text to insert, for the keyboard and for History → Retry. */
object DictationPipeline {
    /** Below this much audio, or this quiet, nothing is inserted (P0: silence or a mis-tap inserts nothing). */
    const val MIN_SECONDS = 0.3
    const val SILENCE_DBFS = -50.0

    data class Result(val text: String, val engine: String, val ms: Long, val skippedReason: String? = null)

    fun run(ctx: Context, clip: AudioClip): Result {
        val transcriber = Transcribers.create(ctx)
        if (clip.durationSeconds < MIN_SECONDS) return Result("", transcriber.label, 0, "too short")
        if (clip.peakDbfs() < SILENCE_DBFS && transcriber !is MockTranscriber) {
            return Result("", transcriber.label, 0, "no speech heard")
        }
        val started = System.nanoTime()
        val raw = transcriber.transcribe(clip)
        val text = if (transcriber is MockTranscriber) raw else TextCleanup.apply(raw, Prefs.dictionary(ctx))
        val ms = (System.nanoTime() - started) / 1_000_000
        return Result(text, transcriber.label, ms, if (text.isBlank()) "no speech heard" else null)
    }
}
