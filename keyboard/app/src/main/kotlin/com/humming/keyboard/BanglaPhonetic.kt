package com.humming.keyboard

/**
 * Humming: Bangla phonetic typing (romanised Bangla in, Bangla script out), the way Avro and Ridmik users type:
 * "ami tomake bhalobashi" → "আমি তোমাকে ভালবাশি". Pure Kotlin, no Android types (BanglaPhoneticTest).
 *
 * Written for Humming from the common Avro-style conventions, not copied from Avro's rule data:
 * - longest match first ("kkh" before "kh" before "k");
 * - a vowel after a consonant is its sign (kar), elsewhere the full letter; "o" after a consonant is the inherent
 *   vowel and writes nothing;
 * - two joining consonants in a row get a hasanta, which gives reph ("rk" → "র্ক"), ra-phala ("pr" → "প্র"),
 *   ya-phala ("by" → "ব্য") and ba-phala ("sw" → "স্ব");
 * - capitals carry Avro's meaning (T D N R S and Th Dh Rh Sh Ng NG for the other consonants, I U O OI OU for long or
 *   full vowels); any other capital is read as its lowercase letter.
 * The keyboard shows the result as the auto-commit suggestion on a Bengali subtype with a Latin layout
 * (LatinLanguageProvider), so space or punctuation types the Bangla word.
 */
object BanglaPhonetic {
    private const val HASANTA = "্"

    private class Consonant(val text: String, val joins: Boolean = true)
    private class Vowel(val full: String, val sign: String)

    private val CONSONANTS: Map<String, Consonant> = mapOf(
        "kkh" to Consonant("ক্ষ"), "kh" to Consonant("খ"), "k" to Consonant("ক"), "q" to Consonant("ক"),
        "x" to Consonant("ক্স"),
        "gg" to Consonant("জ্ঞ"), "gh" to Consonant("ঘ"), "g" to Consonant("গ"),
        "Ng" to Consonant("ঙ"), "NG" to Consonant("ঞ"),
        "chh" to Consonant("ছ"), "ch" to Consonant("চ"), "c" to Consonant("চ"),
        "jh" to Consonant("ঝ"), "j" to Consonant("জ"),
        "Th" to Consonant("ঠ"), "T" to Consonant("ট"), "Dh" to Consonant("ঢ"), "D" to Consonant("ড"),
        "N" to Consonant("ণ"),
        "th" to Consonant("থ"), "t" to Consonant("ত"), "dh" to Consonant("ধ"), "d" to Consonant("দ"),
        "n" to Consonant("ন"),
        "ph" to Consonant("ফ"), "f" to Consonant("ফ"), "p" to Consonant("প"),
        "bh" to Consonant("ভ"), "v" to Consonant("ভ"), "b" to Consonant("ব"), "m" to Consonant("ম"),
        "z" to Consonant("য"), "r" to Consonant("র"), "l" to Consonant("ল"),
        "Rh" to Consonant("ঢ়"), "R" to Consonant("ড়"),
        "Sh" to Consonant("ষ"), "sh" to Consonant("শ"), "S" to Consonant("শ"), "s" to Consonant("স"),
        "h" to Consonant("হ"),
    )

    private val VOWELS: Map<String, Vowel> = mapOf(
        "rri" to Vowel("ঋ", "ৃ"),
        "OI" to Vowel("ঐ", "ৈ"), "OU" to Vowel("ঔ", "ৌ"),
        "oo" to Vowel("উ", "ু"), "ee" to Vowel("ঈ", "ী"),
        "a" to Vowel("আ", "া"), "i" to Vowel("ই", "ি"), "I" to Vowel("ঈ", "ী"),
        "u" to Vowel("উ", "ু"), "U" to Vowel("ঊ", "ূ"), "e" to Vowel("এ", "ে"),
        "O" to Vowel("ও", "ো"), "o" to Vowel("অ", ""),
    )

    /** At the start of a word only: "oi" / "ou" are the full letters (after a consonant they read as "o" + "i"). */
    private val WORD_START = mapOf("oi" to "ঐ", "ou" to "ঔ")

    private const val CAPITALS = "OIUTDNRS"
    private const val VOWEL_LETTERS = "aeiouAEIOU"
    private const val MAX_KEY = 3

    /** True for a word the keyboard should offer in Bangla: plain Latin letters only. */
    fun isPhoneticInput(word: String): Boolean =
        word.isNotEmpty() && word.all { it in 'a'..'z' || it in 'A'..'Z' }

    /** Capitals without a phonetic meaning are read as lowercase ("Ami" = "ami"); "NG" keeps its G. */
    private fun normalize(input: String): String {
        val sb = StringBuilder(input.length)
        for ((i, c) in input.withIndex()) {
            val keep = c !in 'A'..'Z' || c in CAPITALS || (c == 'G' && i > 0 && input[i - 1] == 'N')
            sb.append(if (keep) c else c.lowercaseChar())
        }
        return sb.toString()
    }

    fun transliterate(latin: String): String {
        val s = normalize(latin)
        val out = StringBuilder()
        // afterConsonant: the next vowel is written as its sign. joinable: the next consonant needs a hasanta first.
        var afterConsonant = false
        var joinable = false
        var wordStart = true
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c in '0'..'9') {
                out.append('০' + (c - '0'))
                afterConsonant = false; joinable = false; wordStart = true
                i++
                continue
            }
            if (c !in 'a'..'z' && c !in 'A'..'Z') {
                out.append(c)
                afterConsonant = false; joinable = false; wordStart = true
                i++
                continue
            }
            if (wordStart) {
                val two = s.substring(i, minOf(i + 2, s.length))
                val full = WORD_START[two]
                if (full != null) {
                    out.append(full)
                    afterConsonant = false; joinable = false; wordStart = false
                    i += 2
                    continue
                }
            }
            // "ng": a nasal "ং" before a consonant or at the end, the letter "ঙ" before a vowel ("bangali" → "বাঙালি").
            if (s.startsWith("ng", i)) {
                val next = s.getOrNull(i + 2)
                if (next != null && next in VOWEL_LETTERS) {
                    if (joinable) out.append(HASANTA)
                    out.append("ঙ")
                    afterConsonant = true; joinable = true
                } else {
                    out.append("ং")
                    afterConsonant = false; joinable = false
                }
                wordStart = false
                i += 2
                continue
            }
            // "y" and "w" after a consonant are its ya-phala / ba-phala; elsewhere "য়" and "ও".
            if (c == 'y' || c == 'w') {
                if (joinable) {
                    out.append(HASANTA).append(if (c == 'y') "য" else "ব")
                    afterConsonant = true; joinable = false
                } else if (c == 'y') {
                    out.append("য়")
                    afterConsonant = true; joinable = false
                } else {
                    out.append("ও")
                    afterConsonant = false; joinable = false
                }
                wordStart = false
                i++
                continue
            }
            var matched = false
            for (len in minOf(MAX_KEY, s.length - i) downTo 1) {
                val key = s.substring(i, i + len)
                val v = VOWELS[key]
                if (v != null) {
                    out.append(if (afterConsonant) v.sign else v.full)
                    afterConsonant = false; joinable = false
                    matched = true
                } else {
                    val k = CONSONANTS[key] ?: continue
                    if (joinable) out.append(HASANTA)
                    out.append(k.text)
                    afterConsonant = true; joinable = k.joins
                    matched = true
                }
                wordStart = false
                i += len
                break
            }
            if (!matched) {
                out.append(c)
                afterConsonant = false; joinable = false; wordStart = false
                i++
            }
        }
        return out.toString()
    }
}
