package com.humming.keyboard

import java.io.File
import java.util.Locale

/**
 * Next-word suggestions learned on the device from what the user types and dictates. Counts "word -> next word"
 * pairs, keeps the most recent [MAX_PAIRS], and persists them to a small TSV file in the app's private
 * storage. A fresh install starts from [seed] (assets/next_words_seed.tsv: a few hundred common English pairs,
 * see its NOTICE); learned pairs always rank above seed pairs.
 */
class NextWords(private val file: File?, private val seed: Map<String, List<String>> = emptyMap()) {
    private val counts = LinkedHashMap<String, HashMap<String, Int>>(256, 0.75f, true)
    private var pairs = 0
    private var dirty = 0

    init {
        runCatching {
            file?.takeIf { it.exists() }?.forEachLine { l ->
                val p = l.split('\t')
                if (p.size == 3) add(p[0], p[1], p[2].toIntOrNull() ?: 1)
            }
        }
        dirty = 0
    }

    private fun add(a: String, b: String, n: Int) {
        val m = counts.getOrPut(a) { HashMap() }
        if (!m.containsKey(b)) pairs++
        m[b] = (m[b] ?: 0) + n
        dirty++
        while (pairs > MAX_PAIRS && counts.isNotEmpty()) {
            val eldest = counts.entries.iterator().next()
            pairs -= eldest.value.size
            counts.remove(eldest.key)
        }
    }

    fun learn(prev: String, next: String) {
        val a = norm(prev)
        val b = norm(next)
        if (a.isEmpty() || b.isEmpty()) return
        synchronized(this) { add(a, b, 1) }
    }

    /** Learns every adjacent pair in [text] (sentence ends break the chain). */
    fun learnText(text: String) {
        for (sentence in text.split('.', '?', '!', '\n')) {
            val w = words(sentence)
            for (i in 1 until w.size) learn(w[i - 1], w[i])
        }
    }

    /** Up to [n] likely next words after [prev], most frequent first. */
    fun predict(prev: String, n: Int = 3): List<String> {
        val key = norm(prev)
        val m = synchronized(this) { counts[key]?.toMap() }.orEmpty()
        val learned = m.entries.sortedByDescending { it.value }.map { it.key }
        return (learned + seed[key].orEmpty()).distinct().take(n)
    }

    fun save() {
        val f = file ?: return
        val snapshot = synchronized(this) {
            if (dirty == 0) return
            dirty = 0
            counts.flatMap { (a, m) -> m.map { (b, c) -> "$a\t$b\t$c" } }
        }
        runCatching {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(snapshot.joinToString("\n"))
            tmp.renameTo(f)
        }
    }

    companion object {
        const val MAX_PAIRS = 20000

        /** Parses next_words_seed.tsv (prev, next, weight; separated by \t), each list ordered by weight, highest first. */
        fun parseSeed(lines: Sequence<String>): Map<String, List<String>> {
            val m = LinkedHashMap<String, ArrayList<Pair<String, Int>>>()
            for (l in lines) {
                val p = l.split('\t')
                if (p.size < 2) continue
                val a = norm(p[0]); val b = norm(p[1])
                if (a.isEmpty() || b.isEmpty()) continue
                m.getOrPut(a) { ArrayList() }.add(Pair(b, p.getOrNull(2)?.trim()?.toIntOrNull() ?: 1))
            }
            return m.mapValues { (_, v) -> v.sortedByDescending { it.second }.map { it.first }.distinct() }
        }

        fun loadSeed(ctx: android.content.Context): Map<String, List<String>> =
            runCatching { ctx.assets.open("next_words_seed.tsv").bufferedReader().useLines { parseSeed(it) } }.getOrDefault(emptyMap())

        fun norm(w: String): String = w.lowercase(Locale.US).trim { !it.isLetterOrDigit() && it != '\'' }

        fun words(s: String): List<String> =
            s.split(' ', ',', ';', ':', '\t').map { norm(it) }.filter { it.isNotEmpty() }

        /** The last complete word before a trailing separator in [before], or "" when the text ends mid-word. */
        fun previousWord(before: String): String {
            if (before.isEmpty() || before.last().isLetterOrDigit() || before.last() == '\'') return ""
            val t = before.trimEnd()
            if (t.isEmpty() || t.last() in ".?!") return ""
            return words(t).lastOrNull() ?: ""
        }
    }
}
