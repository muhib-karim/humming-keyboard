//! Integration test: transcribe a real spoken WAV and check key words.
//!
//! Needs a model and a WAV:
//!   HUMMING_MODEL=/path/ggml-base.en.bin HUMMING_TEST_WAV=/path/hello.wav cargo test --test transcribe_wav
//! CI generates the WAV with a TTS engine (espeak-ng on Linux, `say` on macOS) speaking TEST_PHRASE,
//! and sets HUMMING_REQUIRE_MODEL=1 so a missing model fails instead of skipping.

#![cfg(feature = "whisper")]

use std::path::PathBuf;
use humming_core::{audio, Engine, EngineConfig};

/// Must match the phrase CI speaks (scripts/make-test-wav.sh).
const KEYWORDS: &[&str] = &["hello", "dictation", "milk", "bread", "tomorrow"];

fn env_path(name: &str) -> Option<PathBuf> {
    std::env::var_os(name).filter(|v| !v.is_empty()).map(PathBuf::from)
}

#[test]
fn transcribes_tts_phrase() {
    let required = std::env::var("HUMMING_REQUIRE_MODEL").map(|v| v == "1").unwrap_or(false);
    let (Some(model), Some(wav)) = (env_path("HUMMING_MODEL"), env_path("HUMMING_TEST_WAV")) else {
        assert!(!required, "HUMMING_MODEL and HUMMING_TEST_WAV must be set in CI");
        eprintln!("skipping: set HUMMING_MODEL and HUMMING_TEST_WAV to run");
        return;
    };

    let engine = Engine::new(EngineConfig::new(&model)).expect("model loads");
    let (samples, rate) = audio::read_wav(&wav).expect("wav reads");
    let d = engine.dictate(&samples, rate).expect("dictation works");

    eprintln!("raw:     {:?}", d.raw.text);
    eprintln!("cleaned: {:?}", d.text);
    eprintln!(
        "timing:  audio={}ms speech={}ms stt={}ms cleanup={}ms",
        d.raw.audio_ms, d.raw.speech_ms, d.raw.ms, d.cleanup_ms
    );

    let lower = d.text.to_lowercase();
    let hits: Vec<&str> = KEYWORDS.iter().copied().filter(|k| lower.contains(k)).collect();
    assert!(
        hits.len() >= 4,
        "expected at least 4 of {KEYWORDS:?} in {:?}, got {hits:?}",
        d.text
    );
    assert!(d.raw.speech_ms > 1000, "VAD kept too little audio: {} ms", d.raw.speech_ms);
    assert!(d.text.chars().next().map(|c| c.is_uppercase()).unwrap_or(false), "cleanup capitalizes");
}

/// Parity: language auto-detect with a multilingual model (HUMMING_MODEL_MULTI, e.g. ggml-base.bin).
#[test]
fn auto_detects_english_with_multilingual_model() {
    let required = std::env::var("HUMMING_REQUIRE_MODEL").map(|v| v == "1").unwrap_or(false);
    let (Some(model), Some(wav)) = (env_path("HUMMING_MODEL_MULTI"), env_path("HUMMING_TEST_WAV")) else {
        assert!(!required, "HUMMING_MODEL_MULTI and HUMMING_TEST_WAV must be set in CI");
        eprintln!("skipping: set HUMMING_MODEL_MULTI and HUMMING_TEST_WAV to run");
        return;
    };
    let mut cfg = EngineConfig::new(&model);
    cfg.language = None; // auto
    let engine = Engine::new(cfg).expect("multilingual model loads");
    let (samples, rate) = audio::read_wav(&wav).expect("wav reads");
    let d = engine.dictate(&samples, rate).expect("dictation works");
    eprintln!("auto: lang={:?} text={:?} stt={}ms", d.raw.language, d.text, d.raw.ms);
    assert_eq!(d.raw.language.as_deref(), Some("en"));
    let lower = d.text.to_lowercase();
    let hits = KEYWORDS.iter().filter(|k| lower.contains(*k)).count();
    assert!(hits >= 3, "multilingual model should still get most keywords: {:?}", d.text);
}

/// Parity P0-15: auto-detect keeps a Spanish sentence in Spanish (no translation to English).
#[test]
fn auto_detects_spanish_and_keeps_it_spanish() {
    let (Some(model), Some(wav)) = (env_path("HUMMING_MODEL_MULTI"), env_path("HUMMING_TEST_WAV_ES")) else {
        eprintln!("skipping: set HUMMING_MODEL_MULTI and HUMMING_TEST_WAV_ES to run");
        return;
    };
    let mut cfg = EngineConfig::new(&model);
    cfg.language = None; // auto
    let engine = Engine::new(cfg).expect("multilingual model loads");
    let (samples, rate) = audio::read_wav(&wav).expect("wav reads");
    let d = engine.dictate(&samples, rate).expect("dictation works");
    eprintln!("auto-es: lang={:?} text={:?} stt={}ms", d.raw.language, d.text, d.raw.ms);
    assert_eq!(d.raw.language.as_deref(), Some("es"));
    let lower = d.text.to_lowercase();
    let es_hits = ["hola", "prueba", "dictado", "leche", "pan"].iter().filter(|k| lower.contains(*k)).count();
    assert!(es_hits >= 2, "expected Spanish words, got {:?}", d.text);
    assert!(!lower.contains("milk") && !lower.contains("bread"), "must not translate: {:?}", d.text);
}

/// Parity: dictionary bias + replacement end to end.
#[test]
fn dictionary_applies_to_transcript() {
    let (Some(model), Some(wav)) = (env_path("HUMMING_MODEL"), env_path("HUMMING_TEST_WAV")) else {
        eprintln!("skipping: set HUMMING_MODEL and HUMMING_TEST_WAV to run");
        return;
    };
    let mut cfg = EngineConfig::new(&model);
    cfg.cleanup.dictionary = vec![dict_entry("milk", "Oat Milk")];
    let engine = Engine::new(cfg).expect("model loads");
    let (samples, rate) = audio::read_wav(&wav).expect("wav reads");
    let d = engine.dictate(&samples, rate).expect("dictation works");
    eprintln!("dictionary: {:?}", d.text);
    assert!(d.text.contains("Oat Milk"), "dictionary replacement missing: {:?}", d.text);
}

fn dict_entry(spoken: &str, written: &str) -> humming_core::DictionaryEntry {
    humming_core::DictionaryEntry { spoken: spoken.into(), written: written.into() }
}

#[test]
fn silence_returns_empty_text() {
    let Some(model) = env_path("HUMMING_MODEL") else {
        eprintln!("skipping: set HUMMING_MODEL to run");
        return;
    };
    let engine = Engine::new(EngineConfig::new(&model)).expect("model loads");
    let t = engine.transcribe_pcm(&vec![0.0f32; 16_000 * 2], 16_000).expect("silence is ok");
    assert_eq!(t.text, "");
    assert_eq!(t.speech_ms, 0);
}
