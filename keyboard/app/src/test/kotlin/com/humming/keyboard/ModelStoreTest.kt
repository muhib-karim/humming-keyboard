package com.humming.keyboard

import android.app.Application
import android.content.Context
import com.sun.net.httpserver.HttpServer
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.security.MessageDigest

/**
 * Humming 1.7.1: model management ([ModelStore]) end to end against a local HTTP server on 127.0.0.1: download,
 * resume, SHA-256 check, unpacking only the files the engine needs, delete. The archive is built here, a few bytes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ModelStoreTest {
    private val ctx: Context = RuntimeEnvironment.getApplication()
    private lateinit var server: HttpServer
    private val requests = mutableListOf<String?>()
    private var honourRange = true
    private val archive = tarBz2(
        "test-model/model.int8.onnx" to ByteArray(4096) { (it % 251).toByte() },
        "test-model/tokens.txt" to "a 0\nb 1\n".toByteArray(),
        "test-model/test_wavs/0.wav" to ByteArray(64),
        "test-model/../../escaped.txt" to "x".toByteArray(),
    )

    @Before fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/m.tar.bz2") { ex ->
            val range = ex.requestHeaders.getFirst("Range")
            requests += range
            val from = range?.takeIf { honourRange }?.removePrefix("bytes=")?.removeSuffix("-")?.toInt() ?: 0
            val body = archive.copyOfRange(from, archive.size)
            if (from > 0) ex.responseHeaders.add("Content-Range", "bytes $from-${archive.size - 1}/${archive.size}")
            ex.sendResponseHeaders(if (from > 0) 206 else 200, body.size.toLong())
            ex.responseBody.use { it.write(body) }
        }
        server.start()
    }

    @After fun stopServer() = server.stop(0)

    private fun model(sha: String = sha256(archive), files: List<String> = listOf("model.int8.onnx", "tokens.txt")) =
        VoiceModel(
            id = "test", label = "Test", blurb = "", url = "http://127.0.0.1:${server.address.port}/m.tar.bz2",
            downloadBytes = archive.size.toLong(), sha256 = sha, dirName = "test-model", files = files,
            diskBytes = 0, ramMb = 0, minRamMb = 0, kind = VoiceModel.Kind.NEMO_CTC, werPct = 0.0,
        )

    private fun part(m: VoiceModel) = File(ModelStore.root(ctx), "${m.id}.tar.bz2.part")

    @Test fun downloadVerifiesAndUnpacksOnlyTheModelFiles() {
        val m = model()
        assertFalse(ModelStore.ready(ctx, m))
        val progress = mutableListOf<Int>()
        ModelStore.download(ctx, m) { pct, _ -> progress += pct }
        assertTrue(ModelStore.ready(ctx, m))
        val dir = ModelStore.dir(ctx, m)
        assertEquals(setOf("model.int8.onnx", "tokens.txt", ".verified"), dir.list()!!.toSet())
        assertEquals(m.sha256, File(dir, ".verified").readText())
        assertEquals("a 0\nb 1\n", File(dir, "tokens.txt").readText())
        assertFalse("archive entries never leave the model directory", File(ctx.filesDir, "escaped.txt").exists())
        assertFalse(File(ctx.filesDir.parentFile, "escaped.txt").exists())
        assertFalse("archive deleted after unpacking", part(m).exists())
        assertEquals(100, progress.last())
        assertTrue("progress never goes back", progress.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(dir.listFiles()!!.sumOf { it.length() }, ModelStore.bytesOnDisk(ctx, m))
    }

    @Test fun aChecksumMismatchKeepsNothing() {
        val m = model(sha = "0".repeat(64))
        try {
            ModelStore.download(ctx, m) { _, _ -> }
            fail("expected a checksum error")
        } catch (e: IOException) {
            assertEquals("model checksum mismatch", e.message)
        }
        assertFalse(ModelStore.ready(ctx, m))
        assertFalse(part(m).exists())
        assertFalse(File(ModelStore.dir(ctx, m), ".verified").exists())
    }

    @Test fun anInterruptedDownloadResumesWithARangeRequest() {
        val m = model()
        ModelStore.root(ctx).mkdirs()
        part(m).writeBytes(archive.copyOfRange(0, 100))
        ModelStore.download(ctx, m) { _, _ -> }
        assertEquals(listOf<String?>("bytes=100-"), requests)
        assertTrue(ModelStore.ready(ctx, m))
    }

    @Test fun aServerThatIgnoresRangeRestartsFromTheBeginning() {
        honourRange = false
        val m = model()
        ModelStore.root(ctx).mkdirs()
        part(m).writeBytes(ByteArray(100) { 7 }) // not even the right bytes: the full 200 response replaces them
        ModelStore.download(ctx, m) { _, _ -> }
        assertTrue(ModelStore.ready(ctx, m))
    }

    @Test fun aPartialFileLargerThanTheModelIsDiscarded() {
        val m = model()
        ModelStore.root(ctx).mkdirs()
        part(m).writeBytes(ByteArray(archive.size + 10))
        ModelStore.download(ctx, m) { _, _ -> }
        assertEquals(listOf<String?>(null), requests)
        assertTrue(ModelStore.ready(ctx, m))
    }

    @Test fun anArchiveMissingARequiredFileIsRejected() {
        val m = model(files = listOf("model.int8.onnx", "tokens.txt", "joiner.int8.onnx"))
        try {
            ModelStore.download(ctx, m) { _, _ -> }
            fail("expected a missing-files error")
        } catch (e: IOException) {
            assertEquals("model archive is missing files", e.message)
        }
        assertFalse(ModelStore.ready(ctx, m))
    }

    @Test fun readyNeedsTheVerifiedMarkerAndEveryFile() {
        val m = model()
        ModelStore.download(ctx, m) { _, _ -> }
        File(ModelStore.dir(ctx, m), "tokens.txt").writeText("")
        assertFalse("an empty file is not ready", ModelStore.ready(ctx, m))
        ModelStore.download(ctx, m) { _, _ -> }
        File(ModelStore.dir(ctx, m), ".verified").delete()
        assertFalse("no marker, not ready", ModelStore.ready(ctx, m))
    }

    @Test fun deleteRemovesTheModelAndAnyPartialDownload() {
        val m = model()
        ModelStore.download(ctx, m) { _, _ -> }
        part(m).writeBytes(ByteArray(10))
        assertTrue(ModelStore.delete(ctx, m))
        assertFalse(ModelStore.ready(ctx, m))
        assertFalse(ModelStore.dir(ctx, m).exists())
        assertFalse(part(m).exists())
        assertEquals(0L, ModelStore.bytesOnDisk(ctx, m))
    }

    @Test fun legacyWhisperFilesAreDroppedAndNothingElse() {
        val root = ModelStore.root(ctx).apply { mkdirs() }
        File(root, "ggml-base.en.bin").writeBytes(ByteArray(8))
        File(root, "keep.txt").writeText("x")
        ModelStore.dropLegacy(ctx)
        assertEquals(listOf("keep.txt"), root.list()!!.toList())
    }

    private fun sha256(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") {
        "%02x".format(it)
    }

    private fun tarBz2(vararg entries: Pair<String, ByteArray>): ByteArray {
        val bytes = ByteArrayOutputStream()
        TarArchiveOutputStream(BZip2CompressorOutputStream(bytes)).use { tar ->
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            for ((name, data) in entries) {
                tar.putArchiveEntry(TarArchiveEntry(name, true).apply { size = data.size.toLong() })
                tar.write(data)
                tar.closeArchiveEntry()
            }
        }
        return bytes.toByteArray()
    }
}
