package com.humming.keyboard.predict

import android.content.Context
import java.io.File

/**
 * Loads the predictor once per process (off the main thread) from the bundled assets and the user's own
 * on-device model, and saves what was learned. The lexicon and n-gram files ship inside the APK: nothing is
 * downloaded and nothing leaves the device.
 */
object PredictionEngine {
    /** Languages with a bundled dictionary. English also has next-word statistics and emoji hints. */
    val LANGUAGES = listOf("en", "de", "es", "fr", "it", "pt")

    private val instances = java.util.concurrent.ConcurrentHashMap<String, Predictor>()
    @Volatile private var lastSave = 0L

    /** The dictionary language for a subtype locale ("de-CH" -> "de"); null when none is bundled. */
    fun languageFor(localeTag: String?): String? {
        val lang = localeTag?.substringBefore('-')?.substringBefore('_')?.lowercase(java.util.Locale.ROOT) ?: return "en"
        return lang.takeIf { it in LANGUAGES }
    }

    fun loaded(lang: String = "en"): Predictor? = instances[lang]

    fun get(ctx: Context, lang: String = "en"): Predictor {
        instances[lang]?.let { return it }
        synchronized(this) {
            instances[lang]?.let { return it }
            val app = ctx.applicationContext
            val lexicon = app.assets.open("predict/lexicon_$lang.tsv").bufferedReader().useLines { Lexicon.parse(it) }
            val ngrams = if (lang == "en") Ngrams(app.assets.open("predict/ngrams_en.tsv").use { it.readBytes() }) else Ngrams(ByteArray(0))
            val user = UserModel(userFile(app, lang))
            return Predictor(lexicon, ngrams, user).also { instances[lang] = it }
        }
    }

    @Volatile private var emoji: Map<String, String>? = null

    /** English word -> emoji hints (assets/predict/emoji_en.tsv, from Unicode CLDR annotations). */
    fun emoji(ctx: Context): Map<String, String> {
        emoji?.let { return it }
        val m = runCatching {
            ctx.applicationContext.assets.open("predict/emoji_en.tsv").bufferedReader().useLines { lines ->
                lines.mapNotNull { l -> l.indexOf('\t').takeIf { it > 0 }?.let { l.substring(0, it) to l.substring(it + 1) } }.toMap()
            }
        }.getOrDefault(emptyMap())
        emoji = m
        return m
    }

    fun userFile(ctx: Context, lang: String = "en"): File =
        File(ctx.applicationContext.filesDir, if (lang == "en") "predict_user.tsv" else "predict_user_$lang.tsv")

    /** Every language's learned-words file (backup, clear). */
    fun userFiles(ctx: Context): List<File> = LANGUAGES.map { userFile(ctx, it) }

    /** Saves learned words at most every 15 s while typing (and always when [force] is set). */
    fun saveSoon(force: Boolean = false) {
        val all = instances.values.toList()
        if (all.isEmpty()) return
        val now = System.currentTimeMillis()
        if (force || now - lastSave > 15_000) {
            lastSave = now
            if (force) Thread({ all.forEach { it.user.save() } }, "humming-predict-save").start() else all.forEach { it.user.save() }
        }
    }
}
