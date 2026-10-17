package com.humming.keyboard

/**
 * Humming: which shared image URIs the "Copy to clipboard" share target (FlorisCopyToClipboardActivity, exported) may
 * open. Only content:// URIs served by another app: a file:// URI or one of Humming's own providers would let any app
 * make Humming read its private files with Humming's permissions and put them on the clipboard.
 * Pure Kotlin (SharedUriPolicyTest).
 */
object SharedUriPolicy {
    fun acceptable(scheme: String?, authority: String?, ownPackage: String): Boolean {
        if (!scheme.equals("content", ignoreCase = true) || authority.isNullOrEmpty()) return false
        return authority != ownPackage && !authority.startsWith("$ownPackage.")
    }
}
