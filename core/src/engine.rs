use crate::audio::resample_linear;
use crate::cleanup::{cleanup, CleanupOpts};
use crate::vad::{self, VadConfig};
use crate::{CoreError, SAMPLE_RATE};
use std::borrow::Cow;
use std::path::PathBuf;
use std::time::Instant;

#[derive(Debug, Clone, PartialEq, serde::Serialize, serde::Deserialize)]
pub struct EngineConfig {
    pub model_path: PathBuf,
    /// `Some("en")` by default; `None` = auto-detect.
    pub language: Option<String>,
    pub vad: VadConfig,
    /// `None` = min(4, available cores).
    pub threads: Option<u32>,
    pub cleanup: CleanupOpts,
    /// Personal-dictionary bias passed to whisper as its initial prompt.
    pub initial_prompt: Option<String>,
    /// Decode-speed knobs (M3). Defaults are the measured fast path; see `SpeedOpts`.
    pub speed: SpeedOpts,
}

/// Decode-speed knobs. whisper.cpp's encoder always runs a full 30 s window (1500 frames) unless `audio_ctx` says
/// otherwise, so a 4 s phrase costs as much as 30 s of audio. With `dynamic_audio_ctx` the encoder window is cut to
/// the phrase length plus `audio_ctx_pad_ms` (never below `min_audio_ctx` frames; 50 frames = 1 s).
#[derive(Debug, Clone, PartialEq, serde::Serialize, serde::Deserialize)]
#[cfg_attr(feature = "ffi", derive(uniffi::Record))]
pub struct SpeedOpts {
    pub dynamic_audio_ctx: bool,
    pub audio_ctx_pad_ms: u32,
    /// Lower bound for the encoder window in frames (0 = none). Ignored when `dynamic_audio_ctx` is off.
    pub min_audio_ctx: u32,
    /// whisper's temperature fallback: re-decodes a window at higher temperature when the greedy pass looks
    /// unsure. Off = one greedy pass (beam 1) per window.
    pub temperature_fallback: bool,
    /// One segment per window, no timestamp tokens (fewer decode steps; the app never shows timestamps).
    pub single_segment: bool,
    pub no_timestamps: bool,
}

impl Default for SpeedOpts {
    fn default() -> Self {
        Self {
            dynamic_audio_ctx: true,
            audio_ctx_pad_ms: 1000,
            // Measured (core-bench 36587130559): with no floor, 2-4 s phrases get a 128-224 frame window and the
            // greedy decoder loops ("What time is it? What time is it? ..."); 512 frames (~10 s) keeps set20 WER
            // at the M2 figure while a 10-15 s dictation still skips most of the 30 s window.
            min_audio_ctx: 512,
            temperature_fallback: false,
            single_segment: true,
            no_timestamps: true,
        }
    }
}

impl SpeedOpts {
    /// The M2 behaviour: full 30 s window, fallback on, timestamps on (for before/after benches).
    pub fn m2() -> Self {
        Self {
            dynamic_audio_ctx: false,
            audio_ctx_pad_ms: 0,
            min_audio_ctx: 0,
            temperature_fallback: true,
            single_segment: false,
            no_timestamps: false,
        }
    }

    /// Encoder frames for `n_samples` of 16 kHz speech; 0 = whisper's default (full window).
    pub fn audio_ctx_for(&self, n_samples: usize) -> i32 {
        if !self.dynamic_audio_ctx {
            return 0;
        }
        let ms = n_samples as u64 * 1000 / SAMPLE_RATE as u64 + self.audio_ctx_pad_ms as u64;
        let frames = ((ms * 50 + 999) / 1000) as u32;
        // Round up to a multiple of 32 (friendlier matmul shapes), clamp to the model window.
        let frames = ((frames + 31) / 32 * 32).max(self.min_audio_ctx);
        if frames >= 1500 {
            0
        } else {
            frames as i32
        }
    }
}

