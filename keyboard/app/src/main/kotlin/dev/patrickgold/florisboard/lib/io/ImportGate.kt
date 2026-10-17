/*
 * Copyright (C) 2021-2025 The FlorisBoard Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.patrickgold.florisboard.lib.io

/**
 * Decides whether a URI handed to the exported settings activity (VIEW / SEND) may be copied
 * into the import cache. Only `content://` URIs from another app's provider with an extension
 * MIME type are accepted; everything else is ignored before any file is written.
 */
object ImportGate {
    val ALLOWED_MIME_TYPES = setOf(
        "application/vnd.florisboard.extension+zip",
        "application/zip",
        "application/x-zip-compressed", // Windows-origin zips, some messengers
        "application/x-zip",
        "application/octet-stream",
    )

    fun isAllowed(scheme: String?, authority: String?, mimeType: String?, ownPackageName: String): Boolean {
        if (!scheme.equals("content", ignoreCase = true)) return false
        if (authority.isNullOrBlank()) return false
        // Never let a caller point us at one of our own providers.
        val auth = authority.substringAfterLast('@')
        if (auth == ownPackageName || auth.startsWith("$ownPackageName.")) return false
        val type = mimeType?.substringBefore(';')?.trim()?.lowercase() ?: return false
        return type in ALLOWED_MIME_TYPES
    }
}
