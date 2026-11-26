//! Humming desktop shell: global push-to-talk hotkey -> microphone -> Parakeet on sherpa-onnx (the Android app's
//! engine) -> the Android cleanup rules -> text pasted into the focused app. Lives in the tray / menu bar; the main window is settings, history and
//! the personal dictionary; a small click-through overlay shows the mic level while dictating.

mod audio;
mod focus;
mod history;
mod insert;
mod llm;
mod local_api;
mod meetings;
mod models;
mod settings;
mod stt;
mod text_cleanup;
mod updater;
mod voice_edit;

use stt::SherpaEngine;
use serde::Serialize;
use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};
use std::time::{Instant, SystemTime, UNIX_EPOCH};
use tauri::image::Image;
use tauri::menu::{Menu, MenuItem, PredefinedMenuItem};
use tauri::tray::{MouseButton, MouseButtonState, TrayIconBuilder, TrayIconEvent};
use tauri::{AppHandle, Emitter, Manager, State, WindowEvent};
use tauri_plugin_global_shortcut::{GlobalShortcutExt, ShortcutState};

use history::{Entry, History};
use settings::Settings;

const TRAY_IDLE: &[u8] = include_bytes!("../icons/tray.png");
const TRAY_REC: &[u8] = include_bytes!("../icons/tray-rec.png");

struct LoadedEngine {
    engine: Arc<SherpaEngine>,
    key: String,
}

struct ActiveMeeting {
    id: u64,
    started_ms: u64,
    stop: Arc<AtomicBool>,
}

pub struct AppState {
    settings: Mutex<Settings>,
    settings_path: PathBuf,
    models_dir: PathBuf,
    history: Mutex<History>,
    meetings: Mutex<meetings::MeetingStore>,
    active_meeting: Mutex<Option<ActiveMeeting>>,
    meeting_recorder: audio::Recorder,
    recorder: audio::Recorder,
    engine: Mutex<Option<LoadedEngine>>,
    status: Mutex<Status>,
    busy: AtomicBool,
    downloading: AtomicBool,
    /// Where the current dictation's text goes: true = paste into the focused app, false = the in-app test box.
    to_focused_app: AtomicBool,
    /// True when the active recording was started via Command Mode (rewrite highlighted text in focused app).
    command_mode: AtomicBool,
    command_selection: Arc<Mutex<Option<String>>>,
    /// Name of the app that had focus when the current dictation started (per-app dictionary & tone).
    focused_app: Arc<Mutex<Option<String>>>,
    /// Humming's own last insertion, the only text a voice edit may touch (1.8).
    last_insert: Mutex<voice_edit::LastInsert>,
    local_api: Mutex<Option<local_api::ServerHandle>>,
}

#[derive(Debug, Clone, Serialize, PartialEq)]
pub struct Status {
    /// no_model | loading | ready | recording | transcribing | error
    pub state: String,
    pub message: String,
}

#[derive(Debug, Clone, Serialize)]
struct ModelRow {
    #[serde(flatten)]
    info: models::VoiceModel,
    downloaded: bool,
    active: bool,
}

#[derive(Debug, Clone, Serialize)]
struct Progress {
    id: String,
    done: u64,
    total: u64,
}

#[derive(Debug, Clone, Serialize)]
struct DictationEvent {
    entry: Entry,
    to_focused_app: bool,
    error: Option<String>,
}

fn now_ms() -> u64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_millis() as u64).unwrap_or(0)
}

fn set_status(app: &AppHandle, state: &str, message: impl Into<String>) {
    let st = Status { state: state.to_string(), message: message.into() };
    if let Some(s) = app.try_state::<AppState>() {
        if let Ok(mut g) = s.status.lock() {
            *g = st.clone();
        }
    }
    if let Some(tray) = app.tray_by_id("main") {
        let icon = if state == "recording" { TRAY_REC } else { TRAY_IDLE };
        if let Ok(img) = Image::from_bytes(icon) {
            let _ = tray.set_icon(Some(img));
        }
        let tip = if st.message.is_empty() { format!("Humming - {}", st.state) } else { format!("Humming - {}", st.message) };
        let _ = tray.set_tooltip(Some(tip));
    }
    let _ = app.emit("status", st);
}

fn engine_key(s: &Settings) -> String {
    s.model.clone()
}

/// Downloads one model, reporting progress to the window. Blocking; call it on a worker thread.
fn fetch_model(app: &AppHandle, dir: &std::path::Path, info: &'static models::VoiceModel) -> Result<(), String> {
    let progress_app = app.clone();
    let pid = info.id.to_string();
    set_status(app, "loading", format!("Downloading the {} model", info.label));
    models::download(dir, info, move |done, total| {
        let _ = progress_app.emit("model-progress", Progress { id: pid.clone(), done, total });
    })
}

/// Loads (or reloads) the whisper model for the current settings on a worker thread.
fn load_engine(app: &AppHandle) {
    let app = app.clone();
    std::thread::spawn(move || {
        let state = app.state::<AppState>();
        let s = state.settings.lock().map(|g| g.clone()).unwrap_or_default();
        let Some(picked) = models::find(&s.model) else { return };
        // The picked model, or (while it downloads) the best model already on disk.
        let Some(info) = models::usable(&s.model, |m| models::is_downloaded(&state.models_dir, m)) else {
            if let Ok(mut g) = state.engine.lock() {
                *g = None;
            }
            set_status(&app, "no_model", format!("Download the {} model to start", picked.label));
            return;
        };
        let key = info.id.to_string();
        if state.engine.lock().map(|g| g.as_ref().map(|e| e.key == key).unwrap_or(false)).unwrap_or(false) {
            set_status(&app, "ready", "");
            return;
        }
        set_status(&app, "loading", "Loading the speech model");
        if let Ok(mut g) = state.engine.lock() {
            *g = None; // free the old model before loading the next one
        }
        match SherpaEngine::load(&state.models_dir, info) {
            Ok(engine) => {
                engine.warmup();
                if let Ok(mut g) = state.engine.lock() {
                    *g = Some(LoadedEngine { engine: Arc::new(engine), key });
                }
                set_status(&app, "ready", "");
            }
            Err(e) => set_status(&app, "error", format!("Could not load the speech model: {e}")),
        }
    });
}

