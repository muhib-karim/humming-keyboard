//! Dictation cleanup: a line-for-line port of the Android keyboard's `TextCleanup.kt`, so a sentence dictated on
//! the phone and on the desktop comes out the same. Order: drop non-speech/hallucinations -> fillers -> spoken
//! punctuation and "delete last word" -> leading discourse markers -> self-corrections ("3, no, 4pm" -> "4pm") -> repeated words ->
//! dictionary casing -> compact times -> capitals and final punctuation.
//!
//! Uses `fancy_regex` because the Kotlin patterns need look-around and back-references.

use fancy_regex::{Captures, Regex};
use once_cell::sync::Lazy;

const W: &str = r"[\p{L}\p{N}_']";
const UNIT: &str = r"(?:[ap]\.?\s?m\.?|o'?clock|%|percent|dollars?|bucks|minutes?|mins?|hours?|days?|weeks?|k|kg|km|miles?)";

fn re(p: &str) -> Regex {
    Regex::new(p).unwrap_or_else(|e| panic!("bad cleanup pattern {p}: {e}"))
}

static NON_SPEECH: Lazy<Regex> = Lazy::new(|| {
    re(r"(?i)\[[^\]]*\]|\((?:[^)]*(?:music|applause|silence|blank|inaudible|laugh)[^)]*)\)|\*[^*]+\*")
});
static FILLER: Lazy<Regex> = Lazy::new(|| re(&format!(r"(?i)(?<!{W})(?:um+|uh+|erm+|er|hmm+)(?!{W})[,.]?\s*")));
static YOU_KNOW: Lazy<Regex> = Lazy::new(|| re(r"(?i),?\s*\byou know\b,\s*"));
static LEADING: Lazy<Regex> = Lazy::new(|| re(r"(?i)^(?:(?:so|well|okay|ok|like|right)\s*,\s*)+"));
static MARKER: Lazy<Regex> = Lazy::new(|| {
    re(r"(?i),\s*(no wait|wait,? no|sorry,? i mean|i mean|sorry|no|scratch that|make that|or rather)\s*,?\s+")
});
static NO_FUSED: Lazy<Regex> =
    Lazy::new(|| re(r"(?i)(?<![\d.])(\d{1,2})\.0\.(\d{1,2}(?::\d\d)?\s*(?:[ap]\.?\s?m\.?|o'?clock))(?!\w)"));
static NO_MISHEARD: Lazy<Regex> = Lazy::new(|| {
    re(&format!(r"(?i)(?<![\w.])(\d+(?:[.:]\d+)?)\s*[,.]?\s*(?:no|oh|know|nope)\s*[,.]?\s+(\d+(?:[.:]\d+)?\s*{UNIT})(?!\w)"))
});
static SPOKEN: Lazy<Vec<(Regex, &'static str)>> = Lazy::new(|| {
    vec![
        (re(r"(?i)\s*\bquestion mark\b"), "?"),
        (re(r"(?i)\s*\bexclamation (?:mark|point)\b"), "!"),
        (re(r"(?i)\s*\bfull stop\b"), "."),
        (re(r"(?i)\s*\bnew paragraph\b\s*"), "\n\n"),
        (re(r"(?i)\s*\bnew line\b\s*"), "\n"),
        (re(r"(?i)\s*\bcomma\b"), ","),
        // 1.7: the same additions as Android and iOS. "period" and "dash" stay out: too often meant as words.
        (re(r"(?i)\s*\bsemicolon\b"), ";"),
        (re(r"(?i)\s*\bcolon\b"), ":"),
        // Openers and closers also take the commas a punctuating engine puts around them, and keep a preceding line break.
        (re(r"(?i)[ \t]*\b(?:open|begin) quotes?\b[,.]?\s*"), " \""),
        (re(r"(?i)[ \t]*,?[ \t]*\b(?:(?:close|end) quotes?|unquote)\b"), "\""),
        (re(r"(?i)[ \t]*,?[ \t]*\bopen (?:bracket|paren|parenthesis)\b[,.]?\s*"), " ("),
        (re(r"(?i)[ \t]*,?[ \t]*\bclose (?:bracket|paren|parenthesis)\b"), ")"),
        (re(r"(?i)\s*\b(?:ellipsis|dot dot dot)\b"), "..."),
    ]
});
// "delete last word" / "scratch the last word" removes the word said before it (or just itself at the start).
static DELETE_WORD: Lazy<Regex> =
    Lazy::new(|| re(r"(?i)[\p{L}\p{N}'\x{2019}]+[,.!?]?\s+(?:delete|scratch) (?:the )?last word\b[,.!?]?"));
