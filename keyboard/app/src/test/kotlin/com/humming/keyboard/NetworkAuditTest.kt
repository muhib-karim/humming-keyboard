package com.humming.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * "Offline by design", enforced: only ModelDownload.kt (the one-time voice model fetch), GifSearch.kt (the opt-in
 * GIF search, off by default), LlmCleanup.kt (the opt-in AI clean-up with the user's own key, off by default) and
 * AppUpdate.kt (the update check of the sideloaded APK, 1.6, can be switched off) may use a network API.
 * Scans every Kotlin/Java source of the app and its libraries. Parsing a URI (java.net.URI) is not network I/O.
 */
class NetworkAuditTest {
    private val networkApis = listOf(
        "HttpURLConnection", "HttpsURLConnection", "URLConnection", ".openConnection(", ".openStream(",
        "java.net.Socket", "java.net.ServerSocket", "DatagramSocket", "SocketChannel", "okhttp", "io.ktor",
        "android.webkit", "DownloadManager", "java.net.URL", "java.net.http",
    )
    private val allowed = setOf("ModelDownload.kt", "GifSearch.kt", "LlmCleanup.kt", "AppUpdate.kt")

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
        assertEquals("network APIs outside ModelDownload.kt, GifSearch.kt, LlmCleanup.kt and AppUpdate.kt", emptyList<String>(), offenders)
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
    fun gifSearchIsOptInHttpsOnlyAndKeyless() {
        val t = sources().first { it.name == "GifSearch.kt" }.readText()
        assertTrue(t.contains("require(url.startsWith(\"https://\"))"))
        assertTrue("no API key or account", !t.contains("key=") && !t.contains("Authorization"))
        assertTrue(!GifSearch.allowed(enabled = false, incognito = false, allowInIncognito = true))
        assertTrue(!GifSearch.allowed(enabled = true, incognito = true, allowInIncognito = false))
        assertTrue(GifSearch.allowed(enabled = true, incognito = false, allowInIncognito = false))
        // Nothing but the sticker panel calls it.
        val callers = sources().filter { it.name != "GifSearch.kt" && it.readText().contains("GifSearch.search") }.map { it.name }
        assertEquals(listOf("StickerPanel.kt"), callers)
    }

    @Test
    fun theUpdaterIsHttpsOnlyChecksummedAndCertificatePinned() {
        val t = sources().first { it.name == "AppUpdate.kt" }.readText()
        assertTrue("one fixed HTTPS feed", t.contains("const val FEED_BASE = \"https://humming-keyboard.pages.dev/updates/\""))
        assertTrue(t.contains("require(url.startsWith(\"https://\"))"))
        assertTrue("SHA-256 against SHA256SUMS.txt", t.contains("SHA256SUMS.txt") && t.contains("checksum mismatch"))
        assertTrue("signing certificate pinned", t.contains("certMatches") && t.contains("GET_SIGNING_CERTIFICATES"))
        assertTrue("Android's installer, never a silent root trick", t.contains("PackageInstaller") && !t.contains("Runtime.getRuntime"))
        assertTrue("no account, key or identifier sent", !t.contains("Authorization") && !t.contains("ANDROID_ID"))
        assertEquals("8C8B68BBE63AA42A856159AAA5B0C90CD954EEF78A980D39BCB66941753E1FC6", UpdatePolicy.RELEASE_CERT_SHA256)
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
