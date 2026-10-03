# humming-core — interface v0 (M1)

Humming: voice dictation that types clean text anywhere.

Status: v0, 2026-09-27 (UTC). Contract between the Rust core and the Tauri desktop shell.
Anything marked **[v0, may change]** can move
before M1 ships; everything else is what the shell should build against now.

## 1. Crate shape

- Crate `humming-core`, directory `core/`. Library + binary (`humming-core` CLI).
- The Tauri shell depends on it directly as a Rust crate (path or git dependency). No FFI needed on desktop.
- Features:
  - `whisper` (default): real STT through whisper.cpp (`whisper-rs`). Needs a C/C++ toolchain and CMake.
  - `metal` / `coreml`: pass-through acceleration flags for Apple builds **[v0, may change]**. M1 CI builds with Metal off.
  - `--no-default-features`: builds without whisper.cpp. `Engine::new` then returns `CoreError::SttUnavailable`, but
    `cleanup`, `vad` and `audio` all work. Use it to build the shell fast while whisper.cpp compiles elsewhere.
- MSRV: current stable Rust.

## 2. Audio format contract

- **Input to the engine: 16 kHz, mono, `f32` samples in [-1.0, 1.0].** This is what whisper.cpp consumes natively.
- Other sample rates are accepted by `transcribe_pcm` and resampled internally (linear, good enough for speech),
  but the shell should capture at 16 kHz when the device allows it.
- Stereo/multichannel: the caller downmixes to mono before calling (`audio::downmix` helps).
- `i16` capture: convert with `audio::i16_to_f32` (divides by 32768).
- Utterance length: any length works (whisper windows internally at 30 s); the UX cap is 60 s per dictation.
- Empty or all-silence input returns `Ok` with `text == ""` (never an error), so push-to-talk taps are harmless.

## 3. Rust API

```rust
use humming_core::{Engine, EngineConfig, VadConfig, CleanupOpts, Transcript, Dictation, CoreError};

// ---- config ----
pub struct EngineConfig {
    pub model_path: std::path::PathBuf,  // ggml whisper model, e.g. ggml-base.en.bin (never bundled in git)
    pub language: Option<String>,        // Some("en") default; None = auto-detect
    pub vad: VadConfig,                  // speech trimming before STT
    pub threads: Option<u32>,            // None = min(4, available cores)
    pub cleanup: CleanupOpts,            // used by Engine::dictate
    pub initial_prompt: Option<String>,  // dictionary bias for whisper (personal terms) [v0, may change]
}
impl EngineConfig { pub fn new(model_path: impl Into<PathBuf>) -> Self; } // all other fields default

pub struct VadConfig {
    pub enabled: bool,        // default true
    pub threshold_db: f32,    // default -40.0 (frame RMS in dBFS counted as speech above this)
    pub frame_ms: u32,        // default 30
    pub min_speech_ms: u32,   // default 150; shorter bursts are ignored
    pub padding_ms: u32,      // default 200 kept around speech
}
// M1 VAD is energy-based. Silero (ONNX) can replace it behind the same struct later.

// ---- engine ----
pub struct Engine { /* opaque */ }            // Send + Sync; load once, reuse for every dictation
impl Engine {
    pub fn new(cfg: EngineConfig) -> Result<Engine, CoreError>;   // loads the model: slow (100 ms – 2 s). Do it at app start.
    pub fn config(&self) -> &EngineConfig;

    /// Raw STT: VAD trim + whisper. No cleanup.
    pub fn transcribe_pcm(&self, samples: &[f32], sample_rate: u32) -> Result<Transcript, CoreError>;

    /// What the shell inserts: transcribe_pcm + cleanup(cfg.cleanup).
    pub fn dictate(&self, samples: &[f32], sample_rate: u32) -> Result<Dictation, CoreError>;
}

pub struct Transcript {
    pub text: String,            // whisper text, segments joined, trimmed
    pub segments: Vec<Segment>,
    pub ms: u64,                 // wall time spent inside transcribe_pcm (VAD + STT)
    pub audio_ms: u64,           // length of the input audio
    pub speech_ms: u64,          // audio left after VAD trim
    pub language: Option<String>, // configured language, or the detected one when language = None (auto)
}
pub struct Segment { pub start_ms: u64, pub end_ms: u64, pub text: String }

pub struct Dictation {
    pub raw: Transcript,
    pub text: String,            // cleaned text, ready to insert
    pub cleanup_ms: u64,
}

// ---- cleanup (pure, no model) ----
pub fn cleanup(text: &str, opts: &CleanupOpts) -> String;
pub struct CleanupOpts {
    pub remove_fillers: bool,    // default true: um, uh, erm, er, hmm, "you know," at clause edges [list v0]
    pub capitalize: bool,        // default true: sentence starts and standalone "i"
    pub fix_spacing: bool,       // default true: no space before , . ? ! : ; one space after; collapse runs
    pub commands: bool,          // default true: spoken commands below
    pub ensure_final_punctuation: bool, // default true: add "." if the text ends without . ? !
    pub remove_repeats: bool,    // default true: "I I think" -> "I think" (keeps "that that", "had had")
    pub self_corrections: bool,  // default true: "at 3, no, 4pm" -> "at 4pm"; "John, sorry, I mean Jane" -> "Jane"
    pub dictionary: Vec<DictionaryEntry>, // default empty; applied last, and biases whisper (see below)
}
pub struct DictionaryEntry { pub spoken: String, pub written: String }
// spoken is matched case-insensitively as a whole phrase and replaced by written; empty spoken = casing fix only
// ("github" -> "GitHub"). When EngineConfig.initial_prompt is None, the engine passes "Vocabulary: <written, ...>."
// to whisper as its initial prompt.
// Silence hallucinations ("Thank you for watching", "Subtitles by ...") are dropped per segment; short ones
// ("Thank you", "you") only when whisper's own no-speech probability is > 0.6.
// Spoken commands (M1, English): "new line" -> "\n", "new paragraph" -> "\n\n",
// "period"/"full stop" -> ".", "comma" -> ",", "question mark" -> "?", "exclamation mark" -> "!",
// "colon" -> ":", "semicolon" -> ";". Matched as whole words, case-insensitive.
// Speak-to-edit ("make that shorter") is NOT in M1; it lands in M4 with its own API.

// ---- helpers ----
pub mod audio {
    pub fn i16_to_f32(samples: &[i16]) -> Vec<f32>;
    pub fn downmix(interleaved: &[f32], channels: u16) -> Vec<f32>;
    pub fn resample_linear(samples: &[f32], from_hz: u32, to_hz: u32) -> Vec<f32>;
    pub fn read_wav(path: &Path) -> Result<(Vec<f32>, u32), CoreError>; // any PCM/float WAV -> mono f32 + rate
}
pub mod vad {
    pub fn speech_ranges(samples: &[f32], sample_rate: u32, cfg: &VadConfig) -> Vec<(usize, usize)>;
    pub fn trim(samples: &[f32], sample_rate: u32, cfg: &VadConfig) -> Vec<f32>;
}
pub mod model {
    pub const DEFAULT_MODEL_FILE: &str = "ggml-base.en.bin";
    pub fn default_model_path() -> PathBuf; // $HUMMING_MODEL, else <data dir>/humming/models/ggml-base.en.bin
    pub const BASE_EN_URL: &str; // download URL used by scripts/fetch-model.sh and CI
}

// ---- errors ----
#[non_exhaustive]
pub enum CoreError {           // implements std::error::Error + Display
    ModelNotFound(PathBuf),
    ModelLoad(String),
    SttUnavailable,             // built without the `whisper` feature
    Audio(String),              // bad WAV, zero sample rate, etc.
    Transcribe(String),
}
```

