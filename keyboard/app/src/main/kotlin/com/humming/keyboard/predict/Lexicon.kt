package com.humming.keyboard.predict

import java.util.Locale

/**
 * The English word list the predictor searches: about 156k words, each with a smoothed log probability.
 *
 * Source (see assets/predict/NOTICE.txt): the Android Open Source Project LatinIME en_US word list (Apache-2.0),
 * whose frequency classes are blended with word counts from the Tatoeba English sentences (CC-BY 2.0 FR) so everyday,
 * conversational words rank the way people actually type them. File format, one word per line, best first:
 * `word<TAB>q[<TAB>flags]` with log P = q / 10 - 24; flags `o` = possibly offensive, `a` = abbreviation (valid in any case).
 * Pure logic (no Android types) so it is covered by JVM unit tests.
 */
class Lexicon private constructor(
    private val keys: Array<String>,
    private val display: Array<String>,
    private val logP: FloatArray,
    private val flags: ByteArray,
) {
    private val index = HashMap<String, Int>(keys.size * 2)
    private val sorted: IntArray
    private val byLen: Array<IntArray>
    /** Word indices by (length, first letter) and (length, second letter): the typo search scans only the buckets
     *  a typo can reach instead of every word of a length (about 6x fewer distance computations). */
    private val byLenFirst = HashMap<Long, IntArray>()
    private val byLenSecond = HashMap<Long, IntArray>()

    init {
        for (i in keys.indices) index.putIfAbsent(keys[i], i)
        sorted = keys.indices.sortedWith { a, b -> keys[a].compareTo(keys[b]) }.toIntArray()
        val lens = Array(MAX_LEN + 1) { ArrayList<Int>() }
        for (i in keys.indices) if (keys[i].length <= MAX_LEN) lens[keys[i].length].add(i)
        byLen = Array(MAX_LEN + 1) { lens[it].toIntArray() }
        val first = HashMap<Long, ArrayList<Int>>()
        val second = HashMap<Long, ArrayList<Int>>()
        for (len in 1..MAX_LEN) for (i in byLen[len]) {
            val k = keys[i]
            first.getOrPut(bucket(len, k[0])) { ArrayList() }.add(i)
            if (len > 1) second.getOrPut(bucket(len, k[1])) { ArrayList() }.add(i)
        }
        for ((b, l) in first) byLenFirst[b] = l.toIntArray()
        for ((b, l) in second) byLenSecond[b] = l.toIntArray()
    }

    private fun bucket(len: Int, c: Char): Long = (len.toLong() shl 20) or c.code.toLong()

    /** Words of length [len] whose first letter is [c]. */
    fun ofLengthStarting(len: Int, c: Char): IntArray = byLenFirst[bucket(len, c)] ?: EMPTY

    /** Words of length [len] whose second letter is [c]. */
    fun ofLengthSecond(len: Int, c: Char): IntArray = byLenSecond[bucket(len, c)] ?: EMPTY

    val size: Int get() = keys.size

    fun indexOf(key: String): Int = index[key] ?: -1
    fun key(i: Int): String = keys[i]
    fun display(i: Int): String = display[i]
    fun logP(i: Int): Float = logP[i]
    fun isOffensive(i: Int): Boolean = (flags[i].toInt() and FLAG_OFFENSIVE) != 0
    fun isAbbreviation(i: Int): Boolean = (flags[i].toInt() and FLAG_ABBREV) != 0

    /** Word indices of a given length (for the typo search). */
    fun ofLength(len: Int): IntArray = if (len in 0..MAX_LEN) byLen[len] else IntArray(0)

    /** A dictionary word as typed: "gre" is not a word even though "GRE" is listed; abbreviations match any case. */
    fun isWord(typed: String): Boolean {
        val i = indexOf(typed.lowercase(Locale.ROOT))
        if (i < 0) return false
        val d = display[i]
        val allCaps = d.length > 1 && d.all { !it.isLetter() || it.isUpperCase() }
        return !allCaps || isAbbreviation(i) || typed == d
    }

    /** The [limit] most likely words that start with [prefix] (lowercase), excluding the prefix itself. */
    fun completions(prefix: String, limit: Int): List<Int> {
        if (prefix.isEmpty()) return emptyList()
        var lo = 0
        var hi = sorted.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (keys[sorted[mid]] < prefix) lo = mid + 1 else hi = mid
        }
        val found = ArrayList<Int>()
        var k = lo
        while (k < sorted.size && keys[sorted[k]].startsWith(prefix)) {
            if (keys[sorted[k]] != prefix) found.add(sorted[k])
            k++
        }
        // Lower index = more frequent (the file is sorted best first).
        found.sort()
        return if (found.size > limit) found.subList(0, limit) else found
    }

    companion object {
        const val MAX_LEN = 28
        private val EMPTY = IntArray(0)
        const val FLAG_OFFENSIVE = 1
        const val FLAG_ABBREV = 2

        fun parse(lines: Sequence<String>): Lexicon {
            val k = ArrayList<String>(160_000)
            val d = ArrayList<String>(160_000)
            val p = ArrayList<Float>(160_000)
            val f = ArrayList<Byte>(160_000)
            for (line in lines) {
                val t1 = line.indexOf('\t')
                if (t1 <= 0) continue
                val t2 = line.indexOf('\t', t1 + 1)
                val word = line.substring(0, t1)
                val q = (if (t2 < 0) line.substring(t1 + 1) else line.substring(t1 + 1, t2)).trim().toIntOrNull() ?: continue
                var fl = 0
                if (t2 > 0) {
                    val s = line.substring(t2 + 1)
                    if ('o' in s) fl = fl or FLAG_OFFENSIVE
                    if ('a' in s) fl = fl or FLAG_ABBREV
                }
                k.add(word.lowercase(Locale.ROOT))
                d.add(word)
                p.add(q / 10f - 24f)
                f.add(fl.toByte())
            }
            return Lexicon(k.toTypedArray(), d.toTypedArray(), p.toFloatArray(), f.toByteArray())
        }
    }
}