impl EngineConfig {
    pub fn new(model_path: impl Into<PathBuf>) -> Self {
        Self {
            model_path: model_path.into(),
            language: Some("en".to_string()),
            vad: VadConfig::default(),
            threads: None,
            cleanup: CleanupOpts::default(),
            initial_prompt: None,
            speed: SpeedOpts::default(),
        }
    }

    fn thread_count(&self) -> i32 {
        let cores = std::thread::available_parallelism().map(|n| n.get() as u32).unwrap_or(2);
        self.threads.unwrap_or_else(|| cores.min(4)).max(1) as i32
    }
}

#[derive(Debug, Clone, PartialEq, serde::Serialize, serde::Deserialize)]
#[cfg_attr(feature = "ffi", derive(uniffi::Record))]
pub struct Segment {
    pub start_ms: u64,
    pub end_ms: u64,
    pub text: String,
}

#[derive(Debug, Clone, PartialEq, serde::Serialize, serde::Deserialize)]
#[cfg_attr(feature = "ffi", derive(uniffi::Record))]
pub struct Transcript {
    pub text: String,
    pub segments: Vec<Segment>,
    /// Wall time inside `transcribe_pcm` (VAD + STT).
    pub ms: u64,
    pub audio_ms: u64,
    pub speech_ms: u64,
    /// Language whisper used: the configured one, or the detected one when `language` is `None` (auto).
    pub language: Option<String>,
    /// Where the STT time went (zeros when whisper did not run, e.g. all silence).
    pub timing: Timing,
}

/// Wall-time breakdown of one STT call, in milliseconds.
#[derive(Debug, Clone, Default, PartialEq, serde::Serialize, serde::Deserialize)]
#[cfg_attr(feature = "ffi", derive(uniffi::Record))]
pub struct Timing {
    /// Creating the decoder state (first call only; the state is kept and reused afterwards).
    pub state_ms: u64,
    /// PCM -> log-mel spectrogram (inside whisper_full, up to the first encoder start).
    pub mel_ms: u64,
    /// Encoder passes plus the prompt prefill (up to the first sampled token of each window).
    pub encode_ms: u64,
    /// Token-by-token decoding.
    pub decode_ms: u64,
    /// Encoder window used (frames, 50 per second; 1500 = full 30 s window).
    pub audio_ctx: u32,
    pub threads: u32,
    /// Encoder passes (windows) and decoder steps whisper ran.
    pub encoder_passes: u32,
    pub decode_steps: u32,
    /// 1 when the short-window pass looked like a repetition loop and was re-run on the full window.
    pub retried: u32,
}

#[derive(Debug, Clone, PartialEq, serde::Serialize, serde::Deserialize)]
#[cfg_attr(feature = "ffi", derive(uniffi::Record))]
pub struct Dictation {
    pub raw: Transcript,
    /// Cleaned text, ready to insert.
    pub text: String,
    pub cleanup_ms: u64,
}

/// Loaded once, reused for every dictation. `Send + Sync`.
pub struct Engine {
    cfg: EngineConfig,
    #[cfg(feature = "whisper")]
    ctx: whisper_rs::WhisperContext,
    /// Decoder state (KV caches, compute buffers), created on first use and reused: creating it costs
    /// hundreds of ms on a phone.
    #[cfg(feature = "whisper")]
    state: std::sync::Mutex<Option<whisper_rs::WhisperState>>,
}

impl Engine {
    pub fn new(cfg: EngineConfig) -> Result<Self, CoreError> {
        #[cfg(not(feature = "whisper"))]
        {
            let _ = cfg;
            Err(CoreError::SttUnavailable)
        }
        #[cfg(feature = "whisper")]
        {
            if !cfg.model_path.is_file() {
                return Err(CoreError::ModelNotFound(cfg.model_path.clone()));
            }
            // Route whisper.cpp/ggml logging away from stderr (no log backend = silent).
            whisper_rs::install_logging_hooks();
            let mut params = whisper_rs::WhisperContextParameters::default();
            params.use_gpu(cfg!(any(feature = "metal", feature = "coreml")));
            let ctx = whisper_rs::WhisperContext::new_with_params(&cfg.model_path, params)
                .map_err(|e| CoreError::ModelLoad(e.to_string()))?;
            Ok(Self { cfg, ctx, state: std::sync::Mutex::new(None) })
        }
    }

