/*
 * Humming (added file, Apache-2.0 like the rest of this fork).
 *
 * The voice hero of the keyboard: a big gradient mic in the Smartbar opens a full-height voice panel with a live
 * transcript, a level-reactive mic orb, Clean mode, Cancel and Done. Nothing reaches the app's text field until
 * Done (DictationOutput); the on-device engine is humming-core (whisper.cpp + VAD + rule cleanup).
 */
package dev.patrickgold.florisboard.ime.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.humming.keyboard.CoreLoader
import com.humming.keyboard.DictationOutput
import com.humming.keyboard.DictationPipeline
import com.humming.keyboard.HistoryStore
import com.humming.keyboard.ModelDownloader
import com.humming.keyboard.ModelStore
import com.humming.keyboard.PermissionActivity
import com.humming.keyboard.Prefs
import com.humming.keyboard.StreamingSession
import com.humming.keyboard.TranscriberUnavailable
import com.humming.keyboard.VoiceModels
import com.humming.keyboard.WavRecorder
import dev.patrickgold.florisboard.BuildConfig
import dev.patrickgold.florisboard.editorInstance
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** What the voice panel shows. Immutable snapshot, published through [HummingVoice.state]. */
data class VoiceState(
    val open: Boolean = false,
    val phase: Phase = Phase.IDLE,
    val transcript: String = "",
    val message: String = "",
    val level: Float = 0f,
    val levels: List<Float> = emptyList(),
    val startedAt: Long = 0L,
    val clean: Boolean = true,
    val downloadPct: Int = -1,
    /** The speech model is on the phone: dictation needs no network at all (the panel shows an Offline badge). */
    val offline: Boolean = false,
    /** Bluetooth headset in use for this dictation, or null. */
    val headset: String? = null,
    /** Nothing from this dictation is kept (keyboard incognito or voice incognito). */
    val incognito: Boolean = false,
) {
    enum class Phase { IDLE, PREPARING, LISTENING, FINISHING, ERROR }
}

object HummingVoice {
    private const val TAG = "HUMMING"
    private const val LEVEL_HISTORY = 40

    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(VoiceState())
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private var recorder: WavRecorder? = null
    private var session: StreamingSession? = null
    private var output: DictationOutput? = null
    private var glue = ""
    private var busy = false
    @Volatile private var warmed = false
    private var appContext: Context? = null

    val isOpen: Boolean get() = _state.value.open

    /** The long-press voice menu (Humming offline / Gboard voice). */
    private val _menu = MutableStateFlow(false)
    val menu: StateFlow<Boolean> = _menu.asStateFlow()
    fun showMenu(on: Boolean) { _menu.value = on }

    /** Keyboard became visible: make sure the speech model is there (one-time download) and warm the engine. */
    fun onStartInputView(ctx: Context) {
        val app = ctx.applicationContext
        appContext = app
        _state.update { it.copy(clean = Prefs.cleanMode(app), offline = ModelStore.ready(app)) }
        ModelDownloader.removeListener(modelListener)
        ModelDownloader.addListener(modelListener)
        if (Prefs.mode(app) == Prefs.MODE_DEVICE && CoreLoader.whyUnavailable(app) == null) {
            if (ModelDownloader.ensure(app)) scheduleWarm(app)
        }
    }

    /** Keyboard hidden mid-dictation: keep the words (History), never type into a field the user left. */
    fun onFinishInputView() {
        _menu.value = false
        main.removeCallbacks(warmRunnable)
        if (recorder != null) stop(toField = false)
        else if (!busy) close()
    }

