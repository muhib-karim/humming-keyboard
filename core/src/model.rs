//! Where the whisper model lives. Models are never committed; fetch with scripts/fetch-model.sh.

use std::path::PathBuf;

pub const DEFAULT_MODEL_FILE: &str = "ggml-base.en.bin";
pub const BASE_EN_URL: &str = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en.bin";
pub const TINY_EN_URL: &str = "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-tiny.en.bin";

/// `$HUMMING_MODEL` if set, else `<data dir>/humming/models/ggml-base.en.bin`.
/// Data dir: `$XDG_DATA_HOME`, else `~/Library/Application Support` (macOS),
/// `%APPDATA%` (Windows), `~/.local/share` (other).
pub fn default_model_path() -> PathBuf {
    if let Some(p) = std::env::var_os("HUMMING_MODEL").filter(|v| !v.is_empty()) {
        return PathBuf::from(p);
    }
    data_dir().join("humming").join("models").join(DEFAULT_MODEL_FILE)
}

fn data_dir() -> PathBuf {
    if let Some(x) = std::env::var_os("XDG_DATA_HOME").filter(|v| !v.is_empty()) {
        return PathBuf::from(x);
    }
    if cfg!(windows) {
        if let Some(a) = std::env::var_os("APPDATA") {
            return PathBuf::from(a);
        }
    }
    let home = std::env::var_os("HOME").map(PathBuf::from).unwrap_or_else(|| PathBuf::from("."));
    if cfg!(target_os = "macos") {
        home.join("Library").join("Application Support")
    } else {
        home.join(".local").join("share")
    }
}