    pub fn config(&self) -> &EngineConfig {
        &self.cfg
    }

    /// VAD trim + whisper. No cleanup.
    pub fn transcribe_pcm(&self, samples: &[f32], sample_rate: u32) -> Result<Transcript, CoreError> {
        let started = Instant::now();
        if sample_rate == 0 {
            return Err(CoreError::Audio("sample rate is 0".into()));
        }
        let audio_ms = samples.len() as u64 * 1000 / sample_rate as u64;
        let pcm: Cow<[f32]> = if sample_rate == SAMPLE_RATE {
            Cow::Borrowed(samples)
        } else {
            Cow::Owned(resample_linear(samples, sample_rate, SAMPLE_RATE))
        };
        let mut speech = if self.cfg.vad.enabled {
            vad::trim(&pcm, SAMPLE_RATE, &self.cfg.vad)
        } else {
            pcm.into_owned()
        };
        let speech_ms = speech.len() as u64 * 1000 / SAMPLE_RATE as u64;
        if speech.is_empty() {
            return Ok(Transcript {
                text: String::new(),
                segments: Vec::new(),
                ms: started.elapsed().as_millis() as u64,
                audio_ms,
                speech_ms: 0,
                language: self.cfg.language.clone(),
                timing: Timing::default(),
            });
        }
        // whisper.cpp refuses input under 1 s; pad short clips with silence.
        let min_len = SAMPLE_RATE as usize * 11 / 10;
        if speech.len() < min_len {
            speech.resize(min_len, 0.0);
        }
        let (segments, language, timing) = self.run_stt(&speech)?;
        let mut text = segments.iter().map(|s| s.text.as_str()).collect::<Vec<_>>().join(" ").trim().to_string();
        if is_hallucination(&text, 0.0) {
            text.clear();
        }
        Ok(Transcript { text, segments, ms: started.elapsed().as_millis() as u64, audio_ms, speech_ms, language, timing })
    }

    /// One throwaway whisper pass over 2 s of near-silence (bypassing VAD, so whisper really runs): creates the
    /// decoder state and faults the model weights in, keeping that cost out of the first dictation.
    pub fn warmup(&self) -> Result<Timing, CoreError> {
        let n = SAMPLE_RATE as usize * 2;
        let pcm: Vec<f32> = (0..n).map(|i| ((i as f32) * 0.05).sin() * 1e-4).collect();
        let (_segs, _lang, timing) = self.run_stt(&pcm)?;
        Ok(timing)
    }

    /// `transcribe_pcm` + `cleanup(cfg.cleanup)`: the text the shell inserts.
    pub fn dictate(&self, samples: &[f32], sample_rate: u32) -> Result<Dictation, CoreError> {
        let raw = self.transcribe_pcm(samples, sample_rate)?;
        let t = Instant::now();
        let text = cleanup(&raw.text, &self.cfg.cleanup);
        Ok(Dictation { raw, text, cleanup_ms: t.elapsed().as_millis() as u64 })
    }

