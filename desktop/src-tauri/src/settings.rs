//! User settings, stored as JSON in the app config dir.

use humming_core::DictionaryEntry;
use serde::{Deserialize, Serialize};
use std::path::Path;

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(default)]
pub struct Settings {
    /// Global shortcut, in the accelerator syntax ("CommandOrControl+Shift+Space", "Alt+F9", ...).
    pub hotkey: String,
    /// "hold": record while the hotkey is held (push-to-talk). "toggle": press once to start, again to stop.
    pub mode: String,
    /// Model id from `models::CATALOG` ("fast" or "best", as on Android).
    pub model: String,
    /// Clean mode: fillers, self-corrections, spoken lists, punctuation. Off = verbatim.
    pub clean_mode: bool,
    pub remove_fillers: bool,
    pub self_corrections: bool,
    pub voice_commands: bool,
    pub format_lists: bool,
    /// Accuracy mode (1.4): strict clean-up on top of Clean mode (hedge fillers set off by commas, a final "period").
    pub accuracy_mode: bool,
    /// Personal dictionary. `written` alone fixes spelling/casing; `spoken` -> `written` replaces a phrase.
    pub dictionary: Vec<DictionaryEntry>,
    /// "paste" (clipboard + Ctrl/Cmd+V, fastest and safest for long text) or "type" (synthetic key presses).
    pub insert_method: String,
    pub restore_clipboard: bool,
    pub paste_delay_ms: u64,
    pub trailing_space: bool,
    /// Input device name; empty = system default.
    pub input_device: String,
    pub show_overlay: bool,
    pub launch_at_login: bool,
    pub history_enabled: bool,
    pub history_limit: usize,
    pub max_seconds: u32,
}

impl Default for Settings {
    fn default() -> Self {
        Self {
            hotkey: "CommandOrControl+Shift+Space".into(),
            mode: "hold".into(),
            model: "fast".into(),
            clean_mode: true,
            remove_fillers: true,
            self_corrections: true,
            voice_commands: true,
            format_lists: true,
            accuracy_mode: false,
            dictionary: Vec::new(),
            insert_method: "paste".into(),
            restore_clipboard: true,
            paste_delay_ms: 150,
            trailing_space: true,
            input_device: String::new(),
            show_overlay: true,
            launch_at_login: false,
            history_enabled: true,
            history_limit: 500,
            // 0 = no length limit: record until the shortcut is released (or pressed again in toggle mode).
            max_seconds: 0,
        }
    }
}

impl Settings {
    pub fn load(path: &Path) -> Self {
        std::fs::read_to_string(path)
            .ok()
            .and_then(|s| serde_json::from_str::<Settings>(&s).ok())
            .map(|s| s.sanitized())
            .unwrap_or_default()
    }

    pub fn save(&self, path: &Path) -> Result<(), String> {
        if let Some(dir) = path.parent() {
            std::fs::create_dir_all(dir).map_err(|e| e.to_string())?;
        }
        let tmp = path.with_extension("json.tmp");
        let body = serde_json::to_string_pretty(self).map_err(|e| e.to_string())?;
        std::fs::write(&tmp, body).map_err(|e| e.to_string())?;
        std::fs::rename(&tmp, path).map_err(|e| e.to_string())
    }

    /// Clamp values a hand-edited file could break.
    pub fn sanitized(mut self) -> Self {
        let d = Settings::default();
        if self.hotkey.trim().is_empty() {
            self.hotkey = d.hotkey;
        }
        if self.mode != "hold" && self.mode != "toggle" {
            self.mode = d.mode;
        }
        if crate::models::find(&self.model).is_none() {
            self.model = d.model;
        }
        if self.insert_method != "paste" && self.insert_method != "type" {
            self.insert_method = d.insert_method;
        }
        self.paste_delay_ms = self.paste_delay_ms.clamp(20, 2000);
        self.history_limit = self.history_limit.clamp(10, 5000);
        // 300 was the 0.1.0 default cap; 1.3.0 has no limit unless the user sets one.
        if self.max_seconds == 300 {
            self.max_seconds = 0;
        }
        if self.max_seconds != 0 {
            self.max_seconds = self.max_seconds.clamp(10, 86_400);
        }
        self.dictionary.retain(|e| !e.written.trim().is_empty());
        self
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn defaults_survive_a_round_trip() {
        let s = Settings::default();
        let j = serde_json::to_string(&s).unwrap();
        assert_eq!(serde_json::from_str::<Settings>(&j).unwrap(), s);
    }

    #[test]
    fn missing_and_bad_fields_fall_back() {
        let s: Settings = serde_json::from_str(r#"{"mode":"sideways","paste_delay_ms":0}"#).unwrap();
        let s = s.sanitized();
        assert_eq!(s.mode, "hold");
        assert_eq!(s.paste_delay_ms, 20);
        assert_eq!(s.hotkey, Settings::default().hotkey);
    }
}
