package dev.patrickgold.florisboard.lib.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SafeFileNameTest {
    private val fixed = { "fallback" }

    @Test
    fun keepsOrdinaryNames() {
        assertEquals("theme.flex", SafeFileName.sanitize("theme.flex", fixed))
        assertEquals("my theme (1).zip", SafeFileName.sanitize("my theme (1).zip", fixed))
    }

    @Test
    fun stripsDirectoryComponents() {
        assertEquals("evil.xml", SafeFileName.sanitize("../../shared_prefs/evil.xml", fixed))
        assertEquals("evil.xml", SafeFileName.sanitize("..\\..\\evil.xml", fixed))
        assertEquals("x", SafeFileName.sanitize("/abs/path/x", fixed))
    }

    @Test
    fun rejectsDotNamesAndHiddenFiles() {
        assertEquals("fallback", SafeFileName.sanitize("..", fixed))
        assertEquals("fallback", SafeFileName.sanitize(".", fixed))
        assertEquals("fallback", SafeFileName.sanitize("../..", fixed))
        assertEquals("profile", SafeFileName.sanitize(".profile", fixed))
    }

    @Test
    fun dropsControlAndReservedCharacters() {
        assertEquals("ab.zip", SafeFileName.sanitize("a\u0000b.zip", fixed))
        assertEquals("ab.zip", SafeFileName.sanitize("a\nb.zip", fixed))
        assertEquals("ab.zip", SafeFileName.sanitize("a:*?\"<>|b.zip", fixed))
    }

    @Test
    fun fallsBackWhenNothingIsLeft() {
        assertEquals("fallback", SafeFileName.sanitize(null, fixed))
        assertEquals("fallback", SafeFileName.sanitize("", fixed))
        assertEquals("fallback", SafeFileName.sanitize("   ", fixed))
        assertEquals("fallback", SafeFileName.sanitize("dir/", fixed))
        assertTrue(SafeFileName.sanitize(null).startsWith("import-"))
    }

    @Test
    fun neverSplitsASurrogatePair() {
        val out = SafeFileName.sanitize("\uD83D\uDE00".repeat(100), fixed)
        assertTrue(out.length <= SafeFileName.MAX_LENGTH)
        assertTrue(!out.last().isHighSurrogate())
    }

    @Test
    fun capsLengthAndKeepsExtension() {
        val out = SafeFileName.sanitize("a".repeat(500) + ".zip", fixed)
        assertEquals(SafeFileName.MAX_LENGTH, out.length)
        assertTrue(out.endsWith(".zip"))
    }

    @Test
    fun containmentCheck() {
        val root = Files.createTempDirectory("sfn").toFile()
        try {
            val dir = File(root, "input").also { it.mkdirs() }
            assertTrue(SafeFileName.isContained(dir, File(dir, "ok.zip")))
            assertFalse(SafeFileName.isContained(dir, File(dir, "../escape.zip")))
            assertFalse(SafeFileName.isContained(dir, dir))
            assertTrue(SafeFileName.isContained(dir, File(dir, SafeFileName.sanitize("../../x.zip"))))
        } finally {
            root.deleteRecursively()
        }
    }
}
