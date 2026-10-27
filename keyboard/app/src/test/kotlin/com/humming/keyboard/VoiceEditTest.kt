@file:Suppress("LoopWithTooManyJumpStatements")

package com.humming.keyboard

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Runs the SAME vectors as the Rust core (core/testdata/voice_edit_vectors.json), so both stay in step. */
class VoiceEditTest {
    private fun vectorsFile(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            val f = File(dir, "core/testdata/voice_edit_vectors.json")
            if (f.isFile) return f
            dir = dir.parentFile
        }
        error("core/testdata/voice_edit_vectors.json not found above " + System.getProperty("user.dir"))
    }

    @Test fun sharedVectors() {
        val vs = Json.parseToJsonElement(vectorsFile().readText()).jsonArray
        assertTrue("need a real vector set", vs.size >= 30)
        for (el in vs) {
            val v = el.jsonObject
            val text = v["text"]!!.jsonPrimitive.content
            val utt = v["utterance"]!!.jsonPrimitive.content
            val isCmd = v["command"]!!.jsonPrimitive.booleanOrNull ?: false
            val undo = v["undo"]?.jsonPrimitive?.booleanOrNull ?: false
            val got = VoiceEdit.voiceCommand(text, utt)
            if (!isCmd) {
                assertNull("false positive: $utt on $text", got)
                continue
            }
            assertNotNull("missed command: $utt on $text", got)
            if (undo) {
                assertEquals(VoiceEdit.Command.Undo, got!!.command)
                continue
            }
            val out = got!!.outcome!!
            assertEquals("utterance $utt", v["expect"]!!.jsonPrimitive.contentOrNull, out.text)
            val rebuilt = text.substring(0, out.start) + out.inserted + text.substring(out.start + out.oldLen)
            assertEquals("span mismatch for $utt", out.text, rebuilt)
            // what the keyboard does to the field leaves exactly the edited text
            val (del, commit) = VoiceEdit.fieldEdit(text, out)
            assertEquals(out.text, text.substring(0, text.length - del) + commit)
        }
    }

    @Test fun longSentenceIsNeverACommand() {
        assertNull(
            VoiceEdit.parse(
                "change of plans we are going to the beach to meet with Sam and his family later today after lunch",
            ),
        )
    }

    @Test fun offsetsAreUtf16ForTheEditor() {
        val o = VoiceEdit.voiceCommand("café is open", "change open to closed")!!.outcome!!
        assertEquals(8, o.start)
        assertEquals("café is closed", o.text)
    }

    @Test fun undoOnlyRemovesTheKeyboardsOwnLastCommit() {
        assertEquals(5, VoiceEdit.undoCount("Hello world", "world"))
        assertEquals(0, VoiceEdit.undoCount("Hello world!", "world"))
        assertEquals(0, VoiceEdit.undoCount("Hello world", null))
    }

    @Test fun matchAtTheStartOfATruncatedWindowIsNotACommand() {
        // Codex review finding 6: "Mon|day" cut by the context window is not the word "day".
        val full = "Monday " + "x".repeat(996)
        val window = full.takeLast(VoiceEdit.CONTEXT_CHARS)
        assertTrue(window.startsWith("day "))
        assertNull(VoiceEdit.voiceCommand(window, "change day to night", truncated = true))
        assertNotNull(VoiceEdit.voiceCommand("day one", "change day to night"))
        assertNotNull(VoiceEdit.voiceCommand("the day one", "change day to night", truncated = true))
    }
}