    /// One STT pass with the configured speed knobs; when a cut-down encoder window produced a repetition loop
    /// (or far more words than the audio can hold), re-run once on the full window with temperature fallback.
    #[cfg(feature = "whisper")]
    fn run_stt(&self, audio: &[f32]) -> Result<(Vec<Segment>, Option<String>, Timing), CoreError> {
        let (segs, lang, timing) = self.run_stt_pass(audio, false)?;
        if timing.audio_ctx >= 1500 {
            return Ok((segs, lang, timing));
        }
        let text = segs.iter().map(|s| s.text.as_str()).collect::<Vec<_>>().join(" ");
        let audio_s = audio.len() as f32 / SAMPLE_RATE as f32;
        if !looks_looping(&text, audio_s) {
            return Ok((segs, lang, timing));
        }
        let (segs2, lang2, mut t2) = self.run_stt_pass(audio, true)?;
        t2.state_ms += timing.state_ms;
        t2.mel_ms += timing.mel_ms;
        t2.encode_ms += timing.encode_ms;
        t2.decode_ms += timing.decode_ms;
        t2.encoder_passes += timing.encoder_passes;
        t2.decode_steps += timing.decode_steps;
        t2.retried = 1;
        Ok((segs2, lang2, t2))
    }

    #[cfg(feature = "whisper")]
    fn run_stt_pass(&self, audio: &[f32], full_window: bool) -> Result<(Vec<Segment>, Option<String>, Timing), CoreError> {
        use whisper_rs::{FullParams, SamplingStrategy};
        let mut timing = Timing::default();
        let mut guard = self.state.lock().unwrap_or_else(|p| p.into_inner());
        if guard.is_none() {
            let t = Instant::now();
            *guard = Some(self.ctx.create_state().map_err(|e| CoreError::Transcribe(e.to_string()))?);
            timing.state_ms = t.elapsed().as_millis() as u64;
        }
        let state = guard.as_mut().expect("state");
        let sp = &self.cfg.speed;
        let lang: &str = self.cfg.language.as_deref().unwrap_or("auto");
        let mut params = FullParams::new(SamplingStrategy::Greedy { best_of: 1 });
        let threads = self.cfg.thread_count();
        params.set_n_threads(threads);
        params.set_language(Some(lang));
        params.set_print_special(false);
        params.set_print_progress(false);
        params.set_print_realtime(false);
        params.set_print_timestamps(false);
        params.set_no_context(true);
        params.set_suppress_blank(true);
        let long = audio.len() > SAMPLE_RATE as usize * 29;
        let audio_ctx = if full_window { 0 } else { sp.audio_ctx_for(audio.len()) };
        if audio_ctx > 0 {
            params.set_audio_ctx(audio_ctx);
        }
        if !sp.temperature_fallback && !full_window {
            params.set_temperature_inc(0.0);
        }
        // Timestamps drive whisper's seek across 30 s windows, so long audio keeps them.
        if sp.no_timestamps && !long {
            params.set_no_timestamps(true);
        }
        if sp.single_segment && !long {
            params.set_single_segment(true);
        }
        let prompt = self
            .cfg
            .initial_prompt
            .clone()
            .filter(|p| !p.is_empty())
            .or_else(|| crate::cleanup::dictionary_prompt(&self.cfg.cleanup.dictionary));
        if let Some(prompt) = prompt.as_deref() {
            params.set_initial_prompt(prompt);
        }
        let mut probe = Probe::default();
        // SAFETY: `probe` outlives `state.full` (the only caller of the callbacks); the callbacks only touch it.
        unsafe {
            params.set_start_encoder_callback(Some(probe_encoder_begin));
            params.set_start_encoder_callback_user_data(&mut probe as *mut Probe as *mut std::ffi::c_void);
            params.set_filter_logits_callback(Some(probe_logits));
            params.set_filter_logits_callback_user_data(&mut probe as *mut Probe as *mut std::ffi::c_void);
        }
        let t_full = Instant::now();
        if let Err(e) = state.full(params, audio) {
            *guard = None;
            return Err(CoreError::Transcribe(e.to_string()));
        }
        let total = t_full.elapsed();
        let first_enc = probe.first_enc.map(|t| t.duration_since(t_full)).unwrap_or(total);
        timing.mel_ms = first_enc.as_millis() as u64;
        timing.encode_ms = probe.enc.as_millis() as u64;
        timing.decode_ms = total.saturating_sub(first_enc).saturating_sub(probe.enc).as_millis() as u64;
        timing.audio_ctx = if audio_ctx > 0 { audio_ctx as u32 } else { 1500 };
        timing.threads = threads as u32;
        timing.encoder_passes = probe.passes;
        timing.decode_steps = probe.steps;
        let mut out = Vec::new();
        for seg in state.as_iter() {
            let text = seg.to_str_lossy().map_err(|e| CoreError::Transcribe(e.to_string()))?;
            let text = text.trim();
            if text.is_empty() || is_non_speech_tag(text) || is_hallucination(text, seg.no_speech_probability()) {
                continue;
            }
            out.push(Segment {
                start_ms: seg.start_timestamp().max(0) as u64 * 10,
                end_ms: seg.end_timestamp().max(0) as u64 * 10,
                text: text.to_string(),
            });
        }
        let language = match self.cfg.language.as_deref() {
            Some(l) if l != "auto" => Some(l.to_string()),
            _ => whisper_rs::get_lang_str(state.full_lang_id_from_state()).map(str::to_string),
        };
        Ok((out, language, timing))
    }

