package com.humming.keyboard

import com.humming.keyboard.predict.Predictor
import com.humming.keyboard.predict.StripOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StickersTest {
    private val pack by lazy { Stickers.parseIndex(File("src/main/assets/stickers/index.tsv").readLines().asSequence()) }

    @Test fun builtInPackIsCompleteAndSmall() {
        assertEquals(32, pack.size)
        var bytes = 0L
        for (s in pack) {
            val f = File("src/main/assets/" + s.path)
            assertTrue("${s.path} exists", f.exists())
            val head = f.readBytes().copyOfRange(0, 8)
            assertTrue("${s.path} is a PNG", head[1] == 'P'.code.toByte() && head[2] == 'N'.code.toByte())
            bytes += f.length()
        }
        assertTrue("pack is $bytes bytes", bytes < 600_000)
    }

    @Test fun searchMatchesCaptionsAndKeywords() {
        assertEquals(pack.size, Stickers.search(pack, "  ").size)
        assertEquals("LOL", Stickers.search(pack, "lol").first().label)
        assertTrue(Stickers.search(pack, "birthday").any { it.label == "HAPPY BDAY" })
        assertTrue(Stickers.search(pack, "coffee morning").all { it.label == "COFFEE?" })
        assertTrue(Stickers.search(pack, "zzzz").isEmpty())
    }

    @Test fun sendsOnlyWhatTheAppAccepts() {
        assertTrue(Stickers.accepts(arrayOf("image/*"), "image/png"))
        assertTrue(Stickers.accepts(arrayOf("image/gif", "image/png"), "image/gif"))
        assertFalse(Stickers.accepts(arrayOf("image/gif"), "image/png"))
        assertFalse(Stickers.accepts(emptyArray(), "image/png"))
        assertEquals("image/gif", Stickers.mimeOf("/x/a.GIF"))
        assertEquals("image/png", Stickers.mimeOf("stickers/s01.png"))
    }

    @Test fun gifSearchIsOffByDefaultAndRespectsIncognito() {
        assertFalse(GifSearch.allowed(enabled = false, incognito = false, allowInIncognito = false))
        assertFalse(GifSearch.allowed(enabled = true, incognito = true, allowInIncognito = false))
        assertTrue(GifSearch.allowed(enabled = true, incognito = true, allowInIncognito = true))
        val u = GifSearch.queryUrl("happy cat & dog")
        assertTrue(u, u.startsWith("https://api.openverse.org/v1/images/?q=happy+cat+%26+dog&"))
        assertTrue(u.contains("extension=gif") && u.contains("license=cc0,pdm,by") && u.contains("mature=false"))
        // Openverse refuses page_size > 20 without an account (HTTP 401); the 1.2 CI screenshot caught it.
        assertTrue(u, u.contains("page_size=20&"))
        assertTrue(GifSearch.queryUrl("x", pageSize = 50).contains("page_size=20&"))
    }

    @Test fun parsesOpenverseResults() {
        val body = """{"result_count":2,"results":[
            {"id":"a1","title":"Cat","url":"https://upload.wikimedia.org/c.gif","thumbnail":"https://api.openverse.org/v1/images/a1/thumb/","license":"by","creator":"Ann","source":"wikimedia","filesize":2000},
            {"id":"b2","title":"Huge","url":"https://x.org/h.gif","license":"cc0","filesize":99999999},
            {"id":"c3","title":"Plain http","url":"http://x.org/p.gif","license":"cc0"}]}"""
        val gifs = GifSearch.parse(body)
        assertEquals(listOf("a1"), gifs.map { it.id })
        assertEquals("CC BY \u00B7 Ann \u00B7 wikimedia", gifs[0].credit)
        assertEquals("Public domain", GifSearch.licenseName("pdm"))
        assertTrue(GifSearch.parse("not json").isEmpty())
    }

    @Test fun stripCanPutTheBestSuggestionFirst() {
        fun c(w: String) = Predictor.Candidate(w, 0f, 0f, false)
        val next = Predictor.Result("", listOf(c("you"), c("we"), c("the")), null, false)
        assertEquals(listOf("we", "you", "the"), StripOrder.arrange(next).map { it.text })
        assertEquals(listOf("you", "we", "the"), StripOrder.arrange(next, 3, bestInMiddle = false).map { it.text })
        val fix = Predictor.Result("teh", listOf(c("the"), c("then")), "the", false)
        assertEquals(listOf("the", "teh"), StripOrder.arrange(fix, 3, bestInMiddle = false).take(2).map { it.text })
    }
}