### Threading
- `Engine` is `Send + Sync`. Wrap it in `Arc<Engine>` and call from a worker thread, never the UI thread.
- Each `transcribe_pcm` call creates its own whisper state, so calls are safe to overlap, but the shell should run one
  dictation at a time (CPU-bound; overlapping just splits the cores).
- No async in the core. The shell uses `tauri::async_runtime::spawn_blocking` (or a std thread).

### Latency budget (desktop M1 gate: p50 hotkey-release → text ≤ 1.5 s, base model, Apple Silicon)
`Transcript.ms` + `Dictation.cleanup_ms` is the core's share; the shell adds capture flush and insertion. The CLI prints
both so QA can measure the core's share on its own.

## 4. CLI

```
humming-core transcribe <file.wav> [--model PATH] [--lang en|auto] [--no-cleanup] [--no-vad] [--json]
humming-core cleanup "<text>"
```
Plain output: the cleaned text on line 1, then `timing: audio=…ms speech=…ms stt=…ms cleanup=…ms rtf=…`.
`--json` prints the whole `Dictation` as JSON. Exit code 2 = model missing, 1 = other error.

## 5. C ABI / uniffi (not in M1)

- **uniffi** (Swift/Kotlin) comes behind a `ffi` feature for the mobile companion apps: `Engine` becomes a uniffi
  Object (`Arc<Engine>`), `EngineConfig`/`Transcript`/`Segment`/`Dictation`/`CleanupOpts` become Records, `CoreError`
  an Error enum. Same names and fields as above. `cleanup` is exported as a free function.
  Per the mobile plan: the iOS keyboard extension never links the core (memory cap); the iOS companion app does;
  the Android IME may link it later.
- **C ABI** (only if a non-Rust host needs it) **[v0, may change]**:
  `tl_engine_new(const char* model_path, const char* lang) -> tl_engine*`,
  `tl_dictate(tl_engine*, const float* samples, size_t n, uint32_t sample_rate, char** out_json) -> int32_t` (0 = ok),
  `tl_string_free(char*)`, `tl_engine_free(tl_engine*)`. Output is the `Dictation` JSON from the CLI.

## 6. Models

- Never committed. `scripts/fetch-model.sh [base.en|small.en|tiny.en]` downloads to `default_model_path()`'s directory
  from the whisper.cpp model repository on Hugging Face and checks the size.
- M1 default: `ggml-base.en.bin` (~148 MB). `tiny.en` (~78 MB) for CI speed and older CPUs.
- Language auto-detect needs a multilingual model (`base`, `small`, `tiny`, no `.en`) with `language: None`;
  `Transcript.language` then reports what whisper detected. The `.en` models are English-only.

## 7. Not in M1
Speak-to-edit, LLM cleanup (arch doc: cloud for Pro, small local model for Free), translate, auto-learned dictionary,
metering, streaming partial text. Streaming partials is the first addition expected after M1:
`Engine::start_stream() -> Stream { push(&[f32]), partial() -> String, finish() -> Dictation }` **[v0, may change]**.
