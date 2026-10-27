/*
 * Humming (added file, Apache-2.0 like the rest of this fork).
 *
 * Settings > Voice: the offline model manager (Fast / Best, download on Wi-Fi only, size, delete), cleanup and
 * microphone options, and the privacy statement.
 */
package dev.patrickgold.florisboard.app.settings.voice

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.humming.keyboard.HistoryRetention
import com.humming.keyboard.HistoryStore
import com.humming.keyboard.LlmCleanup
import com.humming.keyboard.OnlineSession
import com.humming.keyboard.SecretStore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.humming.keyboard.ModelDownloader
import com.humming.keyboard.ModelStore
import com.humming.keyboard.Prefs
import com.humming.keyboard.VoiceModel
import com.humming.keyboard.VoiceModels
import dev.patrickgold.florisboard.lib.compose.FlorisScreen

private val Ok = Color(0xFF1E9E5A)

@Composable
fun VoiceScreen() = FlorisScreen {
    title = "Voice"
    previewFieldVisible = true

    content {
        val context = LocalContext.current
        val app = context.applicationContext
        var dl by remember { mutableStateOf(ModelDownloader.state) }
        var tick by remember { mutableIntStateOf(0) }
        DisposableEffect(Unit) {
            val l: (ModelDownloader.State) -> Unit = { dl = it; tick++ }
            ModelDownloader.addListener(l)
            onDispose { ModelDownloader.removeListener(l) }
        }
        var picked by remember { mutableStateOf(Prefs.voiceModel(app)) }
        val ramMb = remember { ModelStore.totalRamMb(app) }
        val current = remember(picked, tick) { VoiceModels.resolve(picked, ramMb) }
        val ready = remember(current, tick) { ModelStore.ready(app, current) }
        val running = dl.running && dl.modelId == current.id

        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Status card: the one thing that matters.
            Card(
                colors = CardDefaults.cardColors(containerColor = if (ready) Ok.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (ready) Icons.Rounded.CloudOff else Icons.Rounded.Download, null,
                            tint = if (ready) Ok else MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            when {
                                ready -> "Offline voice is ready"
                                running -> "Downloading ${current.label} voice model… ${dl.pct}%"
                                dl.waiting == "wifi" && dl.modelId == current.id -> "Waiting for Wi-Fi"
                                else -> "Voice model not downloaded"
                            },
                            fontWeight = FontWeight.SemiBold, fontSize = 17.sp,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (ready) "Dictation works with no internet at all, even in airplane mode. " +
                            "Nothing you say ever leaves this phone."
                        else "A one-time ${VoiceModels.formatBytes(current.downloadBytes)} download. After that, dictation " +
                            "runs entirely on this phone, with no internet.",
                        fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (running) {
                        Spacer(Modifier.height(10.dp))
                        LinearProgressIndicator(progress = { dl.pct / 100f }, modifier = Modifier.fillMaxWidth())
                    }
                    if (dl.error != null && !dl.running && dl.modelId == current.id) {
                        Spacer(Modifier.height(6.dp))
                        Text("Download failed: ${dl.error}", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (!ready && !running) {
                            Button(onClick = { ModelDownloader.ensure(app, current, userForced = true) }) {
                                Icon(Icons.Rounded.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                                Text("Download ${VoiceModels.formatBytes(current.downloadBytes)}")
                            }
                        }
                        if (ready || ModelStore.bytesOnDisk(app, current) > 0) {
                            OutlinedButton(onClick = {
                                ModelStore.delete(app, current)
                                ModelDownloader.refresh(app); tick++
                            }, enabled = !running) {
                                Icon(Icons.Rounded.Delete, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                                Text("Delete (${VoiceModels.formatBytes(ModelStore.bytesOnDisk(app, current))})")
                            }
                        }
                    }
                }
            }

            Text("Voice model", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
            VoiceModels.all.forEach { m ->
                ModelRow(m, selected = current.id == m.id, fits = VoiceModels.fits(m, ramMb), onDisk = ModelStore.ready(app, m)) {
                    picked = m.id
                    Prefs.set(app, Prefs.KEY_VOICE_MODEL, m.id)
                    ModelDownloader.refresh(app); tick++
                }
            }

            ToggleRow("Download on Wi-Fi only", "Never use mobile data for the model download.", Prefs.KEY_WIFI_ONLY, Prefs.wifiOnly(app))
            HorizontalDivider()
            EngineSection()
            HorizontalDivider()
            ToggleRow("Live text while speaking", "Your words appear as an underlined draft while you speak and are corrected in place, " +
                "then typed once when you stop. Off: nothing appears until you stop, then the whole text is typed at once.",
                Prefs.KEY_LIVE_TYPING, Prefs.liveTyping(app))
            ToggleRow("Clean up speech", "Removes stutters and repeats, applies corrections like \"3pm, no wait, 4pm\" → \"4pm\", " +
                "and turns spoken lists into bullets.",
                Prefs.KEY_CLEAN, Prefs.cleanMode(app))
            ToggleRow("Edit by voice", "Say a whole edit such as \"change Tuesday to Wednesday\", \"delete really\" or " +
                "\"capitalize monday\" and it changes your text instead of being typed. Only when that word is already there.",
                Prefs.KEY_VOICE_EDIT, Prefs.voiceEdit(app))
            ToggleRow("Remove filler words", "Drops um, uh, er and hmm, and \"you know\" between commas. Off keeps them.",
                Prefs.KEY_FILLERS, Prefs.removeFillers(app))
            ToggleRow("Accuracy mode", "Stricter clean-up, still on this phone: also removes \"like\", \"basically\" and \"you know\" " +
                "when they are set off by commas, and a spoken \"period\" at the end. For the most accurate recognition, pick the Best model above.",
                Prefs.KEY_ACCURACY, Prefs.accuracyMode(app))
            ToggleRow("Bluetooth headset mic", "Dictate through a connected headset's microphone.", Prefs.KEY_BT_MIC, Prefs.bluetoothMic(app))
            ToggleRow("Voice incognito", "Keep no voice history. The keyboard's incognito mode also turns this on, and keeps nothing in the clipboard.",
                Prefs.KEY_INCOGNITO, Prefs.voiceIncognito(app))
            HorizontalDivider()
            HistorySection()
            HorizontalDivider()
            CustomWordsSection()
            HorizontalDivider()
            AiCleanupSection()
            HorizontalDivider()
            Text("Voice commands", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
            Text(
                "Say \"new line\" or \"new paragraph\" anywhere, \"bullet point\" to start a list item, and \"delete that\" or " +
                    "\"scratch that\" to remove the sentence before it. A list said as \"first, ... second, ...\" becomes bullets. " +
                    "With AI clean-up on, end with \"make it formal\", \"make it casual\", \"make it shorter\", " +
                    "\"bullet points\", \"fix the grammar\" or \"translate it to Spanish\".",
                fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()
            Text("Offline by design", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
            Text(
                "Humming has no servers and no accounts. In the default Offline mode, speech recognition, clean-up and " +
                    "history run on this phone, and the only time Humming uses the internet is to fetch the voice model you " +
                    "choose, once, from its public release. Online mode and AI clean-up are off unless you turn them on.",
                fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Speech model: NVIDIA Parakeet (CC-BY-4.0), run with sherpa-onnx (Apache-2.0) and ONNX Runtime (MIT). " +
                    "Voice activity detection: Silero VAD (MIT).",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ModelRow(m: VoiceModel, selected: Boolean, fits: Boolean, onDisk: Boolean, onPick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(enabled = fits, onClick = onPick),
        shape = RoundedCornerShape(14.dp),
        border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onPick, enabled = fits)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.label, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(if (onDisk) "On phone" else VoiceModels.formatBytes(m.downloadBytes), fontSize = 12.sp,
                        color = if (onDisk) Ok else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(m.blurb, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${m.werPct}% word errors in our test · ~${m.ramMb} MB RAM while dictating" +
                    if (!fits) " · needs more RAM than this phone has" else "", fontSize = 12.sp,
                    color = if (fits) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, summary: String, key: String, initial: Boolean) {
    val app = LocalContext.current.applicationContext
    var on by remember { mutableStateOf(initial) }
    Row(
        Modifier.fillMaxWidth().clickable { on = !on; Prefs.set(app, key, on) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp)
            Text(summary, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = on, onCheckedChange = { on = it; Prefs.set(app, key, it) })
    }
}

/** How long dictation history (text and audio) stays on this phone, and a way to delete all of it. */
@Composable
private fun HistorySection() {
    val app = LocalContext.current.applicationContext
    var setting by remember { mutableStateOf(Prefs.retention(app)) }
    var deleted by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val pick: (String) -> Unit = { value ->
        setting = value
        Prefs.set(app, Prefs.KEY_RETENTION, value)
        runCatching { HistoryStore(app).prune() }
    }
    Text("Keep dictation history", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
    Text("Text and audio of past dictations, stored only on this phone. Older entries are deleted automatically.",
        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    HistoryRetention.ALL.forEach { (value, label) ->
        Row(Modifier.fillMaxWidth().clickable { pick(value) }, verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = setting == value, onClick = { pick(value) })
            Text(label, fontSize = 15.sp)
        }
    }
    OutlinedButton(onClick = { confirmDelete = true }, enabled = !deleted) {
        Text(if (deleted) "Dictation history deleted" else "Delete all dictation history")
    }
    if (confirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete all dictation history?") },
            text = { Text("Every saved dictation (text and audio) on this phone is deleted. This can't be undone.") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmDelete = false
                    runCatching { HistoryStore(app).deleteAll() }
                    deleted = true
                }) { Text("Delete") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

/** Offline (default) or Online speech engine, with the privacy trade-off spelled out. */
@Composable
private fun EngineSection() {
    val app = LocalContext.current.applicationContext
    var engine by remember { mutableStateOf(Prefs.engine(app)) }
    val onlineOk = remember { OnlineSession.available(app) }
    Text("Speech engine", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
    EngineRow("Offline (recommended)", "Runs on this phone with the model above. Works in airplane mode; your voice never leaves the phone.",
        engine == Prefs.ENGINE_OFFLINE) { engine = Prefs.ENGINE_OFFLINE; Prefs.set(app, Prefs.KEY_ENGINE, Prefs.ENGINE_OFFLINE) }
    EngineRow("Online", if (onlineOk) "Uses this phone's online speech service (usually Google). No account or key. " +
        "Your audio is sent to that service while you dictate, so use it only if you are fine with that." else
        "This phone has no online speech service.", engine == Prefs.ENGINE_ONLINE, enabled = onlineOk) {
        engine = Prefs.ENGINE_ONLINE; Prefs.set(app, Prefs.KEY_ENGINE, Prefs.ENGINE_ONLINE)
    }
}

@Composable
private fun EngineRow(title: String, summary: String, selected: Boolean, enabled: Boolean = true, onPick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onPick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onPick, enabled = enabled)
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(summary, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Personal dictionary: names, jargon and brands, typed with their exact spelling. */
@Composable
private fun CustomWordsSection() {
    val app = LocalContext.current.applicationContext
    var words by remember { mutableStateOf(Prefs.dictionaryText(app)) }
    Text("Custom words", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
    Text("Names, jargon and brands Humming should always spell your way. One per line, e.g. \"Kubernetes\" or \"iPhone\". " +
        "To replace what you say, write \"spoken -> written\", e.g. \"cube cuddle -> kubectl\" or \"my sig -> Best, Sam\". " +
        "Add \" @app\" to use a line in one app only, e.g. \"kubectl @termux\".",
        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedTextField(
        value = words,
        onValueChange = { words = it; Prefs.set(app, Prefs.KEY_DICTIONARY, it) },
        modifier = Modifier.fillMaxWidth().height(140.dp),
        placeholder = { Text("One word or phrase per line") },
    )
}

/** Optional AI clean-up with the user's own key. Off by default; the key is encrypted on the phone. */
@Composable
private fun AiCleanupSection() {
    val app = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var provider by remember { mutableStateOf(LlmCleanup.provider(app)) }
    var hasKey by remember { mutableStateOf(SecretStore.has(app, LlmCleanup.KEY_SECRET)) }
    var key by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(Prefs.get(app).getString(Prefs.KEY_LLM_MODEL, "") ?: "") }
    var status by remember { mutableStateOf("") }
    Text("AI clean-up (optional, your own key)", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
    Text("After you stop, the finished TEXT (never audio) is sent to the provider you pick, with your own API key, to " +
        "remove fillers, fix punctuation and structure, or follow a closing command like \"make it formal\". " +
        "Off by default, never used in incognito, and the on-device text is kept if it fails. Your key is encrypted " +
        "on this phone and is only ever sent to that provider.",
        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    LlmCleanup.Provider.values().forEach { p ->
        EngineRow(p.label, if (p == LlmCleanup.Provider.OFF) "Only on-device clean-up." else "Get a key at ${p.keyUrl}",
            provider == p) { provider = p; Prefs.set(app, Prefs.KEY_LLM_PROVIDER, p.id); status = "" }
    }
    if (provider != LlmCleanup.Provider.OFF) {
        OutlinedTextField(
            value = key, onValueChange = { key = it },
            label = { Text(if (hasKey) "API key (saved; type to replace)" else "API key") },
            singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = model, onValueChange = { model = it; Prefs.set(app, Prefs.KEY_LLM_MODEL, it.trim()) },
            label = { Text("Model (blank = ${provider.defaultModel})") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = {
                if (key.isNotBlank()) { SecretStore.put(app, LlmCleanup.KEY_SECRET, key); key = ""; hasKey = true }
                status = "Testing…"
                scope.launch {
                    status = withContext(Dispatchers.IO) {
                        runCatching { "Works: \"" + LlmCleanup.test(app) + "\"" }.getOrElse { "Didn't work: ${it.message}" }
                    }
                }
            }, enabled = key.isNotBlank() || hasKey) { Text(if (key.isNotBlank()) "Save and test" else "Test") }
            if (hasKey) OutlinedButton(onClick = {
                SecretStore.put(app, LlmCleanup.KEY_SECRET, null); hasKey = false; status = "Key removed"
            }) { Text("Remove key") }
        }
        if (status.isNotEmpty()) Text(status, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
