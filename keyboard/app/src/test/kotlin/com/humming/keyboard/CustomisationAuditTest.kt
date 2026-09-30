package com.humming.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Humming 1.2 customisation audit, run on every build: every user-facing preference in AppPrefs must have a control
 * in a Settings screen (nothing configurable only by editing code), and the key timings that used to be fixed
 * system constants are settings with sane ranges.
 */
class CustomisationAuditTest {
    private val kotlin = File("src/main/kotlin")
    private val prefDecl = Regex("""^\s*val (\w+) = (boolean|int|float|string|enum|custom|long|localTime)\(""", RegexOption.MULTILINE)

    /** Internal state, developer tools and values the keyboard sets itself (not user settings). */
    private val internal = setOf(
        "showPrimaryClip", "showInputStateOverlay", "showSpellingOverlay", "showInlineAutofillOverlay",
        "showKeyTouchBoundaries", "showDragAndDropHelpers", "preferredHairStyle", "historyData", "isImeSetUp",
        "versionOnInstall", "versionLastUse", "versionLastChangelog", "notificationPermissionState",
        "hummingFloatOffsetX", "hummingFloatOffsetY", "activeSubtypeId", "subtypes", "actionArrangement",
        "sharedActionsExpanded", "extendedActionsExpanded", "forceIncognitoModeFromDynamic",
    )

    @Test fun everyUserPreferenceHasASettingsControl() {
        val prefs = File(kotlin, "dev/patrickgold/florisboard/app/AppPrefs.kt").readText()
        val names = prefDecl.findAll(prefs).map { it.groupValues[1] }.toSet()
        assertTrue("found ${names.size} preferences", names.size > 150)
        val screens = File(kotlin, "dev/patrickgold/florisboard/app/settings").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }.joinToString("\n") { it.readText() }
        val missing = names.filter { it !in internal && !Regex("""\.$it\b""").containsMatchIn(screens) }
        assertEquals("preferences with no Settings control: $missing", emptyList<String>(), missing)
    }

    @Test fun ownersListIsAllCustomisable() {
        // The owner's list: prediction strength, suggestion count, key height, themes and colours, sounds,
        // haptics, long-press delay, layouts; plus the 1.2 key timings.
        for (q in listOf(
            "smart prediction", "suggestions shown", "keyboard height", "theme", "accent colour", "key press sound",
            "vibration", "long-press delay", "languages and layouts", "key repeat", "double-tap", "autocorrect",
        )) assertFalse("settings search finds \"$q\"", SettingsIndex.search(q).isEmpty())
    }

    @Test fun keyTimingsAreSettingsWithSaneRanges() {
        assertEquals(50L, KeyTiming.repeatDelay(KeyTiming.DEFAULT_REPEAT_MS))
        assertEquals(250L, KeyTiming.repeatDelay(50, 5f))
        assertEquals(KeyTiming.MIN_REPEAT_MS.toLong(), KeyTiming.repeatDelay(0))
        assertEquals(KeyTiming.MAX_REPEAT_MS.toLong(), KeyTiming.repeatDelay(10_000))
        assertTrue(KeyTiming.isDoubleTap(200, KeyTiming.DEFAULT_DOUBLE_TAP_MS))
        assertFalse(KeyTiming.isDoubleTap(400, KeyTiming.DEFAULT_DOUBLE_TAP_MS))
        assertTrue(KeyTiming.isDoubleTap(400, 600))
        assertFalse(KeyTiming.isDoubleTap(-1, 600))
        assertFalse("clamped to the maximum", KeyTiming.isDoubleTap(900, 5_000))
        val screen = File(kotlin, "dev/patrickgold/florisboard/app/settings/keyboard/KeyboardScreen.kt").readText()
        assertTrue(screen.contains("hummingKeyRepeatDelay") && screen.contains("hummingDoubleTapTimeout"))
    }
}
