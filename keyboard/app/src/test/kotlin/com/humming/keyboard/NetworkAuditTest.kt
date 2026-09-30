package com.humming.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * "Offline by design", enforced: only ModelDownload.kt (the one-time voice model fetch) may use a network API.
 * Scans every Kotlin/Java source of the app and its libraries. Parsing a URI (java.net.URI) is not network I/O.
 */
class NetworkAuditTest {
    private val networkApis = listOf(
        "HttpURLConnection", "HttpsURLConnection", "URLConnection", ".openConnection(", ".openStream(",
        "java.net.Socket", "java.net.ServerSocket", "DatagramSocket", "SocketChannel", "okhttp", "io.ktor",
        "android.webkit", "DownloadManager", "java.net.URL", "java.net.http",
    )
    private val allowed = setOf("ModelDownload.kt")

    private fun sources(): List<File> {
        val roots = listOf(File("src/main"), File("../lib"))
        return roots.filter { it.isDirectory }.flatMap { r ->
            r.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "java") && "/build/" !in it.path && "/test/" !in it.path }.toList()
        }
    }

    @Test
    fun onlyTheModelDownloaderTouchesTheNetwork() {
        val files = sources()
        assertTrue("found the sources (${files.size})", files.size > 200)
        val offenders = files.filter { f -> f.name !in allowed && networkApis.any { f.readText().contains(it) } }
            .map { f -> f.path + ": " + networkApis.filter { f.readText().contains(it) } }
        assertEquals("network APIs outside ModelDownload.kt", emptyList<String>(), offenders)
    }

    @Test
    fun theVoicePathHasNoNetworkAtAll() {
        val voice = listOf("HummingVoice.kt", "StreamingSession.kt", "LocalEngine.kt", "Transcriber.kt", "WavRecorder.kt",
            "Segmenter.kt", "TextCleanup.kt", "History.kt", "DictationPipeline.kt", "DictationOutput.kt", "BluetoothMic.kt")
        val found = sources().filter { it.name in voice }
        assertEquals(voice.toSet(), found.map { it.name }.toSet())
        for (f in found) {
            val t = f.readText()
            assertTrue("${f.name} must not open network connections", networkApis.none { t.contains(it) } && !t.contains("java.net"))
        }
    }

    @Test
    fun theDownloaderIsPinnedToChecksums() {
        val t = sources().first { it.name == "ModelDownload.kt" }.readText()
        assertTrue(t.contains("MessageDigest.getInstance(\"SHA-256\")") && t.contains("checksum mismatch"))
        for (m in VoiceModels.all) {
            assertTrue(m.url.startsWith("https://github.com/k2-fsa/sherpa-onnx/releases/download/"))
            assertEquals(64, m.sha256.length)
        }
    }
}
