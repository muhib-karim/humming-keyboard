//! User settings, stored as JSON in the app config dir.

use humming_core::DictionaryEntry;
use serde::{Deserialize, Serialize};

/// One personal-dictionary entry. `apps` (1.5) limits it to the apps named there, matched case-insensitively
/// against the focused app's name ("Slack", "Code", "Outlook"); empty = every app.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, Default)]
#[serde(default)]
pub struct VocabEntry {
    pub spoken: String,
    pub written: String,
    pub apps: Vec<String>,
}

impl VocabEntry {
    /// Whether this entry applies while `app` (the focused app's name, if known) has focus.
    pub fn applies_to(&self, app: Option<&str>) -> bool {
        let wanted: Vec<String> = self.apps.iter().map(|a| a.trim().to_lowercase()).filter(|a| !a.is_empty()).collect();
        if wanted.is_empty() {
            return true;
        }
        let Some(app) = app.map(|a| a.to_lowercase()) else { return false };
        wanted.iter().any(|w| app.contains(w.as_str()) || w.contains(app.as_str()))
    }

    pub fn to_core(&self) -> DictionaryEntry {
        DictionaryEntry { spoken: self.spoken.clone(), written: self.written.clone() }
    }
}
use std::path::Path;

/// Current settings file format (see `Settings::schema`).
pub const SCHEMA: u32 = 2;

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(default)]
pub struct Settings {
    /// Global shortcut, in the accelerator syntax ("CommandOrControl+Shift+Space", "Alt+F9", ...).
    pub hotkey: String,
    /// "hold": record while the hotkey is held (push-to-talk). "toggle": press once to start, again to stop.
    pub mode: String,
    /// Model id from `models::CATALOG` ("lite", "fast" or "best", as on Android).
    pub model: String,
    /// Clean mode: fillers, self-corrections, spoken lists, punctuation. Off = verbatim.
    pub clean_mode: bool,
    pub remove_fillers: bool,
    pub self_corrections: bool,
    pub voice_commands: bool,
    /// Edit by voice (1.8): "change X to Y", "delete X", "capitalize X", "undo that" edit Humming's own last
    /// insertion in the same app instead of being typed.
    pub voice_edit: bool,
    pub format_lists: bool,
    /// Accuracy mode (1.4): strict clean-up on top of Clean mode (hedge fillers set off by commas, a final "period").
    pub accuracy_mode: bool,
    /// Personal dictionary. `written` alone fixes spelling/casing; `spoken` -> `written` replaces a phrase;
    /// `apps` makes an entry per-app (1.5).
    pub dictionary: Vec<VocabEntry>,
    /// First run (1.5): fetch the 30 MB Lite model first so dictation works within seconds, then the picked model.
    pub quick_start: bool,
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
    /// Self-update (1.6, default on): download and install a new version on its own and relaunch (never mid-dictation).
    /// Off = Humming still checks and shows "Update available", and installs only when asked.
    pub auto_update: bool,
    /// Settings file format. Files written before 1.6.3 have none (0); the one-time migrations in `sanitized` run only
    /// for those, so a value the user picks later (for example a 300 s cap) is kept.
    #[serde(default)]
    pub schema: u32,
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
            voice_edit: true,
            format_lists: true,
            accuracy_mode: false,
            dictionary: Vec::new(),
            quick_start: true,
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
            auto_update: true,
            schema: SCHEMA,
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
        // 300 was the 0.1.0 default cap; 1.3.0 has no limit unless the user sets one. Migrate it once, only in a file
        // written before the schema field existed, so a 300 s cap chosen later survives load and save.
        // Deliberate: a schemaless 300 cannot be told apart from the old default (and 1.3.0-1.6.0 never kept a chosen
        // 300), so it is not preserved; desktop/README.md "Old 5-minute caps" tells users to set it again.
        if self.schema < SCHEMA {
            if self.max_seconds == 300 {
                self.max_seconds = 0;
            }
            self.schema = SCHEMA;
        }
        if self.max_seconds != 0 {
            self.max_seconds = self.max_seconds.clamp(10, 86_400);
        }
        self.dictionary.retain(|e| !e.written.trim().is_empty());
        for e in self.dictionary.iter_mut() {
            e.apps.retain(|a| !a.trim().is_empty());
        }
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