    private val modelListener: (ModelDownloader.State) -> Unit = listener@{ st ->
        main.post {
            val s = _state.value
            val m = appContext?.let { ModelStore.current(it) } ?: VoiceModels.FAST
            if (st.running) {
                _state.update {
                    it.copy(
                        downloadPct = st.pct, offline = false,
                        message = if (st.error != null) "Connection dropped, retrying… ${st.pct}%"
                        else "Getting the voice model (one time) · ${st.pct}% of ${VoiceModels.formatBytes(m.downloadBytes)}",
                    )
                }
            } else if (st.waiting != null && !st.ready) {
                _state.update {
                    it.copy(downloadPct = -1, offline = false, message = if (st.waiting == "wifi")
                        "Voice runs fully offline after a one-time ${VoiceModels.formatBytes(m.downloadBytes)} download. " +
                            "It starts on Wi-Fi, or now from Settings › Voice."
                    else "Voice runs fully offline after a one-time ${VoiceModels.formatBytes(m.downloadBytes)} download. " +
                        "Connect to the internet once to get it.")
                }
            } else if (st.ready) {
                _state.update { it.copy(offline = true) }
                if (s.downloadPct >= 0) {
                    _state.update { it.copy(downloadPct = -1, message = "") }
                    appContext?.let { scheduleWarm(it) }
                    if (s.open && s.phase == VoiceState.Phase.PREPARING) appContext?.let { start(it) }
                }
            } else if (st.error != null) {
                _state.update { it.copy(downloadPct = -1, phase = if (it.open) VoiceState.Phase.ERROR else it.phase,
                    message = "Voice model download failed (${st.error}). Tap the mic to retry.") }
            }
        }
    }

    private val warmRunnable = Runnable { appContext?.let { if (shouldPrewarm()) warmEngine(it) } }

    /**
     * Pre-warming runs the whole speech model once. On a phone with fast big cores that takes about a second and makes
     * the first dictation instant; on a slow CPU (baseline library, or 1-2 cores) it would starve typing for tens of
     * seconds, so there the engine loads on the first mic tap instead. Never during the first moments of typing.
     */
    private fun shouldPrewarm(): Boolean =
        !warmed && Runtime.getRuntime().availableProcessors() >= 4

    private fun scheduleWarm(app: Context) {
        main.removeCallbacks(warmRunnable)
        main.postDelayed(warmRunnable, 3000)
    }

    private fun warmEngine(app: Context) {
        Thread({
            runCatching {
                if (!ModelStore.ready(app)) return@runCatching
                val e = CoreLoader.engine(app)
                if (!warmed) {
                    warmed = true
                    val w = synchronized(CoreLoader.lock) { e.warmup() }
                    if (BuildConfig.DEBUG) Log.i(TAG, "HUMMING_WARM variant=${CoreLoader.variant} warmup=$w")
                }
            }
        }, "humming-warm").apply { priority = Thread.MIN_PRIORITY }.start()
    }

    fun toggleClean(ctx: Context) {
        val app = ctx.applicationContext
        val on = !Prefs.cleanMode(app)
        Prefs.get(app).edit().putBoolean(Prefs.KEY_CLEAN, on).apply()
        _state.update { it.copy(clean = on) }
    }

    /** The mic: open the panel and start listening (or stop, if already listening). */
    fun toggle(ctx: Context) {
        if (recorder != null) done() else start(ctx)
    }

