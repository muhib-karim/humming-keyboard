package com.humming.keyboard.predict

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.tanh

/**
 * Humming's on-device neural next-word model: a small word-level LSTM (16k words, 128-dim tied embeddings,
 * 256 hidden units) with int8 weights, run by this hand-written inference in plain Kotlin. No native library, no
 * download, no network: the model ships in the APK as assets/predict/hlm_en.bin (about 2.7 MB) and reads the
 * words of the current sentence to give P(next word | sentence so far).
 *
 * File format (little-endian), written by keyboard/lm/train.py:
 * "HLM1", V, E, H, vocabBytes, vocab (UTF-8, one word per line; 0 = unknown, 1 = sentence start), then
 * emb int8[V*E], embScale f32[V], wIh int8[4H*E], ihScale f32[4H], wHh int8[4H*H], hhScale f32[4H],
 * bias f32[4H] (gates i, f, g, o), proj int8[E*H], projScale f32[E], projBias f32[E], outBias f32[V].
 * Logits = embᵀ · (proj · h + projBias) + outBias (the output layer shares the embedding matrix).
 *
 * Each sentence prefix's state is cached, so typing letters costs nothing and each new word costs one step
 * (about 2.5 M multiply-adds).
 */
class HummingLm(data: ByteArray) {
    val vocabSize: Int
    val embDim: Int
    val hidden: Int
    private val words: Array<String>
    private val index: HashMap<String, Int>
    private val emb: ByteArray
    private val embScale: FloatArray
    private val wIh: ByteArray
    private val ihScale: FloatArray
    private val wHh: ByteArray
    private val hhScale: FloatArray
    private val bias: FloatArray
    private val proj: ByteArray
    private val projScale: FloatArray
    private val projBias: FloatArray
    private val outBias: FloatArray

    init {
        val b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val magic = ByteArray(4).also { b.get(it) }
        require(String(magic, Charsets.US_ASCII) == "HLM1") { "not a Humming language model" }
        vocabSize = b.int; embDim = b.int; hidden = b.int
        val vocabBytes = b.int
        val vb = ByteArray(vocabBytes).also { b.get(it) }
        words = String(vb, Charsets.UTF_8).split('\n').toTypedArray()
        require(words.size == vocabSize) { "vocabulary size ${words.size} != $vocabSize" }
        index = HashMap<String, Int>(vocabSize * 2).apply { words.forEachIndexed { i, w -> put(w, i) } }
        fun bytes(n: Int) = ByteArray(n).also { b.get(it) }
        fun floats(n: Int) = FloatArray(n).also { b.asFloatBuffer().get(it); b.position(b.position() + 4 * n) }
        val g = 4 * hidden
        emb = bytes(vocabSize * embDim); embScale = floats(vocabSize)
        wIh = bytes(g * embDim); ihScale = floats(g)
        wHh = bytes(g * hidden); hhScale = floats(g)
        bias = floats(g)
        proj = bytes(embDim * hidden); projScale = floats(embDim); projBias = floats(embDim)
        outBias = floats(vocabSize)
    }

    /** The LSTM state after a sentence prefix and the log-probabilities of every next word. */
    class State(internal val h: FloatArray, internal val c: FloatArray, val logP: FloatArray)

    fun id(word: String): Int = index[word] ?: UNK
    fun word(id: Int): String = words[id]
    fun contains(word: String): Boolean = index.containsKey(word)

    private val zero by lazy { State(FloatArray(hidden), FloatArray(hidden), FloatArray(0)) }

