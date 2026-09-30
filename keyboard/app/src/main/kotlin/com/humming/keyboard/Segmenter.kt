package com.humming.keyboard

import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Splits a live 16 kHz mono PCM16 stream into speech segments at natural pauses, so each segment can be
 * transcribed while the user keeps talking (live text, long dictations, fast insert after stop).
 * Pure Kotlin (no Android types): covered by SegmenterTest.
 *
 * A segment is cut when it is at least [minSegSec] long and the speaker has paused for [pauseMs], or when it
 * reaches [maxSegSec] (whisper's window is 30 s), in which case it is cut at the quietest 20 ms frame of its
 * last few seconds and the rest carries into the next segment. Segments with no speech are dropped.
 */
class Segmenter(
    private val sampleRate: Int = 16_000,
    private val minSegSec: Double = 1.5,
    private val maxSegSec: Double = 24.0,
    private val pauseMs: Int = 450,
    private val onSegment: (FloatArray) -> Unit,
) {
    private val frame = sampleRate / 50 // 20 ms
    private var buf = FloatArray(sampleRate * 32)
    private var len = 0
    private val frameDb = ArrayList<Float>()
    private var pending = FloatArray(frame)
    private var pendingLen = 0
    private var floor = Float.NaN
    private var silentRun = 0
    var segmentsEmitted = 0
        private set
    var segmentsDropped = 0
        private set

    fun feed(samples: ShortArray, n: Int = samples.size) {
        var i = 0
        while (i < n) {
            val take = minOf(frame - pendingLen, n - i)
            for (k in 0 until take) pending[pendingLen + k] = samples[i + k] / 32768f
            pendingLen += take
            i += take
            if (pendingLen == frame) {
                addFrame(pending)
                pendingLen = 0
            }
        }
    }

    /** Emits whatever is buffered (end of dictation). */
    fun flush() {
        if (pendingLen > 0) {
            addFrameSamples(pending, pendingLen)
            pendingLen = 0
        }
        emit(len)
    }

    private fun addFrame(f: FloatArray) {
        addFrameSamples(f, frame)
        val db = frameDb.last()
        val speechLevel = speechThreshold()
        silentRun = if (db < speechLevel - 4f && db < -35f) silentRun + 1 else 0
        val segSec = len / sampleRate.toDouble()
        if (segSec >= minSegSec && silentRun * 20 >= pauseMs && hasSpeech(0, frameDb.size)) {
            emit(len)
        } else if (segSec >= maxSegSec) {
            val frames = frameDb.size
            val from = maxOf(0, frames - 5 * 50)
            var best = frames - 1
            for (k in from until frames) if (frameDb[k] <= frameDb[best]) best = k
            emit((best + 1) * frame)
        }
    }

    private fun addFrameSamples(f: FloatArray, n: Int) {
        if (len + n > buf.size) buf = buf.copyOf(buf.size * 2)
        var sum = 0.0
        for (k in 0 until n) {
            buf[len + k] = f[k]
            sum += f[k] * f[k]
        }
        len += n
        val rms = sqrt(sum / maxOf(n, 1))
        val db = if (rms <= 1e-7) -140f else (20 * log10(rms)).toFloat()
        frameDb.add(db)
        // Noise floor: follows quiet frames down at once and drifts up slowly (0.5 dB/s) in loud stretches.
        floor = if (floor.isNaN() || db < floor) db else floor + 0.01f
    }

    private fun speechThreshold(): Float = maxOf(-48f, (if (floor.isNaN()) -60f else floor) + 12f)

    private fun hasSpeech(fromFrame: Int, toFrame: Int): Boolean {
        val t = speechThreshold()
        var n = 0
        for (k in fromFrame until toFrame) if (frameDb[k] > t) n++
        return n >= 8 // at least 160 ms above the speech level
    }

    /** Frames above -40 dBFS: a segment this loud is never dropped, whatever the noise floor estimate says. */
    private fun loudFrames(fromFrame: Int, toFrame: Int): Int {
        var n = 0
        for (k in fromFrame until toFrame) if (frameDb[k] > -40f) n++
        return n
    }

    private fun emit(cutSamples: Int) {
        val cut = cutSamples.coerceIn(0, len)
        val cutFrames = minOf(frameDb.size, (cut + frame - 1) / frame)
        if (cut > 0) {
            if (hasSpeech(0, cutFrames) || loudFrames(0, cutFrames) >= 8) {
                segmentsEmitted++
                onSegment(buf.copyOfRange(0, cut))
            } else {
                segmentsDropped++
            }
        }
        val rest = len - cut
        if (rest > 0) System.arraycopy(buf, cut, buf, 0, rest)
        len = rest
        val keep = ArrayList(frameDb.subList(cutFrames, frameDb.size))
        frameDb.clear()
        frameDb.addAll(keep)
        silentRun = 0
    }
}
