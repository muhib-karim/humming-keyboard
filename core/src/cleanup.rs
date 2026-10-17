//! Rule-based dictation cleanup (M1). The LLM cleanup pass from the arch doc comes later and
//! runs after this, so these rules stay deterministic and conservative.

use once_cell::sync::Lazy;
use regex::Regex;

#[derive(Debug, Clone, PartialEq, serde::Serialize, serde::Deserialize)]
#[cfg_attr(feature = "ffi", derive(uniffi::Record))]
pub struct CleanupOpts {
    pub remove_fillers: bool,
    pub capitalize: bool,
    pub fix_spacing: bool,
    pub commands: bool,
    pub ensure_final_punctuation: bool,
    /// "I I think" -> "I think".
    pub remove_repeats: bool,
    /// "at 3, no, 4pm" -> "at 4pm"; "John, sorry, I mean Jane" -> "Jane".
    pub self_corrections: bool,
    /// Personal dictionary: exact-phrase replacements applied last (also biases STT, see EngineConfig).
    pub dictionary: Vec<DictionaryEntry>,
}

/// One personal-dictionary term. `spoken` is matched case-insensitively as a whole phrase and replaced by
/// `written`. Leave `spoken` empty to only fix casing of `written` (e.g. "github" -> "GitHub").
#[cfg_attr(feature = "ffi", derive(uniffi::Record))]
#[derive(Debug, Clone, PartialEq, serde::Serialize, serde::Deserialize)]
pub struct DictionaryEntry {
    pub spoken: String,
    pub written: String,
}

impl Default for CleanupOpts {
    fn default() -> Self {
        Self {
            remove_fillers: true,
            capitalize: true,
            fix_spacing: true,
            commands: true,
            ensure_final_punctuation: true,
            remove_repeats: true,
            self_corrections: true,
            dictionary: Vec::new(),
        }
    }
}

impl CleanupOpts {
    /// Everything off: `cleanup` returns the input trimmed.
    pub fn none() -> Self {
        Self {
            remove_fillers: false,
            capitalize: false,
            fix_spacing: false,
            commands: false,
            ensure_final_punctuation: false,
            remove_repeats: false,
            self_corrections: false,
            dictionary: Vec::new(),
        }
    }
}

fn re(p: &str) -> Regex {
    Regex::new(p).expect("static regex")
}

// Spoken commands, longest first. Whisper often wraps them in its own punctuation
// ("Hello, comma, world."), which the punctuation-run collapse below absorbs.
static COMMANDS: Lazy<Vec<(Regex, &'static str)>> = Lazy::new(|| {
    vec![
        (re(r"(?i)\bnew paragraph\b"), "\n\n"),
        (re(r"(?i)\bnew line\b"), "\n"),
        (re(r"(?i)\bquestion mark\b"), "?"),
        (re(r"(?i)\bexclamation (?:mark|point)\b"), "!"),
        (re(r"(?i)\bfull stop\b"), "."),
        (re(r"(?i)\bperiod\b"), "."),
        (re(r"(?i)\bcomma\b"), ","),
        (re(r"(?i)\bsemicolon\b"), ";"),
        (re(r"(?i)\bcolon\b"), ":"),
    ]
});