    #[cfg(not(feature = "whisper"))]
    fn run_stt(&self, _audio: &[f32]) -> Result<(Vec<Segment>, Option<String>, Timing), CoreError> {
        Err(CoreError::SttUnavailable)
    }
}

/// Timestamps collected by whisper.cpp callbacks during one `full` call.
#[cfg(feature = "whisper")]
#[derive(Default)]
struct Probe {
    first_enc: Option<Instant>,
    enc_begin: Option<Instant>,
    enc: std::time::Duration,
    passes: u32,
    steps: u32,
}

#[cfg(feature = "whisper")]
unsafe extern "C" fn probe_encoder_begin(
    _ctx: *mut whisper_rs::WhisperSysContext,
    _state: *mut whisper_rs::WhisperSysState,
    user_data: *mut std::ffi::c_void,
) -> bool {
    let p = &mut *(user_data as *mut Probe);
    let now = Instant::now();
    if p.first_enc.is_none() {
        p.first_enc = Some(now);
    }
    p.enc_begin = Some(now);
    p.passes += 1;
    true
}

#[cfg(feature = "whisper")]
unsafe extern "C" fn probe_logits(
    _ctx: *mut whisper_rs::WhisperSysContext,
    _state: *mut whisper_rs::WhisperSysState,
    _tokens: *const whisper_rs::WhisperTokenData,
    _n_tokens: std::os::raw::c_int,
    _logits: *mut f32,
    user_data: *mut std::ffi::c_void,
) {
    let p = &mut *(user_data as *mut Probe);
    p.steps += 1;
    if let Some(b) = p.enc_begin.take() {
        p.enc += b.elapsed();
    }
}

/// Known Whisper hallucinations on silence/noise (lowercase, punctuation stripped). Always dropped when a whole
/// segment is exactly one of these.
const HALLUCINATIONS: &[&str] = &[
    "thank you for watching",
    "thanks for watching",
    "thank you for watching and see you next time",
    "thank you so much for watching",
    "please subscribe",
    "please like and subscribe",
    "like and subscribe",
    "subscribe to my channel",
    "see you in the next video",
    "subtitles by the amaraorg community",
    "transcription by castingwords",
];
/// Short phrases that are real speech often, so dropped only when whisper itself says "probably no speech".
const WEAK_HALLUCINATIONS: &[&str] = &["thank you", "thanks", "you", "bye", "okay", "so"];

fn normalize_phrase(text: &str) -> String {
    text.chars()
        .filter(|c| c.is_alphanumeric() || c.is_whitespace())
        .collect::<String>()
        .split_whitespace()
        .collect::<Vec<_>>()
        .join(" ")
        .to_lowercase()
}

