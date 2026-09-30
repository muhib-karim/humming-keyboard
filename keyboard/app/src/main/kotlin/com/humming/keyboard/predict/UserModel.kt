package com.humming.keyboard.predict

import java.io.File
import java.util.Locale

/**
 * What the keyboard learns from the user, kept only on this device (app-private storage, never sent anywhere):
 * how often each word is typed, which word follows which, and words the user removed from suggestions.
 * A word that is not in the dictionary becomes a real word after it has been typed [NEW_WORD_MIN_USES] times.
 * Nothing is learned while incognito (the caller checks) and [clear] wipes it all.
 * File format (tab-separated): `u word count`, `b previous word count`, `x word` (removed).
 */
class UserModel(private val file: File?) {
    private val uni = HashMap<String, Int>()
    private val bi = HashMap<String, HashMap<String, Int>>()
    private val blocked = HashSet<String>()
    private val display = HashMap<String, String>()
    @Volatile var dirty = false
        private set

    init { load() }

    @Synchronized fun unigram(word: String): Int = uni[word.lowercase(Locale.ROOT)] ?: 0
    @Synchronized fun bigram(prev: String, word: String): Int = bi[prev]?.get(word) ?: 0
    @Synchronized fun bigramTotal(prev: String): Int = bi[prev]?.values?.sum() ?: 0
    @Synchronized fun followers(prev: String): List<String> =
        bi[prev]?.entries?.sortedByDescending { it.value }?.take(8)?.map { it.key } ?: emptyList()
    @Synchronized fun isBlocked(word: String): Boolean = word.lowercase(Locale.ROOT) in blocked
    @Synchronized fun displayOf(key: String): String = display[key] ?: key

    /** Learned words (typed often enough) starting with [prefix]. */
    @Synchronized fun wordsStartingWith(prefix: String, minUses: Int = NEW_WORD_MIN_USES): List<String> =
        uni.entries.filter { it.key.startsWith(prefix) && it.value >= minUses && it.key != prefix }
            .sortedByDescending { it.value }.take(10).map { it.key }

    @Synchronized fun learnedWordsOfLength(len: Int, minUses: Int = NEW_WORD_MIN_USES): List<String> =
        uni.entries.filter { it.key.length == len && it.value >= minUses }.map { it.key }

    /** Records that [word] was typed after [prev] ("<s>" at the start of a sentence). */
    @Synchronized fun learn(prev: String?, word: String) {
        val w = word.trim().trim('\'')
        if (w.isEmpty() || w.length > Lexicon.MAX_LEN || !w.all { it.isLetter() || it == '\'' }) return
        val key = w.lowercase(Locale.ROOT)
        if (key in blocked) blocked.remove(key)
        uni[key] = (uni[key] ?: 0) + 1
        // "Hello" at the start of a sentence says nothing about how the word is spelt; "Karim" mid-sentence does.
        val sentenceCap = prev == SENTENCE_START && w.first().isUpperCase() && w.drop(1).none { it.isUpperCase() }
        if (!sentenceCap) display[key] = w else display.putIfAbsent(key, key)
        if (prev != null) {
            val m = bi.getOrPut(prev.lowercase(Locale.ROOT)) { HashMap() }
            m[key] = (m[key] ?: 0) + 1
        }
        prune()
        dirty = true
    }

    /** Long-press "remove" on a suggestion: it is never suggested again until typed again. */
    @Synchronized fun block(word: String) {
        val key = word.lowercase(Locale.ROOT)
        blocked.add(key)
        uni.remove(key)
        for (m in bi.values) m.remove(key)
        dirty = true
    }

    @Synchronized fun clear() {
        uni.clear(); bi.clear(); blocked.clear(); display.clear()
        dirty = true
        save()
    }

    @Synchronized fun stats(): Pair<Int, Int> = Pair(uni.size, bi.values.sumOf { it.size })

    private fun prune() {
        if (uni.size > MAX_WORDS) {
            val keep = uni.entries.sortedByDescending { it.value }.take(MAX_WORDS * 9 / 10).map { it.key }.toHashSet()
            uni.keys.retainAll(keep)
            display.keys.retainAll(keep)
        }
        val pairs = bi.values.sumOf { it.size }
        if (pairs > MAX_PAIRS) {
            for (m in bi.values) m.entries.removeIf { it.value <= 1 }
            bi.entries.removeIf { it.value.isEmpty() }
        }
    }

    @Synchronized fun save() {
        val f = file ?: return
        if (!dirty) return
        runCatching {
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.bufferedWriter().use { out ->
                for ((k, v) in uni) out.write("u\t${display[k] ?: k}\t$v\n")
                for ((p, m) in bi) for ((k, v) in m) out.write("b\t$p\t$k\t$v\n")
                for (k in blocked) out.write("x\t$k\n")
            }
            tmp.renameTo(f)
            dirty = false
        }
    }

    private fun load() {
        val f = file ?: return
        if (!f.exists()) return
        runCatching {
            f.forEachLine { line ->
                val p = line.split('\t')
                when {
                    p.size == 3 && p[0] == "u" -> {
                        val key = p[1].lowercase(Locale.ROOT)
                        uni[key] = p[2].toIntOrNull() ?: 0
                        if (p[1] != key) display[key] = p[1]
                    }
                    p.size == 4 && p[0] == "b" -> bi.getOrPut(p[1]) { HashMap() }[p[2]] = p[3].toIntOrNull() ?: 0
                    p.size == 2 && p[0] == "x" -> blocked.add(p[1])
                }
            }
        }
    }

    companion object {
        const val SENTENCE_START = "<s>"
        const val NEW_WORD_MIN_USES = 2
        const val MAX_WORDS = 20_000
        const val MAX_PAIRS = 60_000
    }
}