/// The text Humming inserts: the user's phrase replacements, the Android cleanup rules (with the user's switches;
/// verbatim mode keeps every word), then "bullet point" items or spoken lists as bullets.
pub fn finalize_text(raw: &str, s: &Settings) -> String {
    finalize_text_for(raw, s, None)
}

/// As `finalize_text`, with per-app dictionary entries applied only while `app` (the focused app) has focus.
pub fn finalize_text_for(raw: &str, s: &Settings, app: Option<&str>) -> String {
    let active: Vec<&settings::VocabEntry> = s.dictionary.iter().filter(|d| d.applies_to(app)).collect();
    let phrases: Vec<humming_core::DictionaryEntry> =
        active.iter().filter(|d| !d.spoken.trim().is_empty()).map(|d| d.to_core()).collect();
    let replaced = humming_core::cleanup::apply_dictionary(raw, &phrases);
    let terms: Vec<String> = active.iter().map(|d| d.written.clone()).collect();
    // The clean-up rules are English (fillers, self-corrections, spoken commands, lists). With the Multilingual
    // model the speech may be German, French, ...: keep the words and only normalise spacing (1.8).
    let english = models::english_cleanup(&s.model);
    if !english {
        let mut text = text_cleanup::neutral(&replaced, &terms);
        if s.trailing_space && !text.is_empty() && !text.ends_with('\n') {
            text.push(' ');
        }
        return text;
    }
    let opts = if s.clean_mode && english {
        text_cleanup::Opts {
            remove_fillers: s.remove_fillers,
            voice_commands: s.voice_commands,
            self_corrections: s.self_corrections,
            strict: s.accuracy_mode,
        }
    } else {
        text_cleanup::Opts { remove_fillers: false, voice_commands: s.voice_commands && english, self_corrections: false, strict: false }
    };
    let mut text = text_cleanup::apply(&replaced, &terms, opts);
    // "bullet point" items (1.7) first; otherwise a spoken "first, ... second, ..." list. Verbatim mode keeps the words.
    let bullets = if english && s.clean_mode && s.voice_commands { text_cleanup::bullet_list(&text) } else { None };
    if let Some(list) = bullets {
        text = list;
    } else if english && s.clean_mode && s.format_lists {
        if let Some(list) = humming_core::cleanup::format_spoken_list(&text) {
            text = list;
        }
    }
    if s.trailing_space && !text.is_empty() && !text.ends_with('\n') {
        text.push(' ');
    }
    text
}

fn show_overlay(app: &AppHandle) {
    let Some(w) = app.get_webview_window("overlay") else { return };
    let _ = w.set_ignore_cursor_events(true);
    if let Ok(Some(m)) = w.primary_monitor() {
        let scale = m.scale_factor();
        let (ww, wh) = ((240.0 * scale) as i32, (64.0 * scale) as i32);
        let x = m.position().x + (m.size().width as i32 - ww) / 2;
        let y = m.position().y + m.size().height as i32 - wh - (96.0 * scale) as i32;
        let _ = w.set_position(tauri::PhysicalPosition::new(x, y));
    }
    let _ = w.show();
}

fn hide_overlay(app: &AppHandle) {
    if let Some(w) = app.get_webview_window("overlay") {
        let _ = w.hide();
    }
}

fn show_main(app: &AppHandle, tab: Option<&str>) {
    if let Some(w) = app.get_webview_window("main") {
        let _ = w.show();
        let _ = w.unminimize();
        let _ = w.set_focus();
        if let Some(t) = tab {
            let _ = app.emit_to("main", "navigate", t.to_string());
        }
    }
}

fn start_dictation(app: &AppHandle, to_focused_app: bool, command_mode: bool) -> Result<(), String> {
    let state = app.state::<AppState>();
    if state.recorder.is_recording() || state.busy.load(Ordering::SeqCst) {
        return Ok(());
    }
    if state.engine.lock().map(|g| g.is_none()).unwrap_or(true) {
        let st = state.status.lock().map(|g| g.clone()).ok();
        let msg = match st {
            Some(s) if s.state == "loading" => "The speech model is still loading".to_string(),
            _ => "Download a speech model first".to_string(),
        };
        if to_focused_app {
            show_main(app, Some("home"));
        }
        return Err(msg);
    }
    let s = state.settings.lock().map(|g| g.clone()).unwrap_or_default();
    state.to_focused_app.store(to_focused_app, Ordering::SeqCst);
    state.command_mode.store(command_mode, Ordering::SeqCst);
    if let Ok(mut g) = state.command_selection.lock() {
        *g = None;
    }
    if let Ok(mut g) = state.focused_app.lock() {
        *g = None;
    }
    if to_focused_app {
        let slot = state.focused_app.clone();
        let sel_slot = state.command_selection.clone();
        let restore_cb = s.restore_clipboard;
        std::thread::spawn(move || {
            let name = focus::focused_app();
            if let Ok(mut g) = slot.lock() {
                *g = name;
            }
            if command_mode {
                let sel = insert::grab_selection(restore_cb);
                if let Ok(mut g) = sel_slot.lock() {
                    *g = sel;
                }
            }
        });
    }
    let level_app = app.clone();
    state
        .recorder
        .start(&s.input_device, s.max_seconds, move |rms| {
            let _ = level_app.emit("level", rms);
        })
        .map_err(|e| {
            set_status(app, "error", e.clone());
            e
        })?;
    set_status(app, "recording", if command_mode { "Command Mode: speak instruction" } else { "Listening" });
    if s.show_overlay && to_focused_app {
        show_overlay(app);
    }
    Ok(())
}

