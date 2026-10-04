package com.humming.keyboard

import dev.patrickgold.florisboard.app.Routes
import java.util.Locale

/**
 * The index behind Settings > Search: every user-facing setting with where it lives and the words people use for it.
 * Covered by SettingsIndexTest (every entry resolves to a screen; common searches find the right setting).
 */
object SettingsIndex {
    enum class Screen { STICKERS, VOICE, LANGUAGES, THEME, KEYBOARD, FEEDBACK, TOOLBAR, TYPING, SHORTCUTS, DICTIONARY, GESTURES, CLIPBOARD, EMOJI, PRIVACY, BACKUP, RESTORE, OTHER, ABOUT }

    data class Entry(val title: String, val path: String, val screen: Screen, val keywords: String = "")

    val entries: List<Entry> = listOf(
        Entry("Voice model (Fast / Best)", "Voice", Screen.VOICE, "dictation parakeet engine model download offline speech"),
        Entry("Download on Wi-Fi only", "Voice", Screen.VOICE, "data mobile network model"),
        Entry("Clean mode (remove um, uh)", "Voice", Screen.VOICE, "filler punctuation dictation"),
        Entry("Accuracy mode (strict clean-up)", "Voice", Screen.VOICE, "accuracy filler like basically you know period strict"),
        Entry("Bluetooth headset microphone", "Voice", Screen.VOICE, "bt mic headset"),
        Entry("Voice incognito", "Voice", Screen.VOICE, "privacy history dictation"),
        Entry("Dictation history retention", "Voice", Screen.VOICE, "history keep delete"),
        Entry("Personal dictionary for dictation", "Voice", Screen.VOICE, "names words spelling"),
        Entry("Languages and layouts", "Languages & layouts", Screen.LANGUAGES, "language multilingual layout qwerty azerty qwertz subtype spanish german french add"),
        Entry("Theme (light, dark, AMOLED)", "Theme", Screen.THEME, "dark light black amoled night day colour color"),
        Entry("Accent colour", "Theme", Screen.THEME, "color colour custom accent"),
        Entry("Background image", "Theme", Screen.THEME, "photo picture wallpaper background image"),
        Entry("Theme editor (colours, fonts, key shapes)", "Theme", Screen.THEME, "font family color colour custom corner shape editor"),
        Entry("Keyboard height", "Keyboard", Screen.KEYBOARD, "size tall height bigger smaller"),
        Entry("Key spacing", "Keyboard", Screen.KEYBOARD, "gap spacing key size"),
        Entry("Font size", "Keyboard", Screen.KEYBOARD, "font text size label"),
        Entry("Number row", "Keyboard", Screen.KEYBOARD, "numbers digits row"),
        Entry("Long-press symbols (hints)", "Keyboard", Screen.KEYBOARD, "long press symbols hint popup"),
        Entry("Long-press delay", "Keyboard", Screen.KEYBOARD, "long press delay"),
        Entry("Key repeat speed", "Keyboard", Screen.KEYBOARD, "repeat delete backspace hold speed fast slow"),
        Entry("Double-tap speed", "Keyboard", Screen.KEYBOARD, "double tap space period shift caps lock speed"),
        Entry("One-handed mode", "Keyboard", Screen.KEYBOARD, "one hand left right thumb"),
        Entry("Split keyboard (landscape, tablets)", "Keyboard", Screen.KEYBOARD, "split tablet landscape"),
        Entry("Floating keyboard", "Keyboard", Screen.KEYBOARD, "float floating move drag"),
        Entry("Bottom offset", "Keyboard", Screen.KEYBOARD, "offset bottom gesture bar"),
        Entry("Key press sound", "Keyboard > Input feedback", Screen.FEEDBACK, "sound audio click volume"),
        Entry("Vibration on key press", "Keyboard > Input feedback", Screen.FEEDBACK, "vibration haptic vibrate"),
        Entry("Voice button size", "Smartbar", Screen.TOOLBAR, "mic microphone voice button size bigger smaller"),
        Entry("Animate the toolbar", "Smartbar", Screen.TOOLBAR, "animation slide smartbar"),
        Entry("Toolbar buttons (add, remove, reorder)", "Smartbar", Screen.TOOLBAR, "toolbar smartbar quick actions buttons reorder"),
        Entry("Autocorrect strength", "Typing > Prediction", Screen.TYPING, "autocorrect correction strength aggressive mild off"),
        Entry("Next-word prediction", "Typing > Prediction", Screen.TYPING, "prediction next word"),
        Entry("Learn from my typing", "Typing > Prediction", Screen.TYPING, "learning learn personal words privacy"),
        Entry("Clear learned words", "Privacy", Screen.PRIVACY, "forget reset learned delete"),
        Entry("Emoji in suggestions", "Typing > Prediction", Screen.TYPING, "emoji suggestion strip"),
        Entry("Suggestions on/off", "Typing", Screen.TYPING, "suggestions strip candidates"),
        Entry("Auto-capitalisation", "Typing", Screen.TYPING, "capital caps capitalization"),
        Entry("Double-space period", "Typing", Screen.TYPING, "period full stop space"),
        Entry("Text shortcuts (text expansion)", "Typing > Text shortcuts", Screen.SHORTCUTS, "shortcut expansion snippet phrase macro"),
        Entry("Personal dictionary", "Typing > Dictionary", Screen.DICTIONARY, "dictionary words user"),
        Entry("Glide typing (swipe)", "Gestures", Screen.GESTURES, "glide swipe gesture typing trail"),
        Entry("Swipe gestures", "Gestures", Screen.GESTURES, "swipe up down left right space bar delete cursor trackpad"),
        Entry("Clipboard history", "Clipboard", Screen.CLIPBOARD, "clipboard copy paste history"),
        Entry("Clipboard retention", "Clipboard", Screen.CLIPBOARD, "clipboard keep delete clean old retention"),
        Entry("Emoji skin tone and history", "Emoji", Screen.EMOJI, "emoji skin tone recent history"),
        Entry("Sticker panel", "Emoji > Stickers & GIFs", Screen.STICKERS, "sticker stickers panel pack"),
        Entry("Add my own stickers", "Emoji > Stickers & GIFs", Screen.STICKERS, "sticker import image photo own custom"),
        Entry("Online GIF search", "Emoji > Stickers & GIFs", Screen.STICKERS, "gif gifs animated search online openverse network"),
        Entry("Smart prediction strength (neural model)", "Typing > Prediction", Screen.TYPING, "neural smart ai model prediction strength next word"),
        Entry("Weight of my own typing", "Typing > Prediction", Screen.TYPING, "personal learning weight history"),
        Entry("Suggestions shown in the strip", "Typing > Prediction", Screen.TYPING, "suggestion count number candidates strip slots"),
        Entry("Best suggestion in the middle", "Typing > Prediction", Screen.TYPING, "strip order middle left layout suggestion position"),
        Entry("Emoji search (type :word)", "Emoji", Screen.EMOJI, "emoji search colon find"),
        Entry("Incognito mode", "Privacy", Screen.PRIVACY, "incognito private privacy"),
        Entry("Privacy and network", "Privacy", Screen.PRIVACY, "privacy network offline internet data"),
        Entry("Export settings to a file", "Other > Backup", Screen.BACKUP, "export backup save file settings"),
        Entry("Import settings from a file", "Other > Restore", Screen.RESTORE, "import restore load file settings"),
        Entry("Settings app theme", "Other", Screen.OTHER, "app theme settings language icon"),
        Entry("Licences", "About", Screen.ABOUT, "licence license open source notice parakeet credits"),
    )