static FILLERS: Lazy<Regex> = Lazy::new(|| re(r"(?i)\b(?:u+m+|u+h+m*|e+r+m+|e+r+|h+m+|m+h*m+)\b[,.]?"));
static YOU_KNOW: Lazy<Regex> = Lazy::new(|| re(r"(?i)(^|[,.!?;:]|\n)\s*you know,"));
static SPACES_AROUND_NL: Lazy<Regex> = Lazy::new(|| re(r"[ \t]*\n[ \t]*"));
static PUNCT_AFTER_NL: Lazy<Regex> = Lazy::new(|| re(r"\n[ \t]*[,.;:!?]+"));
static PUNCT_BEFORE_NL: Lazy<Regex> = Lazy::new(|| re(r",+\n"));
// A run of punctuation marks (spaces allowed between) collapses to its last mark,
// except a literal ellipsis "...".
static PUNCT_RUN: Lazy<Regex> = Lazy::new(|| re(r"[,.;:!?](?:[ \t]*[,.;:!?])+"));
static SPACE_BEFORE_PUNCT: Lazy<Regex> = Lazy::new(|| re(r"[ \t]+([,.;:!?])"));
static NO_SPACE_AFTER_PUNCT: Lazy<Regex> = Lazy::new(|| re(r"([,;:!?])([A-Za-z0-9])"));
static NO_SPACE_AFTER_PERIOD: Lazy<Regex> = Lazy::new(|| re(r"\.([A-Za-z])"));
static MULTI_SPACE: Lazy<Regex> = Lazy::new(|| re(r"[ \t]{2,}"));
static LEADING_PUNCT: Lazy<Regex> = Lazy::new(|| re(r"(^|\n)[ \t]*[,.;:]+[ \t]*"));
// Strong self-correction markers: drop the word before the marker and the marker itself.
static SELF_CORRECT_STRONG: Lazy<Regex> = Lazy::new(|| {
    re(r"(?i)\b[\w'’$%:.\-]+,?\s+(?:sorry,?\s+i\s+mean|i\s+mean|scratch\s+that)\s*,?\s+")
});
// Weak markers ("no", "wait", ...): only when both sides are the same kind (numbers/times or capitalized names).
static SELF_CORRECT_WEAK: Lazy<Regex> = Lazy::new(|| {
    re(r"(\$?\b[\w'’$%:.\-]+),\s+(?:no|wait|no wait|actually|sorry|or rather),\s+([\w'’$%:.\-]+)")
});
// "three, no, four pm" that whisper wrote as "3.0.4 pm" ("no" heard as "oh"/zero): keep the corrected time.
// Only with am/pm after it, where "N.0.M" can never be a real value.
static SELF_CORRECT_TIME_ZERO: Lazy<Regex> =
    Lazy::new(|| re(r"(?i)\b(?:[01]?\d)\.0\.([01]?\d)\s?([ap]\.?m\b\.?)"));
// Comma-marked discourse openers at the start of the utterance or a sentence: "so, I think" -> "I think".
// Without the comma ("so I think") the word is kept: it often carries meaning.
static LEADING_DISCOURSE: Lazy<Regex> =
    Lazy::new(|| re(r"(?i)(^[\s,]*|[.!?]\s+)(?:so|okay,?\s*so|ok,?\s*so|like|basically|alright,?\s*so),\s*"));
// Clean mode (Rambler-style), all deterministic:
// a filler between two commas takes both commas with it: "we could, um, go" -> "we could go".
static FILLER_IN_COMMAS: Lazy<Regex> =
    Lazy::new(|| re(r"(\b\w+\s+[\w'’]*[a-z]),\s*(?:u+m+|u+h+m*|e+r+m+|e+r+|h+m+|m+h*m+|you know|like)\s*,\s*([a-z])"));
// "ten minutes, uh, fifteen minutes": a hesitation between two amounts of the same unit keeps the second.
static AMOUNT_RESTART: Lazy<Regex> = Lazy::new(|| {
    re(r"(?i)(\$?\b[\w.]+)\s+([a-z]+),?\s+(?:u+h+|u+m+|e+r+m*|sorry|no|i mean),?\s+(\$?[\w.]+)\s+([a-z]+)\b")
});
// A retracted sentence: "Book Friday. Scratch that. Book Saturday." -> "Book Saturday."
static RETRACT_SENTENCE: Lazy<Regex> = Lazy::new(|| {
    re(r"(?i)(^|[.!?]\s+|\n)[^.!?\n]*[.!?]\s+(?:scratch that|never mind|forget that|delete that|strike that)[.!,]?\s*")
});
// "... at three thirty? Actually, no, four o'clock." -> the last place/time phrase is replaced.
static SENTENCE_RESTART: Lazy<Regex> = Lazy::new(|| {
    re(r"(?i)([^.!?\n]*)([.!?])\s+(?:actually,?\s+no|no,?\s+wait|no,?\s+actually|wait,?\s+no|sorry,?\s+no),?\s+([^.!?\n]+)([.!?]?)")
});
// Spoken list markers ("first, eggs, second, milk" / "number one, ... number two, ...").
static LIST_MARKER: Lazy<Regex> = Lazy::new(|| {
    re(r"(?i)(?:^|[\s,.;:])((?:and\s+)?(?:first(?:ly)?|second(?:ly)?|third(?:ly)?|fourth(?:ly)?|fifth(?:ly)?|finally|lastly|number\s+(?:one|two|three|four|five|[1-5])))\b,\s+")
});
static LONE_I: Lazy<Regex> = Lazy::new(|| re(r"\bi\b"));
// "[^.]" before the mark keeps "well... okay" lowercase.
static SENTENCE_START: Lazy<Regex> = Lazy::new(|| re(r"(^|[^.][.!?][ \t]+|\n[ \t]*)([a-z])"));

