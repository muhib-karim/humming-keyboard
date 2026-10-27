package com.humming.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceModelsTest {
    @Test
    fun fastIsTheDefault() {
        assertEquals("fast", VoiceModels.byId(null).id)
        assertEquals("fast", VoiceModels.byId("nonsense").id)
        assertEquals("best", VoiceModels.byId("best").id)
    }

    @Test
    fun bestNeedsSixGigPhones() {
        assertEquals("fast", VoiceModels.resolve("best", 3_800).id) // 4 GB phone reports ~3.8 GB
        assertEquals("best", VoiceModels.resolve("best", 7_600).id)
        assertTrue(VoiceModels.fits(VoiceModels.FAST, 2_800))
        assertFalse(VoiceModels.fits(VoiceModels.BEST, 3_800))
        assertTrue(VoiceModels.fits(VoiceModels.BEST, 0)) // unknown RAM: don't block
    }

    @Test
    fun sizes() {
        assertEquals("104 MB", VoiceModels.formatBytes(VoiceModels.FAST.downloadBytes))
        assertEquals("482 MB", VoiceModels.formatBytes(VoiceModels.BEST.downloadBytes))
        assertEquals("1.3 GB", VoiceModels.formatBytes(1_300_000_000L))
    }

    @Test
    fun modelsNeedTheirFiles() {
        assertEquals(listOf("model.int8.onnx", "tokens.txt"), VoiceModels.FAST.files)
        assertEquals(4, VoiceModels.BEST.files.size)
        assertTrue(VoiceModels.BEST.werPct < VoiceModels.FAST.werPct)
    }

    @Test
    fun multilingualIsOptionalPinnedAndGatedLikeBest() {
        assertEquals(listOf("fast", "best", "multi"), VoiceModels.all.map { it.id })
        assertEquals("multi", VoiceModels.byId("multi").id)
        assertEquals("fast", VoiceModels.resolve("multi", 3_800).id)
        assertEquals("multi", VoiceModels.resolve("multi", 7_600).id)
        assertEquals("487 MB", VoiceModels.formatBytes(VoiceModels.MULTI.downloadBytes))
        assertEquals(VoiceModels.BEST.files, VoiceModels.MULTI.files)
        assertEquals(VoiceModel.Kind.NEMO_TRANSDUCER, VoiceModels.MULTI.kind)
        assertEquals(VoiceModels.all.size, VoiceModels.all.map { it.dirName }.toSet().size)
    }

    @Test fun englishCleanupIsOffOnlyForMultilingual() {
        assertTrue(!VoiceModels.englishCleanup(VoiceModels.MULTI.id))
        val others = VoiceModels.all.filter { it.id != VoiceModels.MULTI.id }
        for (m in others) assertTrue(m.id, VoiceModels.englishCleanup(m.id))
    }
}
