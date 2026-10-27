//! D5: voice-command editing. A whole dictated utterance such as "change Tuesday to Wednesday"
//! is recognised as an edit of the text the user already has, not as new text to insert.
//!
//! Safety rules (each one has false-positive tests below and in `testdata/voice_edit_vectors.json`):
//! * whole-utterance only: the command must be the entire utterance (a filler such as "um" or a
//!   trailing full stop is ignored), never a phrase inside a longer sentence;
//! * short: a command utterance has at most 14 words;
//! * the target must exist in the existing text; when it doesn't, the utterance is NOT a command
//!   and the host inserts it as ordinary dictation ("change the oil to synthetic" stays text).
//! The last (closest to the cursor) whole-word, case-insensitive match is the one edited.

use once_cell::sync::Lazy;
use regex::Regex;

/// A recognised edit command.
#[derive(Debug, Clone, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
#[cfg_attr(feature = "ffi", derive(uniffi::Enum))]
pub enum EditCommand {
    Replace { target: String, replacement: String },
    Delete { target: String },
    Capitalize { target: String },
    /// "undo that" / "scratch that": hosts remove their own last insertion.
    Undo,
}

/// The result of applying a command to the existing text.
#[derive(Debug, Clone, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
#[cfg_attr(feature = "ffi", derive(uniffi::Record))]
pub struct EditOutcome {
    /// The whole edited text (hosts that can replace the region before the cursor use this).
    pub text: String,
    /// Char offset (Unicode scalar values) where the edited span starts in the ORIGINAL text.
    pub start: u32,
    /// Length in chars of the original span that was replaced.
    pub old_len: u32,
    /// What now stands in that span.
    pub inserted: String,
}

const MAX_WORDS: usize = 14;

