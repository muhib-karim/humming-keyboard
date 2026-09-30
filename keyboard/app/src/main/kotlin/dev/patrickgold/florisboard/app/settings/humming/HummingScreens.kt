package dev.patrickgold.florisboard.app.settings.humming

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.jetpref.datastore.ui.DialogSliderPreference
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.humming.keyboard.SettingsIndex
import com.humming.keyboard.predict.PredictionEngine
import com.humming.keyboard.predict.TextShortcuts
import dev.patrickgold.florisboard.app.LocalNavController
import dev.patrickgold.florisboard.app.Routes
import dev.patrickgold.florisboard.app.enumDisplayEntriesOf
import dev.patrickgold.florisboard.ime.keyboard.IncognitoMode
import dev.patrickgold.florisboard.lib.compose.FlorisScreen
import dev.patrickgold.jetpref.datastore.model.observeAsState
import dev.patrickgold.jetpref.datastore.ui.ExperimentalJetPrefDatastoreUi
import dev.patrickgold.jetpref.datastore.ui.ListPreference
import dev.patrickgold.jetpref.datastore.ui.Preference
import dev.patrickgold.jetpref.datastore.ui.PreferenceGroup
import dev.patrickgold.jetpref.datastore.ui.SwitchPreference
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.focusRequester

private val storePrefs by FlorisPreferenceStore

/** jetpref's set() is a suspend function; click handlers hand the write to a background scope (never block the UI thread). */
private val settingsWriteScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)

private fun setPref(block: suspend () -> Unit) {
    settingsWriteScope.launch { block() }
}

/** Humming: Settings > Typing > Text shortcuts (text expansion). */
@Composable
fun TextShortcutsScreen() = FlorisScreen {
    title = "Text shortcuts"
    previewFieldVisible = true

    content {
        val raw by prefs.suggestion.textShortcuts.observeAsState()
        var shortcut by remember { mutableStateOf("") }
        var phrase by remember { mutableStateOf("") }
        Text(
            "Type a shortcut and the phrase appears in the middle of the suggestion strip; space expands it.",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = shortcut,
                onValueChange = { v -> shortcut = v.filter { it.isLetterOrDigit() }.take(20) },
                label = { Text("Shortcut (letters or digits)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = phrase,
                onValueChange = { phrase = it.take(500) },
                label = { Text("Phrase") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = shortcut.isNotBlank() && phrase.isNotBlank(),
                onClick = {
                    setPref { storePrefs.suggestion.textShortcuts.set(TextShortcuts.add(raw, shortcut, phrase)) }
                    shortcut = ""
                    phrase = ""
                },
            ) { Text("Add shortcut") }
        }
        val map = TextShortcuts.parse(raw)
        if (map.isEmpty()) {
            Text("No shortcuts yet.", modifier = Modifier.padding(16.dp))
        }
        for ((k, v) in map.toSortedMap()) {
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                    Text(k, style = MaterialTheme.typography.titleMedium)
                    Text(v, style = MaterialTheme.typography.bodyMedium)
                }
                IconButton(onClick = { setPref { storePrefs.suggestion.textShortcuts.set(TextShortcuts.remove(raw, k)) } }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete shortcut $k")
                }
            }
        }
    }
}

