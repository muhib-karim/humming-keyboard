/*
 * Humming (added, debug builds only): lets the CI screenshot run put the voice panel in a mid-dictation state
 * (no microphone on the emulator) and flip a few preferences.
 */
package com.humming.keyboard.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.patrickgold.florisboard.clipboardManager
import dev.patrickgold.florisboard.ime.ImeUiMode
import dev.patrickgold.florisboard.ime.voice.HummingVoice
import dev.patrickgold.florisboard.keyboardManager

class DebugVoiceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "com.humming.keyboard.debug.VOICE_PREVIEW" -> HummingVoice.previewListening(
                intent.getStringExtra("text") ?: "Running ten minutes late, save me a seat and order the dumplings",
                intent.getBooleanExtra("clean", true),
            )
            "com.humming.keyboard.debug.VOICE_CLOSE" -> HummingVoice.close()
            // Real dictation with a WAV file as the microphone (the emulator has no mic), then Done.
            "com.humming.keyboard.debug.VOICE_TEST" -> {
                com.humming.keyboard.TestAudio.set(intent.getStringExtra("wav"))
                HummingVoice.start(context)
            }
            "com.humming.keyboard.debug.VOICE_DONE" -> HummingVoice.done()
            // M5: fetch the voice model now (CI prepares it before going offline).
            "com.humming.keyboard.debug.MODEL_DOWNLOAD" ->
                com.humming.keyboard.ModelDownloader.ensure(context, userForced = true)
            // M5: a few clipboard items for the clipboard screenshot.
            "com.humming.keyboard.debug.CLIP_SEED" -> {
                val cm by context.clipboardManager()
                (intent.getStringExtra("items") ?: "").split("|").filter { it.isNotBlank() }.forEach { cm.addNewPlaintext(it) }
            }
            "com.humming.keyboard.debug.VOICE_MENU" -> HummingVoice.showMenu(intent.getBooleanExtra("on", true))
            "com.humming.keyboard.debug.HANDOFF_GBOARD" -> {
                val ok = dev.patrickgold.florisboard.FlorisImeService.switchToGboardVoice()
                android.util.Log.i("HUMMING", "HUMMING_HANDOFF result=$ok available=${com.humming.keyboard.GboardHandoff.available(context)}")
            }
            "com.humming.keyboard.debug.UI_MODE" -> {
                val km by context.keyboardManager()
                km.activeState.imeUiMode = when (intent.getStringExtra("mode")) {
                    "clipboard" -> ImeUiMode.CLIPBOARD
                    "media" -> ImeUiMode.MEDIA
                    else -> ImeUiMode.TEXT
                }
            }
        }
    }
}
