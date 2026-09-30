package com.humming.keyboard

import android.content.Context
import android.view.inputmethod.InputMethodManager

/**
 * Humming (M5): hand dictation to Google's own voice typing inside Gboard ("Rambler" on Pixels and patched
 * Gboards). Gboard is closed source, so Humming cannot embed it; it switches input methods instead, and Gboard's
 * own keyboard button (switch back / previous input method) returns to Humming.
 */
object GboardHandoff {
    const val GBOARD = "com.google.android.inputmethod.latin"

    data class Ime(val id: String, val packageName: String, val subtypeModes: List<String>)
    data class Target(val id: String, val subtypeIndex: Int?)

    /** Gboard's voice subtype when it declares one, else Gboard itself (its mic is one tap away). Pure: GboardHandoffTest. */
    fun pick(enabled: List<Ime>): Target? {
        val g = enabled.firstOrNull { it.packageName == GBOARD } ?: return null
        val v = g.subtypeModes.indexOfFirst { it == "voice" }
        return Target(g.id, v.takeIf { it >= 0 })
    }

    fun enabledImes(ctx: Context): List<Ime> {
        val imm = ctx.getSystemService(InputMethodManager::class.java) ?: return emptyList()
        return runCatching {
            imm.enabledInputMethodList.map { imi ->
                val subs = imm.getEnabledInputMethodSubtypeList(imi, true)
                Ime(imi.id, imi.packageName, subs.map { it.mode ?: "" })
            }
        }.getOrDefault(emptyList())
    }

    /** Show the "Gboard voice" option only when Gboard is installed and enabled as a keyboard. */
    fun available(ctx: Context): Boolean = pick(enabledImes(ctx)) != null
}
