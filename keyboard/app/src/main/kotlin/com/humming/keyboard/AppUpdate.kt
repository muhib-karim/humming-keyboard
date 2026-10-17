/*
 * Humming (added file, 1.6). In-app updates for the sideloaded APK.
 *
 * The SECOND place in the app that touches the network (NetworkAuditTest allows it by name). What it does:
 *  1. Reads the small update feed (android.json) that CI publishes with every GitHub Release, mirrored to the
 *     project's public GitHub Pages site because the source repository is private.
 *  2. If the feed's versionCode is higher than this app's, downloads the APK (on Wi-Fi only when it runs on its
 *     own), computes its SHA-256 and compares it with the line for that file in the release's SHA256SUMS.txt.
 *  3. Reads the downloaded APK's signing certificate and installs it only if it is Humming's release certificate
 *     (SHA-256 8C:8B:...:1F:C6, the same one CI checks) and the package name and versionCode are what the feed said.
 *  4. Hands the APK to Android's PackageInstaller. The session asks for USER_ACTION_REQUIRED (Android 12+), so
 *     Android always shows its own "Update this app?" confirmation; no update is ever installed without the user's tap.
 * The background check and the automatic download are OFF by default (opt in under Settings > Updates); "Check for
 * updates" in Settings always works.
 * Builds made for Google Play (BuildConfig.SELF_UPDATE = false) never run any of this: Play updates them.
 */
package com.humming.keyboard

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale

/** Pure parts of the updater, unit-tested in AppUpdateTest (no Android, no network). */
object UpdatePolicy {
    /** Background update checks are opt-in. */
    const val AUTO_CHECK_DEFAULT = false
    /** Automatic downloads are opt-in. */
    const val AUTO_DOWNLOAD_DEFAULT = false

    data class Release(val versionCode: Int, val versionName: String, val apk: String, val notes: String)

    /** Humming's release (upload) certificate, SHA-256, as published and checked by the android-release workflow. */
    const val RELEASE_CERT_SHA256 = "8C8B68BBE63AA42A856159AAA5B0C90CD954EEF78A980D39BCB66941753E1FC6"

    private val apkName = Regex("^Humming-[0-9]+(\\.[0-9]+){1,3}\\.apk$")

    fun parseFeed(json: String): Release {
        val o = Json.parseToJsonElement(json).jsonObject
        val r = Release(
            versionCode = o.getValue("versionCode").jsonPrimitive.int,
            versionName = o.getValue("versionName").jsonPrimitive.content,
            apk = o.getValue("apk").jsonPrimitive.content,
            notes = o["notes"]?.jsonPrimitive?.content ?: "",
        )
        require(isSafeApkName(r.apk)) { "unexpected file name in the update feed" }
        return r
    }

    /** Only a plain "Humming-1.2.3.apk" file name, never a path or another host. */
    fun isSafeApkName(name: String): Boolean = apkName.matches(name)

    fun isNewer(remoteVersionCode: Int, installedVersionCode: Long): Boolean = remoteVersionCode.toLong() > installedVersionCode

    /** The SHA-256 listed for [fileName] in a sha256sum file ("<hex>  <name>" or "<hex> *<name>"), lower-case. */
    fun sumFor(sums: String, fileName: String): String? = sums.lineSequence()
        .map { it.trim() }
        .mapNotNull { line ->
            val m = Regex("^([0-9a-fA-F]{64})\\s+\\*?(.+)$").find(line) ?: return@mapNotNull null
            m.groupValues[2].trim().substringAfterLast('/') to m.groupValues[1].lowercase(Locale.ROOT)
        }
        .firstOrNull { it.first == fileName }?.second

    fun normalizeHex(s: String): String = s.filter { it.isLetterOrDigit() }.uppercase(Locale.ROOT)

    fun certMatches(certSha256: String): Boolean = normalizeHex(certSha256) == RELEASE_CERT_SHA256

    /** Background checks: at most one per [everyMs]. */
    fun dueForCheck(nowMs: Long, lastMs: Long, everyMs: Long): Boolean = lastMs <= 0L || nowMs - lastMs >= everyMs || nowMs < lastMs

    /** What a background check may do: download only on an unmetered network, unless the user pressed a button. */
    fun mayDownload(autoDownload: Boolean, unmetered: Boolean, userAsked: Boolean): Boolean = userAsked || (autoDownload && unmetered)
}

object AppUpdate {
    const val FEED_BASE = "https://humming-keyboard.pages.dev/updates/"
    const val FEED_URL = FEED_BASE + "android.json"
    const val SUMS_URL = FEED_BASE + "SHA256SUMS.txt"
    const val CHECK_EVERY_MS = 6L * 60 * 60 * 1000

    const val KEY_AUTO_CHECK = "update_auto_check"
    const val KEY_AUTO_DOWNLOAD = "update_auto_download"
    private const val KEY_LAST_CHECK = "update_last_check"
    private const val CHANNEL = "humming_updates"
    private const val MAX_APK_BYTES = 300L * 1024 * 1024