pub fn cleanup(text: &str, opts: &CleanupOpts) -> String {
    let mut s = text.trim().to_string();
    if s.is_empty() {
        return s;
    }

    if opts.commands {
        for (pattern, replacement) in COMMANDS.iter() {
            s = pattern.replace_all(&s, *replacement).into_owned();
        }
    }

    if opts.self_corrections {
        s = AMOUNT_RESTART
            .replace_all(&s, |c: &regex::Captures| {
                let unit = |u: &str| u.to_lowercase().trim_end_matches('s').to_string();
                let num = |w: &str| {
                    let l = w.to_lowercase();
                    word_kind(w) == WordKind::Number
                        || l.ends_with("teen")
                        || ["twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety", "hundred"]
                            .iter()
                            .any(|t| l.starts_with(t))
                };
                if num(&c[1]) && num(&c[3]) && unit(&c[2]) == unit(&c[4]) {
                    format!("{} {}", &c[3], &c[4])
                } else {
                    c[0].to_string()
                }
            })
            .into_owned();
        loop {
            let t = RETRACT_SENTENCE.replace_all(&s, "$1").into_owned();
            if t == s {
                break;
            }
            s = t;
        }
        s = restart_sentence(&s);
    }

    if opts.remove_fillers {
        s = FILLER_IN_COMMAS.replace_all(&s, "$1 $2").into_owned();
        s = FILLERS.replace_all(&s, "").into_owned();
        s = YOU_KNOW.replace_all(&s, "$1").into_owned();
        for _ in 0..3 {
            s = LEADING_DISCOURSE.replace_all(&s, "$1").into_owned();
        }
    }

    if opts.self_corrections {
        s = apply_self_corrections(&s);
    }

    if opts.remove_repeats {
        s = remove_repeats(&s);
    }

    if opts.fix_spacing || opts.commands || opts.remove_fillers {
        s = PUNCT_AFTER_NL.replace_all(&s, "\n").into_owned();
        s = SPACES_AROUND_NL.replace_all(&s, "\n").into_owned();
        s = PUNCT_BEFORE_NL.replace_all(&s, "\n").into_owned();
        s = PUNCT_RUN
            .replace_all(&s, |c: &regex::Captures| {
                let run = &c[0];
                if run == "..." {
                    "...".to_string()
                } else {
                    run.chars().filter(|ch| !ch.is_whitespace()).last().map(String::from).unwrap_or_default()
                }
            })
            .into_owned();
        s = s.trim_matches(|c: char| c == ' ' || c == '\t').to_string();
        s = LEADING_PUNCT.replace_all(&s, "$1").into_owned();
    }

    if opts.fix_spacing {
        s = SPACE_BEFORE_PUNCT.replace_all(&s, "$1").into_owned();
        s = NO_SPACE_AFTER_PUNCT.replace_all(&s, "$1 $2").into_owned();
        s = NO_SPACE_AFTER_PERIOD.replace_all(&s, ". $1").into_owned();
        s = MULTI_SPACE.replace_all(&s, " ").into_owned();
    }
    s = s.trim_matches(|c: char| c == ' ' || c == '\t').to_string();

    if opts.capitalize {
        s = LONE_I.replace_all(&s, "I").into_owned();
        // Twice: matches consume the char before the mark, so back-to-back short sentences need a second pass.
        for _ in 0..2 {
            s = SENTENCE_START
                .replace_all(&s, |c: &regex::Captures| format!("{}{}", &c[1], c[2].to_uppercase()))
                .into_owned();
        }
    }

    // Trim spaces but keep intentional trailing newlines from "new line" commands.
    let mut out = s.trim_matches(|c: char| c == ' ' || c == '\t').to_string();
    while out.starts_with('\n') {
        out.remove(0);
    }

    if !opts.dictionary.is_empty() {
        out = apply_dictionary(&out, &opts.dictionary);
    }

    let listed = if opts.fix_spacing { format_spoken_list(&out) } else { None };
    if let Some(l) = listed {
        return l;
    }

    if opts.ensure_final_punctuation {
        if let Some(last) = out.chars().last() {
            if last.is_alphanumeric() || last == '"' || last == ')' {
                out.push('.');
            }
        }
    }
    out
}

