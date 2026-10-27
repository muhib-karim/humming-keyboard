//! Edit by voice on desktop (1.8, D5). A spoken command ("change Tuesday to Wednesday", "delete actually",
//! "capitalize humming", "undo that") edits only Humming's own last insertion, and only while the app that
//! received it still has focus. Humming never reads the focused app's text: it knows exactly what it typed,
//! so it removes the changed tail of that insertion with Backspace and types the new tail. Parsing and the
//! edit itself are the shared Rust core (`humming_core::voice_command`), tested on the same
//! core/testdata/voice_edit_vectors.json as Android and iOS.

use humming_core::{parse_command, voice_command, EditCommand};

/// What the desktop does to the focused app for one dictation.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Action {
    /// Ordinary dictation: type/paste this text.
    Insert(String),
    /// Press Backspace `backspaces` times (never more than Humming's own last insertion), then type `text`.
    Replace { backspaces: usize, text: String },
    /// Nothing to type (silence, or "undo that" with nothing of ours to undo).
    Nothing,
}

/// Humming's own last insertion and the app it went into.
#[derive(Debug, Default, Clone)]
pub struct LastInsert {
    text: String,
    app: Option<String>,
}

fn chars(s: &str) -> usize {
    s.chars().count()
}

impl LastInsert {
    /// Decide what to do. `spoken` is the raw transcript (commands are read from it, so the clean-up rules
    /// can't eat them); `dictation` is the cleaned text that is typed when it is not a command.
    pub fn plan(&self, spoken: &str, dictation: &str, app: Option<&str>, enabled: bool) -> Action {
        let spoken = spoken.trim();
        let plain = || if dictation.trim().is_empty() { Action::Nothing } else { Action::Insert(dictation.to_string()) };
        if !enabled || spoken.is_empty() {
            return plain();
        }
        let ours = !self.text.is_empty() && self.app.as_deref() == app;
        if !ours {
            // Nothing of ours to edit here: "undo that" types nothing, anything else is dictation.
            return if parse_command(spoken) == Some(EditCommand::Undo) { Action::Nothing } else { plain() };
        }
        match voice_command(&self.text, spoken) {
            Some((EditCommand::Undo, _)) => Action::Replace { backspaces: chars(&self.text), text: String::new() },
            Some((_, Some(out))) => {
                let keep = (out.start as usize).min(chars(&self.text));
                let tail: String = out.text.chars().skip(keep).collect();
                Action::Replace { backspaces: chars(&self.text) - keep, text: tail }
            }
            _ => plain(),
        }
    }

    /// Record what was actually typed (call only after the keystrokes succeeded).
    pub fn commit(&mut self, action: &Action, app: Option<&str>) {
        match action {
            Action::Insert(t) => {
                self.text = t.clone();
                self.app = app.map(str::to_string);
            }
            Action::Replace { backspaces, text } => {
                let keep = chars(&self.text).saturating_sub(*backspaces);
                let mut s: String = self.text.chars().take(keep).collect();
                s.push_str(text);
                self.text = s;
                if self.text.is_empty() {
                    self.app = None;
                }
            }
            Action::Nothing => {}
        }
    }

    /// Forget the last insertion (a failed paste, a new target), so no edit can touch text we don't own.
    pub fn forget(&mut self) {
        self.text.clear();
        self.app = None;
    }

    #[cfg(test)]
    pub fn text(&self) -> &str {
        &self.text
    }
}

#[cfg(test)]
mod tests {
    use super::*;

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
        l.commit(&Action::Insert(t.to_string()), Some(app));
        l
    }

    #[test]
    fn shared_vectors_edit_only_our_last_insertion() {
        let raw = include_str!("../../../core/testdata/voice_edit_vectors.json");
        let vs: Vec<Vector> = serde_json::from_str(raw).unwrap();
        assert!(vs.len() >= 30);
        for v in vs {
            let mut l = typed(&v.text, "Notes");
            let a = l.plan(&v.utterance, &v.utterance, Some("Notes"), true);
            if !v.command {
                assert_eq!(a, Action::Insert(v.utterance.clone()), "false positive {:?}", v.utterance);
                continue;
            }
            match &a {
                Action::Replace { backspaces, .. } => assert!(*backspaces <= v.text.chars().count()),
                other => panic!("missed command {:?} -> {:?}", v.utterance, other),
            }
            l.commit(&a, Some("Notes"));
            if v.undo {
                assert_eq!(l.text(), "", "undo removes our whole insertion");
            } else {
                assert_eq!(l.text(), v.expect.clone().unwrap(), "utterance {:?}", v.utterance);
            }
        }
    }

    #[test]
    fn replace_retypes_only_the_changed_tail() {
        let l = typed("see you Tuesday at noon", "Mail");
        let a = l.plan("change Tuesday to Wednesday", "Change Tuesday to Wednesday. ", Some("Mail"), true);
        assert_eq!(a, Action::Replace { backspaces: "Tuesday at noon".len(), text: "Wednesday at noon".into() });
    }

    #[test]
    fn other_app_or_switched_off_is_plain_dictation() {
        let l = typed("see you Tuesday", "Mail");
        let d = "Change Tuesday to Wednesday. ";
        assert_eq!(l.plan("change Tuesday to Wednesday", d, Some("Slack"), true), Action::Insert(d.into()));
        assert_eq!(l.plan("change Tuesday to Wednesday", d, Some("Mail"), false), Action::Insert(d.into()));
        assert_eq!(l.plan("change Friday to Monday", d, Some("Mail"), true), Action::Insert(d.into()));
    }

    #[test]
    fn undo_with_nothing_of_ours_types_nothing_and_undo_twice_is_safe() {
        let empty = LastInsert::default();
        assert_eq!(empty.plan("undo that", "", Some("Mail"), true), Action::Nothing);
        let mut l = typed("hello there ", "Mail");
        let a = l.plan("undo that", "", Some("Mail"), true);
        assert_eq!(a, Action::Replace { backspaces: 12, text: String::new() });
        l.commit(&a, Some("Mail"));
        assert_eq!(l.plan("undo that", "", Some("Mail"), true), Action::Nothing);
    }

    #[test]
    fn silence_types_nothing_and_forget_drops_ownership() {
        let mut l = typed("café au lait", "Notes");
        assert_eq!(l.plan("", "", Some("Notes"), true), Action::Nothing);
        let a = l.plan("delete lait", "", Some("Notes"), true);
        assert!(matches!(a, Action::Replace { .. }));
        l.forget();
        assert_eq!(l.plan("delete lait", "Delete lait.", Some("Notes"), true), Action::Insert("Delete lait.".into()));
    }
}