    fun start(ctx: Context) {
        val app = ctx.applicationContext
        appContext = app
        if (recorder != null || busy) return
        _state.update { VoiceState(open = true, phase = VoiceState.Phase.PREPARING, clean = Prefs.cleanMode(app),
            downloadPct = it.downloadPct, message = it.message) }
        if (app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            app.startActivity(Intent(app, PermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            fail("Allow the microphone, then tap the mic again.")
            return
        }
        val streaming = Prefs.mode(app) == Prefs.MODE_DEVICE
        if (streaming) {
            CoreLoader.whyUnavailable(app)?.let { fail("Voice isn't available on this device: $it"); return }
            ModelDownloader.removeListener(modelListener)
            ModelDownloader.addListener(modelListener)
            if (!ModelDownloader.ensure(app)) {
                val st = ModelDownloader.state
                if (st.running) {
                    val m = ModelStore.current(app)
                    _state.update { it.copy(downloadPct = st.pct.coerceAtLeast(0),
                        message = "Getting the voice model (one time) · ${st.pct}% of ${VoiceModels.formatBytes(m.downloadBytes)}") }
                } else {
                    _state.update { it.copy(phase = VoiceState.Phase.ERROR) }
                }
                return // modelListener starts listening when the model lands
            }
        }
        val editor by app.editorInstance()
        val before = editor.activeContent.textBeforeSelection
        glue = if (before.isNotEmpty() && !before.last().isWhitespace()) " " else ""
        val s = if (streaming) StreamingSession(app) { partial -> main.post { onPartial(partial) } } else null
        output = DictationOutput(
            object : DictationOutput.Field {
                override fun commit(text: String) {
                    val ed by app.editorInstance()
                    ed.commitText(text)
                }
            },
        ) { p -> _state.update { it.copy(transcript = p) } }
        val r = WavRecorder(ctx = app, onChunk = { buf: ShortArray, n: Int ->
            s?.feed(buf, n)
            pushLevel(buf, n)
        }) {
            main.post { if (recorder != null) _state.update { it.copy(message = "Time's up · tap Done to insert") } }
        }
        if (!r.start()) {
            s?.cancel()
            fail("Couldn't open the microphone.")
            return
        }
        session = s
        recorder = r
        _state.update { it.copy(phase = VoiceState.Phase.LISTENING, startedAt = SystemClock.elapsedRealtime(),
            transcript = "", message = "", levels = emptyList(), level = 0f, downloadPct = -1,
            offline = streaming && ModelStore.ready(app), headset = r.bluetoothName, incognito = incognito(app)) }
        if (BuildConfig.DEBUG) Log.i(TAG, "HUMMING_REC start streaming=$streaming")
    }

    private fun onPartial(text: String) {
        if (session == null) return
        output?.onPartial(glue.trimStart() + text)
    }

    private var levelAcc = 0.0
    private var levelN = 0

    private fun pushLevel(buf: ShortArray, n: Int) {
        var sum = 0.0
        for (i in 0 until n) { val v = buf[i] / 32768.0; sum += v * v }
        levelAcc += sum
        levelN += n
        if (levelN < 1600) return // ~10 updates per second at 16 kHz
        val rms = sqrt(levelAcc / levelN)
        levelAcc = 0.0; levelN = 0
        // Speech RMS sits around 0.02–0.2; map to 0..1 on a soft log-ish curve.
        val lv = (sqrt(rms * 12.0)).coerceIn(0.0, 1.0).toFloat()
        main.post {
            _state.update { st ->
                val hist = (st.levels + lv).takeLast(LEVEL_HISTORY)
                st.copy(level = lv, levels = hist)
            }
        }
    }

    private fun fail(msg: String) {
        _state.update { it.copy(open = true, phase = VoiceState.Phase.ERROR, message = msg, level = 0f) }
    }

    /** Done: stop the mic and put the final text into the field (one commit). */
    fun done() {
        if (recorder != null) stop(toField = true) else if (!busy) close()
    }

    /** Cancel: stop and throw the words away (they are still kept in History). */
    fun cancel() {
        if (recorder != null) stop(toField = false, discard = true) else if (!busy) close()
    }

    fun close() {
        // Never leave the microphone running behind a closed panel.
        if (recorder != null) { stop(toField = false, discard = true); return }
        _state.update { VoiceState(clean = it.clean, downloadPct = it.downloadPct, offline = it.offline,
            message = if (it.downloadPct >= 0) it.message else "") }
    }

    /** Set by the keyboard when its incognito mode is on (private fields, or the incognito toggle). */
    @Volatile var keyboardIncognito: Boolean = false

    /** Incognito: no voice history for this dictation. */
    fun incognito(ctx: Context): Boolean = keyboardIncognito || Prefs.voiceIncognito(ctx)

    private fun stop(toField: Boolean, discard: Boolean = false) {
        val r = recorder ?: return
        val app = appContext ?: return
        output?.onStop()
        recorder = null
        busy = true
        val stopAt = SystemClock.elapsedRealtime()
        val clip = r.stop()
        val s = session
        _state.update { it.copy(phase = VoiceState.Phase.FINISHING, level = 0f,
            open = !discard && it.open) }
        Thread({
            val started = System.nanoTime()
            val result: Result<Pair<String, String>> = runCatching {
                if (s != null) {
                    val res = s.finish()
                    if (res.text.isBlank() && res.error != null) throw TranscriberUnavailable(res.error)
                    Pair(res.text, "on-device · ${CoreLoader.variant}")
                } else {
                    val res = DictationPipeline.run(app, clip)
                    Pair(res.text, res.engine)
                }
            }
            val ms = (System.nanoTime() - started) / 1_000_000
            result.getOrNull()?.let { (text, engine) ->
                if (text.isNotBlank() && !incognito(app)) runCatching { HistoryStore(app).add(text, engine, ms, clip) }
            }
            main.post {
                result.onSuccess { (text, engine) ->
                    val out = output
                    if (text.isBlank()) {
                        out?.onFinal("", glue)
                    } else if (!toField || out == null) {
                        out?.onFinal(text, glue, toField = false)
                    } else {
                        out.onFinal(text, glue)
                    }
                    if (BuildConfig.DEBUG) Log.i(TAG, "HUMMING_FINAL engine=$engine chars=${text.length} " +
                        "stop_to_insert_ms=${SystemClock.elapsedRealtime() - stopAt} to_field=$toField")
                    session = null; output = null; busy = false
                    if (text.isBlank() && toField) {
                        _state.update { it.copy(phase = VoiceState.Phase.ERROR, transcript = "",
                            message = "Didn't catch that. Tap the mic and try again.") }
                    } else {
                        close()
                    }
                }.onFailure { e ->
                    output?.onFinal("", glue)
                    session = null; output = null; busy = false
                    fail("Couldn't transcribe: ${e.message ?: e.javaClass.simpleName}")
                }
            }
        }, "humming-finish").start()
    }

    /** Screenshot/preview hook: a mid-dictation state with fake levels. */
    fun previewListening(text: String, clean: Boolean = true) {
        val lv = List(LEVEL_HISTORY) { i -> (0.25f + 0.6f * kotlin.math.abs(sin(i * 0.55f)) * (0.5f + 0.5f * ((i * 37) % 11) / 10f)).coerceIn(0f, 1f) }
        _state.value = VoiceState(open = true, phase = VoiceState.Phase.LISTENING, transcript = text, level = 0.62f,
            levels = lv, startedAt = SystemClock.elapsedRealtime() - 14_000, clean = clean)
    }
}

/* ----------------------------------------------------------------------------------------------------------------
 * UI
 * -------------------------------------------------------------------------------------------------------------- */

/** Humming palette. The accent gradient is the brand; surfaces follow the keyboard theme's day/night. */
data class HummingPalette(
    val background: Color,
    val surface: Color,
    val onBackground: Color,
    val secondary: Color,
    val accentStart: Color,
    val accentEnd: Color,
    val onAccent: Color,
    val rec: Color,
    val ok: Color = Color(0xFF1E9E5A),
) {
    val accentBrush: Brush get() = Brush.linearGradient(listOf(accentStart, accentEnd))

    companion object {
        val Light = HummingPalette(
            background = Color(0xFFEEF0F5), surface = Color(0xFFFFFFFF), onBackground = Color(0xFF11131A),
            secondary = Color(0xFF626879), accentStart = Color(0xFF7B61FF), accentEnd = Color(0xFF3D8BFF),
            onAccent = Color.White, rec = Color(0xFFFF3B5C),
        )
        val Dark = HummingPalette(
            background = Color(0xFF121418), surface = Color(0xFF24272E), onBackground = Color(0xFFF3F4F8),
            secondary = Color(0xFF9BA1AF), accentStart = Color(0xFF8B74FF), accentEnd = Color(0xFF4C96FF),
            onAccent = Color.White, rec = Color(0xFFFF4D6A), ok = Color(0xFF3DD68C),
        )
    }
}

/** The hero mic in the Smartbar: gradient circle, soft glow, springy press. */
@Composable
fun HummingMicButton(isNight: Boolean, modifier: Modifier = Modifier, size: Dp = 38.dp) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val p = if (isNight) HummingPalette.Dark else HummingPalette.Light
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.88f else 1f, spring(dampingRatio = 0.45f, stiffness = 600f), label = "micPress")
    Box(
        modifier = modifier
            .padding(horizontal = 6.dp)
            .size(size)
            .scale(scale)
            .shadow(elevation = 6.dp, shape = CircleShape, ambientColor = p.accentStart, spotColor = p.accentStart)
            .clip(CircleShape)
            .background(p.accentBrush)
            .combinedClickable(
                interactionSource = interaction, indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    HummingVoice.toggle(context)
                },
                // Long-press: choose the voice engine (Humming offline, or Gboard's voice typing when installed).
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (com.humming.keyboard.GboardHandoff.available(context)) HummingVoice.showMenu(true)
                    else HummingVoice.toggle(context)
                },
            )
            .semantics { contentDescription = "Dictate with Humming. Long-press for more voice options" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Mic, contentDescription = null, tint = p.onAccent, modifier = Modifier.size(size * 0.56f))
    }
}

