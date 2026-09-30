package com.humming.keyboard

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/*
 * Humming: the ONE place in the app that touches the network.
 *
 * A speech model is fetched once (on Wi-Fi by default), checked against a pinned SHA-256, unpacked into the app's
 * private storage, and the archive is deleted. After that, dictation never opens a socket: recording, voice
 * activity detection, recognition, cleanup and history all run on the phone. NetworkAuditTest fails the build if
 * any other source file in the app uses a network API.
 */

/** Pure download policy, unit-tested: may a model be fetched right now? */
object ModelPolicy {
    enum class Decision { ALREADY_THERE, DOWNLOAD, WAIT_FOR_WIFI, OFFLINE }

    fun decide(ready: Boolean, online: Boolean, unmetered: Boolean, wifiOnly: Boolean, userForced: Boolean): Decision = when {
        ready -> Decision.ALREADY_THERE
        !online -> Decision.OFFLINE
        userForced -> Decision.DOWNLOAD
        wifiOnly && !unmetered -> Decision.WAIT_FOR_WIFI
        else -> Decision.DOWNLOAD
    }

    /** Only the files a model needs leave the archive, and never outside its directory. Pure: ModelPolicyTest. */
    fun extractTarget(entryName: String, m: VoiceModel): String? {
        val name = entryName.removePrefix("./")
        if (name.contains("..")) return null
        val rel = name.removePrefix(m.dirName + "/")
        return rel.takeIf { it in m.files }
    }
}

object ModelStore {
    private const val OK = ".verified"

    fun totalRamMb(ctx: Context): Long = runCatching {
        val mi = ActivityManager.MemoryInfo()
        ctx.getSystemService(ActivityManager::class.java).getMemoryInfo(mi)
        mi.totalMem / 1_000_000
    }.getOrDefault(0L)

    /** The model dictation uses: the one picked in Settings > Voice when the phone can run it, else Fast. */
    fun current(ctx: Context): VoiceModel = VoiceModels.resolve(Prefs.voiceModel(ctx), totalRamMb(ctx))

    fun root(ctx: Context): File = File(ctx.filesDir, "models")
    fun dir(ctx: Context, m: VoiceModel): File = File(root(ctx), m.dirName)

    fun ready(ctx: Context, m: VoiceModel = current(ctx)): Boolean {
        val d = dir(ctx, m)
        return File(d, OK).isFile && m.files.all { File(d, it).let { f -> f.isFile && f.length() > 0 } }
    }

    fun bytesOnDisk(ctx: Context, m: VoiceModel): Long =
        (dir(ctx, m).listFiles()?.sumOf { it.length() } ?: 0L) + archive(ctx, m).let { if (it.isFile) it.length() else 0L }

    private fun archive(ctx: Context, m: VoiceModel) = File(root(ctx), "${m.id}.tar.bz2.part")

    /** Removes a model (and any partial download). Dictation with it stays off until it is downloaded again. */
    fun delete(ctx: Context, m: VoiceModel): Boolean {
        CoreLoader.release()
        archive(ctx, m).delete()
        return dir(ctx, m).deleteRecursively()
    }

    /** Whisper files from Humming M2-M4 builds are dead weight now. */
    fun dropLegacy(ctx: Context) {
        root(ctx).listFiles()?.forEach { f -> if (f.isFile && f.name.startsWith("ggml-")) f.delete() }
    }

