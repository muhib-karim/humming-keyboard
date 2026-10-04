package com.humming.keyboard

import android.content.Context
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * One live dictation. Audio from the recorder thread goes through [Segmenter]; each finished segment is
 * transcribed on a background thread while the user keeps talking, and [onPartial] gets the cleaned text so far
 * (the keyboard shows it as composing text in the field). [finish] transcribes only the tail after the last pause,
 * so the text lands quickly after stop even for a 5-minute dictation.
 */
class StreamingSession(
    private val ctx: Context,
    private val onPartial: (String) -> Unit,
) {
    data class Result(
        val text: String,
        val raw: String,
        val segments: Int,
        val finishMs: Long,
        val error: String?,
        /** The last STT call (the tail transcribed after stop, when there was one). */
        val tailAudioMs: Long = 0,
        val tailSttMs: Long = 0,
        val tailTiming: String = "{}",
        /** A closing style command ("make it formal") for the optional AI clean-up, or null. */
        val instruction: String? = null,
    )

    private val app = ctx.applicationContext
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "humming-stt").apply { priority = Thread.NORM_PRIORITY + 1 } }
    private val raws = ArrayList<String>()
    private val dictionary = Prefs.dictionary(app)
    private val cleanMode = Prefs.cleanMode(app)
    @Volatile private var engine: LocalEngine? = null
    private val acquired = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var closed = false

    /** Hands the engine back once, so the model can be unloaded under memory pressure when no one dictates. */
    private fun releaseEngine() {
        if (acquired.compareAndSet(true, false)) CoreLoader.done()
    }
    @Volatile private var error: String? = null
    private val queued = ArrayDeque<FloatArray>()
    private val onSegment: (FloatArray) -> Unit = { seg ->
        synchronized(queued) { queued.addLast(seg) }
        worker.execute { transcribeQueued() }
    }
    /** Silero VAD (neural, robust to noise) when it loads; the energy segmenter otherwise. */
    private val segmenter: SpeechSegmenter = SileroSegmenter.create(app, onSegment)
        ?: EnergySegmenter(Segmenter(minSegSec = 1.0, maxSegSec = 12.0, onSegment = onSegment))

    init {
        // Load (or reuse) the model while the user starts talking.
        worker.execute {
            try {
                CoreLoader.whyUnavailable(app)?.let { throw TranscriberUnavailable(it) }
                if (!ModelStore.ready(app)) throw TranscriberUnavailable("the speech model is still downloading")
                engine = CoreLoader.acquire(app)
                acquired.set(true)
                if (closed) releaseEngine()
            } catch (e: Throwable) {
                error = e.message ?: e.javaClass.simpleName
            }
        }
    }

    /** Recorder thread: raw PCM16 as it arrives. */
    fun feed(samples: ShortArray, n: Int) = segmenter.feed(samples, n)

    /**
     * Takes every segment waiting in the queue (up to whisper's window) and transcribes them as one piece.
     * On a fast phone each pause is its own piece, so text appears phrase by phrase; on a slow device the
     * queue backs up and pieces merge, so the transcriber keeps up with the speaker instead of falling behind
     * (each call has a fixed setup cost).
     */
    private fun transcribeQueued() {
        val parts = ArrayList<FloatArray>()
        var total = 0
        synchronized(queued) {
            while (queued.isNotEmpty()) {
                val next = queued.first()
                val add = next.size + if (parts.isEmpty()) 0 else GAP
                if (parts.isNotEmpty() && total + add > MAX_PIECE) break
                parts.add(queued.removeFirst())
                total += add
            }
        }
        if (parts.isEmpty()) return
        if (parts.size == 1) return transcribe(parts[0])
        val merged = FloatArray(total)
        var at = 0
        for ((i, p) in parts.withIndex()) {
            if (i > 0) at += GAP
            System.arraycopy(p, 0, merged, at, p.size)
            at += p.size
        }
        if (BuildConfig.DEBUG) Log.i("HUMMING", "HUMMING_MERGE pieces=${parts.size} audio_ms=${total * 1000L / WavRecorder.SAMPLE_RATE}")
        transcribe(merged)
    }

    @Volatile private var lastAudioMs = 0L
    @Volatile private var lastSttMs = 0L
    @Volatile private var lastTiming = "{}"

    private fun transcribe(seg: FloatArray) {
        val e = engine ?: return
        val t0 = System.nanoTime()
        var timing = "{}"
        val text = try {
            synchronized(CoreLoader.lock) {
                val t = e.transcribe(seg, WavRecorder.SAMPLE_RATE)
                timing = e.lastTiming
                t
            }.trim()
        } catch (t: Throwable) {
            error = t.message ?: t.javaClass.simpleName
            return
        }
        val ms = (System.nanoTime() - t0) / 1_000_000
        val audioMs = seg.size * 1000L / WavRecorder.SAMPLE_RATE
        lastAudioMs = audioMs; lastSttMs = ms; lastTiming = timing
        if (BuildConfig.DEBUG) Log.i("HUMMING", "HUMMING_SEGMENT vad=${segmenter.name} audio_ms=$audioMs stt_ms=$ms timing=$timing text=${org.json.JSONObject.quote(text)}")
        if (text.isEmpty() || TextCleanup.isHallucination(text)) return
        val partial = synchronized(raws) {
            raws.add(text)
            clean(raws.joinToString(" "))
        }
        if (partial.isNotBlank()) onPartial(partial)
    }

    /** Spoken editing commands ("delete that") applied; a closing style command is left for the AI pass. */
    private fun clean(raw: String): String = VoiceCommands.apply(cleanText(raw)).text

    private fun cleanText(raw: String): String {
        if (!cleanMode) return TextCleanup.applyDictionary(raw.trim().replace(Regex("\\s+"), " "), dictionary)
        val core = try { synchronized(CoreLoader.lock) { engine?.cleanup(raw) } ?: raw } catch (t: Throwable) { raw }
        // A spoken list the core already formatted as bullets keeps its lines as they are.
        if (core.contains("\n- ")) return TextCleanup.applyDictionary(core, dictionary)
        return TextCleanup.apply(core, dictionary)
    }

    /** Blocking (call off the main thread): flushes the tail, waits for the transcriber, returns the final text. */
    fun finish(): Result {
        val t0 = System.nanoTime()
        val before = segmenter.segmentsEmitted
        segmenter.flush()
        val tail = segmenter.segmentsEmitted > before
        segmenter.release()
        worker.shutdown()
        worker.awaitTermination(600, TimeUnit.SECONDS)
        val raw = synchronized(raws) { raws.joinToString(" ") }
        val cmd = if (raw.isBlank()) VoiceCommands.Result("", null) else VoiceCommands.apply(cleanText(raw))
        val text = cmd.text
        closed = true
        releaseEngine()
        val ms = (System.nanoTime() - t0) / 1_000_000
        return Result(text, raw, segmenter.segmentsEmitted, ms, if (text.isBlank()) error else null,
            if (tail) lastAudioMs else 0, if (tail) lastSttMs else 0, if (tail) lastTiming else "{}", cmd.instruction)
    }

    private companion object {
        const val GAP = WavRecorder.SAMPLE_RATE / 5 // 200 ms of silence between merged phrases
        const val MAX_PIECE = WavRecorder.SAMPLE_RATE * 20
    }

    /** Drops everything (keyboard hidden mid-dictation). */
    fun cancel() {
        worker.shutdownNow()
        segmenter.release()
        closed = true
        // Hand the engine back only once the transcriber thread has stopped touching it.
        Thread({ runCatching { worker.awaitTermination(60, TimeUnit.SECONDS) }; releaseEngine() }, "humming-stt-release").start()
    }
}

