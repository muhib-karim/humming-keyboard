package dev.patrickgold.florisboard.ime.media

import android.content.Context
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.humming.keyboard.GifSearch
import com.humming.keyboard.Stickers
import dev.patrickgold.florisboard.app.FlorisPreferenceStore
import dev.patrickgold.florisboard.editorInstance
import dev.patrickgold.florisboard.ime.theme.FlorisImeUi
import dev.patrickgold.florisboard.keyboardManager
import dev.patrickgold.jetpref.datastore.model.observeAsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.florisboard.lib.snygg.ui.SnyggBox
import java.io.File

/** The panel's state, kept across recompositions and panel switches (and settable by the debug build for CI). */
object StickerPanelState {
    val open = mutableStateOf(false)
    val query = mutableStateOf("")
    val submitted = mutableStateOf("")
    val gifTab = mutableStateOf(false)
    val typing = mutableStateOf(false)
}

/**
 * Humming 1.2: the sticker & GIF panel (opened from the emoji panel's bottom row once Settings > Stickers & GIFs
 * turns it on). Built-in and imported stickers work offline; the "GIFs" tab searches Openverse only when online GIF
 * search is switched on, and not in incognito unless that is allowed too. The search box has its own small letter
 * keys, so what is typed there never reaches the app being typed in.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StickerPanel(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs by FlorisPreferenceStore
    val keyboardManager by context.keyboardManager()
    val editorInstance by context.editorInstance()
    val scope = rememberCoroutineScope()
    val gifEnabled by prefs.emoji.gifSearchEnabled.observeAsState()
    val gifInIncognito by prefs.emoji.gifSearchInIncognito.observeAsState()
    val columns by prefs.emoji.stickerColumns.observeAsState()
    val incognito = keyboardManager.activeState.isIncognitoMode
    val gifAllowed = GifSearch.allowed(gifEnabled, incognito, gifInIncognito)

    var query by StickerPanelState.query
    var typing by StickerPanelState.typing
    var gifTab by StickerPanelState.gifTab
    var submitted by StickerPanelState.submitted
    var reload by remember { mutableStateOf(0) }
    val stickers by produceState(initialValue = emptyList<Stickers.Sticker>(), reload) {
        value = withContext(Dispatchers.IO) { Stickers.user(context) + Stickers.builtIn(context) }
    }
    var gifs by remember { mutableStateOf(emptyList<GifSearch.Gif>()) }
    var gifState by remember { mutableStateOf("") }
    LaunchedEffect(submitted, gifTab, gifAllowed) {
        if (!gifTab || !gifAllowed || submitted.isBlank()) { gifs = emptyList(); gifState = ""; return@LaunchedEffect }
        gifState = "Searching Openverse\u2026"
        val found = withContext(Dispatchers.IO) { GifSearch.searchOrNull(submitted) }
        gifs = found ?: emptyList()
        gifState = when {
            found == null -> "Couldn\u2019t reach Openverse. Check the connection and try again"
            found.isEmpty() -> "No openly licensed GIFs for \u201C$submitted\u201D"
            else -> ""
        }
    }

    fun send(file: File?, mime: String, what: String) {
        if (file == null) return
        val uri = runCatching { FileProvider.getUriForFile(context, context.packageName + ".provider.file", file) }.getOrNull()
        val ok = uri != null && editorInstance.commitImage(uri, mime, what)
        if (!ok) Toast.makeText(context, "This app doesn't accept stickers or GIFs here", Toast.LENGTH_SHORT).show()
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Search row: the box, the Stickers / GIFs tabs and the import shortcut.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SnyggBox(
                elementName = FlorisImeUi.MediaBottomRowButton.elementName,
                modifier = Modifier.weight(1f).height(36.dp),
                clickAndSemanticsModifier = Modifier
                    .clickable { typing = true }
                    .semantics { contentDescription = "Search stickers" },
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = if (query.isEmpty()) "\uD83D\uDD0D Search ${if (gifTab) "GIFs" else "stickers"}" else query + if (typing) "|" else "",
                    modifier = Modifier.padding(horizontal = 10.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = LocalContentColor.current.copy(alpha = if (query.isEmpty()) 0.6f else 1f),
                )
            }
            PanelTab("Stickers", !gifTab) { gifTab = false }
            if (gifEnabled) PanelTab("GIFs", gifTab) { gifTab = true; if (query.isNotBlank()) submitted = query }
            PanelTab("+", false) { openStickerSettings(context) }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                typing -> SearchKeys(
                    onKey = { query = (query + it).take(40) },
                    onDelete = { query = query.dropLast(1) },
                    onSearch = { typing = false; submitted = query.trim() },
                )
                gifTab && !gifAllowed -> Notice(
                    if (incognito) "GIF search is off in incognito. Stickers still work."
                    else "GIF search is off. Turn it on in Settings \u2192 Stickers & GIFs.",
                )
                gifTab -> Column {
                    if (gifState.isNotEmpty() || submitted.isBlank()) {
                        Notice(if (submitted.isBlank()) "Type a search: GIFs come from Openverse (openverse.org), openly licensed, over the internet." else gifState)
                    }
                    LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.weight(1f)) {
                        items(gifs, key = { it.id }) { g ->
                            GifTile(g) {
                                scope.launch {
                                    val f = withContext(Dispatchers.IO) {
                                        val dir = File(context.cacheDir, "stickers").apply { mkdirs() }
                                        File(dir, "gif_" + g.id.filter { c -> c.isLetterOrDigit() }.take(40) + ".gif")
                                            .takeIf { it.exists() || GifSearch.download(g.url, it) }
                                    }
                                    if (f == null) Toast.makeText(context, "Couldn't download that GIF", Toast.LENGTH_SHORT).show()
                                    else send(f, "image/gif", g.title.ifBlank { "GIF" })
                                }
                            }
                        }
                    }
                }
                else -> {
                    val shown = Stickers.search(stickers, submitted)
                    if (shown.isEmpty()) Notice("No stickers match \u201C$submitted\u201D")
                    LazyVerticalGrid(columns = GridCells.Fixed(columns.coerceIn(3, 6)), modifier = Modifier.fillMaxSize()) {
                        items(shown, key = { it.id }) { s ->
                            StickerTile(
                                s,
                                onClick = {
                                    scope.launch {
                                        val f = withContext(Dispatchers.IO) { Stickers.shareableFile(context, s) }
                                        send(f, Stickers.mimeOf(s.path), s.label)
                                    }
                                },
                                onLongClick = {
                                    if (s.source == Stickers.Source.USER && Stickers.delete(context, s)) {
                                        reload++
                                        Toast.makeText(context, "Sticker removed", Toast.LENGTH_SHORT).show()
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelTab(label: String, selected: Boolean, onClick: () -> Unit) {
    SnyggBox(
        elementName = FlorisImeUi.MediaBottomRowButton.elementName,
        modifier = Modifier.height(36.dp),
        clickAndSemanticsModifier = Modifier.clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = LocalContentColor.current.copy(alpha = if (selected) 1f else 0.7f),
        )
    }
}

@Composable
private fun Notice(text: String) {
    Text(text = text, modifier = Modifier.fillMaxWidth().padding(12.dp), fontSize = 13.sp)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerTile(s: Stickers.Sticker, onClick: () -> Unit, onLongClick: () -> Unit) {
    val context = LocalContext.current
    val bmp by produceState<ImageBitmap?>(initialValue = null, s.id) {
        value = withContext(Dispatchers.IO) { loadThumb(context, s) }
    }
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(4.dp)
            .clip(RoundedCornerShape(10.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { contentDescription = "Sticker: " + s.label },
        contentAlignment = Alignment.Center,
    ) {
        bmp?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
    }
}

@Composable
private fun GifTile(g: GifSearch.Gif, onClick: () -> Unit) {
    val context = LocalContext.current
    val bmp by produceState<ImageBitmap?>(initialValue = null, g.id) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(context.cacheDir, "gif_thumbs").apply { mkdirs() }
                val f = File(dir, g.id.filter { it.isLetterOrDigit() }.take(40) + ".img")
                if (f.exists() || GifSearch.download(g.thumbnail, f, 2L * 1024 * 1024)) decode(f.absolutePath, 240) else null
            }.getOrNull()
        }
    }
    Column(
        modifier = Modifier.padding(3.dp).clickable(onClick = onClick).semantics { contentDescription = "GIF: " + g.title + ", " + g.credit },
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp))
                .background(LocalContentColor.current.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            bmp?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        Text(g.credit, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The search box's own letter keys (typing here stays in the panel). */
