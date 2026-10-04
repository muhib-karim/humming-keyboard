package com.humming.keyboard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Online mode (opt-in, off by default): Android's own speech recognizer service (on most phones Google's), with no
 * account and no key. Audio is handled by that service, not by Humming. A recognizer session ends after a pause, so
 * this restarts it until the user taps stop: there is no length limit here either. All calls on the main thread
 * except [finish], which blocks a background thread until the last words arrive.
 */
class OnlineSession(
    ctx: Context,
    private val language: String? = null,
    private val onPartial: (String) -> Unit,
    private val onLevel: (Float) -> Unit = {},
) {
    private val app = ctx.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private val finals = ArrayList<String>()
    private var current = ""
    @Volatile private var stopping = false
    @Volatile var error: String? = null
        private set
    private val done = CountDownLatch(1)

    companion object {
        fun available(ctx: Context): Boolean = SpeechRecognizer.isRecognitionAvailable(ctx)

        /** Errors after which listening simply starts again (a pause, nothing heard, a busy service). */
        private val RESTART = setOf(
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_CLIENT,
        )

        fun describe(code: Int): String = when (code) {
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "no internet connection"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "microphone permission missing"
            SpeechRecognizer.ERROR_AUDIO -> "the microphone is busy"
            SpeechRecognizer.ERROR_SERVER -> "the speech service had a problem"
            else -> "speech service error $code"
        }
    }

    private fun text(): String = (finals + current).filter { it.isNotBlank() }.joinToString(" ").trim()

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) { onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f)) }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            val p = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: return
            current = p
            onPartial(text())
        }

        override fun onResults(results: Bundle?) {
            val r = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (r.isNotBlank()) finals.add(r)
            current = ""
            onPartial(text())
            if (stopping) finishNow() else listen()
        }

        override fun onError(code: Int) {
            if (current.isNotBlank()) { finals.add(current); current = "" }
            if (stopping) { finishNow(); return }
            if (code in RESTART) { main.postDelayed({ if (!stopping) listen() }, 150); return }
            error = describe(code)
            finishNow()
        }
    }

    private fun intent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, app.packageName)
        // Long pauses are fine: keep listening for a while before the service ends a session.
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
        language?.let { putExtra(RecognizerIntent.EXTRA_LANGUAGE, it) }
    }

    private fun listen() {
        val r = recognizer ?: return
        runCatching { r.startListening(intent()) }.onFailure { error = it.message; finishNow() }
    }

    /** Main thread. False when this phone has no speech recognition service. */
    fun start(): Boolean {
        if (!available(app)) { error = "this phone has no online speech service"; return false }
        recognizer = SpeechRecognizer.createSpeechRecognizer(app).apply { setRecognitionListener(listener) }
        listen()
        return true
    }

    private fun finishNow() {
        main.post { runCatching { recognizer?.destroy() }; recognizer = null }
        done.countDown()
    }

    /** Background thread: asks for the last words and waits for them (at most 4 s). */
    fun finish(): String {
        stopping = true
        main.post { val r = recognizer; if (r == null) done.countDown() else runCatching { r.stopListening() } }
        if (!done.await(4, TimeUnit.SECONDS)) finishNow()
        return text()
    }

    /** Main thread: drops everything. */
    fun cancel() {
        stopping = true
        finishNow()
    }
}
