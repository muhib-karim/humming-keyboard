package com.humming.keyboard.predict

import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Humming's word prediction: the suggestion strip, autocorrect and next-word prediction.
 *
 * Every candidate gets one score, log P(word | the two previous words) - 5 x typing cost - a completion penalty:
 * - P(word | context) interpolates trigram, bigram and unigram probabilities ([Ngrams], [Lexicon]) with what this
 *   user has typed before ([UserModel]);
 * - the typing cost is a keyboard-aware weighted Damerau-Levenshtein distance from what was typed to the word
 *   (neighbouring keys, swapped letters, a dropped doubled letter or vowel and a missing apostrophe cost less);
 * - a completion (the word continues past what was typed) pays a small penalty that grows with the missing letters.
 * Autocorrect replaces a non-word on space only when the best candidate is close enough for the chosen strength and
 * clearly ahead of the runner-up; a real word is never replaced except for missing apostrophes ("dont" -> "don't").
 * Pure logic: covered by PredictorTest on the real assets.
 */
class Predictor(
    val lexicon: Lexicon,
    private val ngrams: Ngrams,
    val user: UserModel,
) {
    data class Settings(
        /** 0 = off, 1 = mild, 2 = normal (default), 3 = aggressive. */
        val autocorrectStrength: Int = 2,
        val allowOffensive: Boolean = false,
        val nextWord: Boolean = true,
        /** Candidates to rank (the strip shows 3). */
        val count: Int = 3,
        /** The user's text shortcuts, lowercase shortcut -> phrase ([TextShortcuts]). */
        val shortcuts: Map<String, String> = emptyMap(),
    )

    data class Candidate(val word: String, val score: Float, val cost: Float, val completion: Boolean)

    data class Result(
        val typed: String,
        /** Best first, cased for display. */
        val ranked: List<Candidate>,
        /** What space would commit instead of [typed], or null. */
        val autocorrect: String?,
        val typedIsWord: Boolean,
    )

    // ---- context ----

    /** The last two words before the cursor, lowercase; "<s>" marks the start of a sentence. */
    fun context(before: String): List<String> {
        val tail = if (before.length > 240) before.substring(before.length - 240) else before
        val ctx = ArrayList<String>()
        ctx.add(UserModel.SENTENCE_START)
        for (m in TOKEN.findAll(tail.replace('\u2019', '\''))) {
            val t = m.value
            if (t.length == 1 && t[0] in ".!?\n") {
                ctx.clear(); ctx.add(UserModel.SENTENCE_START)
            } else {
                ctx.add(t.lowercase(Locale.ROOT))
            }
        }
        // Text that ends mid-token (e.g. an email address or URL) gives no useful context.
        return if (ctx.size >= 2) ctx.subList(ctx.size - 2, ctx.size).toList() else ctx.toList()
    }

    fun isSentenceStart(ctx: List<String>): Boolean = ctx.size == 1 && ctx[0] == UserModel.SENTENCE_START

    // ---- language model ----

    fun lm(key: String, ctx: List<String>): Float {
        val i = lexicon.indexOf(key)
        val uses = user.unigram(key)
        var pu = if (i >= 0) exp(lexicon.logP(i).toDouble()) else 0.0
        if (i < 0 && uses > 0) pu = 2e-6 * min(uses, 10)
        if (pu == 0.0) pu = 1e-9
        val bi = ngrams.get(ctx.last())
        val tri = if (ctx.size >= 2) ngrams.get(ctx[0] + " " + ctx[1]) else null
        var p = when {
            tri != null -> 0.5 * tri.p(key) + 0.35 * (bi?.p(key) ?: 0f) + 0.15 * pu
            bi != null -> 0.75 * bi.p(key) + 0.25 * pu
            else -> pu
        }
        val ub = user.bigram(ctx.last(), key)
        if (ub > 0) p += 0.5 * ub / (ub + 2.0)
        return (ln(p) + 0.3 * ln(1.0 + uses)).toFloat()
    }

    // ---- suggestions ----

    fun suggest(before: String, typed: String, settings: Settings = Settings()): Result {
        val ctx = context(before)
        if (typed.isEmpty()) return Result("", if (settings.nextWord) nextWords(ctx, settings) else emptyList(), null, false)
        val t = typed.lowercase(Locale.ROOT).replace('\u2019', '\'')
        val n = t.length
        val cands = HashMap<String, Candidate>()
        fun add(key: String, cost: Float, completion: Boolean) {
            if (user.isBlocked(key) && key != t) return
            val li = lexicon.indexOf(key)
            if (li >= 0 && lexicon.isOffensive(li) && !settings.allowOffensive && key != t) return
            val penalty = if (completion) 0.6f + 0.1f * (key.length - n) else 0f
            val s = lm(key, ctx) - ALPHA * cost - penalty
            val old = cands[key]
            if (old == null || s > old.score) cands[key] = Candidate(key, s, cost, completion)
        }
        val typedIsWord = lexicon.isWord(typed) || user.unigram(t) >= UserModel.NEW_WORD_MIN_USES
        if (typedIsWord) add(t, 0f, false)
        CONTRACTIONS[t]?.let { add(it.lowercase(Locale.ROOT), 0f, false) }
        for (i in lexicon.completions(t, 60)) add(lexicon.key(i), 0f, true)
        for (w in user.wordsStartingWith(t)) add(w, 0f, true)
        for (e in listOfNotNull(if (ctx.size >= 2) ngrams.get(ctx[0] + " " + ctx[1]) else null, ngrams.get(ctx.last()))) {
            for (w in e.next.keys) if (w.length > n && w.startsWith(t)) add(w, 0f, true)
        }
        if (n >= 2 && t.all { it.isLetter() || it == '\'' }) {
            val mc = maxCost(n)
            // Only the buckets a plausible typo can reach (see plausibleStart): same or neighbouring first letter,
            // the first two letters swapped, or a dropped first letter.
            val firsts = LinkedHashSet<Char>().apply { add(t[0]); addAll(neighbours(t[0]).toList()); if (n > 1) add(t[1]) }
            val seen = HashSet<Int>()
            for (len in max(1, n - 2)..min(Lexicon.MAX_LEN, n + 2)) {
                seen.clear()
                val buckets = firsts.map { lexicon.ofLengthStarting(len, it) } + listOf(lexicon.ofLengthSecond(len, t[0]))
                for (bucket in buckets) for (i in bucket) {
                    if (lexicon.logP(i) < FUZZY_MIN_LOGP || !seen.add(i)) continue
                    val k = lexicon.key(i)
                    if (!plausibleStart(k, t)) continue
                    val c = cost(t, k, mc)
                    if (c <= mc) add(k, c, false)
                }
                for (w in user.learnedWordsOfLength(len)) {
                    if (!plausibleStart(w, t)) continue
                    val c = cost(t, w, mc)
                    if (c <= mc) add(w, c, false)
                }
            }
            if (n >= 3) {
                // A slip early in a longer word: "yesterdsy" is on its way to "yesterday's".
                val first = t[0]
                val starts = listOf(first) + neighbours(first).toList()
                for (len in n + 2..Lexicon.MAX_LEN) {
                    for (c0 in starts) for (i in lexicon.ofLengthStarting(len, c0)) {
                        if (lexicon.logP(i) < PREFIX_MIN_LOGP) continue
                        val k = lexicon.key(i)
                        val c = prefixCost(t, k, 1.2f)
                        if (c > 0f && c <= 1.0f) add(k, c, true)
                    }
                }
            }
        }
        val ranked = cands.values.sortedByDescending { it.score }
        val glued = before.isNotEmpty() && before.last() in NO_AUTOCORRECT_AFTER
        val auto = if (glued) null else autocorrect(typed, t, typedIsWord, ctx, ranked, settings)
        val shown = ranked.take(settings.count + 1).map { it.copy(word = displayFor(it.word, typed, false)) }
        // A text shortcut wins: its phrase is the best guess and what space commits.
        settings.shortcuts[t]?.let { phrase ->
            val withPhrase = listOf(Candidate(phrase, 999f, 0f, false)) + shown.filter { it.word != phrase }.take(settings.count)
            return Result(typed, withPhrase, phrase, typedIsWord)
        }
        return Result(typed, shown, auto, typedIsWord)
    }

    private fun autocorrect(
        typed: String, t: String, typedIsWord: Boolean, ctx: List<String>,
        ranked: List<Candidate>, settings: Settings,
    ): String? {
        val strength = settings.autocorrectStrength
        if (strength <= 0 || !t.all { it.isLetter() || it == '\'' }) return null
        if (typedIsWord) {
            if (strength >= 2) CONTRACTIONS[t]?.let { return matchCase(it, typed) }
            if (t == "i") return "I"
            return null
        }
        if (t.length < 2 || ranked.isEmpty()) return null
        // A capitalised unknown word mid-sentence is most likely a name: leave it (aggressive still corrects).
        if (typed[0].isUpperCase() && !isSentenceStart(ctx) && strength < 3) return null
        val limit = if (t.length <= 4) SHORT_LIMIT[strength - 1] else LONG_LIMIT[strength - 1]
        val best = ranked[0]
        val effective = if (best.completion) {
            if (best.word.length - t.length == 1 && t.length >= 3) best.cost + 0.6f else return null
        } else best.cost
        if (effective > limit) return null
        val gap = if (ranked.size > 1) best.score - ranked[1].score else 99f
        if (gap < MARGIN[strength - 1] && t.length < 5) return null
        return displayFor(best.word, typed, false)
    }

    private fun nextWords(ctx: List<String>, settings: Settings): List<Candidate> {
        val pool = LinkedHashSet<String>()
        if (ctx.size >= 2) ngrams.get(ctx[0] + " " + ctx[1])?.next?.keys?.take(12)?.let { pool.addAll(it) }
        ngrams.get(ctx.last())?.next?.keys?.take(12)?.let { pool.addAll(it) }
        pool.addAll(user.followers(ctx.last()))
        // An unseen context still gets the words that most often come next anywhere.
        if (pool.size < settings.count) pool.addAll(FALLBACK_NEXT)
        val start = isSentenceStart(ctx)
        return pool.asSequence()
            .filter { w ->
                val i = lexicon.indexOf(w)
                !user.isBlocked(w) && (i >= 0 || user.unigram(w) >= UserModel.NEW_WORD_MIN_USES) &&
                    (i < 0 || settings.allowOffensive || !lexicon.isOffensive(i))
            }
            .map { Candidate(it, lm(it, ctx), 0f, false) }
            .sortedByDescending { it.score }
            .take(settings.count)
            .map { it.copy(word = displayFor(it.word, "", start)) }
            .toList()
    }

    /** Dictionary casing ("I", "Monday", "iPhone"), then the typed word's shape (Capitalised / ALL CAPS). */
    fun displayFor(key: String, typed: String, sentenceStart: Boolean): String {
        val i = lexicon.indexOf(key)
        val base = CONTRACTIONS_BY_KEY[key] ?: if (i >= 0) lexicon.display(i) else user.displayOf(key)
        return matchCase(base, typed, sentenceStart)
    }

    companion object {
        const val ALPHA = 6.0f
        const val FUZZY_MIN_LOGP = -19.5f
        const val PREFIX_MIN_LOGP = -14.5f
        private val SHORT_LIMIT = floatArrayOf(0.8f, 1.0f, 1.4f)
        private val LONG_LIMIT = floatArrayOf(1.2f, 1.6f, 2.2f)
        private val MARGIN = floatArrayOf(1.5f, 0.7f, 0.2f)
        private val FALLBACK_NEXT = listOf("and", "the", "to", "a", "i", "in", "is", "for", "of", "it")
        /** Right after these the "word" is part of an address, a link, a tag or a number: never autocorrect it. */
        private const val NO_AUTOCORRECT_AFTER = "@./:#_\\0123456789"
        private val TOKEN = Regex("[\\p{L}]+(?:'[\\p{L}]+)*|[.!?\\n]")

        /**
         * A typo rarely changes the first letter to a far-away key: the word must start with the typed first letter,
         * a neighbour of it, or the first two letters swapped. Skips about five in six words before the distance.
         */
        fun plausibleStart(word: String, typed: String): Boolean {
            val w0 = word[0]
            val t0 = typed[0]
            return w0 == t0 || adjacent(w0, t0) || (word.length > 1 && word[1] == t0) || (typed.length > 1 && typed[1] == w0)
        }

        fun maxCost(n: Int): Float = min(2.2f, 0.4f + 0.4f * n)

        /** Common contractions typed without the apostrophe. Excludes real words ("well", "were", "ill", "its"...). */
        val CONTRACTIONS: Map<String, String> = listOf(
            "don't", "doesn't", "didn't", "isn't", "aren't", "wasn't", "weren't", "haven't", "hasn't", "hadn't",
            "wouldn't", "couldn't", "shouldn't", "mustn't", "needn't", "can't", "won't", "I'm", "I've", "I'll", "I'd",
            "you're", "you've", "you'll", "you'd", "they're", "they've", "they'll", "we've", "we're", "that's",
            "there's", "here's", "what's", "who's", "where's", "when's", "why's", "how's", "let's", "he's", "she's",
            "it'll", "that'll", "would've", "could've", "should've", "y'all", "ain't",
        ).associateBy { it.replace("'", "").lowercase(Locale.ROOT) }
            .filterKeys { it !in setOf("well", "were", "shell", "hell", "ill", "id", "its", "wed", "shed", "hed") }

        private val CONTRACTIONS_BY_KEY: Map<String, String> = CONTRACTIONS.values.associateBy { it.lowercase(Locale.ROOT) }

        fun matchCase(word: String, typed: String, sentenceStart: Boolean = false): String = when {
            typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } -> word.uppercase(Locale.ROOT)
            typed.isNotEmpty() && typed[0].isUpperCase() -> word.replaceFirstChar { it.titlecase(Locale.ROOT) }
            typed.isEmpty() && sentenceStart -> word.replaceFirstChar { it.titlecase(Locale.ROOT) }
            else -> word
        }

        private const val ROWS = "qwertyuiop|asdfghjkl|zxcvbnm"
        private val keyPos: Map<Char, Pair<Float, Int>> = buildMap {
            ROWS.split('|').forEachIndexed { r, row -> row.forEachIndexed { c, ch -> put(ch, Pair(c + 0.5f * r, r)) } }
        }

        fun isVowel(c: Char): Boolean = c == 'a' || c == 'e' || c == 'i' || c == 'o' || c == 'u'

        private val NEIGHBOURS: Map<Char, CharArray> by lazy {
            ('a'..'z').associateWith { c -> ('a'..'z').filter { it != c && adjacent(it, c) }.toCharArray() }
        }

        /** Letters next to [c] on the QWERTY layout (empty for letters off the main rows). */
        fun neighbours(c: Char): CharArray = NEIGHBOURS[c] ?: CharArray(0)

        fun adjacent(a: Char, b: Char): Boolean {
            val pa = keyPos[a] ?: return false
            val pb = keyPos[b] ?: return false
            return abs(pa.second - pb.second) <= 1 && abs(pa.first - pb.first) <= 1.1f
        }

        private val scratch = ThreadLocal.withInitial { FloatArray((Lexicon.MAX_LEN + 3) * (Lexicon.MAX_LEN + 3)) }

        /**
         * Weighted optimal-string-alignment table of typed [a] against [b] (flat, row-major, width b.length + 1),
         * in a per-thread scratch buffer (no allocation per word); null once a whole row exceeds [maxC].
         */
        private fun table(a: String, b: String, maxC: Float): FloatArray? {
            val n = a.length
            val m = b.length
            if (n > Lexicon.MAX_LEN + 2 || m > Lexicon.MAX_LEN + 2) return null
            val w = m + 1
            val d = scratch.get()!!
            for (i in 0..n) d[i * w] = i * 1.0f
            for (j in 0..m) d[j] = j * 0.8f
            for (i in 1..n) {
                var rowMin = 99f
                val ai = a[i - 1]
                for (j in 1..m) {
                    val bj = b[j - 1]
                    val sub = when {
                        ai == bj -> 0f
                        adjacent(ai, bj) -> 0.7f
                        ai == '\'' || bj == '\'' -> 0.9f
                        else -> 1.2f
                    }
                    // A dropped doubled letter, a dropped vowel or a missing apostrophe is the commonest omission.
                    val omitted = when {
                        bj == '\'' -> 0.3f
                        (j >= 2 && b[j - 1] == b[j - 2]) || isVowel(bj) -> 0.6f
                        else -> 0.8f
                    }
                    val extra = when {
                        i >= 2 && a[i - 1] == a[i - 2] -> 0.6f
                        adjacent(ai, bj) -> 0.8f
                        else -> 1.0f
                    }
                    var v = minOf(d[(i - 1) * w + j] + extra, d[i * w + j - 1] + omitted, d[(i - 1) * w + j - 1] + sub)
                    if (i > 1 && j > 1 && ai == b[j - 2] && a[i - 2] == bj) {
                        v = min(v, d[(i - 2) * w + j - 2] + if (isVowel(ai) && isVowel(a[i - 2])) 0.5f else 0.7f)
                    }
                    // One letter typed two places early or late ("tounge" for "tongue").
                    if (i > 2 && j > 2 && ai != bj) {
                        val left = a[i - 3] == bj && a[i - 2] == b[j - 3] && ai == b[j - 2]
                        val right = ai == b[j - 3] && a[i - 3] == b[j - 2] && a[i - 2] == bj
                        if (left || right) v = min(v, d[(i - 3) * w + j - 3] + 1.0f)
                    }
                    d[i * w + j] = v
                    if (v < rowMin) rowMin = v
                }
                if (rowMin > maxC) return null
            }
            return d
        }

        /** Cost of typing [a] for the whole word [b]; 99 when above [maxC]. */
        fun cost(a: String, b: String, maxC: Float = 2.2f): Float {
            if (abs(a.length - b.length) > 2) return 99f
            val d = table(a, b, maxC) ?: return 99f
            return d[a.length * (b.length + 1) + b.length]
        }

        /** Cheapest cost of [a] as the start of [b], where b continues past it; 99 when above [maxC]. */
        fun prefixCost(a: String, b: String, maxC: Float): Float {
            val stop = min(b.length - 1, a.length + 1)
            if (stop < 1) return 99f
            val bb = b.substring(0, stop)
            val d = table(a, bb, maxC) ?: return 99f
            var best = 99f
            for (j in max(1, a.length - 1)..stop) best = min(best, d[a.length * (stop + 1) + j])
            return best
        }
    }
}