fn stop_dictation(app: &AppHandle) {
    let state = app.state::<AppState>();
    let released = Instant::now();
    let Some((samples, rate, wall_ms)) = state.recorder.stop() else { return };
    let to_focused_app = state.to_focused_app.load(Ordering::SeqCst);
    let command_mode = state.command_mode.swap(false, Ordering::SeqCst);
    // The model actually loaded (it can be a fallback while the picked one downloads): clean-up rules follow it.
    let engine = state.engine.lock().ok().and_then(|g| g.as_ref().map(|e| (e.engine.clone(), e.key.clone())));
    let Some((engine, loaded_model)) = engine else {
        hide_overlay(app);
        set_status(app, "no_model", "Download a speech model first");
        return;
    };
    // A tap shorter than a quarter second is an accidental press, not speech.
    if wall_ms < 250 || samples.len() < (rate as usize / 4) {
        hide_overlay(app);
        set_status(app, "ready", "");
        return;
    }
    state.busy.store(true, Ordering::SeqCst);
    set_status(app, "transcribing", if command_mode { "Rewriting selection" } else { "Transcribing" });
    let app = app.clone();
    tauri::async_runtime::spawn_blocking(move || {
        let state = app.state::<AppState>();
        let mut s = state.settings.lock().map(|g| g.clone()).unwrap_or_default();
        s.model = loaded_model;
        let pcm = stt::to_16k(&samples, rate);
        let audio_ms = pcm.len() as u64 * 1000 / stt::SAMPLE_RATE as u64;
        // All silence (an accidental press, a muted mic): insert nothing rather than let the model guess.
        let vad = humming_core::VadConfig::default();
        let has_speech = !humming_core::vad::speech_ranges(&pcm, stt::SAMPLE_RATE, &vad).is_empty();
        let raw = if has_speech { engine.transcribe(&pcm) } else { String::new() };
        hide_overlay(&app);
        let focused = state.focused_app.lock().ok().and_then(|g| g.clone());
        let mut error = None;
        let mut inserted = false;

        let text = if command_mode && to_focused_app {
            let selection = state.command_selection.lock().ok().and_then(|mut g| g.take()).unwrap_or_default();
            match llm::rewrite_selection(&selection, &raw, &s, focused.as_deref()) {
                Ok(rewritten) => {
                    match insert::insert(&rewritten, &s.insert_method, s.restore_clipboard, s.paste_delay_ms) {
                        Ok(()) => inserted = true,
                        Err(e) => {
                            let _ = insert::copy(&rewritten);
                            error = Some(format!("{e}. The rewritten text is on your clipboard."));
                        }
                    }
                    if let Ok(mut l) = state.last_insert.lock() {
                        l.forget();
                    }
                    rewritten
                }
                Err(e) => {
                    error = Some(e);
                    String::new()
                }
            }
        } else {
            let base_text = finalize_text_for(&raw, &s, focused.as_deref());
            let mut polished = llm::polish_dictation(&base_text, &s, focused.as_deref());
            if s.trailing_space && !polished.is_empty() && !polished.ends_with('\n') && !polished.ends_with(' ') {
                polished.push(' ');
            }
            if to_focused_app {
                // Edit by voice (1.8): a command edits only our own last insertion in the same app.
                let now = now_ms();
                let mut action = state
                    .last_insert
                    .lock()
                    .map(|l| l.plan(&raw, &polished, focused.as_deref(), s.voice_edit, now))
                    .unwrap_or_else(|_| voice_edit::Action::Insert(polished.clone()));
                if matches!(action, voice_edit::Action::Replace { .. }) {
                    let now_app = focus::focused_app();
                    if now_app.is_none() || now_app != focused {
                        action = voice_edit::Action::Nothing;
                        error = Some("Edit not applied: the app changed".to_string());
                        if let Ok(mut l) = state.last_insert.lock() {
                            l.forget();
                        }
                    }
                }
                let put = |t: &str| insert::insert(t, &s.insert_method, s.restore_clipboard, s.paste_delay_ms);
                let mut insert_app = focused.clone();
                let result = match &action {
                    voice_edit::Action::Insert(t) => {
                        insert_app = focus::focused_app();
                        Some(put(t))
                    }
                    voice_edit::Action::Replace { backspaces, text: t } => {
                        Some(insert::erase(*backspaces).and_then(|()| if t.is_empty() { Ok(()) } else { put(t) }))
                    }
                    voice_edit::Action::Nothing => None,
                };
                match result {
                    Some(Ok(())) => {
                        inserted = true;
                        if let Ok(mut l) = state.last_insert.lock() {
                            let owner = if insert_app == focused { insert_app.as_deref() } else { None };
                            l.commit(&action, owner, now);
                        }
                    }
                    Some(Err(e)) => {
                        let _ = insert::copy(&polished);
                        error = Some(format!("{e}. The text is on your clipboard."));
                        if let Ok(mut l) = state.last_insert.lock() {
                            l.forget();
                        }
                    }
                    None => {}
                }
            }
            polished
        };
        let latency_ms = released.elapsed().as_millis() as u64;
        let entry = Entry { id: 0, ts: now_ms(), text: text.trim_end().to_string(), raw, audio_ms, latency_ms, model: s.model.clone(), inserted };
        let entry = if s.history_enabled && !entry.text.is_empty() {
            state.history.lock().map(|mut h| h.add(entry.clone(), s.history_limit)).unwrap_or(entry)
        } else {
            entry
        };
        state.busy.store(false, Ordering::SeqCst);
        match &error {
            Some(e) => set_status(&app, "error", e.clone()),
            None => set_status(&app, "ready", ""),
        }
        let _ = app.emit("dictation", DictationEvent { entry, to_focused_app, error });
    });
}

fn on_hotkey(app: &AppHandle, pressed: bool, command_mode: bool) {
    let state = app.state::<AppState>();
    let mode = state.settings.lock().map(|g| g.mode.clone()).unwrap_or_else(|_| "hold".into());
    let recording = state.recorder.is_recording();
    if mode == "toggle" {
        if !pressed {
            return;
        }
        if recording {
            stop_dictation(app);
        } else if let Err(e) = start_dictation(app, true, command_mode) {
            set_status(app, "error", e);
        }
    } else if pressed {
        // Key repeat sends more Pressed events while held: start once.
        if !recording {
            if let Err(e) = start_dictation(app, true, command_mode) {
                set_status(app, "error", e);
            }
        }
    } else if recording {
        stop_dictation(app);
    }
}

