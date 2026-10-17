package com.humming.keyboard

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream

/**
 * 16 kHz mono PCM16 capture. No length limit: capture runs until [stop] (live dictation streams every block to
 * [onChunk] and never needs the whole recording). [maxSeconds] > 0 restores a hard cap ([onLimit] then runs on the
 * capture thread and the caller calls [stop]). Only the first [keepSeconds] of audio are kept in memory for the clip
 * [stop] returns (History's "retry" copy); a longer dictation keeps streaming without growing memory.
 * The caller must hold RECORD_AUDIO before calling [start].
 */
class WavRecorder(
    private val ctx: android.content.Context? = null,
    private val maxSeconds: Int = 0,
    private val keepSeconds: Int = BuildConfig.MAX_RECORD_SECONDS,
    private val onChunk: ((ShortArray, Int) -> Unit)? = null,
    private val onLimit: () -> Unit = {},
) {
    companion object {
        const val SAMPLE_RATE = 16_000
    }

    @Volatile private var running = false
    private var thread: Thread? = null
    private var record: AudioRecord? = null
    private var bt: BluetoothMic.Route? = null

    /** The Bluetooth headset dictation is using, or null for the phone's own mic. */
    val bluetoothName: String? get() = bt?.label
    private val pcm = ByteArrayOutputStream()
    private val keepBytes: Long = SAMPLE_RATE * 2L * keepSeconds
    /** Bytes captured so far (the whole session, not just the kept part). */
    @Volatile var capturedBytes = 0L
        private set
    val capturedSeconds: Double get() = capturedBytes / (SAMPLE_RATE * 2.0)

    private fun deliver(buf: ByteArray, n: Int) {
        synchronized(pcm) {
            val room = keepBytes - pcm.size()
            if (room > 0) pcm.write(buf, 0, minOf(n.toLong(), room).toInt())
        }
        capturedBytes += n
        val cb = onChunk ?: return
        val s = ShortArray(n / 2)
        for (i in s.indices) s[i] = ((buf[2 * i + 1].toInt() shl 8) or (buf[2 * i].toInt() and 0xFF)).toShort()
        cb(s, s.size)
    }

    /** Debug builds: plays a WAV file as if it were the mic, in real time, then room silence (see [TestAudio]). */
    private fun startTestSource(path: String): Boolean {
        val bytes = try { java.io.File(path).readBytes() } catch (e: Exception) { return false }
        val data = if (bytes.size > Wav.HEADER_BYTES) bytes.copyOfRange(Wav.HEADER_BYTES, bytes.size) else ByteArray(0)
        running = true
        val maxBytes = if (maxSeconds > 0) SAMPLE_RATE * 2L * maxSeconds else Long.MAX_VALUE
        thread = Thread({
            val block = SAMPLE_RATE / 10 * 2 // 100 ms
            var off = 0
            val t0 = System.nanoTime()
            var sent = 0L
            val silence = ByteArray(block)
            while (running) {
                val n: Int
                val buf: ByteArray
                if (off < data.size) {
                    n = minOf(block, data.size - off)
                    buf = data.copyOfRange(off, off + n)
                    off += n
                } else {
                    n = block
                    buf = silence
                }
                deliver(buf, n)
                sent += n
                if (capturedBytes >= maxBytes) {
                    running = false
                    onLimit()
                    break
                }
                val due = t0 + sent * 1_000_000_000L / (SAMPLE_RATE * 2)
                val wait = (due - System.nanoTime()) / 1_000_000
                if (wait > 0) try { Thread.sleep(wait) } catch (e: InterruptedException) { break }
            }
        }, "humming-rec").apply { start() }
        return true
    }

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        TestAudio.take()?.let { return startTestSource(it) }
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuf <= 0) return false
        val bufSize = maxOf(minBuf, SAMPLE_RATE / 5 * 2) // at least 200 ms
        val rec = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize,
            )
        } catch (e: Exception) {
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return false
        }
        bt = ctx?.let { BluetoothMic.begin(it) }?.also { it.attach(rec) }
        try {
            rec.startRecording()
        } catch (e: IllegalStateException) {
            rec.release()
            bt?.end()
            bt = null
            return false
        }
        record = rec
        running = true
        val maxBytes = if (maxSeconds > 0) SAMPLE_RATE * 2L * maxSeconds else Long.MAX_VALUE
        thread = Thread({
            val buf = ByteArray(bufSize)
            while (running) {
                val n = rec.read(buf, 0, buf.size)
                if (n > 0) deliver(buf, n)
                else if (n < 0) {
                    // ERROR_DEAD_OBJECT etc.: the audio server dropped this recorder (rare; e.g. a call took the mic).
                    // Keep the thread alive briefly instead of spinning; the panel's stop still works.
                    try { Thread.sleep(20) } catch (e: InterruptedException) { break }
                }
                if (capturedBytes >= maxBytes) {
                    running = false
                    onLimit()
                    break
                }
            }
        }, "humming-rec").apply { start() }
        return true
    }

    /** Stops capture and returns the clip. Safe to call twice. */
    fun stop(): AudioClip {
        running = false
        thread?.join(1_000)
        thread = null
        record?.let {
            try {
                it.stop()
            } catch (e: IllegalStateException) {
                // already stopped
            }
            it.release()
        }
        record = null
        bt?.end()
        bt = null
        val data = synchronized(pcm) { pcm.toByteArray() }
        return AudioClip(data, SAMPLE_RATE)
    }
}

/** Debug-build test hook: the next recording reads this WAV instead of the mic. Unused (null) in release builds. */
object TestAudio {
    @Volatile private var next: String? = null
    fun set(path: String?) { next = path }
    fun take(): String? = next.also { next = null }
}
