package com.humming.keyboard

import com.humming.keyboard.ModelPolicy.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.IOException

class ModelPolicyTest {
    private fun d(ready: Boolean = false, online: Boolean = true, unmetered: Boolean = true, wifiOnly: Boolean = true, forced: Boolean = false) =
        ModelPolicy.decide(ready, online, unmetered, wifiOnly, forced)

    @Test fun readyNeedsNothing() = assertEquals(Decision.ALREADY_THERE, d(ready = true, online = false))
    @Test fun wifiDownloads() = assertEquals(Decision.DOWNLOAD, d())
    @Test fun mobileDataWaitsForWifi() = assertEquals(Decision.WAIT_FOR_WIFI, d(unmetered = false))
    @Test fun mobileDataAllowedWhenToggleOff() = assertEquals(Decision.DOWNLOAD, d(unmetered = false, wifiOnly = false))
    @Test fun userButtonOverridesWifiOnly() = assertEquals(Decision.DOWNLOAD, d(unmetered = false, forced = true))
    @Test fun airplaneMode() = assertEquals(Decision.OFFLINE, d(online = false, forced = true))

    @Test
    fun extractsOnlyTheModelFiles() {
        val m = VoiceModels.FAST
        assertEquals("model.int8.onnx", ModelPolicy.extractTarget("${m.dirName}/model.int8.onnx", m))
        assertEquals("tokens.txt", ModelPolicy.extractTarget("./${m.dirName}/tokens.txt", m))
        assertNull(ModelPolicy.extractTarget("${m.dirName}/test_wavs/0.wav", m))
        assertNull(ModelPolicy.extractTarget("${m.dirName}/../../evil.so", m))
        assertNull(ModelPolicy.extractTarget("other/model.int8.onnx", m))
    }

    @Test
    fun outputFilesStayInsideTheModelDirectory() {
        // 1.7.1 (CodeQL java/zipslip): the path check behind extractTarget's name check.
        val dir = File(System.getProperty("java.io.tmpdir"), "humming-zipslip-test")
        assertEquals(File(dir, "model.int8.onnx"), ModelPolicy.outputFile(dir, "model.int8.onnx"))
        ModelPolicy.outputFile(dir, "sub/../tokens.txt") // normalises to a file inside: allowed
        for (bad in listOf("../evil.so", "sub/../../evil.so", "../${dir.name}-sibling/evil.so")) {
            try {
                ModelPolicy.outputFile(dir, bad)
                fail("$bad escaped the model directory")
            } catch (e: IOException) {
                assertTrue(e.message!!.contains(bad))
            }
        }
    }
}
