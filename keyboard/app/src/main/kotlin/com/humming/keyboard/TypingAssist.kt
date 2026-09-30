package com.humming.keyboard

import android.content.Context
import java.util.Locale

/**
 * Typing help for the letter keys: word completions, spelling corrections and autocorrect-on-space.
 * Word list: ~57k of the most frequent words of the AOSP LatinIME en_US list (Apache-2.0, see assets/words_en.NOTICE.txt).
 * Pure logic after [load]; the candidate search is a keyboard-aware weighted Damerau-Levenshtein distance
 * (adjacent-key slips, swapped letters and a missing/extra doubled letter cost less than other edits), ranked with
 * the word's frequency as tie-breaker. Covered by TypingAssistTest.
 */
class TypingAssist(entries: List<Pair<String, Int>>) {
    private val lower: Array<String>
    private val display: Array<String>
    private val freq: IntArray
    private val sortedIdx: IntArray // indices sorted by lowercase word, for prefix search
    private val byLen: Array<IntArray>
    private val index = HashMap<String, Int>(entries.size * 2)

    init {
        val n = entries.size
        lower = Array(n) { entries[it].first.lowercase(Locale.US) }
        display = Array(n) { entries[it].first }
        freq = IntArray(n) { entries[it].second }
        for (i in 0 until n) index.putIfAbsent(lower[i], i)
        sortedIdx = (0 until n).sortedBy { lower[it] }.toIntArray()
        val lens = Array(MAX_LEN + 1) { ArrayList<Int>() }
        for (i in 0 until n) if (lower[i].length <= MAX_LEN) lens[lower[i].length].add(i)
        byLen = Array(MAX_LEN + 1) { lens[it].toIntArray() }
    }

    fun isWord(w: String, extra: Collection<String> = emptyList()): Boolean {
        val l = w.lowercase(Locale.US)
        return index.containsKey(l) || extra.any { it.equals(w, ignoreCase = true) }
    }

    data class Candidate(val word: String, val cost: Float, val freq: Int) {
        val score: Float get() = cost - freq / 120f
    }