#[derive(PartialEq)]
enum WordKind {
    Number,
    Capitalized,
    Other,
}

fn word_kind(w: &str) -> WordKind {
    const NUMBER_WORDS: &[&str] = &[
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten", "eleven",
        "twelve", "noon", "midnight",
    ];
    let c = w.trim_matches(|ch: char| ".,;:!?'’".contains(ch));
    let Some(first) = c.chars().next() else { return WordKind::Other };
    if first.is_ascii_digit() || first == '$' || NUMBER_WORDS.contains(&c.to_lowercase().as_str()) {
        WordKind::Number
    } else if first.is_uppercase() {
        WordKind::Capitalized
    } else {
        WordKind::Other
    }
}

/// Keep only the corrected value when the speaker changes their mind mid-sentence.
pub fn apply_self_corrections(text: &str) -> String {
    let s = SELF_CORRECT_TIME_ZERO.replace_all(text, "$1$2").into_owned();
    let s = SELF_CORRECT_STRONG.replace_all(&s, "").into_owned();
    SELF_CORRECT_WEAK
        .replace_all(&s, |c: &regex::Captures| {
            let (x, y) = (word_kind(&c[1]), word_kind(&c[2]));
            if x != WordKind::Other && x == y {
                c[2].to_string()
            } else {
                c[0].to_string()
            }
        })
        .into_owned()
}

/// "Pick up the kids at three thirty? Actually, no, four o'clock." -> "Pick up the kids at four o'clock?"
/// Only when the first sentence has a place/time preposition in its last few words to hang the correction on.
fn restart_sentence(text: &str) -> String {
    SENTENCE_RESTART
        .replace_all(text, |c: &regex::Captures| {
            let before = &c[1];
            let lower = before.to_lowercase();
            let preps = [" at ", " on ", " in ", " to ", " for ", " by ", " from "];
            let cut = preps.iter().filter_map(|p| lower.rfind(p).map(|i| (i, p.len()))).max_by_key(|x| x.0);
            match cut {
                Some((i, len)) if before.len() - i <= 40 => {
                    let corr = c[3].trim().trim_end_matches(|ch: char| ",;".contains(ch));
                    format!("{}{}{}", &before[..i + len], corr, &c[2])
                }
                _ => c[0].to_string(),
            }
        })
        .into_owned()
}

