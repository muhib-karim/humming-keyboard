//! Dictation history, newest first, stored as JSON in the app data dir. Never leaves the machine.

use serde::{Deserialize, Serialize};
use std::path::{Path, PathBuf};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Entry {
    pub id: u64,
    /// Unix time, milliseconds.
    pub ts: u64,
    /// The inserted (cleaned) text.
    pub text: String,
    /// What the speech model heard, before cleanup.
    pub raw: String,
    pub audio_ms: u64,
    /// Hotkey release to text ready (STT + cleanup), ms.
    pub latency_ms: u64,
    pub model: String,
    pub inserted: bool,
}

pub struct History {
    path: PathBuf,
    pub entries: Vec<Entry>,
}

impl History {
    pub fn load(path: PathBuf) -> Self {
        let entries = std::fs::read_to_string(&path)
            .ok()
            .and_then(|s| serde_json::from_str::<Vec<Entry>>(&s).ok())
            .unwrap_or_default();
        Self { path, entries }
    }

    fn save(&self) {
        if let Some(dir) = self.path.parent() {
            let _ = std::fs::create_dir_all(dir);
        }
        if let Ok(body) = serde_json::to_string(&self.entries) {
            let tmp = self.path.with_extension("json.tmp");
            if std::fs::write(&tmp, body).is_ok() {
                let _ = std::fs::rename(&tmp, &self.path);
            }
        }
    }

    pub fn add(&mut self, mut e: Entry, limit: usize) -> Entry {
        e.id = self.entries.iter().map(|x| x.id).max().unwrap_or(0) + 1;
        self.entries.insert(0, e.clone());
        self.entries.truncate(limit.max(1));
        self.save();
        e
    }

    pub fn delete(&mut self, id: u64) {
        self.entries.retain(|e| e.id != id);
        self.save();
    }

    pub fn clear(&mut self) {
        self.entries.clear();
        self.save();
    }

    pub fn path(&self) -> &Path {
        &self.path
    }
}
