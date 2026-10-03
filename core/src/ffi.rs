//! UniFFI bindings (feature `ffi`): Kotlin for the Android app/IME, Swift for the iOS companion app.
//! Same names and fields as INTERFACE.md. `Transcript`, `Segment`, `Dictation`, `CleanupOpts` and `VadConfig`
//! are exported directly; `EngineConfig` and the error type are mirrored here because they carry `PathBuf`.

use std::sync::Arc;

use crate::{CleanupOpts, Dictation, SpeedOpts, Timing, Transcript, VadConfig};

/// FFI mirror of `crate::EngineConfig` (`model_path` is a plain string path).
#[derive(Debug, Clone, uniffi::Record)]
pub struct EngineConfig {
    pub model_path: String,
    /// `Some("en")` for English; `None` = auto-detect.
    pub language: Option<String>,
    pub vad: VadConfig,
    /// `None` = min(4, available cores).
    pub threads: Option<u32>,
    pub cleanup: CleanupOpts,
    pub initial_prompt: Option<String>,
    pub speed: SpeedOpts,
}

impl From<EngineConfig> for crate::EngineConfig {
    fn from(c: EngineConfig) -> Self {
        let mut cfg = crate::EngineConfig::new(c.model_path);
        cfg.language = c.language;
        cfg.vad = c.vad;
        cfg.threads = c.threads;
        cfg.cleanup = c.cleanup;
        cfg.initial_prompt = c.initial_prompt;
        cfg.speed = c.speed;
        cfg
    }
}

/// FFI error. Kotlin sees `CoreException`, Swift `CoreError`. Fields are `detail`, not `message`
/// (a `message` field clashes with Kotlin's Throwable.message).
#[derive(Debug, uniffi::Error)]
pub enum CoreError {
    ModelNotFound { path: String },
    ModelLoad { detail: String },
    SttUnavailable,
    Audio { detail: String },
    Transcribe { detail: String },
}

impl std::fmt::Display for CoreError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            CoreError::ModelNotFound { path } => write!(f, "whisper model not found at {path}"),
            CoreError::ModelLoad { detail } => write!(f, "could not load the whisper model: {detail}"),
            CoreError::SttUnavailable => write!(f, "speech-to-text is not compiled in"),
            CoreError::Audio { detail } => write!(f, "audio error: {detail}"),
            CoreError::Transcribe { detail } => write!(f, "transcription failed: {detail}"),
        }
    }
}

impl std::error::Error for CoreError {}

impl From<crate::CoreError> for CoreError {
    fn from(e: crate::CoreError) -> Self {
        match e {
            crate::CoreError::ModelNotFound(p) => CoreError::ModelNotFound { path: p.display().to_string() },
            crate::CoreError::ModelLoad(m) => CoreError::ModelLoad { detail: m },
            crate::CoreError::SttUnavailable => CoreError::SttUnavailable,
            crate::CoreError::Audio(m) => CoreError::Audio { detail: m },
            crate::CoreError::Transcribe(m) => CoreError::Transcribe { detail: m },
            #[allow(unreachable_patterns)]
            other => CoreError::Transcribe { detail: other.to_string() },
        }
    }
}

/// The engine as a UniFFI object. Load once (slow), reuse; call from a background thread.
#[derive(uniffi::Object)]
pub struct Engine {
    inner: crate::Engine,
}

#[uniffi::export]
impl Engine {
    #[uniffi::constructor]
    pub fn new(config: EngineConfig) -> Result<Arc<Self>, CoreError> {
        Ok(Arc::new(Self { inner: crate::Engine::new(config.into())? }))
    }

    /// Raw STT (VAD trim + whisper), no cleanup. Samples: mono f32 in [-1, 1]; 16 kHz preferred.
    pub fn transcribe_pcm(&self, samples: Vec<f32>, sample_rate: u32) -> Result<Transcript, CoreError> {
        Ok(self.inner.transcribe_pcm(&samples, sample_rate)?)
    }

    /// Transcribe + cleanup: the text to insert.
    pub fn dictate(&self, samples: Vec<f32>, sample_rate: u32) -> Result<Dictation, CoreError> {
        Ok(self.inner.dictate(&samples, sample_rate)?)
    }

    /// One throwaway whisper pass (VAD bypassed) so the first dictation doesn't pay for state setup.
    pub fn warmup(&self) -> Result<Timing, CoreError> {
        Ok(self.inner.warmup()?)
    }
}

/// The measured fast decode path (M3 default).
#[uniffi::export]
pub fn default_speed_opts() -> SpeedOpts {
    SpeedOpts::default()
}

/// The M2 decode path (full 30 s window, temperature fallback, timestamps), for before/after comparisons.
#[uniffi::export]
pub fn m2_speed_opts() -> SpeedOpts {
    SpeedOpts::m2()
}

/// Rule-based cleanup, no model needed.
#[uniffi::export]
pub fn cleanup(text: String, opts: CleanupOpts) -> String {
    crate::cleanup(&text, &opts)
}

#[uniffi::export]
pub fn default_cleanup_opts() -> CleanupOpts {
    CleanupOpts::default()
}

#[uniffi::export]
pub fn default_vad_config() -> VadConfig {
    VadConfig::default()
}

/// `EngineConfig` with the INTERFACE.md defaults for a given model path.
#[uniffi::export]
pub fn default_engine_config(model_path: String) -> EngineConfig {
    let c = crate::EngineConfig::new(model_path.clone());
    EngineConfig {
        model_path,
        language: c.language,
        vad: c.vad,
        threads: c.threads,
        cleanup: c.cleanup,
        initial_prompt: c.initial_prompt,
        speed: c.speed,
    }
}

/// Crate version, for "which core is linked" checks in the apps.
#[uniffi::export]
pub fn core_version() -> String {
    env!("CARGO_PKG_VERSION").to_string()
}

/// True when whisper.cpp is compiled in (false for `--no-default-features` builds).
#[uniffi::export]
pub fn stt_available() -> bool {
    cfg!(feature = "whisper")
}