/// True for a segment that is a known silence hallucination. `no_speech_prob` is whisper's own estimate (0..1).
pub(crate) fn is_hallucination(text: &str, no_speech_prob: f32) -> bool {
    let n = normalize_phrase(text);
    if n.is_empty() {
        return false;
    }
    if HALLUCINATIONS.contains(&n.as_str()) || n.contains("amaraorg") || n.starts_with("subtitles by") {
        return true;
    }
    no_speech_prob > 0.6 && WEAK_HALLUCINATIONS.contains(&n.as_str())
}

/// whisper emits tags like "[BLANK_AUDIO]" or "(music)" for non-speech.
fn is_non_speech_tag(text: &str) -> bool {
    let t = text.trim();
    (t.starts_with('[') && t.ends_with(']')) || (t.starts_with('(') && t.ends_with(')'))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn tags_filtered() {
        assert!(is_non_speech_tag("[BLANK_AUDIO]"));
        assert!(is_non_speech_tag(" (music) "));
        assert!(!is_non_speech_tag("hello (world)"));
    }

    #[test]
    fn hallucinations_dropped() {
        assert!(is_hallucination("Thank you for watching!", 0.0));
        assert!(is_hallucination(" Thanks for watching. ", 0.1));
        assert!(is_hallucination("Subtitles by the Amara.org community", 0.0));
        assert!(is_hallucination("Please like and subscribe.", 0.0));
        // Real speech that merely contains the words stays.
        assert!(!is_hallucination("Thank you for watching the kids tonight.", 0.0));
        // Weak phrases only go when whisper thinks there was no speech.
        assert!(!is_hallucination("Thank you.", 0.1));
        assert!(is_hallucination("Thank you.", 0.9));
        assert!(!is_hallucination("", 0.9));
    }

    #[test]
    fn missing_model_is_a_clear_error() {
        let err = Engine::new(EngineConfig::new("/nonexistent/ggml-none.bin")).err().expect("must fail");
        #[cfg(feature = "whisper")]
        assert!(matches!(err, CoreError::ModelNotFound(_)));
        #[cfg(not(feature = "whisper"))]
        assert!(matches!(err, CoreError::SttUnavailable));
    }

    #[test]
    fn engine_is_send_sync() {
        fn check<T: Send + Sync>() {}
        check::<Engine>();
    }
}

/// A greedy decode on a short encoder window sometimes repeats one phrase until the token budget runs out.
/// True when the text holds far more words than `audio_s` of speech can carry (~4.5 words/s + slack), or when a
/// phrase of 2-8 words repeats back to back 3+ times.
pub(crate) fn looks_looping(text: &str, audio_s: f32) -> bool {
    let words: Vec<String> = text
        .split_whitespace()
        .map(|w| w.trim_matches(|c: char| !c.is_alphanumeric()).to_lowercase())
        .filter(|w| !w.is_empty())
        .collect();
    let n = words.len();
    if n as f32 > audio_s * 4.5 + 6.0 {
        return true;
    }
    for len in 2..=8usize {
        if n < len * 3 {
            break;
        }
        let mut i = 0;
        while i + len * 3 <= n {
            let a = &words[i..i + len];
            if a == &words[i + len..i + 2 * len] && a == &words[i + 2 * len..i + 3 * len] {
                return true;
            }
            i += 1;
        }
    }
    false
}

#[cfg(test)]
mod loop_tests {
    use super::looks_looping;

    #[test]
    fn flags_repeats_and_overlong_text() {
        assert!(looks_looping("What time is it? What time is it? What time is it? What time is it?", 3.0));
        assert!(looks_looping("See You See You See You See You See.", 2.0));
        assert!(!looks_looping("I think that the plan is good.", 2.5));
        assert!(!looks_looping("Please send the quarterly report to the whole team by Friday afternoon.", 4.0));
        assert!(!looks_looping("no no that is fine", 2.0));
    }
}