@Composable
private fun SearchKeys(onKey: (String) -> Unit, onDelete: () -> Unit, onSearch: () -> Unit) {
    val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
    Column(modifier = Modifier.fillMaxSize().padding(2.dp), verticalArrangement = Arrangement.SpaceEvenly) {
        for (r in rows) {
            Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.Center) {
                for (c in r) SearchKey(c.toString(), Modifier.weight(1f)) { onKey(c.toString()) }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            SearchKey("\u232B", Modifier.weight(1.5f), onDelete)
            SearchKey("space", Modifier.weight(4f)) { onKey(" ") }
            SearchKey("Search", Modifier.weight(2f), onSearch)
        }
    }
}

@Composable
private fun SearchKey(label: String, modifier: Modifier, onClick: () -> Unit) {
    SnyggBox(
        elementName = FlorisImeUi.MediaEmojiKey.elementName,
        modifier = modifier.fillMaxSize().padding(2.dp),
        clickAndSemanticsModifier = Modifier.clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = if (label.length > 1) 13.sp else 17.sp)
    }
}

private fun loadThumb(context: Context, s: Stickers.Sticker): ImageBitmap? = runCatching {
    if (s.source == Stickers.Source.BUILT_IN) {
        context.assets.open(s.path).use { BitmapFactory.decodeStream(it) }?.asImageBitmap()
    } else {
        decode(s.path, 256)
    }
}.getOrNull()

private fun decode(path: String, target: Int): ImageBitmap? {
    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, o)
    var sample = 1
    while (o.outWidth / (sample * 2) >= target && o.outHeight / (sample * 2) >= target) sample *= 2
    return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}

/** Settings > Stickers & GIFs (import, delete, GIF search switch). */
fun openStickerSettings(ctx: Context) {
    runCatching {
        ctx.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("ui://florisboard/settings/stickers"))
                .setPackage(ctx.packageName).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