    /** Settings > Updates state. */
    data class Status(
        val state: String = "idle", // idle | checking | up_to_date | available | downloading | verifying | ready | installing | error
        val message: String = "",
        val latest: String? = null,
        val pct: Int = 0,
    )

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status

    /** True while Settings > Updates is on screen: the install confirmation can then be shown right away. */
    @Volatile var screenVisible: Boolean = false

    @Volatile private var running = false

    val enabled: Boolean get() = dev.patrickgold.florisboard.BuildConfig.SELF_UPDATE

    fun autoCheck(ctx: Context): Boolean = Prefs.get(ctx).getBoolean(KEY_AUTO_CHECK, UpdatePolicy.AUTO_CHECK_DEFAULT)
    fun autoDownload(ctx: Context): Boolean = Prefs.get(ctx).getBoolean(KEY_AUTO_DOWNLOAD, UpdatePolicy.AUTO_DOWNLOAD_DEFAULT)

    fun installedVersionCode(ctx: Context): Long = runCatching {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
    }.getOrDefault(0L)

    fun installedVersionName(ctx: Context): String = runCatching {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
    }.getOrDefault("")

    /** Has the user allowed Humming to install apps ("Install unknown apps")? */
    fun canInstall(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    fun unknownSourcesSettings(ctx: Context): Intent =
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Called when the app process starts and when the keyboard opens: a check at most every 6 hours, if switched on. */
    fun maybeCheckInBackground(context: Context) {
        if (!enabled || running || Build.FINGERPRINT == "robolectric") return
        val ctx = context.applicationContext
        if (!autoCheck(ctx)) return
        val last = Prefs.get(ctx).getLong(KEY_LAST_CHECK, 0L)
        if (!UpdatePolicy.dueForCheck(System.currentTimeMillis(), last, CHECK_EVERY_MS)) return
        checkNow(ctx, userAsked = false)
    }

    /** One check; with [userAsked] (the buttons) the download and install go ahead on any network. */
    fun checkNow(context: Context, userAsked: Boolean) {
        if (!enabled) return
        val ctx = context.applicationContext
        synchronized(this) {
            if (running) return
            running = true
        }
        Thread({
            try {
                run(ctx, userAsked)
            } catch (e: Exception) {
                _status.value = Status("error", "Update failed: ${e.message ?: e.javaClass.simpleName}", _status.value.latest)
            } finally {
                running = false
            }
        }, "humming-update").apply { isDaemon = true }.start()
    }

    private fun run(ctx: Context, userAsked: Boolean) {
        _status.value = Status("checking", "Checking for updates…")
        val release = UpdatePolicy.parseFeed(String(fetch(FEED_URL, 64 * 1024), Charsets.UTF_8))
        Prefs.get(ctx).edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
        val installed = installedVersionCode(ctx)
        if (!UpdatePolicy.isNewer(release.versionCode, installed)) {
            _status.value = Status("up_to_date", "Humming is up to date (${installedVersionName(ctx)}).")
            return
        }
        val available = Status("available", "Humming ${release.versionName} is available.", release.versionName)
        _status.value = available
        if (!UpdatePolicy.mayDownload(autoDownload(ctx), unmetered(ctx), userAsked)) {
            if (!userAsked) notify(ctx, "Humming ${release.versionName} is available", "Open Settings › Updates to install it.", null)
            return
        }
        val apk = download(ctx, release)
        verify(ctx, apk, release)
        _status.value = Status("installing", "Installing Humming ${release.versionName}…", release.versionName)
        install(ctx, apk)
    }

    private fun unmetered(ctx: Context): Boolean = runCatching {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }.getOrDefault(false)

    private fun open(url: String): HttpURLConnection {
        require(url.startsWith("https://")) { "updates are fetched over HTTPS only" }
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", "Humming-Android")
        if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode} for ${url.substringAfterLast('/')}")
        return c
    }

    private fun fetch(url: String, maxBytes: Int): ByteArray {
        val c = open(url)
        try {
            val bytes = c.inputStream.use { it.readBytes() }
            if (bytes.size > maxBytes) throw IOException("update feed too large")
            return bytes
        } finally {
            c.disconnect()
        }
    }

    private fun updatesDir(ctx: Context): File = File(ctx.cacheDir, "updates").apply { mkdirs() }

