package com.humming.keyboard

import com.humming.keyboard.predict.Lexicon
import com.humming.keyboard.predict.Ngrams
import com.humming.keyboard.predict.Predictor
import com.humming.keyboard.predict.StripOrder
import com.humming.keyboard.predict.TextShortcuts
import com.humming.keyboard.predict.UserModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SettingsTest {
    @Test fun settingsSearchFindsTheRightScreen() {
        fun first(q: String) = SettingsIndex.search(q).first()
        assertEquals(SettingsIndex.Screen.TYPING, first("autocorrect").screen)
        assertEquals(SettingsIndex.Screen.KEYBOARD, first("keyboard height").screen)
        assertEquals(SettingsIndex.Screen.VOICE, first("voice model").screen)
        assertEquals(SettingsIndex.Screen.THEME, first("amoled").screen)
        assertEquals(SettingsIndex.Screen.THEME, first("background image").screen)
        assertEquals(SettingsIndex.Screen.CLIPBOARD, first("clipboard retention").screen)
        assertEquals(SettingsIndex.Screen.TOOLBAR, first("toolbar").screen)
        assertEquals(SettingsIndex.Screen.BACKUP, first("export").screen)
        assertEquals(SettingsIndex.Screen.SHORTCUTS, first("text expansion").screen)
        assertTrue(SettingsIndex.search("zzzz").isEmpty())
        assertEquals(SettingsIndex.entries.size, SettingsIndex.search("").size)
    }

    @Test fun everySearchEntryOpensAScreen() {
        for (e in SettingsIndex.entries) assertTrue(e.title, SettingsIndex.route(e).javaClass.name.contains("Routes"))
    }

    @Test fun textShortcutsRoundTrip() {
        var raw = TextShortcuts.DEFAULTS
        assertEquals("On my way!", TextShortcuts.parse(raw)["omw"])
        raw = TextShortcuts.add(raw, "Addr", "1 Main St\nFlat 2")
        assertEquals("1 Main St\nFlat 2", TextShortcuts.parse(raw)["addr"])
        raw = TextShortcuts.remove(raw, "omw")
        assertTrue("omw" !in TextShortcuts.parse(raw))
        assertTrue(TextShortcuts.parse("bad line\n\tno key\nok!\tpunctuation key").isEmpty())
    }

    @Test fun shortcutExpandsOnSpace() {
        val p = Predictor(
            Lexicon.parse(File("src/main/assets/predict/lexicon_en.tsv").readLines().asSequence()),
            Ngrams(File("src/main/assets/predict/ngrams_en.tsv").readBytes()),
            UserModel(null),
        )
        val r = p.suggest("", "omw", Predictor.Settings(shortcuts = TextShortcuts.parse(TextShortcuts.DEFAULTS)))
        assertEquals("On my way!", r.autocorrect)
        val strip = StripOrder.arrange(r)
        assertEquals("On my way!", strip[1].text)
        assertEquals(StripOrder.Kind.AUTOCORRECT, strip[1].kind)
    }

    @Test fun stripOrderPutsBestInTheMiddle() {
        fun c(w: String) = Predictor.Candidate(w, 0f, 0f, false)
        val next = StripOrder.arrange(Predictor.Result("", listOf(c("you"), c("we"), c("the")), null, false))
        assertEquals(listOf("we", "you", "the"), next.map { it.text })
        val valid = StripOrder.arrange(Predictor.Result("the", listOf(c("the"), c("they"), c("there")), null, true))
        assertEquals(listOf("they", "the", "there"), valid.map { it.text })
        val auto = StripOrder.arrange(Predictor.Result("teh", listOf(c("the"), c("ten")), "the", false))
        assertEquals(listOf(StripOrder.Kind.LITERAL, StripOrder.Kind.AUTOCORRECT, StripOrder.Kind.WORD), auto.map { it.kind })
        val dupes = StripOrder.arrange(Predictor.Result("Form", listOf(c("from"), c("From"), c("form")), null, true))
        // "From" and "from" collapse to one slot; "Form" (what was typed) stays one tap away.
        assertEquals(dupes.size, dupes.map { it.text.lowercase() }.distinct().size)
        assertEquals(listOf("Form", "from"), dupes.map { it.text })
    }

    @Test fun backupPrefsRoundTrip() {
        val m = mapOf("mode" to "device", "clean_mode" to true, "n" to 3, "dict" to "Karim\nParakeet\tx")
        assertEquals(m, HummingBackup.parse(HummingBackup.serialize(m)))
    }
}