static DELETE_WORD_START: Lazy<Regex> =
    Lazy::new(|| re(r"(?i)^\s*(?:delete|scratch) (?:the )?last word\b[,.!?]?\s*"));
// Accuracy mode (strict): hedge words that are only filler when set off by commas, extra hesitation sounds, a spoken
// "period" at the very end, and doubled punctuation. Same rules as Android and iOS.
static STRICT_FILLER: Lazy<Regex> = Lazy::new(|| re(&format!(r"(?i)(?<!{W})(?:ah+|eh+|mm+|mhm|hm+)(?!{W})[,.]?\s*")));
static STRICT_HEDGE: Lazy<Regex> = Lazy::new(|| {
    re(r"(?i),\s*(?:like|basically|actually|literally|honestly|sort of|kind of|i mean|you see|anyway)\s*,\s*")
});
static STRICT_LEADING: Lazy<Regex> = Lazy::new(|| {
    re(r"(?i)(^|[.?!]\s+)(?:(?:basically|actually|honestly|i mean|you know|anyway|anyways|so yeah)\s*,\s*)+")
});
static STRICT_TRAILING: Lazy<Regex> = Lazy::new(|| re(r"(?i),\s*(?:you know|i guess|or something)\s*(?=[.?!]|$)"));
static STRICT_PERIOD: Lazy<Regex> = Lazy::new(|| re(r"(?i)\s*\bperiod\s*$"));
static DOUBLED: Lazy<Regex> = Lazy::new(|| re(r"([,;:])\s*([,.?!;:])"));
static SENT_END: Lazy<Regex> = Lazy::new(|| re(r"[.?!;\n]"));
static SPACES: Lazy<Regex> = Lazy::new(|| re(r"[ \t]+"));
static WS: Lazy<Regex> = Lazy::new(|| re(r"\s+"));
static NON_WORD: Lazy<Regex> = Lazy::new(|| re(r"[^\p{L}\p{N}_']"));
static NORM_STRIP: Lazy<Regex> = Lazy::new(|| re(r"[^\p{L}\p{N}\s]"));
static DOUBLE_PAIR: Lazy<Regex> = Lazy::new(|| re(r"(?i)\b(\p{L}+ \p{L}+) \1\b"));
static TIME_AMPM: Lazy<Regex> =
    Lazy::new(|| re(r"(?<![\p{L}\p{N}])(\d{1,2}(?::\d{2})?)\s?([AaPp])\.?\s?[Mm](?=[\s,.;!?]|$)"));