    /**
     * Blocking, resumable download (HTTP Range) with a SHA-256 check, then unpack. [progress] gets (0..100, bytes).
     */
    fun download(ctx: Context, m: VoiceModel, progress: (Int, Long) -> Unit) {
        root(ctx).mkdirs()
        dropLegacy(ctx)
        val part = archive(ctx, m)
        if (part.isFile && part.length() > m.downloadBytes) part.delete()
        val digest = MessageDigest.getInstance("SHA-256")
        var done = if (part.isFile) part.length() else 0L
        if (done < m.downloadBytes) {
            val conn = URL(m.url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.instanceFollowRedirects = true
            if (done > 0) conn.setRequestProperty("Range", "bytes=$done-")
            try {
                val code = conn.responseCode
                if (code !in 200..299) throw IOException("model download HTTP $code")
                val append = done > 0 && code == 206
                if (!append) done = 0
                var lastPct = -1
                conn.inputStream.use { input ->
                    java.io.FileOutputStream(part, append).use { out ->
                        val buf = ByteArray(256 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            val pct = (done * 95 / m.downloadBytes).toInt().coerceAtMost(95)
                            if (pct != lastPct) { lastPct = pct; progress(pct, done) }
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
        }
        part.inputStream().use { input ->
            val buf = ByteArray(256 * 1024)
            while (true) { val n = input.read(buf); if (n < 0) break; digest.update(buf, 0, n) }
        }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        if (hex != m.sha256) {
            part.delete()
            throw IOException("model checksum mismatch")
        }
        progress(96, done)
        val d = dir(ctx, m)
        d.deleteRecursively()
        d.mkdirs()
        TarArchiveInputStream(BZip2CompressorInputStream(BufferedInputStream(part.inputStream(), 1 shl 16))).use { tar ->
            while (true) {
                val e = tar.nextEntry ?: break
                if (e.isDirectory) continue
                val rel = ModelPolicy.extractTarget(e.name, m) ?: continue
                File(d, rel).outputStream().use { out -> tar.copyTo(out, 1 shl 16) }
            }
        }
        if (!m.files.all { File(d, it).isFile }) throw IOException("model archive is missing files")
        File(d, OK).writeText(m.sha256)
        part.delete()
        progress(100, done)
    }
}

/**
 * Fetches a speech model in the background: the first time the keyboard opens (on Wi-Fi by default), or when the
 * user taps Download in Settings > Voice. Listeners (the voice panel, the settings screen) get every change.
 */
object ModelDownloader {
    data class State(
        val ready: Boolean,
        val running: Boolean,
        val pct: Int,
        val bytes: Long,
        val error: String?,
        /** "wifi" (waiting for Wi-Fi), "offline" (no network and no model yet), or null. */
        val waiting: String? = null,
        val modelId: String = VoiceModels.FAST.id,
    )

    @Volatile
    var state = State(ready = false, running = false, pct = 0, bytes = 0, error = null)
        private set
    private val listeners = java.util.concurrent.CopyOnWriteArraySet<(State) -> Unit>()
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    fun addListener(l: (State) -> Unit) { listeners.add(l); l(state) }
    fun removeListener(l: (State) -> Unit) { listeners.remove(l) }

    private fun publish(s: State) {
        state = s
        main.post { listeners.forEach { it(s) } }
    }

    private fun network(ctx: Context): Pair<Boolean, Boolean> {
        val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return false to false
        val caps = runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull() ?: return false to false
        val online = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val unmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        return online to unmetered
    }

    /** After Settings > Voice > Delete or a model switch: republish so every screen shows the right state. */
    fun refresh(ctx: Context) {
        if (state.running) return
        val m = ModelStore.current(ctx)
        publish(State(ModelStore.ready(ctx, m), false, if (ModelStore.ready(ctx, m)) 100 else 0, 0, null, modelId = m.id))
    }

    /**
     * True when [m] is ready now. Otherwise, if policy allows (Wi-Fi only by default; [userForced] from the
     * Settings button lifts that), starts (or keeps) the background download and returns false.
     */
    @Synchronized
    fun ensure(ctx: Context, m: VoiceModel = ModelStore.current(ctx), userForced: Boolean = false): Boolean {
        val app = ctx.applicationContext
        if (state.running) return ModelStore.ready(app, m)
        val (online, unmetered) = network(app)
        when (ModelPolicy.decide(ModelStore.ready(app, m), online, unmetered, Prefs.wifiOnly(app), userForced)) {
            ModelPolicy.Decision.ALREADY_THERE -> {
                if (!state.ready || state.modelId != m.id) publish(State(true, false, 100, m.diskBytes, null, modelId = m.id))
                return true
            }
            ModelPolicy.Decision.OFFLINE -> {
                publish(State(false, false, 0, 0, null, waiting = "offline", modelId = m.id))
                return false
            }
            ModelPolicy.Decision.WAIT_FOR_WIFI -> {
                publish(State(false, false, 0, 0, null, waiting = "wifi", modelId = m.id))
                return false
            }
            ModelPolicy.Decision.DOWNLOAD -> Unit
        }
        publish(State(false, true, 0, 0, null, modelId = m.id))
        android.util.Log.i("HUMMING", "HUMMING_MODEL start ${m.id}")
        Thread({
            var attempt = 0
            while (true) {
                attempt++
                try {
                    var lastLogged = -1
                    ModelStore.download(app, m) { pct, bytes ->
                        publish(State(false, true, pct, bytes, null, modelId = m.id))
                        if (pct / 10 != lastLogged) {
                            lastLogged = pct / 10
                            android.util.Log.i("HUMMING", "HUMMING_MODEL progress $pct")
                        }
                    }
                    publish(State(true, false, 100, m.diskBytes, null, modelId = m.id))
                    android.util.Log.i("HUMMING", "HUMMING_MODEL ready ${m.id} sha256=${m.sha256}")
                    break
                } catch (e: Exception) {
                    android.util.Log.w("HUMMING", "HUMMING_MODEL attempt $attempt failed: ${e.message}")
                    if (attempt >= 4) {
                        publish(State(false, false, state.pct, state.bytes, e.message ?: "download failed", modelId = m.id))
                        break
                    }
                    publish(State(false, true, state.pct, state.bytes, "retrying…", modelId = m.id))
                    Thread.sleep(3_000L * attempt)
                }
            }
        }, "humming-model").apply { isDaemon = true }.start()
        return false
    }
}