/** Where live audio is cut into phrases. */
interface SpeechSegmenter {
    val name: String
    val segmentsEmitted: Int
    fun feed(samples: ShortArray, n: Int)
    fun flush()
    fun release() {}
}

class EnergySegmenter(private val s: Segmenter) : SpeechSegmenter {
    override val name = "energy"
    override val segmentsEmitted: Int get() = s.segmentsEmitted
    override fun feed(samples: ShortArray, n: Int) = s.feed(samples, n)
    override fun flush() = s.flush()
}

/** Silero VAD v4 through sherpa-onnx (bundled asset, MIT). Cuts at pauses of 0.4 s, and every 12 s at most. */
class SileroSegmenter private constructor(
    private val vad: com.k2fsa.sherpa.onnx.Vad,
    private val onSegment: (FloatArray) -> Unit,
) : SpeechSegmenter {
    override val name = "silero"
    @Volatile override var segmentsEmitted = 0
        private set
    private var released = false

    @Synchronized
    override fun feed(samples: ShortArray, n: Int) {
        if (released) return
        val f = FloatArray(n) { samples[it] / 32768f }
        vad.acceptWaveform(f)
        drain()
    }

    private fun drain() {
        while (!vad.empty()) {
            val seg = vad.front().samples
            vad.pop()
            if (seg.size >= WavRecorder.SAMPLE_RATE / 5) {
                segmentsEmitted++
                onSegment(pad(seg))
            }
        }
    }

    /** 150 ms of silence either side: the recognizer is steadier on words that start or end a segment. */
    private fun pad(seg: FloatArray): FloatArray {
        val p = WavRecorder.SAMPLE_RATE * 15 / 100
        val out = FloatArray(seg.size + 2 * p)
        System.arraycopy(seg, 0, out, p, seg.size)
        return out
    }

    @Synchronized
    override fun flush() {
        if (released) return
        vad.flush()
        drain()
    }

    @Synchronized
    override fun release() {
        if (released) return
        released = true
        runCatching { vad.release() }
    }

    companion object {
        const val ASSET = "humming/silero_vad.onnx"

        fun create(ctx: Context, onSegment: (FloatArray) -> Unit): SileroSegmenter? = runCatching {
            val cfg = com.k2fsa.sherpa.onnx.VadModelConfig(
                sileroVadModelConfig = com.k2fsa.sherpa.onnx.SileroVadModelConfig(
                    model = ASSET, threshold = 0.45f, minSilenceDuration = 0.4f, minSpeechDuration = 0.2f,
                    windowSize = 512, maxSpeechDuration = 12f,
                ),
                sampleRate = WavRecorder.SAMPLE_RATE, numThreads = 1,
            )
            SileroSegmenter(com.k2fsa.sherpa.onnx.Vad(ctx.assets, cfg), onSegment)
        }.onFailure { Log.w("HUMMING", "HUMMING_VAD silero unavailable: ${it.message}") }.getOrNull()
    }
}