/// "The list is first, eggs, second, milk, third, bread." -> "The list is:\n- Eggs\n- Milk\n- Bread".
/// Needs an opening marker (first / number one) AND a second one, so a lone "first" in prose is left alone.
pub fn format_spoken_list(text: &str) -> Option<String> {
    static PARAGRAPH: Lazy<Regex> = Lazy::new(|| re(r"\r?\n[ \t\r]*\n"));
    if let Some(m) = PARAGRAPH.find(text) {
        let (head, tail) = (&text[..m.start()], &text[m.end()..]);
        let (a, b) = (format_spoken_list(head), format_spoken_list(tail));
        return if a.is_none() && b.is_none() {
            None
        } else {
            Some(format!("{}{}{}", a.as_deref().unwrap_or(head), m.as_str(), b.as_deref().unwrap_or(tail)))
        };
    }
    // A marker after "a", "the"... is prose ("give me a second"), as in the Android and iOS ListFormat.
    const PROSE_BEFORE: &[&str] = &[
        "a", "an", "the", "this", "that", "these", "those", "each", "every", "another", "my", "your", "his", "her", "its",
        "our", "their", "which", "what", "no", "any", "some", "first", "second", "third", "last", "one",
    ];
    let marks: Vec<(usize, usize, String)> = LIST_MARKER
        .captures_iter(text)
        .filter(|c| {
            let start = c.get(1).expect("group").start();
            let prev = text[..start].split_whitespace().last().unwrap_or("").to_lowercase();
            !PROSE_BEFORE.contains(&prev.as_str())
        })
        .map(|c| {
            let m = c.get(1).expect("group");
            let whole_end = c.get(0).expect("match").end();
            (m.start(), whole_end, m.as_str().to_lowercase())
        })
        .collect();
    let opener = marks.iter().position(|m| {
        let w = m.2.trim_start_matches("and ").trim();
        w.starts_with("first") || w == "number one" || w == "number 1"
    })?;
    let rest = &marks[opener..];
    if rest.len() < 2 {
        return None;
    }
    let second = rest[1].2.trim_start_matches("and ").to_string();
    if !(second.starts_with("second") || second.ends_with("two") || second.ends_with('2') || second == "finally") {
        return None;
    }
    let intro = text[..rest[0].0].trim_end().trim_end_matches(|ch: char| ",.;:".contains(ch)).trim_end();
    let mut items = Vec::new();
    for (k, m) in rest.iter().enumerate() {
        let end = rest.get(k + 1).map(|n| n.0).unwrap_or(text.len());
        let raw = text[m.1..end].trim();
        let raw = raw.trim_end_matches(|ch: char| ",.;: ".contains(ch));
        let raw = raw.strip_suffix(" and").unwrap_or(raw).trim_end_matches(',').trim();
        if raw.is_empty() {
            return None;
        }
        let mut ch = raw.chars();
        let first = ch.next().expect("non-empty").to_uppercase().collect::<String>();
        items.push(format!("{}{}", first, ch.as_str()));
    }
    let head = if intro.is_empty() { String::new() } else { format!("{intro}:\n") };
    Some(format!("{}- {}", head, items.join("\n- ")))
}

/// Drop stutter repeats of a word ("I I think we, we should"), keeping grammatical doubles like "that that".
pub fn remove_repeats(text: &str) -> String {
    const KEEP_DOUBLED: &[&str] = &["that", "had", "bye", "ha", "no", "yeah"];
    let core = |t: &str| t.trim_end_matches(|ch: char| ",.;:!?".contains(ch)).to_lowercase();
    text.split('\n')
        .map(|line| {
            let mut toks: Vec<&str> = Vec::new();
            for t in line.split_whitespace() {
                if let Some(prev) = toks.last().copied() {
                    let (pc, cc) = (core(prev), core(t));
                    let prev_ends_sentence =
                        prev.trim_end_matches(',').ends_with(|ch: char| ".!?".contains(ch));
                    if pc == cc
                        && !cc.is_empty()
                        && cc.chars().all(|ch| ch.is_alphabetic() || ch == '\'' || ch == '’')
                        && !KEEP_DOUBLED.contains(&cc.as_str())
                        && !prev_ends_sentence
                    {
                        *toks.last_mut().expect("non-empty") = t;
                        continue;
                    }
                }
                toks.push(t);
            }
            toks.join(" ")
        })
        .collect::<Vec<_>>()
        .join("\n")
}

fn phrase_regex(phrase: &str) -> Option<Regex> {
    let p = phrase.trim();
    if p.is_empty() {
        return None;
    }
    let starts_word = re(r"^\w").is_match(p);
    let ends_word = re(r"\w$").is_match(p);
    let body = regex::escape(p).replace("\\ ", r"\s+").replace(' ', r"\s+");
    let pat = format!(
        "(?i){}{}{}",
        if starts_word { r"\b" } else { "" },
        body,
        if ends_word { r"\b" } else { "" }
    );
    Regex::new(&pat).ok()
}

