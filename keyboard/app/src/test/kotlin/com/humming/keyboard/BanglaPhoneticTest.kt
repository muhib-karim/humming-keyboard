package com.humming.keyboard

import dev.patrickgold.florisboard.ime.core.Subtype
import dev.patrickgold.florisboard.ime.editor.EditorContent
import dev.patrickgold.florisboard.ime.editor.EditorRange
import dev.patrickgold.florisboard.ime.nlp.LiteralWordCandidate
import dev.patrickgold.florisboard.ime.nlp.WordSuggestionCandidate
import dev.patrickgold.florisboard.ime.nlp.latin.LatinLanguageProvider
import dev.patrickgold.florisboard.lib.FlorisLocale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BanglaPhoneticTest {
    private fun t(latin: String) = BanglaPhonetic.transliterate(latin)

    private val provider by lazy {
        LatinLanguageProvider(lazy { error("Bangla suggestions must not load Android context") })
    }

    private fun suggest(typed: String, composing: Boolean) = runBlocking {
        val text = "বল $typed"
        val word = EditorRange(3, text.length)
        provider.suggest(
            subtype = Subtype.DEFAULT.copy(primaryLocale = FlorisLocale.from("bn")),
            content = EditorContent(text, 0, EditorRange.cursor(text.length),
                if (composing) word else EditorRange.Unspecified, word),
            maxCandidateCount = 3,
            allowPossiblyOffensive = false,
            isPrivateSession = true,
        )
    }

    @Test fun everydayWords() {
        assertEquals("আমি", t("ami"))
        assertEquals("তুমি", t("tumi"))
        assertEquals("কেমন", t("kemon"))
        assertEquals("আমার", t("amar"))
        assertEquals("এক", t("ek"))
        assertEquals("ভালবাশি", t("bhalobashi"))
    }

    @Test fun anusvaraAndNgaBeforeAVowel() {
        assertEquals("বাংলা", t("bangla"))
        assertEquals("বাংলাদেশ", t("bangladesh"))
        assertEquals("বাঙালি", t("bangali"))
    }

    @Test fun consonantsInARowJoinWithHasanta() {
        assertEquals("কর্তে", t("korte")) // reph
        assertEquals("প্রেম", t("prem")) // ra-phala
        assertEquals("ব্যবহার", t("byobohar")) // ya-phala
        assertEquals("স্বামী", t("swamI")) // ba-phala
        assertEquals("বিজ্ঞান", t("biggan"))
        assertEquals("ক্ষমা", t("kkhoma"))
    }

    @Test fun joiningResetsAfterVowelsSeparatorsAndPhalas() {
        assertEquals("ক্ত", t("kt"))
        assertEquals("ব্ঘ", t("bgh"))
        assertEquals("কা ত", t("ka t"))
        assertEquals("কত", t("kot"))
        assertEquals("ব্যক", t("byk"))
        assertEquals("স্বক", t("swk"))
    }

    @Test fun inherentVowelAndDiphthongs() {
        assertEquals("বই", t("boi"))
        assertEquals("বউ", t("bou"))
        assertEquals("ঐ", t("oi"))
        assertEquals("বৈ", t("bOI"))
        assertEquals("আই", t("ai"))
        assertEquals("অনেক", t("onek"))
    }

    @Test fun standaloneWBeforeAVowelUsesAvroGlide() {
        assertEquals("ওয়া", t("wa"))
        assertEquals("ওয়া", t("Wa"))
        assertEquals("ওয়ি", t("wi"))
        assertEquals("ওয়ে", t("we"))
        assertEquals("ওয়ী", t("wI"))
        assertEquals("ওয়ৈ", t("wOI"))
        assertEquals("হাওয়া", t("hawa"))
        assertEquals("ও", t("w"))
        assertEquals("ওক", t("wk"))
        assertEquals("স্বামী", t("swamI"))
    }

    @Test fun capitalsCarryAvroMeaningOthersFoldToLowercase() {
        assertEquals("টাকা", t("Taka"))
        assertEquals("ডাল", t("Dal"))
        assertEquals("কারণ", t("karoN"))
        assertEquals("আমি", t("Ami"))
        assertEquals("বাড়ি", t("baRi"))
    }

    @Test fun digitsAndUnknownPassThrough() {
        assertEquals("১২৩", t("123"))
        assertEquals("আমি, তুমি", t("ami, tumi"))
        assertEquals("", t(""))
    }

    @Test fun onlyPlainLatinWordsAreConverted() {
        assertTrue(BanglaPhonetic.isPhoneticInput("ami"))
        assertTrue(BanglaPhonetic.isPhoneticInput("Taka"))
        assertFalse(BanglaPhonetic.isPhoneticInput("আমি"))
        assertFalse(BanglaPhonetic.isPhoneticInput("a1"))
        assertFalse(BanglaPhonetic.isPhoneticInput(""))
        assertFalse(BanglaPhonetic.isPhoneticInput("don't"))
    }

    @Test fun providerOffersBanglaForAutoCommitAndKeepsLatinAlternative() {
        for (composing in listOf(true, false)) {
            for ((typed, expected) in listOf("ami" to "আমি", "wa" to "ওয়া")) {
                val candidates = suggest(typed, composing)
                assertEquals(listOf(expected, typed), candidates.map { it.text.toString() })
                val bangla = candidates.first()
                assertTrue(bangla is WordSuggestionCandidate)
                assertTrue(bangla.isEligibleForAutoCommit)
                assertFalse(bangla.isEligibleForUserRemoval)
                assertSame(provider, bangla.sourceProvider)
                assertTrue(candidates.last() is LiteralWordCandidate)
                assertFalse(candidates.last().isEligibleForAutoCommit)
            }
        }
    }

    @Test fun providerRejectsMixedBanglaDigitsAndNonLetters() {
        for (composing in listOf(true, false)) {
            for (typed in listOf("amiবাংলা", "আমি", "a1", "123", "১২৩", "don't", "ami!", "")) {
                assertTrue("typed=$typed composing=$composing", suggest(typed, composing).isEmpty())
            }
        }
    }
}
