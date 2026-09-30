package com.humming.keyboard

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.Locale

/**
 * Humming stickers: a built-in pack (assets/stickers, rendered from Noto Emoji images, Apache-2.0, with captions
 * in the Bangers font, OFL-1.1) plus images the user imports (kept in app storage, never uploaded). The sticker
 * panel is off until turned on in Settings > Stickers & GIFs. Sending a sticker hands the app a content:// link
 * through the keyboard's FileProvider when the app accepts images (Android's commitContent); nothing touches the
 * network.
 */
object Stickers {
    enum class Source { BUILT_IN, USER, GIF }

    data class Sticker(val id: String, val label: String, val keywords: String, val source: Source, val path: String)

    const val MAX_USER_STICKERS = 300
    const val MAX_IMPORT_BYTES = 8L * 1024 * 1024
    private val IMAGE_EXT = setOf("png", "jpg", "jpeg", "webp", "gif")

    /** The built-in pack: "file<TAB>label<TAB>keywords" lines of assets/stickers/index.tsv. */
    fun parseIndex(lines: Sequence<String>): List<Sticker> = lines.mapNotNull { l ->
        val p = l.split('\t')
        if (p.size < 2 || p[0].isBlank()) null
        else Sticker("builtin:" + p[0], p[1], p.getOrElse(2) { "" }, Source.BUILT_IN, "stickers/" + p[0])
    }.toList()

    fun builtIn(ctx: Context): List<Sticker> = runCatching {
        ctx.assets.open("stickers/index.tsv").bufferedReader().useLines { parseIndex(it) }
    }.getOrDefault(emptyList())

    fun userDir(ctx: Context): File = File(ctx.applicationContext.filesDir, "stickers").apply { mkdirs() }

    fun user(ctx: Context): List<Sticker> = (userDir(ctx).listFiles() ?: emptyArray())
        .filter { it.isFile && it.extension.lowercase(Locale.ROOT) in IMAGE_EXT }
        .sortedByDescending { it.lastModified() }
        .map { Sticker("user:" + it.name, it.nameWithoutExtension.substringAfter('_').replace('-', ' '), "", Source.USER, it.absolutePath) }

    /** Every sticker whose caption or keywords contain every word of [query] (all stickers for a blank query). */
    fun search(all: List<Sticker>, query: String): List<Sticker> {
        val words = query.lowercase(Locale.ROOT).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return all
        return all.filter { s ->
            val hay = (s.label + " " + s.keywords).lowercase(Locale.ROOT)
            words.all { hay.contains(it) }
        }
    }

    /** The MIME type a sticker file is sent as. */
    fun mimeOf(path: String): String = when (path.substringAfterLast('.').lowercase(Locale.ROOT)) {
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "jpg", "jpeg" -> "image/jpeg"
        else -> "image/png"
    }

    /** True when an editor that lists [accepted] MIME types (e.g. "image/ *") takes [mime]. */
    fun accepts(accepted: Array<String>, mime: String): Boolean = accepted.any { a ->
        a == "*/*" || a.equals(mime, ignoreCase = true) ||
            (a.endsWith("/*") && mime.startsWith(a.dropLast(1), ignoreCase = true))
    }

    /** Copies picked images into the user's sticker folder; returns how many were added. */
    fun import(ctx: Context, uris: List<Uri>): Int {
        var added = 0
        val dir = userDir(ctx)
        for (uri in uris) {
            if ((dir.listFiles()?.size ?: 0) >= MAX_USER_STICKERS) break
            var target: File? = null
            runCatching {
                val type = ctx.contentResolver.getType(uri) ?: "image/png"
                val ext = when {
                    type.endsWith("gif") -> "gif"
                    type.endsWith("webp") -> "webp"
                    type.endsWith("jpeg") || type.endsWith("jpg") -> "jpg"
                    else -> "png"
                }
                val out = File(dir, "${System.currentTimeMillis()}_${added + 1}.$ext")
                target = out
                ctx.contentResolver.openInputStream(uri)?.use { input ->
                    out.outputStream().use { o ->
                        val buf = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > MAX_IMPORT_BYTES) error("too big")
                            o.write(buf, 0, n)
                        }
                    }
                }
                if (out.length() > 0) added++ else out.delete()
            }.onFailure { target?.delete() }
        }
        return added
    }

    fun delete(ctx: Context, s: Sticker): Boolean =
        s.source == Source.USER && File(s.path).let { it.parentFile == userDir(ctx) && it.delete() }

    /** A shareable copy in cache/stickers (built-in stickers come out of the APK's assets). */
    fun shareableFile(ctx: Context, s: Sticker): File? = runCatching {
        val dir = File(ctx.cacheDir, "stickers").apply { mkdirs() }
        when (s.source) {
            Source.BUILT_IN -> File(dir, s.path.substringAfterLast('/')).also { f ->
                if (!f.exists()) ctx.assets.open(s.path).use { i -> f.outputStream().use { i.copyTo(it) } }
            }
            else -> File(s.path).takeIf { it.exists() }?.let { src ->
                if (src.parentFile == dir) src else File(dir, "u_" + src.name).also { if (!it.exists()) src.copyTo(it) }
            }
        }
    }.getOrNull()
}
