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

    pub fn stats(&self) -> UsageStats {
        let total_dictations = self.entries.len();
        let total_words: usize = self.entries.iter().map(|e| e.text.split_whitespace().count()).sum();
        let total_audio_ms: u64 = self.entries.iter().map(|e| e.audio_ms).sum();
        let avg_wpm = if total_audio_ms >= 1000 && total_words > 0 {
            ((total_words as f64) * 60_000.0 / (total_audio_ms as f64)).round() as u32
        } else {
            0
        };
        let typing_secs = (total_words as f64 / 40.0) * 60.0;
        let speaking_secs = total_audio_ms as f64 / 1000.0;
        let time_saved_secs = (typing_secs - speaking_secs).max(0.0).round() as u64;
        let mut latencies: Vec<u64> = self.entries.iter().map(|e| e.latency_ms).collect();
        latencies.sort_unstable();
        let median_latency_ms = if latencies.is_empty() {
            0
        } else {
            latencies[latencies.len() / 2]
        };
        UsageStats { total_dictations, total_words, total_audio_ms, avg_wpm, time_saved_secs, median_latency_ms }
    }
}

#[derive(Debug, Clone, Serialize, PartialEq)]
pub struct UsageStats {
    pub total_dictations: usize,
    pub total_words: usize,
    pub total_audio_ms: u64,
    pub avg_wpm: u32,
    pub time_saved_secs: u64,
    pub median_latency_ms: u64,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn usage_stats_empty_and_populated() {
        let dir = std::env::temp_dir().join(format!("humming-hist-stats-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        let mut h = History::load(dir.join("history.json"));
        let s0 = h.stats();
        assert_eq!(s0.total_dictations, 0);
        assert_eq!(s0.avg_wpm, 0);
        assert_eq!(s0.median_latency_ms, 0);

        h.add(
            Entry {
                id: 0,
                ts: 1,
                text: "one two three four five six seven eight nine ten".into(),
                raw: String::new(),
                audio_ms: 4_000,
                latency_ms: 200,
                model: "fast".into(),
                inserted: true,
            },
            100,
        );
        h.add(
            Entry {
                id: 0,
                ts: 2,
                text: "eleven twelve thirteen fourteen fifteen sixteen seventeen eighteen nineteen twenty".into(),
                raw: String::new(),
                audio_ms: 4_000,
                latency_ms: 400,
                model: "fast".into(),
                inserted: true,
            },
            100,
        );
        let s = h.stats();
        assert_eq!(s.total_dictations, 2);
        assert_eq!(s.total_words, 20);
        assert_eq!(s.total_audio_ms, 8_000);
        assert_eq!(s.avg_wpm, 150);
        assert_eq!(s.time_saved_secs, 22);
        assert_eq!(s.median_latency_ms, 400);
        let _ = std::fs::remove_dir_all(&dir);
    }
}
