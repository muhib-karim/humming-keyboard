package com.humming.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceCommandsTest {
    @Test fun deleteThatRemovesThePreviousSentence() {
        val r = VoiceCommands.apply("Meet at three. Bring the slides. Delete that. See you there.")
        assertEquals("Meet at three. See you there.", r.text)
        assertNull(r.instruction)
    }

    @Test fun scratchThatAtTheStartRemovesNothingElse() {
        assertEquals("Hello.", VoiceCommands.apply("Scratch that. Hello.").text)
    }

    @Test fun closingStyleCommandBecomesTheInstruction() {
        val r = VoiceCommands.apply("hey can you send me the report by friday. Make it formal.")
        assertEquals("hey can you send me the report by friday.", r.text)
        assertEquals("rewrite it in a formal, professional tone", r.instruction)
    }

    @Test fun translateCommand() {
        val r = VoiceCommands.apply("See you tomorrow. Translate it to Spanish.")
        assertEquals("See you tomorrow.", r.text)
        assertEquals("translate it to Spanish", r.instruction)
    }

    @Test fun ordinaryTextIsUntouched() {
        val t = "I will make it formal later, and delete that file. Thanks!"
        assertEquals(t, VoiceCommands.apply(t).text)
        assertNull(VoiceCommands.apply(t).instruction)
    }

    @Test fun llmGateNeverSendsWithoutKeyOrInIncognito() {
        val g = LlmCleanup.Provider.GEMINI
        assertEquals(false, LlmCleanup.allowed(LlmCleanup.Provider.OFF, true, false, "hi"))
        assertEquals(false, LlmCleanup.allowed(g, false, false, "hi"))
        assertEquals(false, LlmCleanup.allowed(g, true, true, "hi"))
        assertEquals(false, LlmCleanup.allowed(g, true, false, "x".repeat(LlmCleanup.MAX_CHARS + 1)))
        assertEquals(true, LlmCleanup.allowed(g, true, false, "hi"))
    }
}