    #[test]
    fn auto_update_is_on_by_default_and_for_old_settings_files() {
        assert!(Settings::default().auto_update);
        let s: Settings = serde_json::from_str(r#"{"mode":"toggle"}"#).unwrap();
        assert!(s.auto_update);
        let s: Settings = serde_json::from_str(r#"{"auto_update":false}"#).unwrap();
        assert!(!s.auto_update);
    }

    #[test]
    fn no_recording_cap_by_default() {
        assert_eq!(Settings::default().max_seconds, 0);
        let s: Settings = serde_json::from_str(r#"{"mode":"toggle"}"#).unwrap();
        assert_eq!(s.sanitized().max_seconds, 0);
    }

    #[test]
    fn old_300_second_default_is_migrated_to_no_cap() {
        // A file from before the schema field: the old 300 s default becomes "no cap", once.
        let s: Settings = serde_json::from_str(r#"{"max_seconds":300}"#).unwrap();
        assert_eq!(s.schema, 0);
        let s = s.sanitized();
        assert_eq!(s.max_seconds, 0);
        assert_eq!(s.schema, SCHEMA);
        let dir = std::env::temp_dir().join(format!("humming-settings-test-old-{}", std::process::id()));
        let path = dir.join("settings.json");
        std::fs::create_dir_all(&dir).unwrap();
        std::fs::write(&path, r#"{"max_seconds":300}"#).unwrap();
        assert_eq!(Settings::load(&path).max_seconds, 0);
        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn a_chosen_300_second_cap_survives_save_and_load() {
        let s = Settings { max_seconds: 300, ..Settings::default() }.sanitized();
        assert_eq!(s.max_seconds, 300);
        let dir = std::env::temp_dir().join(format!("humming-settings-test-new-{}", std::process::id()));
        let path = dir.join("settings.json");
        s.save(&path).unwrap();
        let loaded = Settings::load(&path);
        assert_eq!(loaded.max_seconds, 300);
        assert_eq!(loaded.schema, SCHEMA);
        loaded.save(&path).unwrap();
        assert_eq!(Settings::load(&path).max_seconds, 300);
        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn a_set_cap_is_clamped_to_10_seconds_through_24_hours() {
        let cap = |n: u32| Settings { max_seconds: n, ..Settings::default() }.sanitized().max_seconds;
        assert_eq!(cap(1), 10);
        assert_eq!(cap(10), 10);
        assert_eq!(cap(60), 60);
        assert_eq!(cap(300), 300);
        assert_eq!(cap(86_400), 86_400);
        assert_eq!(cap(100_000), 86_400);
    }

    #[test]
    fn old_dictionary_entries_load_as_every_app() {
        let s: Settings = serde_json::from_str(r#"{"dictionary":[{"spoken":"hum ing","written":"Humming"}]}"#).unwrap();
        assert!(s.dictionary[0].apps.is_empty());
        assert!(s.dictionary[0].applies_to(None));
        assert!(s.dictionary[0].applies_to(Some("Slack")));
    }

    #[test]
    fn per_app_entries_match_the_focused_app() {
        let e = VocabEntry { spoken: "k eight s".into(), written: "k8s".into(), apps: vec!["Code".into(), " slack ".into()] };
        assert!(e.applies_to(Some("Code")));
        assert!(e.applies_to(Some("Slack")));
        assert!(e.applies_to(Some("slack.exe")));
        assert!(!e.applies_to(Some("Outlook")));
        assert!(!e.applies_to(None));
    }
}
