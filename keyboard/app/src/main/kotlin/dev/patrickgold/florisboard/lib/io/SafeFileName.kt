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

import java.io.File
import java.util.UUID

/**
 * Turns an untrusted display name (for example `OpenableColumns.DISPLAY_NAME` reported by a
 * content provider we do not control) into a single, safe path segment, and checks that a
 * resolved file stays inside the directory it was meant for.
 */
object SafeFileName {
    const val MAX_LENGTH = 120
    private const val FALLBACK_PREFIX = "import-"
    private val RESERVED = setOf(':', '*', '?', '"', '<', '>', '|')

    /**
     * Returns the last path segment of [raw] with control and reserved characters removed and
     * leading dots stripped (so `.`, `..` and hidden names cannot appear). Falls back to a random
     * name when nothing usable is left.
     */
    fun sanitize(raw: String?, fallback: () -> String = { FALLBACK_PREFIX + UUID.randomUUID() }): String {
        if (raw == null) return fallback()
        var name = raw.substringAfterLast('/').substringAfterLast('\\')
        name = name.filter { c -> !c.isISOControl() && c !in RESERVED }
        name = name.trim().trimStart('.').trim()
        if (name.length > MAX_LENGTH) {
            val ext = name.substringAfterLast('.', "")
            name = if (ext.isNotEmpty() && ext.length < 16) {
                name.take(MAX_LENGTH - ext.length - 1) + "." + ext
            } else {
                name.take(MAX_LENGTH)
            }
        }
        return name.ifBlank { fallback() }
    }

    /** True when [child] resolves (canonically) to a path strictly inside [parent]. */
    fun isContained(parent: File, child: File): Boolean {
        val p = parent.canonicalFile.path
        val c = child.canonicalFile.path
        return c.startsWith(p + File.separator)
    }
}
