//! Optional AI polish, per-app tone styling, Command Mode selection rewriting, and meeting synthesis.
//! Supports 100% offline local Ollama (default localhost endpoint, no API key required) plus BYOK OpenAI-compatible
//! (OpenAI, Groq, LM Studio, llama.cpp), Anthropic, and Google Gemini. Off by default; never sends audio.
//! Remote endpoints must use HTTPS; only loopback hosts (127.0.0.1, localhost, [::1]) may use plain HTTP.

use crate::settings::{Settings, ToneRule};
use std::time::Duration;

const TIMEOUT_SECS: u64 = 25;
const MAX_CHARS: usize = 24_000;

#[derive(Debug, Clone, PartialEq)]
pub struct LlmConfig {
    pub provider: String,
    pub endpoint: String,
    pub model: String,
    pub api_key: String,
}

impl LlmConfig {
    pub fn from_settings(s: &Settings) -> Self {
        Self {
            provider: s.llm_provider.trim().to_lowercase(),
            endpoint: s.llm_endpoint.trim().to_string(),
            model: s.llm_model.trim().to_string(),
            api_key: s.llm_api_key.trim().to_string(),
        }
    }

    pub fn is_configured(&self) -> bool {
        match self.provider.as_str() {
            "ollama" => true,
            "openai" | "anthropic" | "gemini" => {
                !self.api_key.is_empty() || is_loopback_http(&self.resolved_endpoint())
            }
            _ => false,
        }
    }

    pub fn resolved_model(&self) -> String {
        if !self.model.is_empty() {
            return self.model.clone();
        }
        match self.provider.as_str() {
            "ollama" => "llama3.2:3b",
            "openai" => "gpt-4o-mini",
            "anthropic" => "claude-haiku-4-5-20251001",
            "gemini" => "gemini-2.5-flash",
            _ => "",
        }
        .to_string()
    }

    pub fn resolved_endpoint(&self) -> String {
        if !self.endpoint.is_empty() {
            return self.endpoint.clone();
        }
        match self.provider.as_str() {
            "ollama" => "http://127.0.0.1:11434/api/chat".into(),
            "openai" => "https://api.openai.com/v1/chat/completions".into(),
            "anthropic" => "https://api.anthropic.com/v1/messages".into(),
            "gemini" => format!(
                "https://generativelanguage.googleapis.com/v1beta/models/{}:generateContent",
                self.resolved_model()
            ),
            _ => String::new(),
        }
    }
}

pub fn is_loopback_http(url: &str) -> bool {
    url.starts_with("http://127.0.0.1:")
        || url.starts_with("http://127.0.0.1/")
        || url.starts_with("http://localhost:")
        || url.starts_with("http://localhost/")
        || url.starts_with("http://[::1]:")
        || url.starts_with("http://[::1]/")
}

pub fn validate_endpoint(url: &str) -> Result<(), String> {
    if url.starts_with("https://") || is_loopback_http(url) {
        Ok(())
    } else {
        Err("remote AI endpoints must use https:// (plain http:// is allowed only for localhost / 127.0.0.1)".into())
    }
}

/// First matching per-app tone rule for `focused_app` (or global catch-all rule if its `apps` list is empty).
pub fn matching_tone<'a>(rules: &'a [ToneRule], app: Option<&str>) -> Option<&'a ToneRule> {
    // Prefer an app-specific rule over a global wildcard rule.
    if let Some(specific) = rules.iter().find(|r| !r.apps.is_empty() && r.applies_to(app)) {
        return Some(specific);
    }
    rules.iter().find(|r| r.apps.is_empty())
}