    /** Most frequent words starting with [prefix] (excluding [prefix] itself). */
    fun completions(prefix: String, n: Int = 3): List<Candidate> {
        val p = prefix.lowercase(Locale.US)
        if (p.isEmpty()) return emptyList()
        var lo = 0
        var hi = sortedIdx.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (lower[sortedIdx[mid]] < p) lo = mid + 1 else hi = mid
        }
        val found = ArrayList<Int>()
        var k = lo
        while (k < sortedIdx.size && found.size < 4000 && lower[sortedIdx[k]].startsWith(p)) {
            if (lower[sortedIdx[k]] != p) found.add(sortedIdx[k])
            k++
        }
        return found.sortedByDescending { freq[it] }.take(n).map { Candidate(display[it], 0f, freq[it]) }
    }

    /** Closest dictionary words to [typed] by keyboard-aware edit cost (<= [maxCost]), best first. */
    fun corrections(typed: String, n: Int = 3, maxCost: Float = 2.2f): List<Candidate> {
        val t = typed.lowercase(Locale.US)
        if (t.length < 2 || t.length > MAX_LEN) return emptyList()
        val out = ArrayList<Candidate>()
        for (len in maxOf(1, t.length - 2)..minOf(MAX_LEN, t.length + 2)) {
            for (i in byLen[len]) {
                val c = cost(t, lower[i], maxCost)
                if (c <= maxCost && lower[i] != t) out.add(Candidate(display[i], c, freq[i]))
            }
        }
        out.sortBy { it.score }
        return out.take(n)
    }

    /**
     * The word to put in place of [typed] when space or punctuation follows it, or null to leave it alone:
     * only for a word that is not in the dictionary (nor the user's), with a close candidate
     * (edit cost <= 1.0 for words up to 4 letters, <= 1.6 longer).
     */
    fun autocorrect(typed: String, userWords: Collection<String> = emptyList()): String? {
        if (typed.length < 2 || !typed.all { it.isLetter() || it == '\'' }) return null
        if (isWord(typed, userWords)) return null
        val best = corrections(typed, 1).firstOrNull() ?: return null
        val limit = if (typed.length <= 4) 1.0f else 1.6f
        if (best.cost > limit) return null
        return matchCase(best.word, typed)
    }

    companion object {
        const val MAX_LEN = 24
        private const val ROWS = "qwertyuiop|asdfghjkl|zxcvbnm"
        private val keyPos: Map<Char, Pair<Float, Int>> = buildMap {
            ROWS.split('|').forEachIndexed { r, row -> row.forEachIndexed { c, ch -> put(ch, Pair(c + 0.5f * r, r)) } }
        }

        fun isVowel(c: Char): Boolean = c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u'

        fun adjacent(a: Char, b: Char): Boolean {
            val pa = keyPos[a] ?: return false
            val pb = keyPos[b] ?: return false
            return kotlin.math.abs(pa.second - pb.second) <= 1 && kotlin.math.abs(pa.first - pb.first) <= 1.1f
        }

        /** Weighted optimal-string-alignment distance from what was typed [a] to dictionary word [b]; early exit above [maxC]. */
        fun cost(a: String, b: String, maxC: Float = 2.2f): Float {
            val n = a.length
            val m = b.length
            if (kotlin.math.abs(n - m) > 2) return 99f
            val d = Array(n + 1) { FloatArray(m + 1) }
            for (i in 0..n) d[i][0] = i * 1.0f
            for (j in 0..m) d[0][j] = j * 0.8f
            for (i in 1..n) {
                var rowMin = 99f
                for (j in 1..m) {
                    val sub = if (a[i - 1] == b[j - 1]) 0f else if (adjacent(a[i - 1], b[j - 1])) 0.7f else 1.2f
                    // A dropped doubled letter or a dropped vowel ("happend", "realy") is the commonest omission.
                    val omitted = if ((j >= 2 && b[j - 1] == b[j - 2]) || isVowel(b[j - 1])) 0.6f else 0.8f
                    val extra = if (i >= 2 && a[i - 1] == a[i - 2]) 0.6f else if (adjacent(a[i - 1], b[j - 1])) 0.8f else 1.0f
                    var v = minOf(d[i - 1][j] + extra, d[i][j - 1] + omitted, d[i - 1][j - 1] + sub)
                    if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                        // Swapped vowels ("ie"/"ei", "ua"/"au") are cheaper than other swaps.
                        val swap = if (isVowel(a[i - 1]) && isVowel(a[i - 2])) 0.5f else 0.7f
                        v = minOf(v, d[i - 2][j - 2] + swap)
                    }
                    // One letter typed two places early or late ("tounge" for "tongue"): a 3-letter rotation.
                    if (i > 2 && j > 2 && a[i - 1] != b[j - 1]) {
                        val left = a[i - 3] == b[j - 1] && a[i - 2] == b[j - 3] && a[i - 1] == b[j - 2]
                        val right = a[i - 1] == b[j - 3] && a[i - 3] == b[j - 2] && a[i - 2] == b[j - 1]
                        if (left || right) v = minOf(v, d[i - 3][j - 3] + 1.0f)
                    }
                    d[i][j] = v
                    if (v < rowMin) rowMin = v
                }
                if (rowMin > maxC) return 99f
            }
            return d[n][m]
        }

        /** Keeps the typed word's capitalisation (Capitalised or ALL CAPS); dictionary casing ("I", names) otherwise. */
        fun matchCase(word: String, typed: String): String = when {
            typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } -> word.uppercase(Locale.US)
            typed.first().isUpperCase() -> word.replaceFirstChar { it.titlecase(Locale.US) }
            else -> word
        }

        fun parse(lines: Sequence<String>): List<Pair<String, Int>> = lines.mapNotNull { l ->
            val tab = l.indexOf('\t')
            if (tab <= 0) null else Pair(l.substring(0, tab), l.substring(tab + 1).trim().toIntOrNull() ?: 0)
        }.toList()

        @Volatile private var shared: TypingAssist? = null

        /** Loads the bundled word list once per process (about 57k lines; call off the main thread). */
        fun load(ctx: Context): TypingAssist {
            shared?.let { return it }
            synchronized(this) {
                shared?.let { return it }
                val list = ctx.assets.open("words_en.txt").bufferedReader().useLines { parse(it) }
                return TypingAssist(list).also { shared = it }
            }
        }

        fun loaded(): TypingAssist? = shared
    }
}