fn register_hotkeys(app: &AppHandle, hotkey: &str, command_hotkey: &str) -> Result<(), String> {
    let gs = app.global_shortcut();
    let _ = gs.unregister_all();
    gs.register(hotkey).map_err(|e| format!("cannot use the shortcut \"{hotkey}\": {e}"))?;
    let cmd = command_hotkey.trim();
    if !cmd.is_empty() && cmd != hotkey.trim() {
        gs.register(cmd).map_err(|e| format!("cannot use the Command Mode shortcut \"{cmd}\": {e}"))?;
    }
    Ok(())
}

fn sync_local_api(app: &AppHandle) -> Result<(), String> {
    let state = app.state::<AppState>();
    if let Ok(mut g) = state.local_api.lock() {
        if let Some(srv) = g.take() {
            srv.stop();
        }
    }
    let s = state.settings.lock().map(|g| g.clone()).unwrap_or_default();
    if !s.local_api_enabled {
        return Ok(());
    }
    let token = s.local_api_token.clone();
    let port = s.local_api_port;
    let app_handle = app.clone();
    let srv = local_api::start(port, move |req| handle_local_api(&app_handle, &token, req))?;
    if let Ok(mut g) = state.local_api.lock() {
        *g = Some(srv);
    }
    Ok(())
}

fn handle_local_api(app: &AppHandle, token: &str, req: local_api::HttpRequest) -> local_api::HttpResponse {
    let state = app.state::<AppState>();
    if req.method == "GET" && req.path == "/health" {
        let st = state.status.lock().map(|g| g.clone()).unwrap_or(Status { state: "error".into(), message: String::new() });
        let model = state.engine.lock().ok().and_then(|g| g.as_ref().map(|e| e.key.clone()));
        return local_api::HttpResponse::json(
            200,
            &serde_json::json!({
                "ok": true,
                "version": env!("CARGO_PKG_VERSION"),
                "status": st.state,
                "model": model,
            }),
        );
    }
    if req.method != "POST" {
        return local_api::HttpResponse::err(405, "method not allowed");
    }
    if !local_api::authorized(&req, token) {
        return local_api::HttpResponse::err(401, "missing or invalid Bearer token");
    }
    let s = state.settings.lock().map(|g| g.clone()).unwrap_or_default();
    match req.path.as_str() {
        "/v1/cleanup" => {
            let body: serde_json::Value = match serde_json::from_slice(&req.body) {
                Ok(v) => v,
                Err(e) => return local_api::HttpResponse::err(400, format!("invalid JSON: {e}")),
            };
            let raw = body["text"].as_str().unwrap_or("");
            let app_name = body["app"].as_str();
            let cleaned = finalize_text_for(raw, &s, app_name);
            let polished = llm::polish_dictation(&cleaned, &s, app_name);
            local_api::HttpResponse::json(200, &serde_json::json!({ "text": polished.trim_end() }))
        }
        "/v1/transcribe" => {
            let engine = state.engine.lock().ok().and_then(|g| g.as_ref().map(|e| (e.engine.clone(), e.key.clone())));
            let Some((engine, loaded_model)) = engine else {
                return local_api::HttpResponse::err(503, "no speech model loaded");
            };
            let t0 = Instant::now();
            let ct = req.headers.get("content-type").map(|v| v.to_lowercase()).unwrap_or_default();
            let (samples, rate, clean, app_name) = if ct.contains("application/json") {
                let body: serde_json::Value = match serde_json::from_slice(&req.body) {
                    Ok(v) => v,
                    Err(e) => return local_api::HttpResponse::err(400, format!("invalid JSON: {e}")),
                };
                let Some(path) = body["wav_path"].as_str() else {
                    return local_api::HttpResponse::err(400, "JSON body must include wav_path");
                };
                let bytes = match std::fs::read(path) {
                    Ok(b) => b,
                    Err(e) => return local_api::HttpResponse::err(400, format!("cannot read wav_path: {e}")),
                };
                let (pcm, sr) = match local_api::parse_wav_bytes(&bytes) {
                    Ok(v) => v,
                    Err(e) => return local_api::HttpResponse::err(400, e),
                };
                let clean = body["clean"].as_bool().unwrap_or(true);
                let app_name = body["app"].as_str().map(|a| a.to_string());
                (pcm, sr, clean, app_name)
            } else {
                let (pcm, sr) = match local_api::parse_wav_bytes(&req.body) {
                    Ok(v) => v,
                    Err(e) => return local_api::HttpResponse::err(400, e),
                };
                let clean = req.query.get("clean").map(|v| v != "false" && v != "0").unwrap_or(true);
                let app_name = req.query.get("app").cloned();
                (pcm, sr, clean, app_name)
            };
            let pcm = stt::to_16k(&samples, rate);
            let audio_ms = pcm.len() as u64 * 1000 / stt::SAMPLE_RATE as u64;
            let raw = engine.transcribe(&pcm);
            let mut s_run = s;
            s_run.model = loaded_model.clone();
            let text = if clean {
                let base = finalize_text_for(&raw, &s_run, app_name.as_deref());
                llm::polish_dictation(&base, &s_run, app_name.as_deref()).trim_end().to_string()
            } else {
                raw.clone()
            };
            local_api::HttpResponse::json(
                200,
                &serde_json::json!({
                    "text": text,
                    "raw": raw,
                    "model": loaded_model,
                    "audio_ms": audio_ms,
                    "latency_ms": t0.elapsed().as_millis() as u64,
                }),
            )
        }
        _ => local_api::HttpResponse::err(404, "not found"),
    }
}

fn apply_autostart(app: &AppHandle, on: bool) {
    use tauri_plugin_autostart::ManagerExt;
    let al = app.autolaunch();
    let _ = if on { al.enable() } else { al.disable() };
}

// ---------------- commands (called from the UI) ----------------

#[tauri::command]
fn get_settings(state: State<'_, AppState>) -> Settings {
    state.settings.lock().map(|g| g.clone()).unwrap_or_default()
}