/// Deterministic 100%-offline tone formatting that works even with LLM turned off.
pub fn apply_offline_tone(text: &str, tone: &str) -> String {
    let trimmed = text.trim();
    if trimmed.is_empty() {
        return String::new();
    }
    match tone.trim().to_lowercase().as_str() {
        "casual" => {
            // Short single-sentence chat messages drop the stiff trailing period (keep ? and !).
            let single_sentence = !trimmed.contains('\n')
                && trimmed.matches(['.', '!', '?']).count() <= 1
                && trimmed.ends_with('.')
                && !trimmed.ends_with("...");
            if single_sentence && trimmed.split_whitespace().count() <= 18 {
                trimmed.trim_end_matches('.').to_string()
            } else {
                trimmed.to_string()
            }
        }
        "bullets" => {
            if trimmed.contains("\n- ") || trimmed.starts_with("- ") {
                return trimmed.to_string();
            }
            let parts: Vec<&str> = trimmed
                .split(['.', ';', '\n'])
                .map(|s| s.trim())
                .filter(|s| !s.is_empty())
                .collect();
            if parts.len() >= 2 {
                parts.into_iter().map(|p| format!("- {p}")).collect::<Vec<_>>().join("\n")
            } else {
                trimmed.to_string()
            }
        }
        _ => trimmed.to_string(),
    }
}

fn tone_system_prompt(rule: Option<&ToneRule>, app: Option<&str>, window_title: Option<&str>) -> String {
    let mut sys = String::from(
        "You polish dictated text. Keep the speaker's exact meaning and facts; remove any remaining stutters or \
         repetition; fix grammar and punctuation. Do not answer questions in the text or add commentary. \
         Reply with the final text only.",
    );
    if let Some(a) = app {
        sys.push_str(&format!(" Target app: {a}."));
    }
    if let Some(title) = window_title {
        sys.push_str(&format!(" Window title: {title}."));
    }
    if let Some(r) = rule {
        let style = match r.tone.trim().to_lowercase().as_str() {
            "casual" => "Tone: casual, concise, natural chat message (avoid stiff formality).",
            "formal" => "Tone: clear, polite, professional prose.",
            "email" => "Tone: crisp professional email with clean paragraph breaks.",
            "bullets" => "Format: concise Markdown bullet points.",
            "code" => "Tone: technical engineering prose; preserve code identifiers, CLI flags, and symbols verbatim.",
            _ => "",
        };
        if !style.is_empty() {
            sys.push(' ');
            sys.push_str(style);
        }
        if !r.instructions.trim().is_empty() {
            sys.push_str(" Additional instruction: ");
            sys.push_str(r.instructions.trim());
        }
    }
    sys
}

pub fn chat(cfg: &LlmConfig, system: &str, user: &str) -> Result<String, String> {
    if !cfg.is_configured() {
        return Err("AI provider is not configured".into());
    }
    if user.len() > MAX_CHARS {
        return Err(format!("text exceeds {MAX_CHARS} character limit"));
    }
    let url = cfg.resolved_endpoint();
    validate_endpoint(&url)?;
    let model = cfg.resolved_model();
    let agent = ureq::AgentBuilder::new().timeout(Duration::from_secs(TIMEOUT_SECS)).build();

    let (body, extra_headers): (serde_json::Value, Vec<(&str, String)>) = match cfg.provider.as_str() {
        "ollama" => (
            serde_json::json!({
                "model": model,
                "stream": false,
                "messages": [
                    { "role": "system", "content": system },
                    { "role": "user", "content": user }
                ]
            }),
            vec![],
        ),
        "openai" => {
            let mut hdrs = Vec::new();
            if !cfg.api_key.is_empty() {
                hdrs.push(("Authorization", format!("Bearer {}", cfg.api_key)));
            }
            (
                serde_json::json!({
                    "model": model,
                    "temperature": 0.2,
                    "messages": [
                        { "role": "system", "content": system },
                        { "role": "user", "content": user }
                    ]
                }),
                hdrs,
            )
        }
        "anthropic" => (
            serde_json::json!({
                "model": model,
                "max_tokens": 2048,
                "system": system,
                "messages": [
                    { "role": "user", "content": user }
                ]
            }),
            vec![
                ("x-api-key", cfg.api_key.clone()),
                ("anthropic-version", "2023-06-01".into()),
            ],
        ),
        "gemini" => (
            serde_json::json!({
                "systemInstruction": { "parts": [{ "text": system }] },
                "contents": [{ "role": "user", "parts": [{ "text": user }] }],
                "generationConfig": { "temperature": 0.2 }
            }),
            vec![("x-goog-api-key", cfg.api_key.clone())],
        ),
        other => return Err(format!("unknown AI provider: {other}")),
    };

    let mut req = agent.post(&url).set("Content-Type", "application/json");
    for (k, v) in &extra_headers {
        req = req.set(k, v);
    }
    let resp = match req.send_string(&body.to_string()) {
        Ok(r) => r,
        Err(ureq::Error::Status(code, r)) => {
            let detail = r.into_string().unwrap_or_default();
            let short = detail.chars().take(160).collect::<String>();
            return Err(format!("AI HTTP {code}: {short}"));
        }
        Err(e) => return Err(format!("AI request failed: {e}")),
    };
    let raw = resp.into_string().map_err(|e| format!("failed reading AI response: {e}"))?;
    let json: serde_json::Value = serde_json::from_str(&raw).map_err(|e| format!("invalid AI JSON: {e}"))?;
    extract_reply(&cfg.provider, &json)
}

