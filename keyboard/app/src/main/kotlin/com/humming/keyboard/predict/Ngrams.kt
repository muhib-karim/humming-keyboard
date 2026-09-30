package com.humming.keyboard.predict

/**
 * Next-word statistics: how often each word followed a one- or two-word context in the Tatoeba English sentences
 * (CC-BY 2.0 FR, see assets/predict/NOTICE.txt). One context per line, sorted by the context's UTF-8 bytes:
 * `context<TAB>total<TAB>word:count word:count ...` ("<s>" is the start of a sentence). The file stays as one byte
 * array searched by binary search, so it costs its own size in memory (about 5 MB) and no parsing at start-up;
 * the few contexts in use are decoded on demand and cached.
 */
class Ngrams(private val data: ByteArray) {
    private val starts: IntArray

    init {
        var n = if (data.isNotEmpty()) 1 else 0
        for (i in 0 until data.size - 1) if (data[i] == NL) n++
        val s = IntArray(n)
        var j = 0
        if (data.isNotEmpty()) s[j++] = 0
        for (i in 0 until data.size - 1) if (data[i] == NL) s[j++] = i + 1
        starts = s
    }

    class Entry(val total: Int, val next: LinkedHashMap<String, Int>) {
        fun p(word: String): Float = (next[word] ?: 0).toFloat() / total.coerceAtLeast(1)
    }

    private val cache = object : LinkedHashMap<String, Entry?>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry?>?): Boolean = size > 96
    }

    val contexts: Int get() = starts.size

    @Synchronized
    fun get(context: String): Entry? {
        if (cache.containsKey(context)) return cache[context]
        val e = find(context)
        cache[context] = e
        return e
    }

    private fun compareAt(lineStart: Int, key: ByteArray): Int {
        var i = 0
        while (true) {
            val b = data.getOrNull(lineStart + i) ?: NL
            val lineEnded = b == TAB || b == NL
            if (i == key.size) return if (lineEnded) 0 else 1
            if (lineEnded) return -1
            val c = (b.toInt() and 0xff) - (key[i].toInt() and 0xff)
            if (c != 0) return c
            i++
        }
    }

    private fun find(context: String): Entry? {
        val key = context.toByteArray(Charsets.UTF_8)
        var lo = 0
        var hi = starts.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val c = compareAt(starts[mid], key)
            when {
                c < 0 -> lo = mid + 1
                c > 0 -> hi = mid - 1
                else -> return decode(starts[mid], key.size)
            }
        }
        return null
    }

    private fun decode(lineStart: Int, keyLen: Int): Entry? {
        var end = lineStart
        while (end < data.size && data[end] != NL) end++
        val line = String(data, lineStart + keyLen + 1, end - lineStart - keyLen - 1, Charsets.UTF_8)
        val tab = line.indexOf('\t')
        if (tab < 0) return null
        val total = line.substring(0, tab).toIntOrNull() ?: return null
        val map = LinkedHashMap<String, Int>()
        for (pair in line.substring(tab + 1).split(' ')) {
            val colon = pair.lastIndexOf(':')
            if (colon <= 0) continue
            map[pair.substring(0, colon)] = pair.substring(colon + 1).toIntOrNull() ?: continue
        }
        return Entry(total, map)
    }

    companion object {
        private const val NL: Byte = 10
        private const val TAB: Byte = 9
    }
}