#[tauri::command]
fn save_settings(app: AppHandle, state: State<'_, AppState>, settings: Settings) -> Result<Settings, String> {
    let mut new = settings.sanitized();
    if new.local_api_enabled && new.local_api_token.trim().is_empty() {
        new.local_api_token = local_api::generate_token();
    }
    let old = state.settings.lock().map(|g| g.clone()).unwrap_or_default();
    if new.hotkey != old.hotkey || new.command_hotkey != old.command_hotkey {
        if let Err(e) = register_hotkeys(&app, &new.hotkey, &new.command_hotkey) {
            let _ = register_hotkeys(&app, &old.hotkey, &old.command_hotkey);
            return Err(e);
        }
    }
    if new.launch_at_login != old.launch_at_login {
        apply_autostart(&app, new.launch_at_login);
    }
    new.save(&state.settings_path)?;
    if let Ok(mut g) = state.settings.lock() {
        *g = new.clone();
    }
    if engine_key(&new) != engine_key(&old) {
        load_engine(&app);
    }
    if new.local_api_enabled != old.local_api_enabled
        || new.local_api_port != old.local_api_port
        || new.local_api_token != old.local_api_token
    {
        sync_local_api(&app)?;
    }
    Ok(new)
}

#[tauri::command]
fn get_status(state: State<'_, AppState>) -> Status {
    state.status.lock().map(|g| g.clone()).unwrap_or(Status { state: "error".into(), message: String::new() })
}

#[tauri::command]
fn list_models(state: State<'_, AppState>) -> Vec<ModelRow> {
    let active = state.settings.lock().map(|g| g.model.clone()).unwrap_or_default();
    models::CATALOG
        .iter()
        .map(|m| ModelRow { info: m.clone(), downloaded: models::is_downloaded(&state.models_dir, m), active: m.id == active })
        .collect()
}

#[tauri::command]
fn download_model(app: AppHandle, state: State<'_, AppState>, id: String) -> Result<(), String> {
    let info = models::find(&id).ok_or_else(|| format!("unknown model {id}"))?;
    if state.downloading.swap(true, Ordering::SeqCst) {
        return Err("a download is already running".into());
    }
    let dir = state.models_dir.clone();
    // Quick start (first run): nothing on disk yet and a big model picked -> fetch the 30 MB Lite model first and
    // dictate with it while the picked model downloads; the engine switches over when that finishes.
    let quick = state.settings.lock().map(|g| g.quick_start).unwrap_or(true)
        && info.id != models::LITE.id
        && !models::CATALOG.iter().any(|m| models::is_downloaded(&dir, m));
    std::thread::spawn(move || {
        if quick {
            match fetch_model(&app, &dir, &models::LITE) {
                Ok(_) => {
                    let _ = app.emit("model-downloaded", models::LITE.id.to_string());
                    load_engine(&app);
                }
                Err(e) => {
                    let _ = app.emit("model-error", e);
                }
            }
        }
        let r = fetch_model(&app, &dir, info);
        let state = app.state::<AppState>();
        state.downloading.store(false, Ordering::SeqCst);
        match r {
            Ok(_) => {
                let _ = app.emit("model-downloaded", id.clone());
                load_engine(&app);
            }
            Err(e) => {
                let _ = app.emit("model-error", e.clone());
                set_status(&app, "error", e);
            }
        }
    });
    Ok(())
}

#[tauri::command]
fn delete_model(app: AppHandle, state: State<'_, AppState>, id: String) -> Result<(), String> {
    let info = models::find(&id).ok_or_else(|| format!("unknown model {id}"))?;
    let active = state.settings.lock().map(|g| g.model.clone()).unwrap_or_default();
    if active == id {
        if let Ok(mut g) = state.engine.lock() {
            *g = None;
        }
    }
    models::remove(&state.models_dir, info)?;
    if active == id {
        load_engine(&app);
    }
    Ok(())
}

#[tauri::command]
fn list_input_devices() -> Vec<String> {
    audio::input_devices()
}

#[tauri::command]
fn get_history(state: State<'_, AppState>) -> Vec<Entry> {
    state.history.lock().map(|h| h.entries.clone()).unwrap_or_default()
}

#[tauri::command]
fn delete_history(state: State<'_, AppState>, id: u64) {
    if let Ok(mut h) = state.history.lock() {
        h.delete(id);
    }
}

#[tauri::command]
fn clear_history(state: State<'_, AppState>) {
    if let Ok(mut h) = state.history.lock() {
        h.clear();
    }
}

#[tauri::command]
fn copy_text(text: String) -> Result<(), String> {
    insert::copy(&text)
}

/// The in-app "Try it" button: same pipeline, but the text comes back to the window instead of being pasted.
#[tauri::command]
fn test_start(app: AppHandle) -> Result<(), String> {
    start_dictation(&app, false, false)
}

#[tauri::command]
fn test_stop(app: AppHandle) {
    stop_dictation(&app);
}

#[tauri::command]
fn preview_cleanup(state: State<'_, AppState>, text: String) -> String {
    let s = state.settings.lock().map(|g| g.clone()).unwrap_or_default();
    finalize_text(&text, &s)
}

#[tauri::command]
async fn test_llm(state: State<'_, AppState>) -> Result<String, String> {
    let s = state.settings.lock().map(|g| g.clone()).unwrap_or_default();
    let cfg = llm::LlmConfig::from_settings(&s);
    tauri::async_runtime::spawn_blocking(move || {
        llm::chat(
            &cfg,
            "Reply with the cleaned sentence only.",
            "um so this is uh a quick test of local AI polish",
        )
    })
    .await
    .map_err(|e| e.to_string())?
}

#[tauri::command]
fn regenerate_api_token(app: AppHandle, state: State<'_, AppState>) -> Result<Settings, String> {
    let mut s = state.settings.lock().map(|g| g.clone()).unwrap_or_default();
    s.local_api_token = local_api::generate_token();
    s.save(&state.settings_path)?;
    if let Ok(mut g) = state.settings.lock() {
        *g = s.clone();
    }
    if s.local_api_enabled {
        sync_local_api(&app)?;
    }
    Ok(s)
}

// ---------------- offline botless meeting mode (1.9) ----------------

