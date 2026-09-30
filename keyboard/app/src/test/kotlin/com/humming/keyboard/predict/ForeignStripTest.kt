package com.humming.keyboard.predict

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 * 1.2 audit finding: the AOSP word lists for the other languages carry English words with high frequencies, so
 * after an unknown word ("Eu nao ", typed without the accent) the Portuguese strip offered "a | for | to": the
 * unseen-context fallback was an English list. It is now the language's own most frequent words, and
 * keyboard/lm/demote_english.py lowers the English words in those lists.
 */
class ForeignStripTest {
    private val assets = "src/main/assets/predict/"
    // Not words in German, Spanish, French, Italian or Portuguese ("in", "on", "or" are, so they are not listed).
    private val english = setOf("for", "to", "is", "at", "it", "and", "up", "be", "are", "out", "the", "of")

    @Test fun otherLanguagesDoNotSuggestEnglishFunctionWords() {
        val cases = mapOf(
            "pt" to listOf("Eu nao ", "Xyzzy ", ""),
            "de" to listOf("Ich habe ", "Xyzzy ", ""),
            "es" to listOf("Muchas ", "Xyzzy ", ""),
            "fr" to listOf("Je suis ", "Xyzzy ", ""),
            "it" to listOf("Non ho ", "Xyzzy ", ""),
        )
        for ((lang, befores) in cases) {
            val lex = Lexicon.parse(File("${assets}lexicon_$lang.tsv").readLines().asSequence())
            val p = Predictor(lex, Ngrams(File("${assets}ngrams_$lang.tsv").readBytes()), UserModel(null))
            for (b in befores) {
                val top = p.suggest(b, "").ranked.take(3).map { it.word.lowercase(Locale.ROOT) }
                assertTrue("$lang after \"$b\": $top", top.none { it in english })
            }
        }
    }
}
