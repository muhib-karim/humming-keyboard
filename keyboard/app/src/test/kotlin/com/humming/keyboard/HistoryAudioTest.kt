package com.humming.keyboard

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** 1.7.1: voice history keeps no audio; what older builds saved (and nothing could play or delete) is removed. */
class HistoryAudioTest {
    @Test fun legacyAudioIsDeletedAndTheTextLogKept() {
        val dir = Files.createTempDirectory("hist").toFile()
        val audio = File(dir, "history_audio").apply { mkdirs() }
        File(audio, "1700000000000.pcm").writeBytes(ByteArray(3200))
        val log = File(dir, "history.json").apply { writeText("[]") }

        assertTrue(HistoryStore.dropLegacyAudio(dir))
        assertFalse(audio.exists())
        assertTrue(log.isFile)
        dir.deleteRecursively()
    }

    @Test fun nothingToDeleteIsFine() {
        val dir = Files.createTempDirectory("hist").toFile()
        assertTrue(HistoryStore.dropLegacyAudio(dir))
        dir.deleteRecursively()
    }
}
