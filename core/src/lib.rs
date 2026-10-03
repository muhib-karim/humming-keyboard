//! humming-core: the shared Humming dictation pipeline.
//!
//! audio (16 kHz mono f32) → energy VAD trim → whisper.cpp STT → rule-based cleanup.
//! See `INTERFACE.md` for the contract the desktop shell builds against.

#[cfg(feature = "ffi")]
uniffi::setup_scaffolding!();

pub mod audio;
pub mod cleanup;
mod engine;
mod error;
#[cfg(feature = "ffi")]
pub mod ffi;
pub mod model;
pub mod vad;

pub use cleanup::{cleanup, CleanupOpts, DictionaryEntry};
pub use engine::{Dictation, Engine, EngineConfig, Segment, SpeedOpts, Timing, Transcript};
pub use error::CoreError;
pub use vad::VadConfig;

/// The sample rate the engine works at. Other rates are resampled on the way in.
pub const SAMPLE_RATE: u32 = 16_000;
