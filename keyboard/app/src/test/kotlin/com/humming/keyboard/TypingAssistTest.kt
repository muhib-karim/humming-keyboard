package com.humming.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TypingAssistTest {
    private val assist by lazy {
        TypingAssist(TypingAssist.parse(File("src/main/assets/words_en.txt").readLines().asSequence()))
    }

    @Test fun correctsCommonTypos() {
        assertEquals("the", assist.autocorrect("teh"))
        assertEquals("receive", assist.autocorrect("recieve"))
        assertEquals("because", assist.autocorrect("becuase"))
        assertEquals("until", assist.autocorrect("untill"))
        assertEquals("with", assist.autocorrect("wtih"))
    }

    @Test fun keepsRealWordsAndCase() {
        assertNull(assist.autocorrect("hello"))
        assertNull(assist.autocorrect("the"))
        assertNull(assist.autocorrect("Humming", listOf("Humming")))
        assertEquals("The", assist.autocorrect("Teh"))
        assertEquals("I", TypingAssist.matchCase("I", "i"))
    }

    @Test fun completesPrefixes() {
        val c = assist.completions("tomo").map { it.word }
        assertTrue(c.toString(), "tomorrow" in c)
    }

    @Test fun adjacencyIsKeyboardAware() {
        assertTrue(TypingAssist.adjacent('q', 'w'))
        assertTrue(!TypingAssist.adjacent('q', 'p'))
        assertTrue(TypingAssist.cost("teh", "the") < TypingAssist.cost("tah", "the") + 0.01f)
    }

    @Test fun nextWordsLearnFromText() {
        val nw = NextWords(null)
        nw.learnText("see you soon. See you later, see you soon")
        assertEquals(listOf("you"), nw.predict("see"))
        assertEquals("soon", nw.predict("you").first())
        assertTrue(nw.predict("soon").isEmpty())
        assertEquals("you", NextWords.previousWord("thank you see you "))
        assertEquals("", NextWords.previousWord("thank yo"))
        assertEquals("", NextWords.previousWord("done. "))
    }

    // The head-to-head protocol's fixed lists (bench/h2h/h2h.py MISSPELLINGS / NEXT_WORD), checked on the logic.
    private val h2hMisspellings = listOf(
        "teh" to "the", "recieve" to "receive", "adress" to "address", "wierd" to "weird", "tommorow" to "tomorrow",
        "definately" to "definitely", "seperate" to "separate", "becuase" to "because", "freind" to "friend",
        "untill" to "until", "goverment" to "government", "occured" to "occurred", "beleive" to "believe",
        "thier" to "their", "wich" to "which", "realy" to "really", "happend" to "happened", "tounge" to "tongue",
        "calender" to "calendar", "acommodate" to "accommodate",
    )
    private val h2hNext = listOf(
        "see you" to listOf("soon", "later", "tomorrow", "there"), "thank" to listOf("you"), "how are" to listOf("you"),
        "good" to listOf("morning", "night", "luck"), "i love" to listOf("you", "it"), "on my" to listOf("way"),
        "what time" to listOf("is", "are", "do"), "let me" to listOf("know"), "happy" to listOf("birthday"),
        "talk to you" to listOf("later", "soon"),
    )

    @Test fun h2hMisspellingsAllFixedOnSpace() {
        val results = h2hMisspellings.map { (typo, want) -> Triple(typo, want, assist.autocorrect(typo)) }
        val missed = results.filter { it.third != it.second }
        println("H2H_AUTOCORRECT ${results.size - missed.size}/${results.size} missed=$missed")
        assertTrue("missed: $missed", missed.isEmpty())
    }

    @Test fun h2hNextWordColdFromSeed() {
        val seed = NextWords.parseSeed(File("src/main/assets/next_words_seed.tsv").readLines().asSequence())
        val nw = NextWords(null, seed)
        val results = h2hNext.map { (prefix, accept) ->
            val prev = NextWords.previousWord("$prefix ")
            Triple(prefix, accept, nw.predict(prev, 3))
        }
        val missed = results.filter { r -> r.third.none { it in r.second } }
        println("H2H_NEXTWORD ${results.size - missed.size}/${results.size} missed=$missed")
        assertTrue("missed: $missed", missed.isEmpty())
    }

    @Test fun learnedPairsRankAboveSeed() {
        val nw = NextWords(null, mapOf("see" to listOf("it", "the")))
        assertEquals(listOf("it", "the"), nw.predict("see"))
        nw.learn("see", "you")
        assertEquals(listOf("you", "it", "the"), nw.predict("see"))
    }
}
