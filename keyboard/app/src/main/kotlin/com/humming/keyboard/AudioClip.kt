package com.humming.keyboard

import kotlin.math.abs
import kotlin.math.log10

/**
 * One dictation: 16-bit little-endian mono PCM plus its sample rate.
 * Transcribers take this instead of a file so an on-device engine can read samples directly
 * (e.g. a core call shaped like transcribe_pcm(samples: FloatArray, sampleRate = 16000)).
 */
class AudioClip(val pcm16le: ByteArray, val sampleRate: Int) {

    val sampleCount: Int get() = pcm16le.size / 2

    val durationSeconds: Double get() = sampleCount / sampleRate.toDouble()

    fun sample(i: Int): Int {
        val lo = pcm16le[2 * i].toInt() and 0xFF
        val hi = pcm16le[2 * i + 1].toInt()
        return (hi shl 8) or lo
    }

    /** Samples scaled to [-1, 1], the usual input for on-device speech models. */
    fun toFloat32(): FloatArray = FloatArray(sampleCount) { sample(it) / 32768f }

    /** A canonical WAV file (44-byte header + PCM), for HTTP backends. */
    fun toWav(): ByteArray = Wav.wrap(pcm16le, sampleRate)

    /** Peak level in dBFS; very low values mean the mic delivered silence. */
    fun peakDbfs(): Double {
        var peak = 0
        for (i in 0 until sampleCount) {
            val v = abs(sample(i))
            if (v > peak) peak = v
        }
        return if (peak == 0) -120.0 else 20 * log10(peak / 32768.0)
    }
}
