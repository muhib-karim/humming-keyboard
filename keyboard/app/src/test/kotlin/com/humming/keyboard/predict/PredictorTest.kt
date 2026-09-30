package com.humming.keyboard.predict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PredictorTest {
    private val lexicon by lazy { Lexicon.parse(File("src/main/assets/predict/lexicon_en.tsv").readLines().asSequence()) }
    private val ngrams by lazy { Ngrams(File("src/main/assets/predict/ngrams_en.tsv").readBytes()) }
    private fun fresh(file: File? = null) = Predictor(lexicon, ngrams, UserModel(file))
    private val p by lazy { fresh() }

    private fun words(before: String, typed: String) = p.suggest(before, typed).ranked.map { it.word }
    private fun auto(before: String, typed: String, strength: Int = 2) =
        p.suggest(before, typed, Predictor.Settings(autocorrectStrength = strength)).autocorrect

    @Test fun dictionaryIsRealAndRanked() {
        assertTrue(lexicon.size > 150_000)
        assertTrue(lexicon.isWord("great") && lexicon.isWord("tomorrow") && lexicon.isWord("don't"))
        assertTrue("GRE is listed but gre is not a word", !lexicon.isWord("gre"))
        assertTrue(lexicon.isWord("ok") && lexicon.isWord("OK"))
        val great = lexicon.logP(lexicon.indexOf("great"))
        val grew = lexicon.logP(lexicon.indexOf("grew"))
        assertTrue(great > grew)
        assertTrue(ngrams.contexts > 50_000)
    }

    @Test fun heroScreenshotCaseFixed() {
        // M5 showed "gre | grew | great" with grew as the autocorrect. The best guess after "Sounds" is "great".
        val r = p.suggest("Sounds ", "gre")
        assertEquals("great", r.ranked.first().word)
        assertTrue("no autocorrect to grew", r.autocorrect != "grew")
        val slots = StripOrder.arrange(r)
        assertEquals(3, slots.size)
        assertEquals("great", slots[1].text)
        assertEquals(StripOrder.Kind.LITERAL, slots[0].kind)
    }

    @Test fun correctsCommonTypos() {
        val cases = mapOf(
            "teh" to "the", "recieve" to "receive", "becuase" to "because", "thsi" to "this",
            "adress" to "address", "wierd" to "weird", "definately" to "definitely", "hapy" to "happy",
            "goign" to "going", "tommorow" to "tomorrow", "amazinf" to "amazing", "hlep" to "help", "yhe" to "the",
        )
        for ((typo, want) in cases) assertEquals(typo, want, auto("so ", typo))
        // "so realy" could be "so early"; after "I" it is clearly "really".
        assertEquals("really", auto("I ", "realy"))
        assertEquals("The", auto("", "Teh"))
    }

    @Test fun contextPicksTheRightCorrection() {
        assertEquals("weather", auto("the ", "wether"))
        assertEquals("meet", auto("Let's ", "mee"))
        assertEquals("and", auto("cats ", "abd"))
    }

    @Test fun apostrophesAndI() {
        assertEquals("don't", auto("I ", "dont"))
        assertEquals("I'm", auto("", "im"))
        assertEquals("can't", auto("I ", "cant"))
        assertEquals("I", auto("then ", "i"))
        assertNull("mild keeps cant", auto("I ", "cant", strength = 1))
    }

    @Test fun realWordsNamesAndOffAreLeftAlone() {
        assertNull(auto("I got it ", "form"))
        assertNull(auto("it's ", "ok"))
        assertNull(auto("Hi ", "Omar"))
        assertNull(auto("", "teh", strength = 0))
        assertNull("no autocorrect inside an address", auto("mail me at bob@", "gmial"))
        assertEquals("from", words("I got it ", "form").first())
    }

    @Test fun nextWordPrediction() {
        assertEquals(listOf("be", "do"), words("I'm going to ", "").take(2))
        assertEquals("you", words("how are ", "").first())
        assertEquals("way", words("I'm on my ", "").first())
        assertTrue(words("see you ", "").isNotEmpty())
        assertEquals("I", words("", "").first())
        assertTrue("an unseen context still predicts", words("xylophonist ", "").size == 3)
        val strip = StripOrder.arrange(p.suggest("how are ", ""))
        assertEquals("you", strip[1].text)
    }

    @Test fun completionsFollowContext() {
        assertEquals("later", words("see you ", "l").first())
        assertEquals("you", words("I love ", "yo").first())
        assertEquals("time", words("what ", "tim").first())
    }

    @Test fun learnsLocallyAndForgets() {
        val f = File.createTempFile("predict", ".tsv")
        f.delete()
        val q = fresh(f)
        assertTrue(q.suggest("I love ", "Hummingk").ranked.none { it.word == "Hummingk" })
        q.user.learn("love", "Hummingk")
        q.user.learn("love", "Hummingk")
        assertEquals("Hummingk", q.suggest("I love ", "Hummin").ranked.first().word)
        assertNull("a learned word is a real word", q.suggest("I love ", "hummingk").autocorrect)
        repeat(3) { q.user.learn("you", "soon") }
        assertEquals("soon", q.suggest("see you ", "").ranked.first().word)
        q.user.save()
        val reloaded = fresh(f)
        assertEquals("soon", reloaded.suggest("see you ", "").ranked.first().word)
        reloaded.user.block("soon")
        assertTrue(reloaded.suggest("see you ", "").ranked.none { it.word == "soon" })
        reloaded.user.clear()
        assertEquals(0, fresh(f).user.stats().first)
        f.delete()
    }

    @Test fun offensiveWordsNeedPermission() {
        val i = (0 until lexicon.size).first { lexicon.isOffensive(it) && lexicon.key(it).length >= 4 }
        val w = lexicon.key(i)
        val prefix = w.substring(0, w.length - 1)
        assertTrue(p.suggest("", prefix).ranked.none { it.word.equals(w, true) })
    }

    @Test fun casing() {
        assertEquals("Great", p.suggest("", "Gre").ranked.first().word)
        assertEquals("GREAT", p.suggest("so ", "GRE").ranked.first().word)
        assertEquals("I'm", Predictor.matchCase("I'm", "im"))
    }

    @Test fun keyboardDistance() {
        assertTrue(Predictor.adjacent('q', 'w'))
        assertTrue(!Predictor.adjacent('q', 'p'))
        assertTrue(Predictor.cost("teh", "the") < Predictor.cost("tah", "the") + 0.01f)
        assertTrue(Predictor.cost("dont", "don't") < 0.5f)
        assertTrue(Predictor.prefixCost("grwat", "greatly", 1.2f) <= 1.0f)
    }

    @Test fun fastEnough() {
        repeat(3) { p.suggest("warm ", "upp"); p.suggest("so ", "definately") }
        // Best of five passes: one pass on a shared or memory-tight runner can include a GC pause or another test
        // task's CPU burst (a Lightning build measured 130 ms in a single pass; GitHub CI measures 10-20 ms).
        val ms = (1..5).minOf {
            val t0 = System.nanoTime()
            for (w in listOf("gre", "recieve", "tommorow", "definately", "amazinf", "helo", "yhe")) p.suggest("so ", w)
            (System.nanoTime() - t0) / 1e6 / 7
        }
        println("PREDICT_MS_PER_KEYSTROKE=$ms")
        assertTrue("mean ${ms}ms per keystroke", ms < 40)
    }

    @Test fun ngramFileIsSortedForBinarySearch() {
        val lines = File("src/main/assets/predict/ngrams_en.tsv").readLines()
        val keys = lines.map { it.substringBefore('\t').toByteArray(Charsets.UTF_8) }
        for (i in 1 until keys.size) {
            val a = keys[i - 1]
            val b = keys[i]
            var c = 0
            for (k in 0 until minOf(a.size, b.size)) {
                c = (a[k].toInt() and 0xff) - (b[k].toInt() and 0xff)
                if (c != 0) break
            }
            if (c == 0) c = a.size - b.size
            assertTrue("line $i out of order", c < 0)
        }
    }

    @Test fun chatPhrasesPredictLikeAMessagingKeyboard() {
        // Tatoeba alone gave "see you" -> again | in | at; the messaging boost makes it read like a chat keyboard.
        assertEquals("soon", words("See you ", "").first())
        assertTrue(words("See you ", "").take(3).contains("later"))
        assertTrue(words("I'm running ", "").take(3).contains("late"))
        assertTrue(words("Please let me ", "").take(3).contains("know"))
        assertTrue(words("Thanks, no ", "").take(3).contains("worries"))
    }
}
