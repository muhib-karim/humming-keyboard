//! Edit by voice on desktop (1.8, D5). A spoken command ("change Tuesday to Wednesday", "delete actually",
//! "capitalize humming", "undo that") edits only Humming's own last insertion, and only while the app that
//! received it still has focus. Humming never reads the focused app's text: it knows exactly what it typed,
//! so it removes the changed tail of that insertion with Backspace and types the new tail. Parsing and the
//! edit itself are the shared Rust core (`humming_core::voice_command`), tested on the same
//! core/testdata/voice_edit_vectors.json as Android and iOS.
//!
//! Limits, because Humming cannot see what the user did in the app after it typed:
//! - an edit is refused unless the focused app is known both at insertion and now, and is the same app;
//! - an edit is allowed only within `EDIT_WINDOW_MS` of the insertion, and only once per insertion
//!   (after an edit Humming forgets the text, so a second command is ordinary dictation);
//! - Backspace counts are user-perceived characters (grapheme clusters, e.g. one press for a family emoji),
//!   and an edit whose cut would split a cluster is refused.

use humming_core::{parse_command, voice_command, EditCommand};
use unicode_segmentation::UnicodeSegmentation;

/// How long after an insertion a voice edit may still touch it. Typing or moving the cursor in the app is not
/// visible to Humming, so the window is kept short.
pub const EDIT_WINDOW_MS: u64 = 30_000;

/// What the desktop does to the focused app for one dictation.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Action {
    /// Ordinary dictation: type/paste this text.
    Insert(String),
    /// Press Backspace `backspaces` times (grapheme clusters, never more than Humming's own last insertion),
    /// then type `text`.
    Replace { backspaces: usize, text: String },
    /// Nothing to type (silence, or "undo that" with nothing of ours to undo).
    Nothing,
}

/// Humming's own last insertion, the app it went into and when.
#[derive(Debug, Default, Clone)]
pub struct LastInsert {
    text: String,
    app: Option<String>,
    at_ms: u64,
}

fn chars(s: &str) -> usize {
    s.chars().count()
}

fn graphemes(s: &str) -> usize {
    s.graphemes(true).count()
}

/// Byte offset of the `n`th char, if it is also a grapheme-cluster boundary of `s`.
fn cluster_cut(s: &str, n: usize) -> Option<usize> {
    let byte = s.char_indices().nth(n).map(|(b, _)| b).unwrap_or(s.len());
    let on_boundary = byte == s.len() || s.grapheme_indices(true).any(|(b, _)| b == byte);
    on_boundary.then_some(byte)
}

impl LastInsert {
    /// Whether an edit may touch our last insertion: same known app, inside the time window.
    fn ours(&self, app: Option<&str>, now_ms: u64) -> bool {
        let same_app = matches!((self.app.as_deref(), app), (Some(a), Some(b)) if a == b);
        let fresh = now_ms >= self.at_ms && now_ms - self.at_ms <= EDIT_WINDOW_MS;
        !self.text.is_empty() && same_app && fresh
    }

    /// Decide what to do. `spoken` is the raw transcript of this recording (commands are read from it, so the
    /// clean-up rules can't eat them); `dictation` is the cleaned text that is typed when it is not a command;
    /// `app` is the focused app now (None = unknown); `now_ms` is the current time in milliseconds.
    pub fn plan(&self, spoken: &str, dictation: &str, app: Option<&str>, enabled: bool, now_ms: u64) -> Action {
        let spoken = spoken.trim();
        let plain = || if dictation.trim().is_empty() { Action::Nothing } else { Action::Insert(dictation.to_string()) };
        if !enabled || spoken.is_empty() {
            return plain();
        }
        if !self.ours(app, now_ms) {
            // Nothing of ours to edit here: "undo that" types nothing, anything else is dictation.
            return if parse_command(spoken) == Some(EditCommand::Undo) { Action::Nothing } else { plain() };
        }
        match voice_command(&self.text, spoken) {
            Some((EditCommand::Undo, _)) => Action::Replace { backspaces: graphemes(&self.text), text: String::new() },
            Some((_, Some(out))) => {
                let keep = (out.start as usize).min(chars(&self.text));
                match (cluster_cut(&self.text, keep), cluster_cut(&out.text, keep)) {
                    (Some(old_cut), Some(new_cut)) => Action::Replace {
                        backspaces: graphemes(&self.text[old_cut..]),
                        text: out.text[new_cut..].to_string(),
                    },
                    // The cut would split a character cluster: refuse rather than guess the Backspace count.
                    _ => Action::Nothing,
                }
            }
            _ => plain(),
        }
    }

