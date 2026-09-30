package com.humming.keyboard

import android.content.Context
import android.os.Build
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import java.io.File

/**
 * An on-device speech engine. M5: NVIDIA Parakeet through sherpa-onnx (onnxruntime on the CPU), chosen by the
 * bake-off over whisper.cpp and FUTO's Whisper ACFT models. Everything runs on the phone.
 */
interface LocalEngine : AutoCloseable {
    val version: String

    /** Transcribe + rule-based cleanup. Samples are mono floats in [-1, 1]. Blocking. */
    fun dictate(samples: FloatArray, sampleRate: Int): String

    /** The engine's raw transcript (before any cleanup) of the last dictation, for diagnostics. */
    val lastRaw: String get() = ""

    /** Raw speech-to-text of one segment, no cleanup. Blocking. */
    fun transcribe(samples: FloatArray, sampleRate: Int): String

    /** Engine-side cleanup on joined text (Parakeet already punctuates; TextCleanup does the rest). */
    fun cleanup(text: String): String = text

    /** Where the last call's time went, as JSON. */
    val lastTiming: String get() = "{}"

    /** Model load time of this engine (ms). */
    val loadMs: Long get() = 0

    /** One throwaway pass so the first dictation doesn't pay for setup; returns its timing JSON. */
    fun warmup(): String = "{}"
}

/** Parakeet (CTC or TDT transducer) through sherpa-onnx's offline recognizer. */
class SherpaEngine(private val model: VoiceModel, dir: File, threads: Int) : LocalEngine {
    override val version = "sherpa-onnx 1.13.8 · ${model.dirName}"
    private val recognizer: OfflineRecognizer
    override val loadMs: Long
    @Volatile override var lastRaw: String = ""
        private set
    @Volatile override var lastTiming: String = "{}"
        private set

    init {
        val t0 = System.nanoTime()
        val tokens = File(dir, "tokens.txt").absolutePath
        val mc = when (model.kind) {
            VoiceModel.Kind.NEMO_CTC -> OfflineModelConfig(
                nemo = OfflineNemoEncDecCtcModelConfig(model = File(dir, "model.int8.onnx").absolutePath),
                tokens = tokens, numThreads = threads, provider = "cpu",
            )
            VoiceModel.Kind.NEMO_TRANSDUCER -> OfflineModelConfig(
                transducer = OfflineTransducerModelConfig(
                    encoder = File(dir, "encoder.int8.onnx").absolutePath,
                    decoder = File(dir, "decoder.int8.onnx").absolutePath,
                    joiner = File(dir, "joiner.int8.onnx").absolutePath,
                ),
                tokens = tokens, numThreads = threads, provider = "cpu", modelType = "nemo_transducer",
            )
        }
        recognizer = OfflineRecognizer(null, OfflineRecognizerConfig(featConfig = FeatureConfig(16000, 80), modelConfig = mc))
        loadMs = (System.nanoTime() - t0) / 1_000_000
    }

    override fun transcribe(samples: FloatArray, sampleRate: Int): String {
        if (samples.isEmpty()) return ""
        val t0 = System.nanoTime()
        val s = recognizer.createStream()
        try {
            s.acceptWaveform(samples, sampleRate)
            recognizer.decode(s)
            val text = recognizer.getResult(s).text.trim()
            val ms = (System.nanoTime() - t0) / 1_000_000
            lastTiming = "{\"stt_ms\":$ms,\"audio_ms\":${samples.size * 1000L / sampleRate},\"model\":\"${model.id}\"}"
            lastRaw = text
            return text
        } finally {
            s.release()
        }
    }

    override fun dictate(samples: FloatArray, sampleRate: Int): String = transcribe(samples, sampleRate)

    override fun warmup(): String {
        transcribe(FloatArray(16000), 16000)
        return lastTiming
    }

    override fun close() = recognizer.release()
}

object CoreLoader {
    private var engine: LocalEngine? = null
    private var engineKey: String? = null

    /** The model id of the loaded engine ("fast" / "best"), for logs. */
    @Volatile var variant: String = "none"
        private set

    /** The engine ships for 64-bit ARM phones and x86_64 emulators/Chromebooks. */
    private val SUPPORTED = setOf("arm64-v8a", "x86_64")

    /**
     * Decoder threads = the phone's performance cores (cores within 25% of the top clock count as "big").
     * Equal clocks or unreadable cpufreq (emulators): min(4, cores).
     */
    fun threads(): Int {
        val n = Runtime.getRuntime().availableProcessors()
        val freqs = (0 until n).mapNotNull { i ->
            runCatching { File("/sys/devices/system/cpu/cpu$i/cpufreq/cpuinfo_max_freq").readText().trim().toLong() }.getOrNull()
        }
        if (freqs.size < 2 || freqs.distinct().size == 1) return minOf(4, n).coerceAtLeast(1)
        val top = freqs.max()
        return freqs.count { it >= top * 3 / 4 }.coerceIn(2, 4)
    }

    /** Null when the engine can run on this phone's CPU. */
    fun whyUnavailable(ctx: Context): String? =
        if (Build.SUPPORTED_ABIS.none { it in SUPPORTED }) "voice isn't built for this phone's CPU (${Build.SUPPORTED_ABIS.firstOrNull()})"
        else null

    /** Loads the model once (about a second) and keeps it; reloads only when the model changes. */
    @Synchronized
    fun engine(ctx: Context, m: VoiceModel = ModelStore.current(ctx)): LocalEngine {
        val key = m.id
        engine?.let { if (engineKey == key) return it }
        synchronized(lock) { engine?.close() }
        engine = null
        val e = SherpaEngine(m, ModelStore.dir(ctx, m), threads())
        android.util.Log.i("HUMMING", "HUMMING_ENGINE loaded ${m.id} load_ms=${e.loadMs} threads=${threads()}")
        engine = e
        engineKey = key
        variant = m.id
        return e
    }

    fun loaded(): Boolean = engine != null

    fun lastRaw(): String = engine?.lastRaw ?: ""

    /** Held around every native engine call so two dictations never run the engine at once. */
    val lock = Any()

    @Synchronized
    fun release() {
        synchronized(lock) { engine?.close() }
        engine = null
        engineKey = null
        variant = "none"
    }
}
