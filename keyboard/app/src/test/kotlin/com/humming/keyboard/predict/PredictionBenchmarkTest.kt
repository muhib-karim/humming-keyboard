package com.humming.keyboard.predict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale
import kotlin.math.abs

/**
 * Held-out next-word benchmark: for every word of sentences no Humming 1.2 model was trained on (a 1 % hash split
 * of Tatoeba and Common Voice, src/test/resources/predict_bench), is the word among the three next-word
 * suggestions the strip would show after the words before it? "before" is the 1.1 predictor (English n-gram
 * tables only; no next-word tables for the other languages), "after" is 1.2 (the neural model for English, the new
 * tables for German, Spanish, French, Italian and Portuguese). The English 1.1 tables were counted from all of
 * Tatoeba, held-out sentences included, so the English "before" score on the Tatoeba half is flattering; the
 * Common Voice half is new to both.
 */
class PredictionBenchmarkTest {
    private val assets = "src/main/assets/predict/"
    private val word = Regex("[\\p{L}]+(?:'[\\p{L}]+)*")

    data class Score(val hits: Int, val n: Int, val bySource: Map<String, Pair<Int, Int>>) {
        val top3: Double get() = hits.toDouble() / n.coerceAtLeast(1)
        fun of(src: String): Double = bySource[src]?.let { it.first.toDouble() / it.second.coerceAtLeast(1) } ?: 0.0
    }

    private fun lexicon(lang: String) = Lexicon.parse(File("${assets}lexicon_$lang.tsv").readLines().asSequence())
    private fun neural(lang: String) = File("${assets}hlm_$lang.bin").takeIf { it.exists() }?.let { HummingLm(it.readBytes()) }

    private fun bench(p: Predictor, lang: String, settings: Predictor.Settings = Predictor.Settings()): Score {
        var hits = 0
        var n = 0
        val by = HashMap<String, IntArray>()
        for (line in File("src/test/resources/predict_bench/$lang.txt").readLines()) {
            val tab = line.indexOf('\t')
            if (tab < 0) continue
            val src = line.substring(0, tab)
            val toks = word.findAll(line.substring(tab + 1).replace('\u2019', '\'')).map { it.value.lowercase(Locale.ROOT) }.toList()
            for (i in toks.indices) {
                val before = if (i == 0) "" else toks.subList(0, i).joinToString(" ") + " "
                val top = p.suggest(before, "", settings).ranked.take(3).map { it.word.lowercase(Locale.ROOT) }
                val hit = toks[i] in top
                if (hit) hits++
                n++
                val c = by.getOrPut(src) { IntArray(2) }
                if (hit) c[0]++
                c[1]++
            }
        }
        return Score(hits, n, by.mapValues { it.value[0] to it.value[1] })
    }

    private fun report(lang: String, name: String, s: Score) = println(
        "HUMMING_BENCH lang=$lang model=$name top3=${"%.4f".format(Locale.ROOT, s.top3)} n=${s.n} " +
            "tatoeba=${"%.4f".format(Locale.ROOT, s.of("tatoeba"))} commonvoice=${"%.4f".format(Locale.ROOT, s.of("cv"))}",
    )

    @Test fun englishNeuralBeatsTheTables() {
        val lex = lexicon("en")
        val ngrams = Ngrams(File("${assets}ngrams_en.tsv").readBytes())
        val before = bench(Predictor(lex, ngrams, UserModel(null)), "en")
        report("en", "1.1-ngram", before)
        val nn = neural("en") ?: return
        val p = Predictor(lex, ngrams, UserModel(null), nn)
        val t0 = System.nanoTime()
        val after = bench(p, "en")
        val ms = (System.nanoTime() - t0) / 1e6 / after.n
        report("en", "1.2-neural", after)
        println("HUMMING_BENCH_MS_PER_NEXT_WORD=${"%.2f".format(Locale.ROOT, ms)}")
        val nnOnly = bench(p, "en", Predictor.Settings(neuralWeight = 100))
        report("en", "1.2-neural-only", nnOnly)
        assertTrue("1.2 ${after.top3} vs 1.1 ${before.top3}", after.top3 >= before.top3 + 0.02)
        assertTrue("unseen sentences improve too", after.of("cv") > before.of("cv"))
    }

    @Test fun newLanguagesPredictTheNextWord() {
        for (lang in listOf("de", "es", "fr", "it", "pt")) {
            val lex = lexicon(lang)
            val before = bench(Predictor(lex, Ngrams(ByteArray(0)), UserModel(null)), lang)
            val after = bench(Predictor(lex, Ngrams(File("${assets}ngrams_$lang.tsv").readBytes()), UserModel(null)), lang)
            report(lang, "1.1-none", before)
            report(lang, "1.2-ngram", after)
            assertTrue("$lang: ${after.top3} vs ${before.top3}", after.top3 >= before.top3 + 0.08)
        }
    }

    @Test fun neuralInferenceMatchesTheTrainer() {
        val nn = neural("en") ?: return
        val ref = File("src/test/resources/predict_bench/nn_ref.tsv").takeIf { it.exists() } ?: return
        var checked = 0
        for (line in ref.readLines().take(120)) {
            val (prefix, tops) = line.split('\t').let { it[0] to it[1] }
            val s = nn.distribution(if (prefix.isEmpty()) emptyList() else prefix.split(' '))
            val expected = tops.split(' ').map { it.substringBeforeLast(':') to it.substringAfterLast(':').toFloat() }
            // Same words in the same order (ties aside) and the same log-probabilities to within rounding.
            assertEquals("after \"$prefix\"", expected[0].first, nn.word(s.logP.indices.maxByOrNull { s.logP[it] }!!))
            for ((w, lp) in expected) assertTrue("$w after \"$prefix\": ${s.logP[nn.id(w)]} vs $lp", abs(s.logP[nn.id(w)] - lp) < 2e-3f)
            checked++
        }
        assertTrue(checked > 50)
    }

    @Test fun tablesAreSortedForBinarySearch() {
        for (lang in PredictionEngine.LANGUAGES) {
            val keys = File("${assets}ngrams_$lang.tsv").readLines().map { it.substringBefore('\t').toByteArray(Charsets.UTF_8) }
            assertTrue("$lang has ${keys.size} contexts", keys.size > 20_000)
            for (i in 1 until keys.size) {
                val a = keys[i - 1]
                val b = keys[i]
                var c = 0
                for (k in 0 until minOf(a.size, b.size)) {
                    c = (a[k].toInt() and 0xff) - (b[k].toInt() and 0xff)
                    if (c != 0) break
                }
                if (c == 0) c = a.size - b.size
                assertTrue("$lang line $i out of order", c < 0)
            }
        }
    }
}
