/*
 * Humming (added file, Apache-2.0 like the rest of this fork).
 */
package dev.patrickgold.florisboard.app.ext

import androidx.compose.runtime.Composable
import dev.patrickgold.florisboard.R
import org.florisboard.lib.compose.stringRes

/**
 * Built-in extensions keep their upstream IDs (saved settings, subtypes and themes refer to them, so they are never
 * renamed); the settings UI shows "Built-in" in place of the raw ID.
 */
fun isBuiltInExtensionId(id: String): Boolean = id.startsWith("org.florisboard.")

/** What the settings UI shows for an extension ID: "Built-in" for the bundled ones, else the ID itself. */
@Composable
fun extensionDisplayId(id: String): String =
    if (isBuiltInExtensionId(id)) stringRes(R.string.ext__meta__builtin) else id