    /// Record what was actually typed (call only after the keystrokes succeeded). An insertion into an
    /// unknown app is not recorded; after an edit the insertion is forgotten (one edit per insertion).
    pub fn commit(&mut self, action: &Action, app: Option<&str>, now_ms: u64) {
        match action {
            Action::Insert(t) => match app {
                Some(a) => {
                    self.text = t.clone();
                    self.app = Some(a.to_string());
                    self.at_ms = now_ms;
                }
                None => self.forget(),
            },
            Action::Replace { .. } => self.forget(),
            Action::Nothing => {}
        }
    }

    /// Forget the last insertion (a failed paste, a new target, focus moved), so no edit can touch text we
    /// don't own.
    pub fn forget(&mut self) {
        self.text.clear();
        self.app = None;
        self.at_ms = 0;
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const T0: u64 = 1_000_000;

    #[derive(serde::Deserialize)]
    struct Vector {
        text: String,
        utterance: String,
        command: bool,
        #[serde(default)]
        expect: Option<String>,
        #[serde(default)]
        undo: bool,
    }

    fn typed(t: &str, app: &str) -> LastInsert {
        let mut l = LastInsert::default();
        l.commit(&Action::Insert(t.to_string()), Some(app), T0);
        l
    }

    /// What the field holds after the action, given it held `before` (Backspace removes one cluster).
    fn apply(before: &str, a: &Action) -> String {
        match a {
            Action::Replace { backspaces, text } => {
                let n = graphemes(before) - backspaces;
                let mut s: String = before.graphemes(true).take(n).collect();
                s.push_str(text);
                s
            }
            Action::Insert(t) => format!("{before}{t}"),
            Action::Nothing => before.to_string(),
        }
    }

    #[test]
    fn shared_vectors_edit_only_our_last_insertion() {
        let raw = include_str!("../../../core/testdata/voice_edit_vectors.json");
        let vs: Vec<Vector> = serde_json::from_str(raw).unwrap();
        assert!(vs.len() >= 30);
        for v in vs {
            let l = typed(&v.text, "Notes");
            let a = l.plan(&v.utterance, &v.utterance, Some("Notes"), true, T0 + 1_000);
            if !v.command {
                assert_eq!(a, Action::Insert(v.utterance.clone()), "false positive {:?}", v.utterance);
                continue;
            }
            match &a {
                Action::Replace { backspaces, .. } => assert!(*backspaces <= graphemes(&v.text)),
                other => panic!("missed command {:?} -> {:?}", v.utterance, other),
            }
            let after = apply(&v.text, &a);
            if v.undo {
                assert_eq!(after, "", "undo removes our whole insertion");
            } else {
                assert_eq!(after, v.expect.clone().unwrap(), "utterance {:?}", v.utterance);
            }
        }
    }

    #[test]
    fn replace_retypes_only_the_changed_tail() {
        let l = typed("see you Tuesday at noon", "Mail");
        let a = l.plan("change Tuesday to Wednesday", "Change Tuesday to Wednesday. ", Some("Mail"), true, T0 + 5);
        assert_eq!(a, Action::Replace { backspaces: "Tuesday at noon".len(), text: "Wednesday at noon".into() });
    }

    #[test]
    fn other_app_or_switched_off_is_plain_dictation() {
        let l = typed("see you Tuesday", "Mail");
        let d = "Change Tuesday to Wednesday. ";
        let c = "change Tuesday to Wednesday";
        assert_eq!(l.plan(c, d, Some("Slack"), true, T0), Action::Insert(d.into()));
        assert_eq!(l.plan(c, d, Some("Mail"), false, T0), Action::Insert(d.into()));
        assert_eq!(l.plan("change Friday to Monday", d, Some("Mail"), true, T0), Action::Insert(d.into()));
    }

    #[test]
    fn unknown_app_never_edits() {
        // Codex review finding 1: both identities unknown must not count as "same app".
        let mut l = LastInsert::default();
        l.commit(&Action::Insert("see you Tuesday".into()), None, T0);
        let d = "Change Tuesday to Wednesday. ";
        assert_eq!(l.plan("change Tuesday to Wednesday", d, None, true, T0), Action::Insert(d.into()));
        let l = typed("see you Tuesday", "Mail");
        assert_eq!(l.plan("change Tuesday to Wednesday", d, None, true, T0), Action::Insert(d.into()));
        assert_eq!(l.plan("undo that", "", None, true, T0), Action::Nothing);
    }

    #[test]
    fn edits_expire_and_happen_once() {
        let mut l = typed("see you Tuesday", "Mail");
        let d = "Change Tuesday to Wednesday. ";
        let c = "change Tuesday to Wednesday";
        assert_eq!(l.plan(c, d, Some("Mail"), true, T0 + EDIT_WINDOW_MS + 1), Action::Insert(d.into()));
        let a = l.plan(c, d, Some("Mail"), true, T0 + EDIT_WINDOW_MS);
        assert!(matches!(a, Action::Replace { .. }));
        l.commit(&a, Some("Mail"), T0 + EDIT_WINDOW_MS);
        // After one edit the insertion is forgotten: a second command is dictation, "undo that" types nothing.
        assert_eq!(l.plan("change Wednesday to Thursday", "x", Some("Mail"), true, T0 + EDIT_WINDOW_MS), Action::Insert("x".into()));
        assert_eq!(l.plan("undo that", "", Some("Mail"), true, T0 + EDIT_WINDOW_MS), Action::Nothing);
    }

    #[test]
    fn backspaces_count_grapheme_clusters() {
        // Codex review finding 3: a family emoji is 7 scalars but one Backspace.
        let fam = "\u{1F468}\u{200D}\u{1F469}\u{200D}\u{1F467}\u{200D}\u{1F466}";
        let l = typed(&format!("hi {fam} "), "Chat");
        let a = l.plan("undo that", "", Some("Chat"), true, T0);
        assert_eq!(a, Action::Replace { backspaces: 5, text: String::new() });
        let l = typed(&format!("see you Tuesday {fam}"), "Chat");
        let a = l.plan("change Tuesday to Wednesday", "x", Some("Chat"), true, T0);
        assert_eq!(a, Action::Replace { backspaces: "Tuesday ".len() + 1, text: format!("Wednesday {fam}") });
        assert_eq!(apply(&format!("see you Tuesday {fam}"), &a), format!("see you Wednesday {fam}"));
        let l = typed("cafe\u{301} au lait", "Notes");
        let a = l.plan("delete lait", "", Some("Notes"), true, T0);
        assert_eq!(apply("cafe\u{301} au lait", &a).trim_end(), "cafe\u{301} au");
    }

    #[test]
    fn undo_with_nothing_of_ours_types_nothing() {
        let empty = LastInsert::default();
        assert_eq!(empty.plan("undo that", "", Some("Mail"), true, T0), Action::Nothing);
        let mut l = typed("hello there ", "Mail");
        let a = l.plan("undo that", "", Some("Mail"), true, T0);
        assert_eq!(a, Action::Replace { backspaces: 12, text: String::new() });
        l.commit(&a, Some("Mail"), T0);
        assert_eq!(l.plan("undo that", "", Some("Mail"), true, T0), Action::Nothing);
    }

    #[test]
    fn silence_types_nothing_and_forget_drops_ownership() {
        let mut l = typed("café au lait", "Notes");
        assert_eq!(l.plan("", "", Some("Notes"), true, T0), Action::Nothing);
        let a = l.plan("delete lait", "", Some("Notes"), true, T0);
        assert!(matches!(a, Action::Replace { .. }));
        l.forget();
        assert_eq!(l.plan("delete lait", "Delete lait.", Some("Notes"), true, T0), Action::Insert("Delete lait.".into()));
    }
}