/** Full-height voice panel shown in place of the keyboard while dictating. */
@Composable
fun VoiceInputLayout(isNight: Boolean, height: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val st by HummingVoice.state.collectAsState()
    val p = if (isNight) HummingPalette.Dark else HummingPalette.Light
    val listening = st.phase == VoiceState.Phase.LISTENING
    val finishing = st.phase == VoiceState.Phase.FINISHING

    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(listening) {
        while (listening) { now = SystemClock.elapsedRealtime(); delay(250) }
    }
    val secs = if (st.startedAt > 0) ((now - st.startedAt) / 1000).coerceAtLeast(0) else 0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(p.background)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        // Top row: Clean mode chip, status + timer.
        Row(modifier = Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
            val chipShape = RoundedCornerShape(50)
            Row(
                modifier = Modifier
                    .clip(chipShape)
                    .then(if (st.clean) Modifier.background(p.accentStart.copy(alpha = 0.14f)) else Modifier.border(1.dp, p.secondary.copy(alpha = 0.5f), chipShape))
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        HummingVoice.toggleClean(context)
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = if (st.clean) p.accentStart else p.secondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (st.clean) "Clean" else "Verbatim", color = if (st.clean) p.accentStart else p.secondary,
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            // Humming M5: where the words go. "Offline" = the model is on the phone, no network is used.
            if (st.offline) {
                Spacer(Modifier.width(8.dp))
                StatusPill(p, if (st.headset != null) Icons.Rounded.Headset else Icons.Rounded.CloudOff,
                    if (st.headset != null) "Offline · headset" else "Offline", p.ok)
            }
            if (st.incognito) {
                Spacer(Modifier.width(6.dp))
                StatusPill(p, Icons.Rounded.VisibilityOff, "Private", p.secondary)
            }
            Spacer(Modifier.weight(1f))
            if (listening) {
                val blink = rememberInfiniteTransition(label = "rec")
                val a by blink.animateFloat(1f, 0.25f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "recDot")
                Box(Modifier.size(8.dp).clip(CircleShape).background(p.rec.copy(alpha = a)))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                when {
                    listening -> String.format(java.util.Locale.US, "%d:%02d", secs / 60, secs % 60)
                    finishing -> "Finishing…"
                    st.downloadPct >= 0 -> "${st.downloadPct}%"
                    else -> ""
                },
                color = p.secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            )
        }

        // Live transcript.
        val scroll = rememberScrollState()
        LaunchedEffect(st.transcript) { scroll.animateScrollTo(scroll.maxValue) }
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp)) {
            val placeholder = when {
                st.message.isNotEmpty() -> st.message
                listening -> "Start talking…"
                finishing -> "Polishing your words…"
                else -> ""
            }
            val needsModel = !st.offline && st.transcript.isEmpty() && st.message.isNotEmpty() && !listening && !finishing
            Text(
                text = st.transcript.ifEmpty { placeholder },
                color = if (st.transcript.isEmpty()) p.secondary else p.onBackground,
                fontSize = if (st.transcript.length > 140) 18.sp else 22.sp,
                lineHeight = if (st.transcript.length > 140) 25.sp else 30.sp,
                fontWeight = if (st.transcript.isEmpty()) FontWeight.Normal else FontWeight.Medium,
                textAlign = if (st.transcript.isEmpty()) TextAlign.Center else TextAlign.Start,
                modifier = Modifier.fillMaxWidth().verticalScroll(scroll).padding(horizontal = 6.dp)
                    .then(if (st.transcript.isEmpty()) Modifier.padding(top = 18.dp) else Modifier)
                    .then(if (needsModel) Modifier.clickable { openVoiceSettings(context) } else Modifier),
            )
        }

        // Bottom row: Cancel · orb · Done.
        Row(modifier = Modifier.fillMaxWidth().height(124.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            RoundAction(p, onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                HummingVoice.cancel()
            }) { Icon(Icons.Rounded.Close, "Cancel", tint = p.onBackground, modifier = Modifier.size(24.dp)) }

            MicOrb(p, st, onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (listening) HummingVoice.done() else if (!finishing) HummingVoice.start(context)
            })

            val doneEnabled = listening
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val sc by animateFloatAsState(if (pressed) 0.92f else 1f, spring(dampingRatio = 0.5f, stiffness = 700f), label = "done")
            Row(
                modifier = Modifier
                    .scale(sc)
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(if (doneEnabled) p.accentBrush else Brush.linearGradient(listOf(p.surface, p.surface)))
                    .clickable(interactionSource = interaction, indication = null, enabled = doneEnabled) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        HummingVoice.done()
                    }
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Check, null, tint = if (doneEnabled) p.onAccent else p.secondary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(6.dp))
                Text("Done", color = if (doneEnabled) p.onAccent else p.secondary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun StatusPill(p: HummingPalette, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: Color) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(50)).background(tint.copy(alpha = 0.13f)).padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Settings > Voice (model download and management). */