/// Personal dictionary: replace each `spoken` phrase (or `written` itself, for casing) with `written`.
pub fn apply_dictionary(text: &str, entries: &[DictionaryEntry]) -> String {
    let mut s = text.to_string();
    for e in entries {
        let written = e.written.trim();
        if written.is_empty() {
            continue;
        }
        let key = if e.spoken.trim().is_empty() { written } else { e.spoken.trim() };
        if let Some(r) = phrase_regex(key) {
            s = r.replace_all(&s, regex::NoExpand(written)).into_owned();
        }
    }
    s
}

/// Whisper initial prompt built from the dictionary's written forms ("Vocabulary: GitHub, Omar.").
pub fn dictionary_prompt(entries: &[DictionaryEntry]) -> Option<String> {
    let terms: Vec<&str> = entries.iter().map(|e| e.written.trim()).filter(|w| !w.is_empty()).collect();
    if terms.is_empty() {
        None
    } else {
        Some(format!("Vocabulary: {}.", terms.join(", ")))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn c(s: &str) -> String {
        cleanup(s, &CleanupOpts::default())
    }

    #[test]
    fn removes_fillers_and_capitalizes() {
        assert_eq!(c("um so i think uh we should ship it"), "So I think we should ship it.");
        assert_eq!(c("Um, hello there."), "Hello there.");
        assert_eq!(c("well, erm, maybe"), "Well, maybe.");
    }

    #[test]
    fn spoken_commands() {
        assert_eq!(c("hello comma world period how are you question mark"), "Hello, world. How are you?");
        assert_eq!(c("first line new line second line"), "First line\nSecond line.");
        assert_eq!(c("intro new paragraph body"), "Intro\n\nBody.");
    }

    #[test]
    fn absorbs_whisper_punctuation_around_commands() {
        assert_eq!(c("Hello, comma, world. Period."), "Hello, world.");
        assert_eq!(c("Buy milk. New line. Buy bread."), "Buy milk.\nBuy bread.");
    }

    #[test]
    fn spacing() {
        assert_eq!(c("hello , world ."), "Hello, world.");
        assert_eq!(c("wait,what"), "Wait, what.");
        assert_eq!(c("version 3.5 is out"), "Version 3.5 is out.");
        assert_eq!(c("a  lot   of    space"), "A lot of space.");
    }

    #[test]
    fn keeps_words_that_contain_fillers() {
        assert_eq!(c("the summer umbrella was humming"), "The summer umbrella was humming.");
        assert_eq!(c("her error"), "Her error.");
    }

    #[test]
    fn you_know_at_clause_edge() {
        assert_eq!(c("you know, it works"), "It works.");
        assert_eq!(c("I know you know what I mean"), "I know you know what I mean.");
    }

    #[test]
    fn empty_and_none() {
        assert_eq!(c("   "), "");
        assert_eq!(cleanup("  um hello  ", &CleanupOpts::none()), "um hello");
    }

    #[test]
    fn short_sentences() {
        assert_eq!(c("so period"), "So.");
        assert_eq!(c("hi. how are you? fine! ok"), "Hi. How are you? Fine! Ok.");
        assert_eq!(c("i'm here and i'll go"), "I'm here and I'll go.");
        assert_eq!(c("hello new line"), "Hello\n");
    }

    // ---- parity P0: repeats, self-corrections, dictionary ----

    #[test]
    fn stutter_repeats_removed() {
        assert_eq!(c("I I think we we should go"), "I think we should go.");
        assert_eq!(c("we, we should"), "We should.");
        assert_eq!(c("I know that that is true"), "I know that that is true.");
        assert_eq!(c("It is. It is fine"), "It is. It is fine.");
    }

    #[test]
    fn self_corrections_keep_the_final_value() {
        assert_eq!(c("Let's meet at 3, no, 4pm."), "Let's meet at 4pm.");
        assert_eq!(c("Invite John, sorry, I mean Jane."), "Invite Jane.");
        assert_eq!(c("invite John, I mean Jane"), "Invite Jane.");
        assert_eq!(c("Monday, no, Tuesday works"), "Tuesday works.");
        assert_eq!(c("call me at five, wait, six"), "Call me at six.");
    }

    #[test]
    fn self_correction_heard_as_zero() {
        // base.en on the kokoro/espeak P0-8 clips ("meet at three, no, four pm") writes "3.0.4 pm".
        assert_eq!(c("Meet at 3.0.4 pm."), "Meet at 4pm.");
        assert_eq!(c("Meet at 3.0.4pm."), "Meet at 4pm.");
        assert_eq!(c("Version 3.0.4 is out."), "Version 3.0.4 is out.");
        assert_eq!(c("It costs 3.04 dollars."), "It costs 3.04 dollars.");
    }

    #[test]
    fn self_corrections_leave_ordinary_no_alone() {
        assert_eq!(c("I said, no, thanks"), "I said, no, thanks.");
        assert_eq!(c("Yes, no, maybe"), "Yes, no, maybe.");
    }

    #[test]
    fn fillers_repeats_and_corrections_together() {
        assert_eq!(c("um so I I need uh 3, no, 4 tickets"), "So I need 4 tickets.");
    }

    #[test]
    fn dictionary_replaces_and_fixes_case() {
        let opts = CleanupOpts {
            dictionary: vec![
                DictionaryEntry { spoken: String::new(), written: "GitHub".into() },
                DictionaryEntry { spoken: "hum ing".into(), written: "Humming".into() },
                DictionaryEntry { spoken: "hum ops".into(), written: "Hum Ops".into() },
            ],
            ..CleanupOpts::default()
        };
        assert_eq!(
            cleanup("push it to github and tell hum ops about hum ing", &opts),
            "Push it to GitHub and tell Hum Ops about Humming."
        );
        assert_eq!(cleanup("githubber stays", &opts), "Githubber stays.");
    }

    #[test]
    fn dictionary_prompt_lists_terms() {
        let d = vec![DictionaryEntry { spoken: "".into(), written: "Omar".into() }];
        assert_eq!(dictionary_prompt(&d).as_deref(), Some("Vocabulary: Omar."));
        assert_eq!(dictionary_prompt(&[]), None);
    }

    #[test]
    fn replacements_do_not_split_combining_marks() {
        for (input, spoken, expected) in [
            ("রাম রামি", "রাম", "X রামি"),
            ("cafe cafe\u{0301}", "cafe", "X cafe\u{0301}"),
            ("cafe\u{0301} cafe\u{0301}s", "cafe\u{0301}", "X cafe\u{0301}s"),
            ("a\u{0301}cat cat", "cat", "a\u{0301}cat X"),
        ] {
            let pairs = [DictionaryEntry { spoken: spoken.into(), written: "X".into() }];
            assert_eq!(apply_dictionary(input, &pairs), expected);
        }
    }

    /// The P0 acceptance lines from the parity spec, verbatim (what the rule layer must do offline).
    #[test]
    fn p0_acceptance_rules() {
        assert_eq!(c("um, so, uh, I think we should ship it"), "I think we should ship it."); // P0-6
        assert_eq!(c("I I think we we should go"), "I think we should go."); // P0-7
        assert_eq!(c("um, meet at 3, no, 4pm"), "Meet at 4pm."); // P0-8
        assert_eq!(c("send it to John, sorry, I mean Jane"), "Send it to Jane."); // P0-9
        assert_eq!(c("what time is it question mark"), "What time is it?"); // P0-10
        assert_eq!(c("see you soon"), "See you soon."); // P0-3 text shape
        let opts = CleanupOpts {
            dictionary: vec![
                DictionaryEntry { spoken: String::new(), written: "Humming".into() },
                DictionaryEntry { spoken: String::new(), written: "Okonkwo".into() },
            ],
            ..CleanupOpts::default()
        };
        assert_eq!(cleanup("open humming and ping okonkwo", &opts), "Open Humming and ping Okonkwo."); // P0-14
    }

    #[test]
    fn discourse_opener_needs_a_comma() {
        assert_eq!(c("so, we ship today. like, maybe tomorrow"), "We ship today. Maybe tomorrow.");
        assert_eq!(c("well, maybe"), "Well, maybe."); // "well," can be the answer itself
        assert_eq!(c("so we ship today"), "So we ship today.");
        assert_eq!(c("I like, really like it"), "I like, really like it.");
    }

    #[test]
    fn ellipsis_kept() {
        assert_eq!(c("well... okay"), "Well... okay.");
    }

    /// CLEAN_SET.md: 20 rambly utterances. Rule-only Clean mode must get at least 16 exactly right.
    #[test]
    fn clean_set_rule_only() {
        let set: &[(&str, &str)] = &[
        ("Um, so, I think we should meet at 5, no wait, 6.", "I think we should meet at 6."),
        ("Can you uh pick up the kids at three thirty? Actually, no, four o'clock.", "Can you pick up the kids at four o'clock?"),
        ("Let's book the table for Friday. Scratch that. Let's book it for Saturday.", "Let's book it for Saturday."),
        ("I need to buy eggs, milk, I mean oat milk, and bread.", "I need to buy eggs, oat milk, and bread."),
        ("So, basically, the meeting is moved to Tuesday.", "The meeting is moved to Tuesday."),
        ("The shopping list is first, eggs, second, milk, third, bread.", "The shopping list is:\n- Eggs\n- Milk\n- Bread"),
        ("I I I think that's that's a great idea.", "I think that's a great idea."),
        ("Hmm, tell John the report is, you know, almost done.", "Tell John the report is almost done."),
        ("Send it to Sarah, sorry, I mean Rachel.", "Send it to Rachel."),
        ("We have three things to do. Number one, call the bank. Number two, pay rent. Number three, book flights.", "We have three things to do:\n- Call the bank\n- Pay rent\n- Book flights"),
        ("Okay, so, the flight lands at 7.30 pm.", "The flight lands at 7.30 pm."),
        ("Meet me at the cafe on Main Street, or rather the one on Oak Street.", "Meet me at the cafe on Oak Street."),
        ("Uh, the budget is $500, no, $600.", "The budget is $600."),
        ("Well, I was thinking we could, um, go hiking this weekend.", "I was thinking we could go hiking this weekend."),
        ("Remind me to call mom. Never mind. Remind me to text mom.", "Remind me to text mom."),
        ("Please bring, like, snacks and drinks.", "Please bring snacks and drinks."),
        ("The deadline is Monday, wait, Tuesday, at noon.", "The deadline is Tuesday, at noon."),
        ("Erm, can we push the call to 3 pm? Thanks.", "Can we push the call to 3 pm? Thanks."),
        ("For the trip we need first, passports, second, chargers, and finally, sunscreen.", "For the trip we need:\n- Passports\n- Chargers\n- Sunscreen"),
        ("Tell him I'll be there in ten minutes, uh, fifteen minutes.", "Tell him I'll be there in fifteen minutes."),
        ];
        let mut exact = 0;
        for (i, (inp, want)) in set.iter().enumerate() {
            let got = c(inp);
            if got == *want {
                exact += 1;
            } else {
                println!("CLEAN_SET miss {}: {:?} -> {:?} (want {:?})", i + 1, inp, got, want);
            }
        }
        println!("CLEAN_SET exact {}/{}", exact, set.len());
        assert!(exact >= 16, "clean set exact {}/20", exact);
    }

    #[test]
    fn a_second_in_prose_is_not_a_list() {
        // "a second" is a moment, not a list marker (review fix, same rule as Android's and iOS's ListFormat).
        assert_eq!(format_spoken_list("First, give me a second, then we can go."), None);
        assert_eq!(
            format_spoken_list("The list is first, eggs, second, milk.").as_deref(),
            Some("The list is:\n- Eggs\n- Milk")
        );
    }

    #[test]
    fn list_needs_two_markers_with_commas() {
        assert_eq!(c("first line new line second line"), "First line\nSecond line.");
        assert_eq!(c("first, we eat."), "First, we eat.");
    }

    #[test]
    fn paragraphs_end_spoken_lists_before_later_markers() {
        assert_eq!(
            format_spoken_list("First, eggs, second, bread.\n\nThanks.\n\nFirst, milk, second, tea.").as_deref(),
            Some("- Eggs\n- Bread\n\nThanks.\n\n- Milk\n- Tea")
        );
        assert_eq!(format_spoken_list("First, eggs.\n\nSecond, milk."), None);
    }
}