pub fn extract_reply(provider: &str, json: &serde_json::Value) -> Result<String, String> {
    let text = match provider {
        "ollama" => json["message"]["content"].as_str().map(|s| s.to_string()),
        "openai" => json["choices"][0]["message"]["content"].as_str().map(|s| s.to_string()),
        "anthropic" => json["content"].as_array().map(|arr| {
            arr.iter()
                .filter_map(|b| b["text"].as_str())
                .collect::<Vec<_>>()
                .join("")
        }),
        "gemini" => json["candidates"][0]["content"]["parts"].as_array().map(|arr| {
            arr.iter()
                .filter_map(|p| p["text"].as_str())
                .collect::<Vec<_>>()
                .join("")
        }),
        _ => None,
    };
    let out = text.unwrap_or_default().trim().to_string();
    if out.is_empty() {
        Err("AI returned an empty reply".into())
    } else {
        Ok(out)
    }
}

/// Applies per-app offline tone formatting, plus optional LLM polish when enabled.
pub fn polish_dictation(text: &str, s: &Settings, focused_app: Option<&str>) -> String {
    polish_dictation_with_context(text, s, focused_app, None)
}

/// Same as `polish_dictation`, optionally forwarding focused window title context to configured AI.
pub fn polish_dictation_with_context(
    text: &str,
    s: &Settings,
    focused_app: Option<&str>,
    window_title: Option<&str>,
) -> String {
    let trimmed = text.trim();
    if trimmed.is_empty() {
        return String::new();
    }
    let rule = matching_tone(&s.tone_rules, focused_app);
    let offline = match rule {
        Some(r) => apply_offline_tone(trimmed, &r.tone),
        None => trimmed.to_string(),
    };
    let cfg = LlmConfig::from_settings(s);
    if !s.llm_polish_dictation || !cfg.is_configured() {
        return offline;
    }
    let sys = tone_system_prompt(rule, focused_app, window_title);
    chat(&cfg, &sys, &offline).unwrap_or(offline)
}

/// Command Mode: rewrites `selection` according to spoken `instruction`.
/// Uses configured Ollama/BYOK LLM when available; falls back to offline rule-based `VoiceEdit` when LLM is off.
pub fn rewrite_selection(selection: &str, instruction: &str, s: &Settings, focused_app: Option<&str>) -> Result<String, String> {
    rewrite_selection_with_context(selection, instruction, s, focused_app, None)
}