fun openVoiceSettings(ctx: Context) {
    runCatching {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("ui://florisboard/settings/voice"))
            .setPackage(ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
private fun RoundAction(p: HummingPalette, onClick: () -> Unit, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val sc by animateFloatAsState(if (pressed) 0.9f else 1f, spring(dampingRatio = 0.5f, stiffness = 700f), label = "round")
    Box(
        modifier = Modifier
            .scale(sc)
            .size(52.dp)
            .shadow(1.dp, CircleShape)
            .clip(CircleShape)
            .background(p.surface)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** The orb: gradient mic with a radial live-level ring and a breathing halo; a spinner arc while finishing. */
@Composable
private fun MicOrb(p: HummingPalette, st: VoiceState, onClick: () -> Unit) {
    val listening = st.phase == VoiceState.Phase.LISTENING
    val finishing = st.phase == VoiceState.Phase.FINISHING
    val level by animateFloatAsState(st.level, tween(120, easing = LinearEasing), label = "level")
    val inf = rememberInfiniteTransition(label = "orb")
    val breath by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath")
    val spin by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(1000, easing = LinearEasing)), label = "spin")
    val levels = st.levels
    Box(modifier = Modifier.size(124.dp).clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick),
        contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val orbR = 38.dp.toPx()
            if (listening) {
                // Halo that swells with the voice.
                val haloR = orbR * (1.18f + 0.35f * level + 0.06f * breath)
                drawCircle(p.accentStart.copy(alpha = 0.16f + 0.10f * level), radius = haloR, center = c)
                // Radial level bars (the last ~4 s of speech, newest at the top).
                val n = 40
                for (i in 0 until n) {
                    val v = levels.getOrNull(levels.size - 1 - i) ?: 0f
                    val ang = (-PI / 2 + 2 * PI * i / n).toFloat()
                    val r0 = orbR + 4.dp.toPx()
                    val r1 = r0 + 2.dp.toPx() + v * 14.dp.toPx()
                    drawLine(
                        color = p.accentEnd.copy(alpha = 0.35f + 0.6f * v),
                        start = Offset(c.x + r0 * cos(ang), c.y + r0 * sin(ang)),
                        end = Offset(c.x + r1 * cos(ang), c.y + r1 * sin(ang)),
                        strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round,
                    )
                }
            } else if (st.downloadPct >= 0) {
                drawCircle(p.secondary.copy(alpha = 0.2f), radius = orbR + 6.dp.toPx(), center = c, style = Stroke(3.dp.toPx()))
                drawArc(p.accentStart, -90f, 3.6f * st.downloadPct, false,
                    topLeft = Offset(c.x - orbR - 6.dp.toPx(), c.y - orbR - 6.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size((orbR + 6.dp.toPx()) * 2, (orbR + 6.dp.toPx()) * 2),
                    style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            }
            if (finishing) {
                val rr = orbR + 6.dp.toPx()
                drawArc(p.accentEnd, spin, 90f, false, topLeft = Offset(c.x - rr, c.y - rr),
                    size = androidx.compose.ui.geometry.Size(rr * 2, rr * 2), style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
            }
            drawCircle(brush = Brush.linearGradient(listOf(p.accentStart, p.accentEnd), start = Offset(c.x - orbR, c.y - orbR),
                end = Offset(c.x + orbR, c.y + orbR)), radius = orbR * (if (listening) 1f + 0.05f * level else 1f), center = c)
        }
        Icon(Icons.Rounded.Mic, contentDescription = if (listening) "Stop and insert" else "Start dictating",
            tint = p.onAccent, modifier = Modifier.size(36.dp))
    }
}


/** Long-press on the mic: pick the voice engine. Shown over the keys; tap outside to dismiss. */
@Composable
fun VoiceEngineMenu(isNight: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val p = if (isNight) HummingPalette.Dark else HummingPalette.Light
    val st by HummingVoice.state.collectAsState()
    Box(
        modifier = modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.28f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { HummingVoice.showMenu(false) },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.padding(top = 10.dp, start = 14.dp, end = 14.dp).fillMaxWidth()
                .shadow(8.dp, RoundedCornerShape(20.dp)).clip(RoundedCornerShape(20.dp)).background(p.surface)
                .padding(vertical = 8.dp),
        ) {
            Text("Dictate with", color = p.secondary, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
            EngineRow(p, "Humming", if (st.offline) "Offline · on this phone · private" else "On this phone · private",
                brush = p.accentBrush) {
                HummingVoice.showMenu(false)
                HummingVoice.start(context)
            }
            EngineRow(p, "Gboard voice typing", "Google's voice typing (Rambler on Pixel). Gboard's keyboard button brings you back.",
                brush = Brush.linearGradient(listOf(Color(0xFF4285F4), Color(0xFF34A853)))) {
                HummingVoice.showMenu(false)
                dev.patrickgold.florisboard.FlorisImeService.switchToGboardVoice()
            }
        }
    }
}

@Composable
private fun EngineRow(p: HummingPalette, title: String, subtitle: String, brush: Brush, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(brush), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Mic, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = p.onBackground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = p.secondary, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}
