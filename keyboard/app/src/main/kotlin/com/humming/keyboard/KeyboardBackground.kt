package com.humming.keyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/** Copies a picked photo into app storage, scaled to at most 1600 px, for the keyboard background. */
object KeyboardBackground {
    private const val PREFIX = "keyboard_background"

    fun import(ctx: Context, uri: Uri): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null
        var sample = 1
        while (longest / (sample * 2) >= 1600) sample *= 2
        val bmp = ctx.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        clear(ctx)
        val out = File(ctx.filesDir, "${PREFIX}_${System.currentTimeMillis()}.jpg")
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        bmp.recycle()
        out.absolutePath
    }.getOrNull()

    fun clear(ctx: Context) {
        ctx.filesDir.listFiles()?.filter { it.name.startsWith(PREFIX) }?.forEach { it.delete() }
    }

    fun files(ctx: Context): List<File> = ctx.filesDir.listFiles()?.filter { it.name.startsWith(PREFIX) } ?: emptyList()
}