/** Humming: Settings > Privacy, every privacy switch in one place. */
@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun PrivacyScreen() = FlorisScreen {
    title = "Privacy"
    previewFieldVisible = false

    val navController = LocalNavController.current
    val context = LocalContext.current

    content {
        Text(
            "Humming works offline. Typing, prediction, clipboard and dictation never leave this phone. The only " +
                "network use is the one-time voice model download (Settings > Voice), pinned by SHA-256, Wi-Fi only by default.",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        PreferenceGroup(title = "Typing") {
            ListPreference(
                prefs.suggestion.incognitoMode,
                title = "Incognito mode",
                entries = enumDisplayEntriesOf(IncognitoMode::class),
            )
            SwitchPreference(
                prefs.suggestion.learnFromTyping,
                title = "Learn from my typing",
                summary = "Words and word pairs you type improve suggestions. Kept on this phone only; never in incognito.",
            )
            Preference(
                title = "Clear learned words",
                summary = "Forget everything the keyboard learned from you",
                onClick = {
                    PredictionEngine.LANGUAGES.forEach { lang -> PredictionEngine.loaded(lang)?.user?.clear() ?: PredictionEngine.userFile(context, lang).delete() }
                    Toast.makeText(context, "Learned words cleared", Toast.LENGTH_SHORT).show()
                },
            )
            SwitchPreference(
                prefs.suggestion.blockPossiblyOffensive,
                title = "Block offensive words",
            )
        }
        PreferenceGroup(title = "Clipboard") {
            SwitchPreference(
                prefs.clipboard.historyEnabled,
                title = "Clipboard history",
                summary = "Off: nothing you copy is kept by the keyboard",
            )
            SwitchPreference(
                prefs.clipboard.historyAutoCleanSensitiveEnabled,
                title = "Auto-delete sensitive items",
                summary = "Passwords and other items apps mark sensitive are removed quickly",
            )
            Preference(title = "Clipboard retention and more", onClick = { navController.navigate(Routes.Settings.Clipboard) })
        }
        PreferenceGroup(title = "Voice") {
            Preference(
                title = "Voice privacy",
                summary = "Voice incognito, dictation history retention, model downloads",
                onClick = { navController.navigate(Routes.Settings.Voice) },
            )
        }
        PreferenceGroup(title = "Your data") {
            Preference(
                title = "Export settings to a file",
                summary = "Settings, themes, shortcuts, voice settings and learned words, as one file you keep",
                onClick = { navController.navigate(Routes.Settings.Backup) },
            )
            Preference(title = "Import settings from a file", onClick = { navController.navigate(Routes.Settings.Restore) })
        }
    }
}

/** Humming: search every setting by name or keyword. */
@Composable
fun SettingsSearchScreen() = FlorisScreen {
    title = "Search settings"
    previewFieldVisible = false

    val navController = LocalNavController.current

    content {
        var query by remember { mutableStateOf("") }
        val focus = remember { androidx.compose.ui.focus.FocusRequester() }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search settings") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .focusRequester(focus)
                .semantics { contentDescription = "Search settings" },
        )
        // Open ready to type, like a search page should.
        androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        val results = SettingsIndex.search(query)
        if (query.isNotBlank() && results.isEmpty()) {
            Text("No setting matches \u201C$query\u201D.", modifier = Modifier.padding(16.dp))
        }
        for (entry in results) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { navController.navigate(SettingsIndex.route(entry)) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Text(entry.title, style = MaterialTheme.typography.titleMedium)
                Text(entry.path, style = MaterialTheme.typography.bodySmall)
            }
            HorizontalDivider()
        }
    }
}

/** Humming: Settings > Theme > Background image. */
@OptIn(ExperimentalJetPrefDatastoreUi::class)
@Composable
fun BackgroundImageScreen() = FlorisScreen {
    title = "Background image"
    previewFieldVisible = true

    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val path = com.humming.keyboard.KeyboardBackground.import(context, uri)
            if (path != null) setPref { storePrefs.theme.hummingBackgroundImage.set(path) }
            else Toast.makeText(context, "Couldn't read that image", Toast.LENGTH_SHORT).show()
        }
    }

    content {
        val path by prefs.theme.hummingBackgroundImage.observeAsState()
        Preference(
            title = "Choose a photo",
            summary = if (path.isEmpty()) "No background image" else "A photo is set behind the keys",
            onClick = { picker.launch("image/*") },
        )
        DialogSliderPreference(
            prefs.theme.hummingBackgroundDim,
            title = "Dim the photo",
            valueLabel = { "$it %" },
            min = 0,
            max = 80,
            stepIncrement = 5,
            enabledIf = { prefs.theme.hummingBackgroundImage.get().isNotEmpty() },
        )
        Preference(
            title = "Remove background image",
            enabledIf = { prefs.theme.hummingBackgroundImage.get().isNotEmpty() },
            onClick = {
                com.humming.keyboard.KeyboardBackground.clear(context)
                setPref { storePrefs.theme.hummingBackgroundImage.set("") }
            },
        )
    }
}
