package com.humming.keyboard

import android.content.Context
import java.io.IOException
import java.util.Locale

/**
 * Turns one dictation into text to insert. Blocking: call off the main thread.
 * Engines: [OnDeviceTranscriber] (Parakeet on the phone, the default) and [MockTranscriber] (test mode).
 * There is no cloud engine: nothing you say leaves the phone.
 */
interface Transcriber {
    val label: String
    fun transcribe(clip: AudioClip): String
}

class TranscriberUnavailable(message: String) : IOException(message)

/** On-device dictation (Parakeet). The model is fetched once by the keyboard itself. */
class OnDeviceTranscriber(private val ctx: Context) : Transcriber {
    override val label = "on-device"

    override fun transcribe(clip: AudioClip): String {
        CoreLoader.whyUnavailable(ctx)?.let { throw TranscriberUnavailable(it) }
        if (!ModelStore.ready(ctx)) throw TranscriberUnavailable("the speech model is still downloading")
        val engine = CoreLoader.acquire(ctx)
        try {
            return synchronized(CoreLoader.lock) { engine.dictate(clip.toFloat32(), clip.sampleRate) }.trim()
        } finally {
            CoreLoader.done()
        }
    }
}

/** Offline test mode: proves mic → insert without any engine. Output is clearly marked. */
class MockTranscriber : Transcriber {
    override val label = "test mode"

    override fun transcribe(clip: AudioClip): String {
        val peak = clip.peakDbfs()
        val heard = if (peak < -60.0) "silence (mic may be blocked)" else String.format(Locale.US, "peak %.0f dBFS", peak)
        return String.format(Locale.US, "[mock] Humming heard %.1f s of audio, %s.", clip.durationSeconds, heard)
    }
}

object Transcribers {
    fun create(ctx: Context): Transcriber = when (Prefs.mode(ctx)) {
        Prefs.MODE_DEVICE -> OnDeviceTranscriber(ctx)
        else -> MockTranscriber()
    }
}