    /** One LSTM step: feeds [token] after [prev] and returns the new state with its next-word distribution. */
    fun step(prev: State, token: Int): State {
        val e = embDim
        val hs = hidden
        val x = FloatArray(e)
        val es = embScale[token]
        val eo = token * e
        for (j in 0 until e) x[j] = emb[eo + j] * es
        val gates = FloatArray(4 * hs)
        val h0 = prev.h
        for (r in 0 until 4 * hs) {
            var a = 0f
            val io = r * e
            for (j in 0 until e) a += wIh[io + j] * x[j]
            var bsum = 0f
            val ho = r * hs
            for (j in 0 until hs) bsum += wHh[ho + j] * h0[j]
            gates[r] = a * ihScale[r] + bsum * hhScale[r] + bias[r]
        }
        val h = FloatArray(hs)
        val c = FloatArray(hs)
        for (j in 0 until hs) {
            val i = sigmoid(gates[j])
            val f = sigmoid(gates[hs + j])
            val gg = tanh(gates[2 * hs + j])
            val o = sigmoid(gates[3 * hs + j])
            c[j] = f * prev.c[j] + i * gg
            h[j] = o * tanh(c[j])
        }
        val p = FloatArray(e)
        for (k in 0 until e) {
            var a = 0f
            val po = k * hs
            for (j in 0 until hs) a += proj[po + j] * h[j]
            p[k] = a * projScale[k] + projBias[k]
        }
        val logits = FloatArray(vocabSize)
        var max = Float.NEGATIVE_INFINITY
        for (v in 0 until vocabSize) {
            var a = 0f
            val vo = v * e
            for (k in 0 until e) a += emb[vo + k] * p[k]
            val l = a * embScale[v] + outBias[v]
            logits[v] = l
            if (l > max) max = l
        }
        var sum = 0.0
        for (v in 0 until vocabSize) sum += exp((logits[v] - max).toDouble())
        val lse = max + ln(sum).toFloat()
        for (v in 0 until vocabSize) logits[v] -= lse
        return State(h, c, logits)
    }

    private val cache = object : LinkedHashMap<String, State>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, State>?): Boolean = size > 48
    }

    /** The next-word distribution after the words of the current sentence (lowercase, oldest first). */
    @Synchronized
    fun distribution(sentence: List<String>): State {
        val toks = if (sentence.size > MAX_CONTEXT) sentence.subList(sentence.size - MAX_CONTEXT, sentence.size) else sentence
        val key = StringBuilder()
        var state: State? = cache[""]
        if (state == null) state = step(zero, BOS).also { cache[""] = it }
        var i = 0
        // Longest cached prefix first, then one step per remaining word.
        var cachedUpTo = 0
        run {
            val k = StringBuilder()
            for ((n, t) in toks.withIndex()) {
                if (n > 0) k.append(' ')
                k.append(t)
                val s = cache[k.toString()] ?: return@run
                state = s; cachedUpTo = n + 1
            }
        }
        for (t in toks) {
            if (i > 0) key.append(' ')
            key.append(t)
            i++
            if (i <= cachedUpTo) continue
            state = step(state!!, id(t)).also { cache[key.toString()] = it }
        }
        return state!!
    }

    /** P(word) in [s]; a word outside the vocabulary shares the unknown-word mass by its dictionary frequency. */
    fun prob(s: State, word: String, unigram: Double): Double {
        val i = index[word]
        return if (i != null && i > BOS) exp(s.logP[i].toDouble()) else exp(s.logP[UNK].toDouble()) * unigram
    }

    /** The [k] most likely next words (never the unknown or start markers), best first. */
    fun top(s: State, k: Int): List<String> {
        val best = IntArray(k) { -1 }
        val bestP = FloatArray(k) { Float.NEGATIVE_INFINITY }
        for (v in 2 until vocabSize) {
            val l = s.logP[v]
            if (l <= bestP[k - 1]) continue
            var j = k - 1
            while (j > 0 && bestP[j - 1] < l) { bestP[j] = bestP[j - 1]; best[j] = best[j - 1]; j-- }
            bestP[j] = l; best[j] = v
        }
        return best.filter { it >= 0 }.map { words[it] }
    }

    companion object {
        const val UNK = 0
        const val BOS = 1
        const val MAX_CONTEXT = 20
        private fun sigmoid(x: Float): Float = 1f / (1f + exp(-x))
    }
}
