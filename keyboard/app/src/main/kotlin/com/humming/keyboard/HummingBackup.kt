package com.humming.keyboard

import android.content.Context
import java.io.File

/**
 * Adds Humming's own settings to FlorisBoard's Backup & restore file (Settings > Other > Backup):
 * the voice settings (SharedPreferences "humming"), the words the keyboard learned (predict_user.tsv) and the
 * keyboard background image. Everything stays inside the user's own backup file.
 */
object HummingBackup {
    private const val DIR = "humming"
    private const val PREFS = "humming_prefs.tsv"
    private val FILES = listOf("predict_user.tsv") + listOf("de", "es", "fr", "it", "pt").map { "predict_user_$it.tsv" }

    fun export(ctx: Context, workspaceFilesDir: File) {
        runCatching {
            val out = File(workspaceFilesDir, DIR).apply { mkdirs() }
            File(out, PREFS).writeText(serialize(Prefs.get(ctx).all))
            for (name in FILES) {
                val f = File(ctx.filesDir, name)
                if (f.exists()) f.copyTo(File(out, name), overwrite = true)
            }
            for (f in KeyboardBackground.files(ctx)) f.copyTo(File(out, f.name), overwrite = true)
        }
    }

    fun import(ctx: Context, workspaceFilesDir: File) {
        runCatching {
            val dir = File(workspaceFilesDir, DIR)
            if (!dir.isDirectory) return
            File(dir, PREFS).takeIf { it.exists() }?.let { f ->
                val e = Prefs.get(ctx).edit()
                for ((k, v) in parse(f.readText())) {
                    when (v) {
                        is Boolean -> e.putBoolean(k, v)
                        is Int -> e.putInt(k, v)
                        is Long -> e.putLong(k, v)
                        is Float -> e.putFloat(k, v)
                        is String -> e.putString(k, v)
                    }
                }
                e.apply()
            }
            for (name in FILES) {
                val f = File(dir, name)
                if (f.exists()) f.copyTo(File(ctx.filesDir, name), overwrite = true)
            }
            dir.listFiles()?.filter { it.name.startsWith("keyboard_background") }?.forEach { it.copyTo(File(ctx.filesDir, it.name), overwrite = true) }
        }
    }

    /** `type<TAB>key<TAB>value` per line; newlines and tabs in values are escaped. */
    fun serialize(all: Map<String, *>): String = buildString {
        for ((k, v) in all.toSortedMap()) {
            val type = when (v) {
                is Boolean -> "b"; is Int -> "i"; is Long -> "l"; is Float -> "f"; is String -> "s"; else -> continue
            }
            append(type).append('\t').append(esc(k)).append('\t').append(esc(v.toString())).append('\n')
        }
    }

    fun parse(text: String): Map<String, Any> = buildMap {
        for (line in text.lines()) {
            val p = line.split('\t')
            if (p.size != 3) continue
            val k = unesc(p[1])
            val s = unesc(p[2])
            val v: Any = when (p[0]) {
                "b" -> s.toBooleanStrictOrNull() ?: continue
                "i" -> s.toIntOrNull() ?: continue
                "l" -> s.toLongOrNull() ?: continue
                "f" -> s.toFloatOrNull() ?: continue
                "s" -> s
                else -> continue
            }
            put(k, v)
        }
    }

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\n", "\\n").replace("\t", "\\t")
    private fun unesc(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) { 'n' -> sb.append('\n'); 't' -> sb.append('\t'); else -> sb.append(s[i + 1]) }
                i += 2
            } else { sb.append(c); i++ }
        }
        return sb.toString()
    }
}
