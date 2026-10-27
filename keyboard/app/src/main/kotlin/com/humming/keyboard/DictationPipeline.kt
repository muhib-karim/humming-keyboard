package com.humming.keyboard

import android.content.Context

/** One place that turns audio into the text to insert, for the keyboard and for History → Retry. */
object DictationPipeline {
    /** Below this much audio, or this quiet, nothing is inserted (P0: silence or a mis-tap inserts nothing). */
    const val MIN_SECONDS = 0.3
    const val SILENCE_DBFS = -50.0

    /** [raw] is the transcript before clean-up (what edit by voice reads, 1.8). */
    data class Result(
        val text: String,
        val engine: String,
        val ms: Long,
        val skippedReason: String? = null,
        val raw: String = "",
    )

    fun run(ctx: Context, clip: AudioClip): Result {
        val transcriber = Transcribers.create(ctx)
        if (clip.durationSeconds < MIN_SECONDS) return Result("", transcriber.label, 0, "too short")
        if (clip.peakDbfs() < SILENCE_DBFS && transcriber !is MockTranscriber) {
            return Result("", transcriber.label, 0, "no speech heard")
        }
        val started = System.nanoTime()
        val raw = transcriber.transcribe(clip)
        val text = when {
            transcriber is MockTranscriber -> raw
            !VoiceModels.englishCleanup(ModelDownload.current(ctx).id) ->
                TextCleanup.neutral(raw, Prefs.dictionary(ctx), Prefs.replacements(ctx))
            else -> {
                val dictionary = Prefs.dictionary(ctx)
                val strict = Prefs.accuracyMode(ctx)
                TextCleanup.apply(raw, dictionary, strict, Prefs.removeFillers(ctx), Prefs.replacements(ctx))
            }
        }
        val ms = (System.nanoTime() - started) / 1_000_000
        return Result(text, transcriber.label, ms, if (text.isBlank()) "no speech heard" else null, raw)
    }
}