fn flush_meeting_chunk(app: &AppHandle, meeting_id: u64, started_ms: u64, samples: Vec<f32>, rate: u32) {
    if samples.len() < (rate as usize / 2) {
        return;
    }
    let state = app.state::<AppState>();
    let engine = state.engine.lock().ok().and_then(|g| g.as_ref().map(|e| (e.engine.clone(), e.key.clone())));
    let Some((engine, loaded_model)) = engine else { return };
    let pcm = stt::to_16k(&samples, rate);
    let vad = humming_core::VadConfig::default();
    if humming_core::vad::speech_ranges(&pcm, stt::SAMPLE_RATE, &vad).is_empty() {
        return;
    }
    let raw = engine.transcribe(&pcm);
    if raw.trim().is_empty() {
        return;
    }
    let mut s = state.settings.lock().map(|g| g.clone()).unwrap_or_default();
    s.model = loaded_model;
    s.trailing_space = false;
    let text = finalize_text(&raw, &s).trim().to_string();
    if text.is_empty() {
        return;
    }
    let now = now_ms();
    let seg = meetings::MeetingSegment {
        ts_ms: now,
        elapsed_s: now.saturating_sub(started_ms) / 1000,
        text,
    };
    let updated = state.meetings.lock().ok().and_then(|mut s| s.append_segment(meeting_id, seg));
    if let Some(u) = updated {
        let _ = app.emit("meeting-updated", u);
    }
}

#[tauri::command]
fn list_meetings(state: State<'_, AppState>) -> Vec<meetings::Meeting> {
    state.meetings.lock().map(|m| m.items.clone()).unwrap_or_default()
}

#[tauri::command]
fn active_meeting_id(state: State<'_, AppState>) -> Option<u64> {
    state.active_meeting.lock().ok().and_then(|g| g.as_ref().map(|a| a.id))
}

#[tauri::command]
fn start_meeting(app: AppHandle, state: State<'_, AppState>, title: String, device: Option<String>) -> Result<meetings::Meeting, String> {
    if state.active_meeting.lock().map(|g| g.is_some()).unwrap_or(false) {
        return Err("a meeting is already recording".into());
    }
    if state.engine.lock().map(|g| g.is_none()).unwrap_or(true) {
        return Err("Download and load a speech model first".into());
    }
    let s = state.settings.lock().map(|g| g.clone()).unwrap_or_default();
    let dev = device
        .filter(|d| !d.trim().is_empty())
        .or_else(|| (!s.meeting_device.trim().is_empty()).then(|| s.meeting_device.clone()))
        .unwrap_or_else(|| s.input_device.clone());
    let started_ms = now_ms();
    let level_app = app.clone();
    state
        .meeting_recorder
        .start(&dev, 0, move |rms| {
            let _ = level_app.emit("meeting-level", rms);
        })?;
    let meeting = state
        .meetings
        .lock()
        .map_err(|_| "meeting store lock poisoned".to_string())?
        .create(title, started_ms);
    let stop_flag = Arc::new(AtomicBool::new(false));
    if let Ok(mut g) = state.active_meeting.lock() {
        *g = Some(ActiveMeeting { id: meeting.id, started_ms, stop: stop_flag.clone() });
    }
    let bg_app = app.clone();
    let mid = meeting.id;
    std::thread::Builder::new()
        .name("humming-meeting-worker".into())
        .spawn(move || {
            while !stop_flag.load(Ordering::SeqCst) {
                for _ in 0..120 {
                    if stop_flag.load(Ordering::SeqCst) {
                        return;
                    }
                    std::thread::sleep(std::time::Duration::from_millis(100));
                }
                let drained = bg_app.state::<AppState>().meeting_recorder.drain();
                if let Some((samples, rate, _)) = drained {
                    flush_meeting_chunk(&bg_app, mid, started_ms, samples, rate);
                }
            }
        })
        .map_err(|e| e.to_string())?;
    let _ = app.emit("meeting-updated", meeting.clone());
    Ok(meeting)
}

#[tauri::command]
async fn stop_meeting(app: AppHandle) -> Result<Option<meetings::Meeting>, String> {
    let active = app.state::<AppState>().active_meeting.lock().ok().and_then(|mut g| g.take());
    let Some(active) = active else { return Ok(None) };
    active.stop.store(true, Ordering::SeqCst);
    let tail = app.state::<AppState>().meeting_recorder.stop();
    let app_bg = app.clone();
    tauri::async_runtime::spawn_blocking(move || {
        if let Some((samples, rate, _)) = tail {
            flush_meeting_chunk(&app_bg, active.id, active.started_ms, samples, rate);
        }
        let updated = app_bg
            .state::<AppState>()
            .meetings
            .lock()
            .ok()
            .and_then(|mut s| s.finish(active.id, now_ms()));
        if let Some(ref m) = updated {
            let _ = app_bg.emit("meeting-updated", m.clone());
        }
        updated
    })
    .await
    .map_err(|e| e.to_string())
}

#[tauri::command]
fn update_meeting_notes(state: State<'_, AppState>, id: u64, title: Option<String>, notes: String) -> Option<meetings::Meeting> {
    state.meetings.lock().ok()?.update_notes(id, title, notes)
}

#[tauri::command]
async fn synthesize_meeting(app: AppHandle, state: State<'_, AppState>, id: u64, template: String) -> Result<meetings::Meeting, String> {
    let meeting = state
        .meetings
        .lock()
        .ok()
        .and_then(|m| m.get(id))
        .ok_or_else(|| "meeting not found".to_string())?;
    let s = state.settings.lock().map(|g| g.clone()).unwrap_or_default();
    let cfg = llm::LlmConfig::from_settings(&s);
    let tpl = template.clone();
    let summary = tauri::async_runtime::spawn_blocking(move || {
        if cfg.is_configured() {
            let sys = meetings::synthesis_prompt(&tpl);
            let tx = meetings::transcript_text(&meeting);
            let user = format!(
                "Meeting title: {}\n\nUser's manual notes:\n{}\n\nTimestamped transcript:\n{}",
                meeting.title,
                if meeting.notes.trim().is_empty() { "(none)" } else { meeting.notes.trim() },
                if tx.is_empty() { "(none)" } else { tx.as_str() }
            );
            llm::chat(&cfg, sys, &user).unwrap_or_else(|_| meetings::offline_digest(&meeting, &tpl))
        } else {
            meetings::offline_digest(&meeting, &tpl)
        }
    })
    .await
    .map_err(|e| e.to_string())?;
    let updated = app
        .state::<AppState>()
        .meetings
        .lock()
        .map_err(|_| "meeting store lock poisoned".to_string())?
        .set_summary(id, template, summary)
        .ok_or_else(|| "meeting not found".to_string())?;
    let _ = app.emit("meeting-updated", updated.clone());
    Ok(updated)
}

