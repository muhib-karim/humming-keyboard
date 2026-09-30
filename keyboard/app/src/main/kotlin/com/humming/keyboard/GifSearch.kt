package com.humming.keyboard

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Online GIF search for the sticker panel: OFF by default, and the only network use besides the one-time voice
 * model download (NetworkAuditTest). It runs only when the user has turned it on in Settings > Stickers & GIFs
 * (which shows what is sent where), never in incognito unless separately allowed, and only for the words typed
 * into the sticker search box. The source is Openverse (openverse.org, WordPress Foundation), a free search of
 * openly licensed media that needs no account or API key; results are limited to CC0, public-domain and CC BY
 * images, and each result carries its creator and licence so it can be credited.
 */
object GifSearch {
    const val API = "https://api.openverse.org/v1/images/"
    const val USER_AGENT = "HummingKeyboard/1.2 (open-source Android keyboard)"
    const val MAX_GIF_BYTES = 6L * 1024 * 1024
    private const val TIMEOUT_MS = 8000
    /** Openverse answers 401 "page_size may not exceed 20 for anonymous requests" above this (1.2 CI found it). */
    const val MAX_PAGE_SIZE = 20

    data class Gif(
        val id: String,
        val title: String,
        val url: String,
        val thumbnail: String,
        val license: String,
        val creator: String,
        val source: String,
    ) {
        /** "CC BY · creator · source" for the credit line. */
        val credit: String get() = listOf(licenseName(license), creator, source)
            .filter { it.isNotBlank() }.joinToString(" \u00B7 ")
    }

    fun licenseName(code: String): String = when (code.lowercase()) {
        "cc0" -> "CC0"
        "pdm" -> "Public domain"
        "" -> ""
        else -> "CC " + code.uppercase().replace('-', ' ')
    }

    /** May the panel search online right now? */
    fun allowed(enabled: Boolean, incognito: Boolean, allowInIncognito: Boolean): Boolean =
        enabled && (!incognito || allowInIncognito)

    /** The search request: animated GIFs only, openly licensed (CC0, public domain, CC BY), no mature results. */
    fun queryUrl(query: String, page: Int = 1, pageSize: Int = MAX_PAGE_SIZE): String {
        val q = URLEncoder.encode(query.trim().take(80), "UTF-8")
        val size = pageSize.coerceIn(1, MAX_PAGE_SIZE)
        return "$API?q=$q&extension=gif&license=cc0,pdm,by&mature=false&page_size=$size&page=$page"
    }

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(body: String): List<Gif> {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() as? JsonObject ?: return emptyList()
        val results = root["results"] as? JsonArray ?: return emptyList()
        return results.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            fun s(k: String) = o[k]?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() } ?: ""
            val url = s("url")
            if (!url.startsWith("https://")) return@mapNotNull null
            val size = o["filesize"]?.let { runCatching { it.jsonPrimitive.intOrNull }.getOrNull() }
            if (size != null && size > MAX_GIF_BYTES) return@mapNotNull null
            Gif(s("id"), s("title"), url, s("thumbnail").ifEmpty { url }, s("license"), s("creator"), s("source"))
        }
    }

    private fun open(url: String): HttpURLConnection {
        require(url.startsWith("https://")) { "https only" }
        return (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", USER_AGENT)
        }
    }

    /** Blocking; call from Dispatchers.IO. Empty on any failure. */
    fun search(query: String): List<Gif> = searchOrNull(query) ?: emptyList()

    /** Blocking; call from Dispatchers.IO. Null when Openverse could not be reached or refused the request. */
    fun searchOrNull(query: String): List<Gif>? {
        if (query.isBlank()) return emptyList()
        return runCatching {
            val c = open(queryUrl(query))
            try {
                if (c.responseCode != 200) null else parse(c.inputStream.bufferedReader().use { it.readText() })
            } finally {
                c.disconnect()
            }
        }.getOrNull()
    }

    /** Downloads [url] into [out] (at most [limit] bytes); true on success. Blocking; call from Dispatchers.IO. */
    fun download(url: String, out: File, limit: Long = MAX_GIF_BYTES): Boolean = runCatching {
        val c = open(url)
        try {
            if (c.responseCode != 200) return false
            c.inputStream.use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > limit) error("too big")
                        o.write(buf, 0, n)
                    }
                }
            }
            out.length() > 0
        } finally {
            c.disconnect()
        }
    }.getOrElse { out.delete(); false }
}
