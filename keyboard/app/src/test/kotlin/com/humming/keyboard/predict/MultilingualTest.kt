package com.humming.keyboard.predict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MultilingualTest {
    private fun load(lang: String) = Predictor(
        Lexicon.parse(File("src/main/assets/predict/lexicon_$lang.tsv").readLines().asSequence()),
        Ngrams(ByteArray(0)),
        UserModel(null),
    )

    @Test fun subtypeLocaleToDictionary() {
        assertEquals("en", PredictionEngine.languageFor("en"))
        assertEquals("en", PredictionEngine.languageFor("EN"))
        assertEquals("de", PredictionEngine.languageFor("de-CH"))
        assertEquals("pt", PredictionEngine.languageFor("pt_BR"))
        assertEquals("fr", PredictionEngine.languageFor("fr"))
        assertNull("no English suggestions on a Japanese keyboard", PredictionEngine.languageFor("ja"))
    }

    @Test fun everyLanguageShipsADictionary() {
        for (lang in PredictionEngine.LANGUAGES) {
            val lex = Lexicon.parse(File("src/main/assets/predict/lexicon_$lang.tsv").readLines().asSequence())
            assertTrue("$lang has ${lex.size} words", lex.size >= 50_000)
        }
    }

    @Test fun germanSuggestsGerman() {
        val p = load("de")
        assertTrue(p.lexicon.isWord("und") && p.lexicon.isWord("möchte"))
        val r = p.suggest("Ich ", "hab")
        assertTrue(r.ranked.map { it.word }.toString(), r.ranked.any { it.word.lowercase().startsWith("hab") && it.word.length > 3 })
        assertNull("a real German word is never corrected", p.suggest("Das ", "und").autocorrect)
    }

    @Test fun spanishCompletesSpanish() {
        val p = load("es")
        val r = p.suggest("Muchas ", "graci")
        assertTrue(r.ranked.map { it.word }.toString(), r.ranked.any { it.word == "gracias" })
    }

    @Test fun emptyNgramsAreSafe() {
        val p = load("fr")
        val r = p.suggest("", "")
        assertTrue(r.ranked.size <= 5)
    }
}