#[tauri::command]
fn export_meeting_markdown(state: State<'_, AppState>, id: u64) -> Result<String, String> {
    let m = state
        .meetings
        .lock()
        .ok()
        .and_then(|s| s.get(id))
        .ok_or_else(|| "meeting not found".to_string())?;
    Ok(meetings::to_markdown(&m))
}

#[tauri::command]
fn delete_meeting(state: State<'_, AppState>, id: u64) {
    if let Ok(mut m) = state.meetings.lock() {
        m.delete(id);
    }
}

#[tauri::command]
fn app_info(state: State<'_, AppState>) -> serde_json::Value {
    serde_json::json!({
        "version": env!("CARGO_PKG_VERSION"),
        "os": std::env::consts::OS,
        "arch": std::env::consts::ARCH,
        "models_dir": state.models_dir.display().to_string(),
        "history_file": state.history.lock().map(|h| h.path().display().to_string()).unwrap_or_default(),
        "meetings_file": state.meetings.lock().map(|m| m.path().display().to_string()).unwrap_or_default(),
    })
}

#[tauri::command]
fn update_status(app: AppHandle) -> updater::UpdateStatus {
    updater::status(&app)
}

/// "Check for updates" (install = false) and "Install now" (install = true) in Settings.
#[tauri::command]
async fn check_for_updates(app: AppHandle, install: bool) -> Result<updater::UpdateStatus, String> {
    Ok(updater::run_once(app, install).await)
}

