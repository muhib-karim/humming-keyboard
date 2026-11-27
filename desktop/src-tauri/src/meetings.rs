//! Offline botless meeting notepad: captures continuous audio chunks (mic or system loopback device), transcribes
//! them locally with the loaded Parakeet engine, pairs timestamped segments with the user's own Markdown notes, and
//! synthesizes structured digests via local Ollama, optional BYOK LLM, or a deterministic 100%-offline fallback.

use serde::{Deserialize, Serialize};
use std::path::{Path, PathBuf};

#[derive(Debug, Clone, Default, Serialize, Deserialize, PartialEq)]
#[serde(default)]
pub struct MeetingSegment {
    pub ts_ms: u64,
    pub elapsed_s: u64,
    pub speaker: String,
    pub text: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq)]
#[serde(default)]
pub struct Meeting {
    pub id: u64,
    pub title: String,
    pub started_ms: u64,
    pub ended_ms: Option<u64>,
    pub notes: String,
    pub summary: String,
    pub template: String,
    pub segments: Vec<MeetingSegment>,
}

impl Default for Meeting {
    fn default() -> Self {
        Self {
            id: 0,
            title: String::new(),
            started_ms: 0,
            ended_ms: None,
            notes: String::new(),
            summary: String::new(),
            template: "summary".into(),
            segments: Vec::new(),
        }
    }
}

pub struct MeetingStore {
    path: PathBuf,
    pub items: Vec<Meeting>,
}

impl MeetingStore {
    pub fn load(path: PathBuf) -> Self {
        let items = std::fs::read_to_string(&path)
            .ok()
            .and_then(|s| serde_json::from_str::<Vec<Meeting>>(&s).ok())
            .unwrap_or_default();
        Self { path, items }
    }

    pub fn path(&self) -> &Path {
        &self.path
    }

    fn save(&self) {
        if let Some(dir) = self.path.parent() {
            let _ = std::fs::create_dir_all(dir);
        }
        let tmp = self.path.with_extension("json.tmp");
        if let Ok(body) = serde_json::to_string_pretty(&self.items) {
            if std::fs::write(&tmp, body).is_ok() {
                let _ = std::fs::rename(&tmp, &self.path);
            }
        }
    }

    pub fn create(&mut self, title: String, started_ms: u64) -> Meeting {
        let id = self.items.iter().map(|m| m.id).max().unwrap_or(0) + 1;
        let title = if title.trim().is_empty() { format!("Meeting #{id}") } else { title.trim().to_string() };
        let m = Meeting { id, title, started_ms, ..Meeting::default() };
        self.items.insert(0, m.clone());
        self.save();
        m
    }

    pub fn get(&self, id: u64) -> Option<Meeting> {
        self.items.iter().find(|m| m.id == id).cloned()
    }

    pub fn append_segment(&mut self, id: u64, seg: MeetingSegment) -> Option<Meeting> {
        let m = self.items.iter_mut().find(|m| m.id == id)?;
        m.segments.push(seg);
        let out = m.clone();
        self.save();
        Some(out)
    }

    pub fn update_notes(&mut self, id: u64, title: Option<String>, notes: String) -> Option<Meeting> {
        let m = self.items.iter_mut().find(|m| m.id == id)?;
        if let Some(t) = title {
            if !t.trim().is_empty() {
                m.title = t.trim().to_string();
            }
        }
        m.notes = notes;
        let out = m.clone();
        self.save();
        Some(out)
    }

    pub fn set_summary(&mut self, id: u64, template: String, summary: String) -> Option<Meeting> {
        let m = self.items.iter_mut().find(|m| m.id == id)?;
        m.template = template;
        m.summary = summary;
        let out = m.clone();
        self.save();
        Some(out)
    }

    pub fn finish(&mut self, id: u64, ended_ms: u64) -> Option<Meeting> {
        let m = self.items.iter_mut().find(|m| m.id == id)?;
        m.ended_ms = Some(ended_ms);
        let out = m.clone();
        self.save();
        Some(out)
    }

    pub fn delete(&mut self, id: u64) {
        self.items.retain(|m| m.id != id);
        self.save();
    }