static LEAD: Lazy<Regex> = Lazy::new(|| Regex::new(r"(?i)^(?:(?:um+|uh+|okay|ok|so|please|hey humming|humming)[,\s]+)+").unwrap());
static TRAIL: Lazy<Regex> = Lazy::new(|| Regex::new(r"(?i)(?:[,\s]+please)?[\s.!?,]*$").unwrap());
static RE_REPLACE: Lazy<Regex> = Lazy::new(|| {
    Regex::new(r#"(?i)^(?:change|replace|switch)\s+["“]?(.+?)["”]?\s+(?:to|with|into)\s+["“]?(.+?)["”]?$"#).unwrap()
});
static RE_DELETE: Lazy<Regex> = Lazy::new(|| Regex::new(r#"(?i)^(?:delete|remove|erase)\s+(?:the\s+word\s+)?["“]?(.+?)["”]?$"#).unwrap());
static RE_CAP: Lazy<Regex> = Lazy::new(|| Regex::new(r#"(?i)^(?:capitalize|capitalise)\s+(?:the\s+word\s+)?["“]?(.+?)["”]?$"#).unwrap());
static RE_UNDO: Lazy<Regex> = Lazy::new(|| Regex::new(r"(?i)^(?:undo(?:\s+that)?|scratch\s+that|delete\s+that|undo\s+the\s+last\s+(?:bit|part|sentence))$").unwrap());

fn normalise(utterance: &str) -> Option<String> {
    let t = utterance.trim();
    let t = LEAD.replace(t, "");
    let t = TRAIL.replace(&t, "");
    let t = t.trim().to_string();
    if t.is_empty() || t.split_whitespace().count() > MAX_WORDS { return None; }
    Some(t)
}

/// Parse an utterance as a command, without looking at the existing text.
/// Most callers want [`apply_voice_command`], which also enforces "the target must exist".
pub fn parse_command(utterance: &str) -> Option<EditCommand> {
    let t = normalise(utterance)?;
    if RE_UNDO.is_match(&t) { return Some(EditCommand::Undo); }
    if let Some(c) = RE_REPLACE.captures(&t) {
        let (a, b) = (c[1].trim(), c[2].trim());
        if !a.is_empty() && !b.is_empty() && !a.eq_ignore_ascii_case(b) {
            return Some(EditCommand::Replace { target: a.to_string(), replacement: b.to_string() });
        }
        return None;
    }
    if let Some(c) = RE_CAP.captures(&t) { return Some(EditCommand::Capitalize { target: c[1].trim().to_string() }); }
    if let Some(c) = RE_DELETE.captures(&t) { return Some(EditCommand::Delete { target: c[1].trim().to_string() }); }
    None
}

/// Find the last whole-word, case-insensitive occurrence of `target` in `text`, as a byte range.
fn find_last(text: &str, target: &str) -> Option<(usize, usize)> {
    let pat = format!(r"(?i)(?:^|\b|\s){}(?:\b|$)", regex::escape(target));
    let re = Regex::new(&pat).ok()?;
    let mut last = None;
    let mut pos = 0;
    while pos <= text.len() {
        let Some(m) = re.find_at(text, pos) else { break };
        let s = m.as_str();
        let lead = s.len() - s.trim_start().len();
        let start = m.start() + lead;
        let end = m.end();
        // require a word boundary on the right when the target ends in a word char
        last = Some((start, end));
        pos = if m.end() > m.start() { m.start() + text[m.start()..].chars().next().map(|c| c.len_utf8()).unwrap_or(1) } else { m.end() + 1 };
    }
    last
}

fn match_case(original: &str, replacement: &str) -> String {
    let orig_upper_first = original.chars().next().map(|c| c.is_uppercase()).unwrap_or(false);
    let all_upper = original.chars().any(|c| c.is_alphabetic()) && original.chars().filter(|c| c.is_alphabetic()).all(|c| c.is_uppercase()) && original.chars().filter(|c| c.is_alphabetic()).count() > 1;
    if all_upper { return replacement.to_uppercase(); }
    let rep_has_upper = replacement.chars().any(|c| c.is_uppercase());
    if orig_upper_first && !rep_has_upper {
        let mut cs = replacement.chars();
        return match cs.next() { Some(f) => f.to_uppercase().collect::<String>() + cs.as_str(), None => String::new() };
    }
    replacement.to_string()
}

fn chars(s: &str) -> u32 { s.chars().count() as u32 }

/// Apply a parsed command to `text` (the text before the cursor). Returns `None` when the
/// command cannot apply (target not present, or `Undo`, which only the host can perform).
pub fn apply_command(text: &str, cmd: &EditCommand) -> Option<EditOutcome> {
    match cmd {
        EditCommand::Undo => None,
        EditCommand::Replace { target, replacement } => {
            let (s, e) = find_last(text, target)?;
            let ins = match_case(&text[s..e], replacement);
            let out = format!("{}{}{}", &text[..s], ins, &text[e..]);
            Some(EditOutcome { text: out, start: chars(&text[..s]), old_len: chars(&text[s..e]), inserted: ins })
        }
        EditCommand::Capitalize { target } => {
            let (s, e) = find_last(text, target)?;
            let ins: String = text[s..e].split(' ').map(|w| { let mut c = w.chars(); match c.next() { Some(f) => f.to_uppercase().collect::<String>() + c.as_str(), None => String::new() } }).collect::<Vec<_>>().join(" ");
            let out = format!("{}{}{}", &text[..s], ins, &text[e..]);
            Some(EditOutcome { text: out, start: chars(&text[..s]), old_len: chars(&text[s..e]), inserted: ins })
        }
        EditCommand::Delete { target } => {
            let (mut s, e) = find_last(text, target)?;
            // take one adjoining space with the word so no double space is left behind
            let before = &text[..s];
            let after = &text[e..];
            if before.ends_with(' ') && (after.is_empty() || after.starts_with(' ') || after.starts_with(|c: char| ",.!?;:".contains(c))) {
                s -= 1;
            } else if after.starts_with(' ') {
                let out = format!("{}{}", before, &after[1..]);
                return Some(EditOutcome { text: out, start: chars(before), old_len: chars(&text[s..e + 1]), inserted: String::new() });
            }
            let out = format!("{}{}", &text[..s], after);
            Some(EditOutcome { text: out, start: chars(&text[..s]), old_len: chars(&text[s..e]), inserted: String::new() })
        }
    }
}

/// The one call hosts make: if `utterance` is a voice command that applies to `text`, return the
/// command and the outcome. `Undo` comes back with `None` outcome (the host undoes its own last
/// insertion). Anything else returns `None`, and the host inserts the utterance as dictation.
pub fn voice_command(text: &str, utterance: &str) -> Option<(EditCommand, Option<EditOutcome>)> {
    let cmd = parse_command(utterance)?;
    if cmd == EditCommand::Undo { return Some((cmd, None)); }
    let out = apply_command(text, &cmd)?;
    Some((cmd, Some(out)))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[derive(serde::Deserialize)]
    struct Vector { text: String, utterance: String, command: bool, #[serde(default)] expect: Option<String>, #[serde(default)] undo: bool }

    #[test]
    fn shared_vectors() {
        let raw = include_str!("../testdata/voice_edit_vectors.json");
        let vs: Vec<Vector> = serde_json::from_str(raw).unwrap();
        assert!(vs.len() >= 30, "need a real vector set");
        for v in vs {
            let got = voice_command(&v.text, &v.utterance);
            if !v.command { assert!(got.is_none(), "false positive: {:?} on {:?} -> {:?}", v.utterance, v.text, got); continue; }
            let (cmd, out) = got.unwrap_or_else(|| panic!("missed command {:?} on {:?}", v.utterance, v.text));
            if v.undo { assert_eq!(cmd, EditCommand::Undo); continue; }
            let out = out.unwrap();
            assert_eq!(out.text, v.expect.clone().unwrap(), "utterance {:?}", v.utterance);
            // the span facts must rebuild the same text
            let orig: Vec<char> = v.text.chars().collect();
            let rebuilt: String = orig[..out.start as usize].iter().collect::<String>() + &out.inserted + &orig[(out.start + out.old_len) as usize..].iter().collect::<String>();
            assert_eq!(rebuilt, out.text, "span mismatch for {:?}", v.utterance);
        }
    }

    #[test]
    fn long_sentence_is_never_a_command() {
        assert!(parse_command("change of plans we are going to the beach to meet with Sam and his family later today after lunch").is_none());
    }

    #[test]
    fn unicode_offsets_are_chars() {
        let (_, o) = voice_command("café is open", "change open to closed").unwrap();
        let o = o.unwrap();
        assert_eq!(o.start, 8);
        assert_eq!(o.text, "café is closed");
    }
}