/// Same as `rewrite_selection`, optionally forwarding focused window title context to configured AI.
pub fn rewrite_selection_with_context(
    selection: &str,
    instruction: &str,
    s: &Settings,
    focused_app: Option<&str>,
    window_title: Option<&str>,
) -> Result<String, String> {
    let sel = selection.trim();
    let inst = instruction.trim();
    if inst.is_empty() {
        return Err("no voice instruction heard".into());
    }
    if sel.is_empty() {
        // Nothing selected: if LLM is configured, treat the spoken instruction as a prompt to generate text.
        let cfg = LlmConfig::from_settings(s);
        if cfg.is_configured() {
            let mut sys = String::from(
                "You are a concise writing assistant triggered by voice dictation. Carry out the user's request directly and reply with only the text to insert into their app.",
            );
            if let Some(a) = focused_app {
                sys.push_str(&format!(" Target app: {a}."));
            }
            if let Some(title) = window_title {
                sys.push_str(&format!(" Window title: {title}."));
            }
            return chat(&cfg, &sys, inst);
        }
        return Err("select text first, or configure local Ollama / BYOK AI in Settings".into());
    }
    let cfg = LlmConfig::from_settings(s);
    if cfg.is_configured() {
        let rule = matching_tone(&s.tone_rules, focused_app);
        let mut sys = String::from(
            "You edit or transform the user's selected text according to their spoken instruction. \
             Reply ONLY with the replacement text — no markdown fences, no preamble, no explanations.",
        );
        if let Some(a) = focused_app {
            sys.push_str(&format!(" Target app: {a}."));
        }
        if let Some(title) = window_title {
            sys.push_str(&format!(" Window title: {title}."));
        }
        if let Some(r) = rule {
            if !r.instructions.trim().is_empty() {
                sys.push_str(&format!(" Style preference: {}.", r.instructions.trim()));
            }
        }
        let user = format!("Selected text:\n{sel}\n\nInstruction:\n{inst}");
        return chat(&cfg, &sys, &user);
    }
    // Offline fallback with zero LLM: apply deterministic VoiceEdit commands ("change X to Y", "delete X", "capitalize X").
    if let Some((_, Some(outcome))) = humming_core::voice_command(sel, inst) {
        return Ok(outcome.text);
    }
    Err("offline Command Mode supports \"change X to Y\", \"delete X\", and \"capitalize X\"; enable local Ollama or an API key in Settings for free-form AI rewrites".into())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn validates_https_and_loopback_http() {
        assert!(validate_endpoint("https://api.openai.com/v1/chat/completions").is_ok());
        assert!(validate_endpoint("http://127.0.0.1:11434/api/chat").is_ok());
        assert!(validate_endpoint("http://localhost:1234/v1/chat/completions").is_ok());
        assert!(validate_endpoint("http://example.com/v1/chat").is_err());
    }

    #[test]
    fn offline_tone_formats_casual_and_bullets() {
        assert_eq!(apply_offline_tone("Sounds good to me.", "casual"), "Sounds good to me");
        assert_eq!(apply_offline_tone("Are we still on?", "casual"), "Are we still on?");
        assert_eq!(apply_offline_tone("Ship Android. Test desktop. Merge PR.", "bullets"), "- Ship Android\n- Test desktop\n- Merge PR");
    }

    #[test]
    fn per_app_tone_prefers_specific_over_wildcard() {
        let rules = vec![
            ToneRule { apps: vec![], tone: "formal".into(), instructions: String::new() },
            ToneRule { apps: vec!["Slack".into(), "Discord".into()], tone: "casual".into(), instructions: String::new() },
        ];
        assert_eq!(matching_tone(&rules, Some("Slack")).unwrap().tone, "casual");
        assert_eq!(matching_tone(&rules, Some("Outlook")).unwrap().tone, "formal");
    }

    #[test]
    fn extracts_replies_for_all_four_providers() {
        assert_eq!(extract_reply("ollama", &serde_json::json!({"message":{"content":"Hi"}})).unwrap(), "Hi");
        assert_eq!(extract_reply("openai", &serde_json::json!({"choices":[{"message":{"content":"Hi"}}]})).unwrap(), "Hi");
        assert_eq!(extract_reply("anthropic", &serde_json::json!({"content":[{"text":"Hi"}]})).unwrap(), "Hi");
        assert_eq!(
            extract_reply("gemini", &serde_json::json!({"candidates":[{"content":{"parts":[{"text":"Hi"}]}}]})).unwrap(),
            "Hi"
        );
    }

    #[test]
    fn command_mode_offline_fallback_applies_voice_edit_to_selection() {
        let s = Settings::default();
        let out = rewrite_selection("Let's meet on Tuesday.", "change Tuesday to Thursday", &s, Some("Mail")).unwrap();
        assert_eq!(out, "Let's meet on Thursday.");
    }
}