pub fn run() {
    tauri::Builder::default()
        .plugin(tauri_plugin_single_instance::init(|app, _args, _cwd| show_main(app, None)))
        .plugin(
            tauri_plugin_global_shortcut::Builder::new()
                .with_handler(|app, shortcut, event| {
                    let is_cmd = app
                        .try_state::<AppState>()
                        .and_then(|st| st.settings.lock().ok().map(|g| g.command_hotkey.clone()))
                        .and_then(|hk| hk.trim().parse::<tauri_plugin_global_shortcut::Shortcut>().ok())
                        .map(|cs| cs == *shortcut)
                        .unwrap_or(false);
                    on_hotkey(app, event.state() == ShortcutState::Pressed, is_cmd);
                })
                .build(),
        )
        .plugin(tauri_plugin_autostart::init(tauri_plugin_autostart::MacosLauncher::LaunchAgent, Some(vec!["--hidden"])))
        .plugin(tauri_plugin_updater::Builder::new().build())
        .setup(|app| {
            #[cfg(target_os = "macos")]
            app.set_activation_policy(tauri::ActivationPolicy::Accessory);

            let handle = app.handle().clone();
            let config_dir = app.path().app_config_dir()?;
            let data_dir = app.path().app_data_dir()?;
            let settings_path = config_dir.join("settings.json");
            let mut settings = Settings::load(&settings_path);
            if settings.local_api_enabled && settings.local_api_token.trim().is_empty() {
                settings.local_api_token = local_api::generate_token();
                let _ = settings.save(&settings_path);
            }
            let models_dir = match std::env::var_os("HUMMING_MODELS_DIR") {
                Some(d) if !d.is_empty() => PathBuf::from(d),
                _ => data_dir.join("models"),
            };
            let history = History::load(data_dir.join("history.json"));
            let meetings = meetings::MeetingStore::load(data_dir.join("meetings.json"));
            app.manage(AppState {
                settings: Mutex::new(settings.clone()),
                settings_path,
                models_dir,
                history: Mutex::new(history),
                meetings: Mutex::new(meetings),
                active_meeting: Mutex::new(None),
                meeting_recorder: audio::Recorder::default(),
                recorder: audio::Recorder::default(),
                engine: Mutex::new(None),
                status: Mutex::new(Status { state: "loading".into(), message: String::new() }),
                busy: AtomicBool::new(false),
                downloading: AtomicBool::new(false),
                to_focused_app: AtomicBool::new(true),
                command_mode: AtomicBool::new(false),
                command_selection: Arc::new(Mutex::new(None)),
                focused_app: Arc::new(Mutex::new(None)),
                last_insert: Mutex::new(voice_edit::LastInsert::default()),
                local_api: Mutex::new(None),
            });

            // Tray / menu bar.
            let open = MenuItem::with_id(app, "open", "Open Humming", true, None::<&str>)?;
            let meetings_item = MenuItem::with_id(app, "meetings", "Meeting Notepad", true, None::<&str>)?;
            let copy_last = MenuItem::with_id(app, "copy_last", "Copy last dictation", true, None::<&str>)?;
            let history_item = MenuItem::with_id(app, "history", "History", true, None::<&str>)?;
            let settings_item = MenuItem::with_id(app, "settings", "Settings", true, None::<&str>)?;
            let sep = PredefinedMenuItem::separator(app)?;
            let quit = MenuItem::with_id(app, "quit", "Quit Humming", true, None::<&str>)?;
            let menu = Menu::with_items(app, &[&open, &meetings_item, &copy_last, &history_item, &settings_item, &sep, &quit])?;
            TrayIconBuilder::with_id("main")
                .icon(Image::from_bytes(TRAY_IDLE)?)
                .tooltip("Humming")
                .menu(&menu)
                .show_menu_on_left_click(false)
                .on_menu_event(|app, event| match event.id.as_ref() {
                    "open" => show_main(app, Some("home")),
                    "meetings" => show_main(app, Some("meetings")),
                    "copy_last" => {
                        let last = app
                            .state::<AppState>()
                            .history
                            .lock()
                            .ok()
                            .and_then(|h| h.entries.first().map(|e| e.text.clone()));
                        if let Some(t) = last {
                            let _ = insert::copy(&t);
                        }
                    }
                    "history" => show_main(app, Some("history")),
                    "settings" => show_main(app, Some("settings")),
                    "quit" => app.exit(0),
                    _ => {}
                })
                .on_tray_icon_event(|tray, event| {
                    if let TrayIconEvent::Click { button: MouseButton::Left, button_state: MouseButtonState::Up, .. } = event {
                        show_main(tray.app_handle(), None);
                    }
                })
                .build(app)?;

            if let Err(e) = register_hotkeys(&handle, &settings.hotkey, &settings.command_hotkey) {
                set_status(&handle, "error", e);
            }
            if let Err(e) = sync_local_api(&handle) {
                eprintln!("humming: local API could not start: {e}");
            }
            load_engine(&handle);
            // Self-update (1.6): first check shortly after launch, then every 4 hours.
            updater::start_background(&handle);

            // First run (no model yet) or a normal launch: show the window; a login launch stays in the tray.
            let hidden = std::env::args().any(|a| a == "--hidden");
            let have_model = models::find(&settings.model)
                .map(|m| models::is_downloaded(&app.state::<AppState>().models_dir, m))
                .unwrap_or(false);
            if !hidden || !have_model {
                show_main(&handle, Some("home"));
            }
            Ok(())
        })
        .on_window_event(|window, event| {
            // Closing the window keeps Humming running in the tray.
            if let WindowEvent::CloseRequested { api, .. } = event {
                if window.label() == "main" {
                    api.prevent_close();
                    let _ = window.hide();
                }
            }
        })
        .invoke_handler(tauri::generate_handler![
            get_settings,
            save_settings,
            get_status,
            list_models,
            download_model,
            delete_model,
            list_input_devices,
            get_history,
            delete_history,
            clear_history,
            copy_text,
            test_start,
            test_stop,
            preview_cleanup,
            test_llm,
            regenerate_api_token,
            list_meetings,
            active_meeting_id,
            start_meeting,
            stop_meeting,
            update_meeting_notes,
            synthesize_meeting,
            export_meeting_markdown,
            delete_meeting,
            app_info,
            update_status,
            check_for_updates
        ])
        .run(tauri::generate_context!())
        .expect("error while running Humming");
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn clean_mode_removes_fillers_and_adds_punctuation() {
        let s = Settings { trailing_space: false, ..Settings::default() };
        let out = finalize_text("um so i think we should ship it", &s);
        assert!(!out.to_lowercase().starts_with("um"), "{out}");
        assert!(out.contains("I think"), "{out}");
        assert!(out.ends_with('.'), "{out}");
    }

    #[test]
    fn verbatim_mode_keeps_words() {
        let s = Settings { clean_mode: false, trailing_space: false, ..Settings::default() };
        let out = finalize_text("um so i think", &s);
        assert!(out.to_lowercase().starts_with("um"), "{out}");
    }

    #[test]
    fn trailing_space_is_added_once() {
        let s = Settings::default();
        let out = finalize_text("hello there", &s);
        assert!(out.ends_with(". ") || out.ends_with("? ") || out.ends_with("! "), "{out:?}");
    }

    #[test]
    fn dictionary_applies() {
        let mut s = Settings { trailing_space: false, ..Settings::default() };
        s.dictionary.push(settings::VocabEntry { spoken: "git hub".into(), written: "GitHub".into(), apps: vec![] });
        s.dictionary.push(settings::VocabEntry { spoken: String::new(), written: "Okonkwo".into(), apps: vec![] });
        let out = finalize_text("push it to git hub for okonkwo", &s);
        assert_eq!(out, "Push it to GitHub for Okonkwo.");
    }

    #[test]
    fn multilingual_model_skips_english_cleanup() {
        // Codex review finding 7: German "Er" (he) is not the English filler "er".
        let s = Settings { trailing_space: false, model: "multi".into(), ..Settings::default() };
        assert_eq!(finalize_text("Er sagt, er kommt morgen", &s), "Er sagt, er kommt morgen");
        assert_eq!(finalize_text("Kot i pies", &s), "Kot i pies");
        let en = Settings { trailing_space: false, ..Settings::default() };
        assert!(!finalize_text("er so i think", &en).to_lowercase().starts_with("er "));
    }

    #[test]
    fn per_app_dictionary_only_applies_in_its_app() {
        let mut s = Settings { trailing_space: false, ..Settings::default() };
        s.dictionary.push(settings::VocabEntry { spoken: "cube cuddle".into(), written: "kubectl".into(), apps: vec!["Terminal".into()] });
        assert_eq!(finalize_text_for("run cube cuddle now", &s, Some("Terminal")), "Run kubectl now.");
        assert_eq!(finalize_text_for("run cube cuddle now", &s, Some("Slack")), "Run cube cuddle now.");
        assert_eq!(finalize_text("run cube cuddle now", &s), "Run cube cuddle now.");
    }

    #[test]
    fn spoken_list_becomes_bullets() {
        let s = Settings { trailing_space: false, ..Settings::default() };
        let out = finalize_text("the list is first, eggs, second, milk, third, bread", &s);
        assert!(out.contains("- Eggs") || out.contains("- eggs"), "{out}");
    }

    #[test]
    fn bullet_points_become_a_list() {
        let s = Settings { trailing_space: false, ..Settings::default() };
        assert_eq!(finalize_text("groceries bullet point eggs bullet point milk", &s), "Groceries:\n- Eggs\n- Milk");
        let off = Settings { voice_commands: false, ..s.clone() };
        assert_eq!(finalize_text("groceries bullet point eggs", &off), "Groceries bullet point eggs.");
        // Verbatim mode keeps the words, as on Android and iPhone.
        let verbatim = Settings { clean_mode: false, ..s.clone() };
        assert!(!finalize_text("groceries bullet point eggs bullet point milk", &verbatim).contains("\n- "));
        // "delete that" runs before the list pass.
        assert_eq!(
            finalize_text("Groceries bullet point eggs. Bullet point milk. Delete that. Bullet point bread.", &s),
            "Groceries:\n- Eggs\n- Bread"
        );
    }
}
