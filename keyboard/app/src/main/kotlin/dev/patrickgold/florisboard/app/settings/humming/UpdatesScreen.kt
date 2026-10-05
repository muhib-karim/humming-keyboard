/*
 * Humming (added file, 1.6). Settings > Updates: in-app updates for the sideloaded APK (see AppUpdate).
 */
package dev.patrickgold.florisboard.app.settings.humming

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.humming.keyboard.AppUpdate
import com.humming.keyboard.Prefs
import dev.patrickgold.florisboard.lib.compose.FlorisScreen

@Composable
fun UpdatesScreen() = FlorisScreen {
    title = "Updates"
    previewFieldVisible = false

    content {
        val ctx = LocalContext.current.applicationContext
        val st by AppUpdate.status.collectAsState()
        DisposableEffect(Unit) {
            AppUpdate.screenVisible = true
            onDispose { AppUpdate.screenVisible = false }
        }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Humming ${AppUpdate.installedVersionName(ctx)} (${AppUpdate.installedVersionCode(ctx)})",
                style = MaterialTheme.typography.titleMedium,
            )
            if (!AppUpdate.enabled) {
                Text("This copy of Humming came from Google Play, which installs its updates.", style = MaterialTheme.typography.bodyMedium)
                return@Column
            }
            Text(
                "Humming looks for a new version on its own (at most every 6 hours) and downloads it on Wi-Fi. A download " +
                    "is installed only if its SHA-256 matches the published checksum and it is signed with Humming's " +
                    "release key. Android asks you to confirm an update with one tap, because Humming is installed from " +
                    "outside the Play Store. On Android 12 and later, once Humming has installed an update itself, Android " +
                    "may install the next ones without asking.",
                style = MaterialTheme.typography.bodyMedium,
            )
            UpdateToggle("Check for updates automatically", "A small file is read from the Humming website. Nothing about you is sent.",
                AppUpdate.KEY_AUTO_CHECK, AppUpdate.autoCheck(ctx))
            UpdateToggle("Download and install updates automatically", "On Wi-Fi. Android may still ask you to confirm.",
                AppUpdate.KEY_AUTO_DOWNLOAD, AppUpdate.autoDownload(ctx))
            if (st.message.isNotEmpty()) {
                Text(st.message, color = if (st.state == "error") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            }
            if (st.state == "downloading") {
                LinearProgressIndicator(progress = { st.pct / 100f }, modifier = Modifier.fillMaxWidth())
            }
            val busy = st.state in setOf("checking", "downloading", "verifying", "installing")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(enabled = !busy, onClick = { AppUpdate.checkNow(ctx, userAsked = false) }) { Text("Check for updates") }
                if (st.state == "available" || st.state == "ready" || (st.state == "error" && st.latest != null)) {
                    Button(enabled = !busy, onClick = { AppUpdate.checkNow(ctx, userAsked = true) }) { Text("Install now") }
                }
            }
            if (!AppUpdate.canInstall(ctx)) {
                Text(
                    "To install updates, Android needs you to allow Humming to install apps (one time).",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = { runCatching { ctx.startActivity(AppUpdate.unknownSourcesSettings(ctx)) } }) {
                    Text("Allow Humming to install updates")
                }
            }
        }
    }
}

@Composable
private fun UpdateToggle(title: String, summary: String, key: String, initial: Boolean) {
    val app = LocalContext.current.applicationContext
    var on by remember { mutableStateOf(initial) }
    Row(
        Modifier.fillMaxWidth().clickable { on = !on; Prefs.set(app, key, on) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = on, onCheckedChange = { on = it; Prefs.set(app, key, it) })
    }
}