    fun search(query: String): List<Entry> {
        val q = query.trim().lowercase(Locale.ROOT)
        if (q.isEmpty()) return entries
        val terms = q.split(Regex("\\s+"))
        return entries
            .map { e ->
                val hay = (e.title + " " + e.path + " " + e.keywords).lowercase(Locale.ROOT)
                val title = e.title.lowercase(Locale.ROOT)
                val hits = terms.count { hay.contains(it) }
                val score = hits * 10 + (if (title.startsWith(q)) 5 else 0) + (if (title.contains(q)) 3 else 0)
                e to if (hits == terms.size) score else 0
            }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    fun route(e: Entry): Any = when (e.screen) {
        Screen.STICKERS -> Routes.Settings.Stickers
        Screen.VOICE -> Routes.Settings.Voice
        Screen.LANGUAGES -> Routes.Settings.Localization
        Screen.THEME -> Routes.Settings.Theme
        Screen.KEYBOARD -> Routes.Settings.Keyboard
        Screen.FEEDBACK -> Routes.Settings.InputFeedback
        Screen.TOOLBAR -> Routes.Settings.Smartbar
        Screen.TYPING -> Routes.Settings.Typing
        Screen.SHORTCUTS -> Routes.Settings.TextShortcuts
        Screen.DICTIONARY -> Routes.Settings.Dictionary
        Screen.GESTURES -> Routes.Settings.Gestures
        Screen.CLIPBOARD -> Routes.Settings.Clipboard
        Screen.EMOJI -> Routes.Settings.Media
        Screen.PRIVACY -> Routes.Settings.Privacy
        Screen.BACKUP -> Routes.Settings.Backup
        Screen.RESTORE -> Routes.Settings.Restore
        Screen.OTHER -> Routes.Settings.Other
        Screen.ABOUT -> Routes.Settings.About
    }
}
