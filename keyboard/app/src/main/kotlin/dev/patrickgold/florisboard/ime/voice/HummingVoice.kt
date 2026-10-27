/*
 * Humming (added file, Apache-2.0 like the rest of this fork).
 *
 * The voice hero of the keyboard: a big gradient mic in the Smartbar opens a full-height voice panel with a live
 * transcript, a level-reactive mic orb (tap to start, tap again to stop: the one control), Clean mode and Cancel.
 * While the user talks the words show in the field as an underlined draft that is corrected in place, and the final
 * text is committed once on stop (DictationOutput; "Live text while speaking" off = nothing until stop). There is no
 * length limit, and a foreground service keeps the mic and the on-device engine running with the screen off (DictationService).
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Headset
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.humming.keyboard.CoreLoader
import com.humming.keyboard.DictationOutput
import com.humming.keyboard.DictationPipeline
import com.humming.keyboard.DictationService
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
    /** Online mode (opt-in): the phone's speech service instead of the on-device engine. */
    private var online: com.humming.keyboard.OnlineSession? = null
    private fun recording() = recorder != null || online != null
    private var session: StreamingSession? = null
    private var output: DictationOutput? = null
    private var glue = ""

    /** Raw words of the recording being committed, before clean-up: what edit by voice reads (1.8). */
    private var spokenRaw = ""

    /** The keyboard's own last dictated commit and the field it went into (what "undo that" may remove). Kept
     *  across recordings; a different field (or a new input session of it) means there is nothing of ours. */
    private var lastCommit: String? = null
    private var lastCommitTarget: com.humming.keyboard.DictationOutput.Target? = null
    private var busy = false
    @Volatile private var warmed = false
    private var appContext: Context? = null

    val isOpen: Boolean get() = _state.value.open

    /** The long-press voice menu (Humming offline / Gboard voice). */
    private val _menu = MutableStateFlow(false)
    val menu: StateFlow<Boolean> = _menu.asStateFlow()
    fun showMenu(on: Boolean) { _menu.value = on }

    /**
     * The field that has input focus now (package, field id, input type, name, hint and input generation), or null
     * once input finished. Tracked by FlorisImeService on onStartInput/onFinishInput, never a stale EditorInfo.
     */
    private fun focusedTarget(): com.humming.keyboard.DictationOutput.Target? =
        dev.patrickgold.florisboard.FlorisImeService.currentDictationTarget

    /** Keyboard became visible: make sure the speech model is there (one-time download) and warm the engine. */
    fun onStartInputView(ctx: Context) {
        val app = ctx.applicationContext
        appContext = app
        // Keyboard shown again while still dictating: keep listening and re-attach. The draft is replayed only in the
        // same input session of the field where the dictation started. Back from screen-off or another app, Android
        // starts a fresh input session (the input generation bumps), so nothing is replayed here and the final text
        // goes to History instead (fails closed).
        if (recording()) output?.attach()
        _state.update { it.copy(clean = Prefs.cleanMode(app), offline = ModelStore.ready(app)) }
        ModelDownloader.removeListener(modelListener)
        ModelDownloader.addListener(modelListener)
        if (Prefs.mode(app) == Prefs.MODE_DEVICE && CoreLoader.whyUnavailable(app) == null) {
            if (ModelDownloader.ensure(app)) scheduleWarm(app)
        }
    }

    /**
     * Keyboard hidden mid-dictation (screen off, lock screen, another app): keep listening; only the user stops a
     * dictation (the panel's mic, or Stop in the notification). Nothing is typed while hidden; the words wait.
     */
    fun onFinishInputView() {
        _menu.value = false
        main.removeCallbacks(warmRunnable)
        if (recording()) output?.detach()
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
        if (recording()) done() else start(ctx)
    }

    /** Stop from the notification (screen off / lock screen): the rest goes into the field if the keyboard is up. */
    private fun stopFromNotification() {
        if (recording()) stop(toField = output?.attached ?: false)
    }

    fun start(ctx: Context) {
        val app = ctx.applicationContext
        appContext = app
        if (recording() || busy) return
        _state.update { VoiceState(open = true, phase = VoiceState.Phase.PREPARING, clean = Prefs.cleanMode(app),
            downloadPct = it.downloadPct, message = it.message) }
        if (app.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            app.startActivity(Intent(app, PermissionActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            fail("Allow the microphone, then tap the mic again.")
            return
        }
        val onlineMode = Prefs.engine(app) == Prefs.ENGINE_ONLINE
        val streaming = !onlineMode && Prefs.mode(app) == Prefs.MODE_DEVICE
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
        val after = editor.activeContent.textAfterSelection
        // The dictation fits the sentence it lands in: no space after "(", lowercase when continuing a sentence, no
        // final period when the text after the cursor carries on (DictationJoin).
        glue = com.humming.keyboard.DictationJoin.glue(before)
        // Snapshot the privacy state at capture start: leaving a private field mid-dictation must not make it keepable.
        sessionIncognito = incognito(app)
        val dictionary = Prefs.dictionary(app)
        val s = if (streaming) StreamingSession(app) { partial -> main.post { onPartial(partial) } } else null
        val startedIn = focusedTarget()
        output = DictationOutput(
            object : DictationOutput.Field {
                // Live words are the field's composing text (the underlined draft), replaced in place on every partial;
                // the final text is committed once. While the draft is up the editor must not re-mark the composing
                // region around the current word (externalComposing), or the next update would land beside the old one.
                private fun ic() = dev.patrickgold.florisboard.FlorisImeService.currentInputConnection()
                override fun setComposing(text: String): Boolean {
                    val ic = ic() ?: return false
                    val ed by app.editorInstance()
                    ic.beginBatchEdit()
                    if (!ed.externalComposing) {
                        ed.externalComposing = true
                        // The keyboard may have marked the word before the cursor as composing (suggestions): fix it in
                        // place first so the draft goes after it instead of replacing it.
                        ic.finishComposingText()
                    }
                    val ok = ic.setComposingText(text, 1)
                    ic.endBatchEdit()
                    return ok
                }

                private fun remember(text: String?) {
                    lastCommit = text
                    lastCommitTarget = if (text == null) null else startedIn
                }

                @Suppress("ReturnCount")
                override fun commitFinal(text: String, replacesDraft: Boolean): Boolean {
                    val ed by app.editorInstance()
                    val ic = ic()
                    if (ic == null) { ed.externalComposing = false; return false }
                    // D5 voice editing: a whole utterance such as "change Tuesday to Wednesday" edits the text
                    // before the cursor instead of being typed. Only when the target is really there;
                    // otherwise it is dictation as usual.
                    val voiceEditOn = com.humming.keyboard.Prefs.voiceEdit(app)
                    // Commands are read from this recording's raw words, not the cleaned text (1.8).
                    val spoken = spokenRaw.ifBlank { text }
                    if (voiceEditOn && com.humming.keyboard.VoiceEdit.parse(spoken) != null) {
                        ic.beginBatchEdit()
                        if (replacesDraft) ic.setComposingText("", 1)
                        ic.finishComposingText()
                        ic.endBatchEdit()
                        val ctxChars = com.humming.keyboard.VoiceEdit.CONTEXT_CHARS
                        val before = ic.getTextBeforeCursor(ctxChars, 0)?.toString() ?: ""
                        val truncated = before.length >= ctxChars
                        val r = com.humming.keyboard.VoiceEdit.voiceCommand(before, spoken, truncated)
                        ic.beginBatchEdit()
                        val done: Boolean
                        val o = r?.outcome
                        if (r == null) {
                            done = ic.commitText(text, 1)
                            if (done) remember(text)
                        } else if (o == null) {
                            // "undo that": remove only the keyboard's own last dictated text (from this or an
                            // earlier recording), only in the same field and if it still sits before the cursor
                            val mine = lastCommit.takeIf { startedIn != null && lastCommitTarget == startedIn }
                            val n = com.humming.keyboard.VoiceEdit.undoCount(before, mine)
                            if (n > 0) ic.deleteSurroundingText(n, 0)
                            remember(null)
                            done = true
                        } else {
                            val (del, put) = com.humming.keyboard.VoiceEdit.fieldEdit(before, o)
                            if (del > 0) ic.deleteSurroundingText(del, 0)
                            done = ic.commitText(put, 1)
                            remember(null)
                        }
                        ic.endBatchEdit()
                        ed.externalComposing = false
                        return done
                    }
                    ic.beginBatchEdit()
                    // commitText replaces the composing text: the draft when there is one, else nothing (the keyboard's
                    // own composing word is fixed first).
                    if (!replacesDraft) ic.finishComposingText()
                    val ok = ic.commitText(text, 1)
                    if (ok) remember(text)
                    ic.endBatchEdit()
                    ed.externalComposing = false
                    return ok
                }
                override fun clearComposing() {
                    val ed by app.editorInstance()
                    ic()?.let { ic ->
                        ic.beginBatchEdit()
                        ic.setComposingText("", 1)
                        ic.finishComposingText()
                        ic.endBatchEdit()
                    }
                    ed.externalComposing = false
                }
            },
            { p -> _state.update { it.copy(transcript = p) } },
            glue = glue,
            live = Prefs.liveTyping(app),
            adapt = { t -> com.humming.keyboard.DictationJoin.adapt(before, t, after, dictionary) },
            // Bound to the field it started in: never replayed into another app or box (inOriginalField).
            target = startedIn,
            currentTarget = { focusedTarget() },
        )
        if (onlineMode) {
            val o = com.humming.keyboard.OnlineSession(app, onPartial = { p -> onOnlinePartial(app, p) },
                onLevel = { lv -> pushLevelValue(lv) })
            if (!o.start()) {
                fail("Online mode isn't available: ${o.error ?: "no speech service"}. Switch to Offline in Settings › Voice.")
                output = null
                return
            }
            online = o
            DictationService.onStopRequested = { main.post { stopFromNotification() } }
            DictationService.begin(app)
            _state.update { it.copy(phase = VoiceState.Phase.LISTENING, startedAt = SystemClock.elapsedRealtime(),
                transcript = "", message = "", levels = emptyList(), level = 0f, downloadPct = -1,
                offline = false, headset = null, incognito = dictationIncognito(app)) }
            return
        }
        // No length limit: the recorder streams until the user stops it.
        val r = WavRecorder(ctx = app, onChunk = { buf: ShortArray, n: Int ->
            s?.feed(buf, n)
            pushLevel(buf, n)
        })
        if (!r.start()) {
            s?.cancel()
            fail("Couldn't open the microphone.")
            return
        }
        session = s
        recorder = r
        // Screen off or keyboard hidden must not end the dictation: foreground service + wake lock while the mic is on.
        DictationService.onStopRequested = { main.post { stopFromNotification() } }
        DictationService.begin(app)
        _state.update { it.copy(phase = VoiceState.Phase.LISTENING, startedAt = SystemClock.elapsedRealtime(),
            transcript = "", message = "", levels = emptyList(), level = 0f, downloadPct = -1,
            offline = streaming && ModelStore.ready(app), headset = r.bluetoothName, incognito = dictationIncognito(app)) }
        if (BuildConfig.DEBUG) Log.i(TAG, "HUMMING_REC start streaming=$streaming")
    }

    private fun onPartial(text: String) {
        if (session == null) return
        output?.onPartial(text)
    }

    /** Online mode: the same on-device clean-up, personal dictionary and spoken commands as offline. */
    private fun cleanOnline(app: Context, raw: String): com.humming.keyboard.VoiceCommands.Result {
        val dict = Prefs.dictionary(app)
        val pairs = Prefs.replacements(app)
        val t = if (Prefs.cleanMode(app)) {
            com.humming.keyboard.TextCleanup.apply(raw, dict, Prefs.accuracyMode(app), Prefs.removeFillers(app), pairs)
        } else {
            com.humming.keyboard.TextCleanup.applyDictionary(com.humming.keyboard.TextCleanup.replacePhrases(raw, pairs), dict)
        }
        return com.humming.keyboard.VoiceCommands.apply(t)
    }

    private fun onOnlinePartial(app: Context, raw: String) {
        if (online == null) return
        output?.onPartial(cleanOnline(app, raw).text)
    }

    private fun pushLevelValue(lv: Float) {
        _state.update { st -> st.copy(level = lv, levels = (st.levels + lv).takeLast(LEVEL_HISTORY)) }
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

    /** Stop (the mic orb, tapped again): the rest of the words go into the field. */
    fun done() {
        if (recording()) stop(toField = output?.attached ?: true) else if (!busy) close()
    }

    /** Cancel: stop and take this dictation's words back out of the field (they are still kept in History). */
    fun cancel() {
        if (recording()) stop(toField = false, discard = true) else if (!busy) close()
    }

    fun close() {
        // Never leave the microphone running behind a closed panel; what was said is kept.
        if (recording()) { stop(toField = output?.attached ?: false); return }
        _state.update { VoiceState(clean = it.clean, downloadPct = it.downloadPct, offline = it.offline,
            message = if (it.downloadPct >= 0) it.message else "") }
    }

    /** Set by the keyboard when its incognito mode is on (private fields, or the incognito toggle). */
    @Volatile var keyboardIncognito: Boolean = false

    /** Set by the keyboard: the focused field is a password field or asked for no personalised learning. */
    @Volatile var fieldForcesIncognito: Boolean = false

    /** Incognito right now: no voice history, no AI clean-up. */
    fun incognito(ctx: Context): Boolean = keyboardIncognito || fieldForcesIncognito || Prefs.voiceIncognito(ctx)

    /** Privacy state captured when this dictation started (see [start]); it only ever makes a dictation stricter. */
    @Volatile private var sessionIncognito = false

    /** Incognito for the running dictation: private when it started or private now. */
    private fun dictationIncognito(ctx: Context): Boolean = sessionIncognito || incognito(ctx)

    private fun stop(toField: Boolean, discard: Boolean = false) {
        val r = recorder
        val o = online
        if (r == null && o == null) return
        val app = appContext ?: return
        // Snapshot this dictation's privacy now: a new dictation may start (and overwrite sessionIncognito) while this
        // one is still being transcribed on the worker thread below.
        val keepPrivate = dictationIncognito(app)
        output?.onStop()
        if (discard) output?.discard()
        recorder = null
        online = null
        DictationService.end(app)
        DictationService.onStopRequested = null
        busy = true
        val stopAt = SystemClock.elapsedRealtime()
        val clip = r?.stop() ?: com.humming.keyboard.AudioClip(ByteArray(0), WavRecorder.SAMPLE_RATE)
        val s = session
        _state.update { it.copy(phase = VoiceState.Phase.FINISHING, level = 0f,
            open = !discard && it.open) }
        Thread({
            val started = System.nanoTime()
            var spoken = ""
            val result: Result<Pair<String, String>> = runCatching {
                var instruction: String? = null
                var (text, engine) = if (o != null) {
                    val raw = o.finish()
                    spoken = raw
                    if (raw.isBlank() && o.error != null) throw TranscriberUnavailable(o.error ?: "")
                    val c = cleanOnline(app, raw)
                    instruction = c.instruction
                    Pair(c.text, "online · speech service")
                } else if (s != null) {
                    val res = s.finish()
                    spoken = res.raw
                    if (res.text.isBlank() && res.error != null) throw TranscriberUnavailable(res.error)
                    instruction = res.instruction
                    Pair(res.text, "on-device · ${CoreLoader.variant}")
                } else {
                    val res = DictationPipeline.run(app, clip)
                    spoken = res.raw
                    Pair(res.text, res.engine)
                }
                // Optional AI clean-up with the user's own key (never in incognito; failure keeps the text above).
                if (!discard && com.humming.keyboard.LlmCleanup.enabled(app, keepPrivate || incognito(app), text)) {
                    main.post { _state.update { it.copy(message = "AI clean-up…") } }
                    runCatching { com.humming.keyboard.LlmCleanup.polish(app, text, instruction) }
                        .onSuccess { text = it; engine += " + AI clean-up" }
                        .onFailure { e -> main.post { _state.update { it.copy(message = "AI clean-up failed (${e.message}); kept the on-device text") } } }
                }
                Pair(text, engine)
            }
            val ms = (System.nanoTime() - started) / 1_000_000
            result.getOrNull()?.let { (text, engine) ->
                if (text.isNotBlank() && !(keepPrivate || incognito(app))) runCatching { HistoryStore(app).add(text, engine, ms, clip) }
            }
            main.post {
                result.onSuccess { (text, engine) ->
                    spokenRaw = spoken
                    val out = output
                    // A whole-utterance "undo that" is consumed by the voice commands (blank text); with edit
                    // by voice on it still removes the keyboard's own last dictated text in this field (1.8).
                    val undoOnly = !discard && toField && out != null && text.isBlank() &&
                        com.humming.keyboard.Prefs.voiceEdit(app) &&
                        com.humming.keyboard.VoiceEdit.parse(spoken) == com.humming.keyboard.VoiceEdit.Command.Undo
                    if (discard) {
                        // Cancelled: the words already typed were taken back out in stop().
                    } else if (undoOnly) {
                        out?.onFinal(spoken)
                    } else if (text.isBlank()) {
                        out?.onFinal("")
                    } else if (!toField || out == null) {
                        out?.onFinal(text, toField = false)
                    } else {
                        out.onFinal(text)
                    }
                    if (BuildConfig.DEBUG) Log.i(TAG, "HUMMING_FINAL engine=$engine chars=${text.length} " +
                        "stop_to_insert_ms=${SystemClock.elapsedRealtime() - stopAt} to_field=$toField")
                    session = null; output = null; busy = false
                    if (text.isBlank() && toField && !undoOnly) {
                        _state.update { it.copy(phase = VoiceState.Phase.ERROR, transcript = "",
                            message = "Didn't catch that. Tap the mic and try again.") }
                    } else {
                        close()
                    }
                }.onFailure { e ->
                    output?.onFinal("")
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
    /** Accent for small text and icons on accent-tinted surfaces: meets 4.5:1 there (docs/BRAND.md tokens). */
    val accentText: Color = accentStart,
) {
    val accentBrush: Brush get() = Brush.linearGradient(listOf(accentStart, accentEnd))

    companion object {
        val Light = HummingPalette(
            background = Color(0xFFEEF0F5), surface = Color(0xFFFFFFFF), onBackground = Color(0xFF11131A),
            // 1.7 contrast pass: secondary and ok darkened so 12-13sp pill text meets 4.5:1 on its tinted pill.
            secondary = Color(0xFF545A6A), accentStart = Color(0xFFE5486B), accentEnd = Color(0xFFCA349C),
            onAccent = Color.White, rec = Color(0xFFFF3B5C), ok = Color(0xFF136B3D), accentText = Color(0xFFB03050),
        )
        val Dark = HummingPalette(
            background = Color(0xFF121418), surface = Color(0xFF24272E), onBackground = Color(0xFFF3F4F8),
            secondary = Color(0xFF9BA1AF), accentStart = Color(0xFFF0607F), accentEnd = Color(0xFFD9479F),
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
    // 1.7: the touch target fills the Smartbar's height and is at least 48dp wide; the visible circle keeps the size
    // the user picked (Settings > Smartbar). Same 50dp footprint as before (6 + 38 + 6).
    Box(
        modifier = modifier
            .padding(horizontal = 1.dp)
            .fillMaxHeight()
            .widthIn(min = 48.dp)
            .combinedClickable(
                interactionSource = interaction, indication = null, role = Role.Button, onLongClickLabel = "More voice options",
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
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer { scaleX = scale; scaleY = scale } // press spring in the draw phase, no recomposition
                .shadow(elevation = 6.dp, shape = CircleShape, ambientColor = p.accentStart, spotColor = p.accentStart)
                .clip(CircleShape)
                .background(p.accentBrush),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Mic, contentDescription = null, tint = p.onAccent, modifier = Modifier.size(size * 0.56f))
        }
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
        if (listening) while (true) { now = SystemClock.elapsedRealtime(); delay(250) } // ends when the key changes
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
        // 1.7: 48dp tall so the Clean chip's touch target meets the 48dp minimum; the visible pill stays compact.
        Row(modifier = Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            val chipShape = RoundedCornerShape(50)
            Box(
                modifier = Modifier
                    .height(48.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null,
                        role = Role.Switch, onClickLabel = if (st.clean) "Switch to verbatim" else "Switch to clean",
                    ) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        HummingVoice.toggleClean(context)
                    }
                    .semantics { stateDescription = if (st.clean) "Clean mode on" else "Clean mode off, verbatim" },
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    modifier = Modifier
                        .clip(chipShape)
                        .then(if (st.clean) Modifier.background(p.accentStart.copy(alpha = 0.14f)) else Modifier.border(1.dp, p.secondary.copy(alpha = 0.5f), chipShape))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = if (st.clean) p.accentText else p.secondary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (st.clean) "Clean" else "Verbatim", color = if (st.clean) p.accentText else p.secondary,
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            // Humming M5: where the words go. "Offline" = the model is on the phone, no network is used.
            if (st.offline) {
                Spacer(Modifier.width(8.dp))
                StatusPill(p, if (st.headset != null) Icons.Rounded.Headset else Icons.Rounded.CloudOff,
                    if (st.headset != null) "Offline · headset" else "Offline", p.ok)
            }
            if (st.incognito) {
                Spacer(Modifier.width(6.dp))
                // Online mode still sends audio to the phone's speech service in incognito; there incognito only skips history.
                StatusPill(p, Icons.Rounded.VisibilityOff, if (st.offline) "Private" else "No history", p.secondary)
            }
            Spacer(Modifier.weight(1f))
            if (listening) {
                val blink = rememberInfiniteTransition(label = "rec")
                val a by blink.animateFloat(1f, 0.25f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "recDot")
                // Alpha is read in the layer (draw phase), so the blink does not recompose the panel every frame.
                Box(Modifier.size(8.dp).graphicsLayer { alpha = a }.clip(CircleShape).background(p.rec))
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

        // Bottom row: Cancel · orb. One control: tap the orb to start, tap it again to stop (words are typed live).
        Row(modifier = Modifier.fillMaxWidth().height(124.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            RoundAction(p, onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                HummingVoice.cancel()
            }) { Icon(Icons.Rounded.Close, if (listening) "Cancel and remove these words" else "Close", tint = p.onBackground,
                modifier = Modifier.size(24.dp)) }

            MicOrb(p, st, onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (listening) HummingVoice.done() else if (!finishing) HummingVoice.start(context)
            })

            // Balances the Cancel button so the orb stays centred.
            Spacer(Modifier.size(52.dp))
        }
        Text(
            when {
                listening -> "Tap the circle to stop · no time limit"
                finishing -> ""
                else -> "Tap the circle to talk"
            },
            color = p.secondary, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
        )
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
            .addCategory(Intent.CATEGORY_BROWSABLE) // without it the app treats the VIEW intent as an extension import
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
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
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
    Box(modifier = Modifier.size(124.dp).clickable(indication = null, interactionSource = remember { MutableInteractionSource() },
            role = Role.Button, onClick = onClick),
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
        Icon(if (listening) Icons.Rounded.Stop else Icons.Rounded.Mic,
            contentDescription = if (listening) "Stop dictating" else "Start dictating",
            tint = p.onAccent, modifier = Modifier.size(if (listening) 30.dp else 36.dp))
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
            val onlineEngine = com.humming.keyboard.Prefs.engine(context) == com.humming.keyboard.Prefs.ENGINE_ONLINE
            EngineRow(p, "Humming", if (onlineEngine) "Online · uses the phone's speech service" else "Offline · on this phone · private",
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
