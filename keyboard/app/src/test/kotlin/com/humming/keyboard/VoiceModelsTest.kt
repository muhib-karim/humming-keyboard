package com.humming.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.io.File

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

    /** The figure Settings › Voice shows is the committed CI benchmark (README "Benchmarks"), LibriSpeech test-clean. */
    @Test
    fun werMatchesTheCommittedBenchmark() {
        val models = Json.parseToJsonElement(File("../../docs/bench/ci-37204875974-wer.json").readText())
            .jsonObject.getValue("models").jsonObject
        fun clean(id: String) = models.getValue(id).jsonObject.getValue("test-clean").jsonObject.getValue("wer_pct").jsonPrimitive.double
        assertEquals(clean("fast"), VoiceModels.FAST.werPct, 0.0)
        assertEquals(clean("best"), VoiceModels.BEST.werPct, 0.0)
    }
}