    /// Cross-meeting search: ranks matching notes and transcript segments across all saved meetings by keyword hits.
    pub fn search_context(&self, query: &str, max_hits: usize) -> Vec<String> {
        let terms: Vec<String> = query
            .to_lowercase()
            .split(|c: char| !c.is_alphanumeric())
            .filter(|w| w.len() >= 2)
            .map(|w| w.to_string())
            .collect();
        if terms.is_empty() {
            return Vec::new();
        }
        let mut scored: Vec<(usize, String)> = Vec::new();
        for m in &self.items {
            for line in m.notes.lines().map(|l| l.trim()).filter(|l| !l.is_empty()) {
                let low = line.to_lowercase();
                let score = terms.iter().filter(|t| low.contains(t.as_str())).count();
                if score > 0 {
                    scored.push((score + 1, format!("[{} · Note] {}", m.title, line)));
                }
            }
            for seg in &m.segments {
                let low = seg.text.to_lowercase();
                let score = terms.iter().filter(|t| low.contains(t.as_str())).count();
                if score > 0 {
                    scored.push((score, format!("[{} · {}] {}", m.title, format_seg_tag(seg), seg.text.trim())));
                }
            }
        }
        scored.sort_by(|a, b| b.0.cmp(&a.0));
        scored.into_iter().take(max_hits).map(|(_, s)| s).collect()
    }
}

pub fn format_timestamp(elapsed_s: u64) -> String {
    let h = elapsed_s / 3600;
    let m = (elapsed_s % 3600) / 60;
    let s = elapsed_s % 60;
    if h > 0 {
        format!("{h:02}:{m:02}:{s:02}")
    } else {
        format!("{m:02}:{s:02}")
    }
}

pub fn format_seg_tag(seg: &MeetingSegment) -> String {
    let ts = format_timestamp(seg.elapsed_s);
    let spk = seg.speaker.trim();
    if spk.is_empty() {
        ts
    } else {
        format!("{ts} · {spk}")
    }
}

pub fn transcript_text(meeting: &Meeting) -> String {
    meeting
        .segments
        .iter()
        .map(|s| format!("[{}] {}", format_seg_tag(s), s.text.trim()))
        .collect::<Vec<_>>()
        .join("\n")
}

/// Action-item cue phrases for deterministic 100%-offline meeting digests when no LLM is configured.
const ACTION_CUES: &[&str] = &[
    "action item",
    "todo",
    "to-do",
    "follow up",
    "follow-up",
    "will send",
    "i'll send",
    "i will",
    "we should",
    "we need to",
    "need to ",
    "let's ",
    "make sure",
    "deadline",
    "by monday",
    "by tuesday",
    "by wednesday",
    "by thursday",
    "by friday",
    "next week",
    "assign",
    "owner",
];

fn extract_actions(meeting: &Meeting) -> Vec<String> {
    let mut out = Vec::new();
    for seg in &meeting.segments {
        for raw in seg.text.split(['.', '!', '?', '\n']) {
            let s = raw.trim();
            if s.split_whitespace().count() < 3 {
                continue;
            }
            let lower = s.to_lowercase();
            if ACTION_CUES.iter().any(|c| lower.contains(c)) {
                out.push(format!("- [{}] {}", format_seg_tag(seg), s));
            }
        }
    }
    out
}

/// Deterministic offline synthesis (zero network, zero LLM required).
pub fn offline_digest(meeting: &Meeting, template: &str) -> String {
    let mut md = String::new();
    md.push_str(&format!("# {}\n\n", meeting.title));
    if !meeting.notes.trim().is_empty() {
        md.push_str("## Your Notes\n\n");
        md.push_str(meeting.notes.trim());
        md.push_str("\n\n");
    }
    let actions = extract_actions(meeting);
    if !actions.is_empty() || template == "actions" {
        md.push_str("## Action Items\n\n");
        if actions.is_empty() {
            md.push_str("- _(No explicit action cues detected in transcript.)_\n\n");
        } else {
            for a in actions {
                md.push_str(&a);
                md.push('\n');
            }
            md.push('\n');
        }
    }
    md.push_str("## Key Moments\n\n");
    if meeting.segments.is_empty() {
        md.push_str("_(No transcript segments recorded yet.)_\n");
    } else {
        for seg in &meeting.segments {
            md.push_str(&format!("- **[{}]** {}\n", format_seg_tag(seg), seg.text.trim()));
        }
    }
    md
}