static NEWLINE_INDENT: Lazy<Regex> = Lazy::new(|| re(r"\n[ \t]+"));
static SPACE_BEFORE_PUNCT: Lazy<Regex> = Lazy::new(|| re(r"\s+([,.?!;:])"));
static SPACE_AFTER_PUNCT: Lazy<Regex> = Lazy::new(|| re(r"([,;:])(?=[^\s\d])"));
static LONE_I: Lazy<Regex> = Lazy::new(|| re(&format!(r"(?<!{W})i(?!{W})")));
// A sentence may open with a quote or bracket ("\"hello" -> "\"Hello").
static SENTENCE_START: Lazy<Regex> = Lazy::new(|| re(r#"(^|[.?!]\s+|\n)(["(]?)(\p{Ll})"#));
static SENTENCE_SPLIT: Lazy<Regex> = Lazy::new(|| re(r"(?<=[.?!])\s+"));

const KEEP_REPEAT: &[&str] =
    &["had", "that", "very", "really", "so", "no", "bye", "ha", "haha", "go", "too", "more", "knock", "yeah", "yes"];
const QUESTION_START: &[&str] = &[
    "what", "why", "how", "when", "where", "who", "which", "is", "are", "do", "does", "did", "can", "could", "would",
    "will", "should", "shall", "may", "am", "was", "were", "have", "has",
];
/// Classic speech-model outputs on silence or noise; a transcript that is only one of these inserts nothing.
const HALLUCINATIONS: &[&str] = &[
    "thank you for watching",
    "thanks for watching",
    "thank you so much for watching",
    "please subscribe",
    "like and subscribe",
    "you",
];

/// Which rule groups run. `Opts::default()` is everything on, which is what Android runs.
#[derive(Debug, Clone, Copy)]
pub struct Opts {
    pub remove_fillers: bool,
    pub voice_commands: bool,
    pub self_corrections: bool,
    /// Accuracy mode: the strict rules (hedge fillers set off by commas, a final spoken "period").
    pub strict: bool,
}

impl Default for Opts {
    fn default() -> Self {
        Self { remove_fillers: true, voice_commands: true, self_corrections: true, strict: false }
    }
}

fn replace_all(r: &Regex, text: &str, rep: &str) -> String {
    r.replace_all(text, rep).into_owned()
}

pub fn apply(raw: &str, dictionary: &[String], o: Opts) -> String {
    let mut t = replace_all(&NON_SPEECH, raw, "");
    if is_hallucination(&t) {
        return String::new();
    }
    let strict = o.strict && o.remove_fillers;
    if o.remove_fillers {
        t = replace_all(&FILLER, &t, "");
        if strict {
            t = replace_all(&STRICT_FILLER, &t, "");
        }
        t = replace_all(&YOU_KNOW, &t, " ");
    }
    if o.voice_commands {
        for (r, rep) in SPOKEN.iter() {
            t = replace_all(r, &t, rep);
        }
        t = delete_last_word(&t);
    }
    if strict {
        t = replace_all(&STRICT_PERIOD, &t, ".");
        t = replace_all(&STRICT_TRAILING, &t, "");
        t = replace_all(&STRICT_HEDGE, &t, " ");
        t = replace_all(&STRICT_LEADING, &t, "$1");
    }
    t = collapse_spaces(&t);
    if o.remove_fillers {
        t = replace_all(&LEADING, &t, "");
    }
    if o.self_corrections {
        t = self_correct(&t);
    }
    t = remove_repeats(&t);
    t = apply_dictionary(&t, dictionary);
    t = compact_times(&t);
    if strict {
        t = replace_all(&DOUBLED, &t, "$2");
    }
    finish(&t)
}

/// "4 pm", "4 p.m.", "10:30 AM" -> "4pm", "10:30am" (the way people type times).
pub fn compact_times(input: &str) -> String {
    TIME_AMPM
        .replace_all(input, |c: &Captures| format!("{}{}m", &c[1], c[2].to_lowercase()))
        .into_owned()
}

pub fn is_hallucination(text: &str) -> bool {
    let n = normalize(text);
    HALLUCINATIONS.contains(&n.as_str()) || n.starts_with("subtitles by") || n.starts_with("transcribed by")
}

fn normalize(s: &str) -> String {
    let lower = s.to_lowercase();
    let stripped = replace_all(&NORM_STRIP, &lower, "");
    replace_all(&WS, stripped.trim(), " ")
}

fn bare(token: &str) -> String {
    replace_all(&NON_WORD, token, "").to_lowercase()
}

fn collapse_spaces(s: &str) -> String {
    replace_all(&SPACES, s, " ").trim().to_string()
}

fn first_is_upper(s: &str) -> bool {
    s.chars().next().map(|c| c.is_uppercase()).unwrap_or(false)
}

/// "meet at 3, no, 4pm" -> "meet at 4pm"; "to John, sorry, I mean Jane" -> "to Jane".
pub fn self_correct(input: &str) -> String {
    let fused = replace_all(&NO_FUSED, input, "${1}, no, ${2}");
    let mut text = replace_all(&NO_MISHEARD, &fused, "${1}, no, ${2}");
    for _ in 0..5 {
        let Ok(Some(caps)) = MARKER.captures(&text) else { return text };
        let whole = caps.get(0).expect("match");
        let marker = caps[1].to_lowercase();
        let pre = &text[..whole.start()];
        let boundary = [pre.rfind(". "), pre.rfind("? "), pre.rfind("! "), pre.rfind('\n')].into_iter().flatten().max();
        let head = match boundary {
            None => "",
            Some(b) if pre.as_bytes()[b] == b'\n' => &pre[..b + 1],
            Some(b) => &pre[..b + 2],
        };
        let seg = &pre[head.len()..];
        let post = &text[whole.end()..];
        let end = SENT_END.find(post).ok().flatten().map(|m| m.start());
        let (y, tail) = match end {
            Some(e) => (&post[..e], &post[e..]),
            None => (post, ""),
        };
        let bw: Vec<&str> = seg.split_whitespace().collect();
        let yw: Vec<&str> = y.split_whitespace().collect();
        if bw.is_empty() || yw.is_empty() {
            return text;
        }
        let y0 = bare(yw[0]);
        let mut cut = bw.iter().rposition(|w| bare(w) == y0);
        if cut.is_none() && yw[0].chars().any(|c| c.is_ascii_digit()) {
            cut = bw.iter().rposition(|w| w.chars().any(|c| c.is_ascii_digit()));
        }
        if cut.is_none() && first_is_upper(yw[0]) {
            cut = (1..bw.len()).rev().find(|&i| first_is_upper(bw[i]));
        }
        let cut = match cut {
            Some(c) => c,
            None => {
                // A bare "no" with no clear slot to replace is probably a real "no": leave the sentence alone.
                if marker == "no" {
                    return text;
                }
                let n = if yw.len() == 1 { 1 } else { yw.len().min(bw.len() - 1) };
                if bw.len() > 1 {
                    bw.len().saturating_sub(n)
                } else {
                    0
                }
            }
        };
        let mut words: Vec<&str> = bw[..cut].to_vec();
        words.extend(yw.iter());
        text = format!("{}{}{}", head, words.join(" "), tail);
    }
    text
}

fn is_word_char(c: char) -> bool {
    c.is_alphanumeric() || c == '\''
}

/// "I I think we we should go" -> "I think we should go"; "we should we should go" -> "we should go".
pub fn remove_repeats(input: &str) -> String {
    let mut out: Vec<String> = Vec::new();
    for tok in input.split(' ') {
        if let Some(prev) = out.last() {
            let a = bare(prev);
            let b = bare(tok);
            let prev_ends_clause = prev.chars().last().map(|c| ",.?!;:".contains(c)).unwrap_or(false);
            if !a.is_empty() && a == b && !KEEP_REPEAT.contains(&a.as_str()) && !prev_ends_clause {
                let trailing = match tok.char_indices().filter(|(_, c)| is_word_char(*c)).last() {
                    Some((i, c)) => &tok[i + c.len_utf8()..],
                    None => tok,
                };
                let kept = prev.trim_end_matches(|c: char| !is_word_char(c)).to_string();
                let last = out.len() - 1;
                out[last] = kept + trailing;
                continue;
            }
        }
        out.push(tok.to_string());
    }
    replace_all(&DOUBLE_PAIR, &out.join(" "), "${1}")
}

/// Dictionary terms win over whatever casing the engine produced ("okonkwo" -> "Okonkwo").
pub fn apply_dictionary(input: &str, terms: &[String]) -> String {
    let mut t = input.to_string();
    for raw in terms {
        let term = raw.trim();
        if term.is_empty() {
            continue;
        }
        let body = term.split_whitespace().map(fancy_regex::escape).collect::<Vec<_>>().join(r"\s+");
        if let Ok(r) = Regex::new(&format!(r"(?i)(?<!{W}){body}(?!{W})")) {
            t = r.replace_all(&t, |_: &Captures| term.to_string()).into_owned();
        }
    }
    t
}

/// "I love cats delete last word dogs" -> "I love dogs"; repeated commands remove one word each.
pub fn delete_last_word(input: &str) -> String {
    let mut t = input.to_string();
    for _ in 0..5 {
        let next = replace_all(&DELETE_WORD_START, &replace_all(&DELETE_WORD, &t, ""), "");
        if next == t {
            break;
        }
        t = next;
    }
    t
}

/// Spacing around punctuation, standalone "i", sentence capitals, and a final mark when one is missing.
pub fn finish(input: &str) -> String {
    let mut t = collapse_spaces(input);
    t = replace_all(&NEWLINE_INDENT, &t, "\n");
    t = replace_all(&SPACE_BEFORE_PUNCT, &t, "${1}");
    t = replace_all(&SPACE_AFTER_PUNCT, &t, "${1} ");
    t = replace_all(&LONE_I, &t, "I");
    t = SENTENCE_START
        .replace_all(&t, |c: &Captures| format!("{}{}{}", &c[1], &c[2], c[3].to_uppercase()))
        .into_owned();
    if let Some(last) = t.chars().last() {
        if last.is_alphanumeric() || last == '\'' || last == '"' {
            let start = SENTENCE_SPLIT.find_iter(&t).filter_map(|m| m.ok()).map(|m| m.end()).last().unwrap_or(0);
            let last_sentence = t[start..].to_string();
            let first = last_sentence.split(' ').next().map(bare).unwrap_or_default();
            t.push(if QUESTION_START.contains(&first.as_str()) { '?' } else { '.' });
        }
    }
    t
}

#[cfg(test)]
mod tests {
    use super::*;

    fn clean(s: &str) -> String {
        apply(s, &[], Opts::default())
    }

    #[test]
    fn fillers_and_capitals() {
        assert_eq!(clean("um so i think we should go"), "So I think we should go.");
    }

    #[test]
    fn self_correction_with_time() {
        assert_eq!(clean("let's meet at 3, no, 4 pm"), "Let's meet at 4pm.");
    }

    #[test]
    fn self_correction_names() {
        assert_eq!(clean("send it to John, sorry, I mean Jane"), "Send it to Jane.");
    }

    #[test]
    fn real_no_is_kept() {
        assert_eq!(clean("I said no, thanks"), "I said no, thanks.");
    }

    #[test]
    fn repeats() {
        assert_eq!(remove_repeats("I I think we we should go"), "I think we should go");
        assert_eq!(remove_repeats("we should we should go"), "we should go");
        assert_eq!(remove_repeats("that that is fine"), "that that is fine");
    }

    #[test]
    fn spoken_punctuation_and_questions() {
        assert_eq!(clean("is it ready question mark new line yes"), "Is it ready?\nYes.");
        assert_eq!(clean("what time is it"), "What time is it?");
    }

    #[test]
    fn hallucinations_insert_nothing() {
        assert_eq!(clean("Thank you for watching."), "");
        assert_eq!(clean("[MUSIC]"), "");
    }

    #[test]
    fn dictionary_casing() {
        assert_eq!(apply("ask okonkwo about github", &["Okonkwo".into(), "GitHub".into()], Opts::default()), "Ask Okonkwo about GitHub.");
    }

    fn strict(s: &str) -> String {
        apply(s, &[], Opts { strict: true, ..Opts::default() })
    }

    #[test]
    fn accuracy_mode_removes_hedge_fillers() {
        assert_eq!(
            strict("So, um, I was, like, thinking we could, you know, meet at 3, no, 4pm"),
            "I was thinking we could meet at 4pm."
        );
        assert_eq!(strict("Basically, the build is, honestly, fine"), "The build is fine.");
        assert_eq!(strict("We ship on Friday, you know."), "We ship on Friday.");
        assert_eq!(strict("ah, mm, let's go"), "Let's go.");
        assert_eq!(strict("send the report period"), "Send the report.");
    }

    #[test]
    fn accuracy_mode_keeps_meaningful_words() {
        assert_eq!(strict("I like this one"), "I like this one.");
        assert_eq!(strict("it looks like rain"), "It looks like rain.");
        assert_eq!(strict("it is actually done"), "It is actually done.");
        assert_eq!(strict("the period ends in May"), "The period ends in May.");
        assert_eq!(clean("it was, like, huge"), "It was, like, huge.");
        assert_eq!(strict("it was, like, huge"), "It was huge.");
    }

    #[test]
    fn verbatim_switches() {
        let o = Opts { remove_fillers: false, voice_commands: false, self_corrections: false, strict: false };
        assert_eq!(apply("um new line", &[], o), "Um new line.");
    }

    // 1.7: the same spoken punctuation and "delete last word" cases as Android's TextCleanupTest.kt.
    #[test]
    fn spoken_colon_semicolon_quotes_brackets_ellipsis() {
        assert_eq!(clean("note colon buy milk"), "Note: buy milk.");
        assert_eq!(clean("first semicolon second"), "First; second.");
        assert_eq!(clean("he said open quote hi close quote"), "He said \"hi\".");
        assert_eq!(clean("open quote hello end quote"), "\"Hello\".");
        assert_eq!(clean("call me open bracket maybe close bracket tomorrow"), "Call me (maybe) tomorrow.");
        assert_eq!(clean("wait dot dot dot"), "Wait...");
        assert_eq!(clean("wait ellipsis"), "Wait...");
        assert_eq!(clean("the colonel quotes semicolons"), "The colonel quotes semicolons.");
    }

    #[test]
    fn delete_last_word() {
        assert_eq!(clean("I love cats delete last word dogs"), "I love dogs.");
        assert_eq!(clean("I love big cats delete last word delete last word dogs"), "I love dogs.");
        assert_eq!(clean("see you soon. Scratch the last word."), "See you.");
        assert_eq!(clean("delete last word"), "");
        assert_eq!(clean("delete last word hello"), "Hello.");
        let o = Opts { remove_fillers: false, voice_commands: false, self_corrections: false, strict: false };
        assert_eq!(apply("cats delete last word", &[], o), "Cats delete last word.");
    }

    #[test]
    fn openers_keep_line_breaks_and_absorb_engine_commas() {
        assert_eq!(clean("first line new line open quote hello close quote"), "First line\n\"Hello\".");
        assert_eq!(clean("He said, open quote, hi, close quote."), "He said, \"hi\".");
        assert_eq!(clean("Call me, open bracket, maybe, close bracket, tomorrow."), "Call me (maybe), tomorrow.");
        assert_eq!(clean("can you remove the last word of the title"), "Can you remove the last word of the title?");
    }
}