    /** Downloads the APK named by the feed, computing its SHA-256 on the way. */
    private fun download(ctx: Context, release: UpdatePolicy.Release): File {
        val dir = updatesDir(ctx)
        dir.listFiles()?.forEach { if (it.name != release.apk) it.delete() }
        val out = File(dir, release.apk)
        val part = File(dir, release.apk + ".part")
        val c = open(FEED_BASE + release.apk)
        try {
            val total = c.contentLengthLong
            if (total > MAX_APK_BYTES) throw IOException("update file too large")
            var done = 0L
            c.inputStream.use { input ->
                part.outputStream().use { output ->
                    val buf = ByteArray(1 shl 16)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (done > MAX_APK_BYTES) throw IOException("update file too large")
                        val pct = if (total > 0) (100 * done / total).toInt() else 0
                        if (pct != _status.value.pct) {
                            _status.value = Status("downloading", "Downloading Humming ${release.versionName}… $pct%", release.versionName, pct)
                        }
                    }
                }
            }
        } finally {
            c.disconnect()
        }
        out.delete()
        if (!part.renameTo(out)) throw IOException("could not save the update")
        return out
    }

    private fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** SHA-256 against SHA256SUMS.txt, then the signing certificate, package name and versionCode. Deletes on failure. */
    private fun verify(ctx: Context, apk: File, release: UpdatePolicy.Release) {
        _status.value = Status("verifying", "Checking Humming ${release.versionName}…", release.versionName, 100)
        try {
            val sums = String(fetch(SUMS_URL, 64 * 1024), Charsets.UTF_8)
            val want = UpdatePolicy.sumFor(sums, release.apk) ?: throw SecurityException("${release.apk} is not listed in SHA256SUMS.txt")
            val got = sha256(apk)
            if (got != want) throw SecurityException("checksum mismatch: the download is not the published file")
            val info = archiveInfo(ctx, apk) ?: throw SecurityException("the download is not a readable APK")
            if (info.packageName != ctx.packageName) throw SecurityException("the download is a different app")
            val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
            if (code != release.versionCode.toLong()) throw SecurityException("the download's version does not match the feed")
            val certs = signerSha256(info)
            if (certs.isEmpty() || !certs.all { UpdatePolicy.certMatches(it) }) {
                throw SecurityException("the download is not signed with Humming's release key")
            }
            val mine = runCatching { signerSha256(ownInfo(ctx)) }.getOrDefault(emptyList())
            if (mine.isNotEmpty() && !mine.all { UpdatePolicy.certMatches(it) }) {
                throw SecurityException("this copy of Humming is signed with a different key, so Android cannot update it in place; install the release APK by hand once")
            }
        } catch (e: Exception) {
            apk.delete()
            throw e
        }
    }

    @Suppress("DEPRECATION")
    private fun archiveInfo(ctx: Context, apk: File): PackageInfo? {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        return if (Build.VERSION.SDK_INT >= 33) {
            ctx.packageManager.getPackageArchiveInfo(apk.path, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            ctx.packageManager.getPackageArchiveInfo(apk.path, flags)
        }
    }

    @Suppress("DEPRECATION")
    private fun ownInfo(ctx: Context): PackageInfo {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        return ctx.packageManager.getPackageInfo(ctx.packageName, flags)
    }

    @Suppress("DEPRECATION")
    private fun signerSha256(info: PackageInfo): List<String> {
        val sigs = if (Build.VERSION.SDK_INT >= 28) {
            val si = info.signingInfo ?: return emptyList()
            if (si.hasMultipleSigners()) si.apkContentsSigners else si.signingCertificateHistory?.takeLast(1)?.toTypedArray()
        } else {
            info.signatures
        } ?: return emptyList()
        return sigs.map { s -> MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02X".format(it) } }
    }

    /** Streams the APK into a PackageInstaller session and commits it; [UpdateInstallReceiver] gets the outcome. */
    private fun install(ctx: Context, apk: File) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(ctx.packageName)
        // Always let Android ask the user to confirm the update.
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(ctx, UpdateInstallReceiver::class.java).setAction(UpdateInstallReceiver.ACTION)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            session.commit(PendingIntent.getBroadcast(ctx, id, intent, flags).intentSender)
        }
    }

    /** Android wants the user's OK: show it now if Settings > Updates is open, else a notification that opens it. */
    internal fun onNeedsConfirm(ctx: Context, confirm: Intent?) {
        val v = _status.value.latest
        _status.value = Status("ready", "Humming ${v ?: ""} is ready. Tap Install to finish (Android asks you to confirm).".replace("  ", " "), v, 100)
        if (confirm == null) return
        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (screenVisible) {
            runCatching { ctx.startActivity(confirm) }.onSuccess { return }
        }
        val pi = PendingIntent.getActivity(ctx, 7, confirm, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        notify(ctx, "Humming ${v ?: "update"} is ready to install", "Tap to install the update.", pi)
    }

    internal fun onResult(success: Boolean, message: String?) {
        val v = _status.value.latest
        _status.value = if (success) Status("up_to_date", "Humming ${v ?: ""} is installed.".replace("  ", " "), v, 100)
        else Status("error", "Update not installed: ${message ?: "cancelled"}", v)
    }

    private fun notify(ctx: Context, title: String, text: String, tap: PendingIntent?) {
        runCatching {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "App updates", NotificationManager.IMPORTANCE_DEFAULT))
            val n = NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setColor(0xFFE5486B.toInt()) // Humming brand pink
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .apply { if (tap != null) setContentIntent(tap) }
                .build()
            nm.notify(4242, n)
        }
    }
}