pub fn synthesis_prompt(template: &str) -> &'static str {
    match template {
        "actions" => "You synthesize meeting notes and transcripts into a crisp Markdown list of Action Items (with owner and deadline if stated), Open Questions, and Decisions. Prioritize items the user noted themselves. Reply with Markdown only.",
        "decisions" => "You synthesize meeting notes and transcripts into Technical Decisions, Trade-offs Discussed, Risks, and Next Steps in Markdown. Ground every point in the user's notes and transcript; do not invent facts. Reply with Markdown only.",
        "followup" => "You synthesize meeting notes and transcripts into a concise 1-on-1 Follow-up: Highlights, Coaching/Feedback, Blockers, and Commitments in Markdown. Reply with Markdown only.",
        _ => "You synthesize a user's rough meeting notes together with the full transcript into clean, structured Markdown: Executive Summary, Key Discussion Points, Decisions, and Action Items. Treat the user's manual notes as the highest-priority signal and flesh them out with accurate details from the transcript without inventing facts. Reply with Markdown only.",
    }
}

pub fn to_markdown(meeting: &Meeting) -> String {
    let mut out = String::new();
    if !meeting.summary.trim().is_empty() {
        out.push_str(meeting.summary.trim());
        out.push_str("\n\n---\n\n");
    } else {
        out.push_str(&format!("# {}\n\n", meeting.title));
        if !meeting.notes.trim().is_empty() {
            out.push_str("## Notes\n\n");
            out.push_str(meeting.notes.trim());
            out.push_str("\n\n");
        }
    }
    out.push_str("## Transcript\n\n");
    let tx = transcript_text(meeting);
    if tx.is_empty() {
        out.push_str("_(Empty transcript)_\n");
    } else {
        out.push_str(&tx);
        out.push('\n');
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn timestamp_formatting() {
        assert_eq!(format_timestamp(0), "00:00");
        assert_eq!(format_timestamp(65), "01:05");
        assert_eq!(format_timestamp(3661), "01:01:01");
    }

    #[test]
    fn store_persists_segments_notes_and_summary() {
        let dir = std::env::temp_dir().join(format!("humming-meetings-test-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        let path = dir.join("meetings.json");
        let mut store = MeetingStore::load(path.clone());
        let m = store.create("Sprint Review".into(), 1_700_000_000_000);
        store.append_segment(
            m.id,
            MeetingSegment {
                ts_ms: 1_700_000_015_000,
                elapsed_s: 15,
                speaker: "Remote".into(),
                text: "We need to ship the Android fix by Friday.".into(),
            },
        );
        store.update_notes(m.id, None, "- check CI gate\n- verify banner".into());
        let digest = offline_digest(&store.get(m.id).unwrap(), "summary");
        assert!(digest.contains("## Your Notes"));
        assert!(digest.contains("check CI gate"));
        assert!(digest.contains("## Action Items"));
        assert!(digest.contains("[00:15 · Remote] We need to ship the Android fix by Friday"));
        store.set_summary(m.id, "summary".into(), digest);

        let reloaded = MeetingStore::load(path);
        assert_eq!(reloaded.items.len(), 1);
        assert_eq!(reloaded.items[0].title, "Sprint Review");
        assert_eq!(reloaded.items[0].segments.len(), 1);
        assert_eq!(reloaded.items[0].segments[0].speaker, "Remote");
        let md = to_markdown(&reloaded.items[0]);
        assert!(md.contains("## Transcript"));
        assert!(md.contains("[00:15 · Remote]"));
        let hits = reloaded.search_context("Android Friday fix", 5);
        assert!(!hits.is_empty());
        assert!(hits[0].contains("Sprint Review"));
        assert_eq!(
            format_seg_tag(&MeetingSegment { ts_ms: 0, elapsed_s: 5, speaker: String::new(), text: String::new() }),
            "00:05"
        );
        let _ = std::fs::remove_dir_all(&dir);
    }
}
