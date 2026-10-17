package com.humming.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BanglaPhoneticTest {
    private fun t(latin: String) = BanglaPhonetic.transliterate(latin)

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

    @Test fun inherentVowelAndDiphthongs() {
        assertEquals("বই", t("boi"))
        assertEquals("বউ", t("bou"))
        assertEquals("ঐ", t("oi"))
        assertEquals("বৈ", t("bOI"))
        assertEquals("আই", t("ai"))
        assertEquals("অনেক", t("onek"))
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
}
