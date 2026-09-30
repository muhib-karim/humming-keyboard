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
            ToggleRow("Clean up speech", "Removes um and uh, stutters and repeats, and applies corrections like \"3pm, no wait, 4pm\" → \"4pm\".",
                Prefs.KEY_CLEAN, Prefs.cleanMode(app))
            ToggleRow("Bluetooth headset mic", "Dictate through a connected headset's microphone.", Prefs.KEY_BT_MIC, Prefs.bluetoothMic(app))
            ToggleRow("Voice incognito", "Keep no voice history. The keyboard's incognito mode also turns this on, and keeps nothing in the clipboard.",
                Prefs.KEY_INCOGNITO, Prefs.voiceIncognito(app))
            HorizontalDivider()
            Text("Offline by design", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
            Text(
                "Humming has no servers and no accounts. Speech recognition, clean-up and history run on this phone. " +
                    "The only time Humming uses the internet is to fetch the voice model you choose, once, from its public release.",
                fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Speech model: NVIDIA Parakeet (CC-BY-4.0), run with sherpa-onnx (Apache-2.0) and ONNX Runtime (MIT). " +
                    "Voice activity detection: Silero VAD (MIT). Keyboard: FlorisBoard (Apache-2.0).",
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
