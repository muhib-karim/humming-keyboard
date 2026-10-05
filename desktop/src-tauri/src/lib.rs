//! Humming desktop shell: global push-to-talk hotkey -> microphone -> Parakeet on sherpa-onnx (the Android app's
//! engine) -> the Android cleanup rules -> text pasted into the focused app. Lives in the tray / menu bar; the main window is settings, history and
//! the personal dictionary; a small click-through overlay shows the mic level while dictating.

mod audio;
mod focus;
mod history;
mod insert;
mod models;
mod settings;
mod stt;
mod text_cleanup;
mod updater;

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

pub struct AppState {
    settings: Mutex<Settings>,
    settings_path: PathBuf,
    models_dir: PathBuf,
    history: Mutex<History>,
    recorder: audio::Recorder,
    engine: Mutex<Option<LoadedEngine>>,
    status: Mutex<Status>,
    busy: AtomicBool,
    downloading: AtomicBool,
    /// Where the current dictation's text goes: true = paste into the focused app, false = the in-app test box.
    to_focused_app: AtomicBool,
    /// Name of the app that had focus when the current dictation started (per-app dictionary, 1.5).
    focused_app: Arc<Mutex<Option<String>>>,
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
/// verbatim mode keeps every word), then spoken lists as bullets.
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
    let opts = if s.clean_mode {
        text_cleanup::Opts {
            remove_fillers: s.remove_fillers,
            voice_commands: s.voice_commands,
            self_corrections: s.self_corrections,
            strict: s.accuracy_mode,
        }
    } else {
        text_cleanup::Opts { remove_fillers: false, voice_commands: s.voice_commands, self_corrections: false, strict: false }
    };
    let mut text = text_cleanup::apply(&replaced, &terms, opts);
    if s.clean_mode && s.format_lists {
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

fn start_dictation(app: &AppHandle, to_focused_app: bool) -> Result<(), String> {
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
    // Which app the text is for (per-app dictionary). Looked up off the hotkey thread; it is ready long before
    // the transcript is.
    if let Ok(mut g) = state.focused_app.lock() {
        *g = None;
    }
    if to_focused_app && s.dictionary.iter().any(|d| !d.apps.is_empty()) {
        let slot = state.focused_app.clone();
        std::thread::spawn(move || {
            let name = focus::focused_app();
            if let Ok(mut g) = slot.lock() {
                *g = name;
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
    set_status(app, "recording", "Listening");
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
    let engine = state.engine.lock().ok().and_then(|g| g.as_ref().map(|e| e.engine.clone()));
    let Some(engine) = engine else {
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
    set_status(app, "transcribing", "Transcribing");
    let app = app.clone();
    tauri::async_runtime::spawn_blocking(move || {
        let state = app.state::<AppState>();
        let s = state.settings.lock().map(|g| g.clone()).unwrap_or_default();
        let pcm = stt::to_16k(&samples, rate);
        let audio_ms = pcm.len() as u64 * 1000 / stt::SAMPLE_RATE as u64;
        // All silence (an accidental press, a muted mic): insert nothing rather than let the model guess.
        let vad = humming_core::VadConfig::default();
        let has_speech = !humming_core::vad::speech_ranges(&pcm, stt::SAMPLE_RATE, &vad).is_empty();
        let raw = if has_speech { engine.transcribe(&pcm) } else { String::new() };
        hide_overlay(&app);
        let focused = state.focused_app.lock().ok().and_then(|g| g.clone());
        let text = finalize_text_for(&raw, &s, focused.as_deref());
        let latency_ms = released.elapsed().as_millis() as u64;
        let mut error = None;
        let mut inserted = false;
        if !text.trim().is_empty() && to_focused_app {
            match insert::insert(&text, &s.insert_method, s.restore_clipboard, s.paste_delay_ms) {
                Ok(()) => inserted = true,
                Err(e) => {
                    // Keep the words: leave them on the clipboard so nothing is lost.
                    let _ = insert::copy(&text);
                    error = Some(format!("{e}. The text is on your clipboard."));
                }
            }
        }
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

fn on_hotkey(app: &AppHandle, pressed: bool) {
    let state = app.state::<AppState>();
    let mode = state.settings.lock().map(|g| g.mode.clone()).unwrap_or_else(|_| "hold".into());
    let recording = state.recorder.is_recording();
    if mode == "toggle" {
        if !pressed {
            return;
        }
        if recording {
            stop_dictation(app);
        } else if let Err(e) = start_dictation(app, true) {
            set_status(app, "error", e);
        }
    } else if pressed {
        // Key repeat sends more Pressed events while held: start once.
        if !recording {
            if let Err(e) = start_dictation(app, true) {
                set_status(app, "error", e);
            }
        }
    } else if recording {
        stop_dictation(app);
    }
}

fn register_hotkey(app: &AppHandle, hotkey: &str) -> Result<(), String> {
    let gs = app.global_shortcut();
    let _ = gs.unregister_all();
    gs.register(hotkey).map_err(|e| format!("cannot use the shortcut \"{hotkey}\": {e}"))
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
    let new = settings.sanitized();
    let old = state.settings.lock().map(|g| g.clone()).unwrap_or_default();
    if new.hotkey != old.hotkey {
        if let Err(e) = register_hotkey(&app, &new.hotkey) {
            let _ = register_hotkey(&app, &old.hotkey);
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
    start_dictation(&app, false)
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
fn app_info(state: State<'_, AppState>) -> serde_json::Value {
    serde_json::json!({
        "version": env!("CARGO_PKG_VERSION"),
        "os": std::env::consts::OS,
        "arch": std::env::consts::ARCH,
        "models_dir": state.models_dir.display().to_string(),
        "history_file": state.history.lock().map(|h| h.path().display().to_string()).unwrap_or_default(),
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
                .with_handler(|app, _shortcut, event| on_hotkey(app, event.state() == ShortcutState::Pressed))
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
            let settings = Settings::load(&settings_path);
            let models_dir = match std::env::var_os("HUMMING_MODELS_DIR") {
                Some(d) if !d.is_empty() => PathBuf::from(d),
                _ => data_dir.join("models"),
            };
            let history = History::load(data_dir.join("history.json"));
            app.manage(AppState {
                settings: Mutex::new(settings.clone()),
                settings_path,
                models_dir,
                history: Mutex::new(history),
                recorder: audio::Recorder::default(),
                engine: Mutex::new(None),
                status: Mutex::new(Status { state: "loading".into(), message: String::new() }),
                busy: AtomicBool::new(false),
                downloading: AtomicBool::new(false),
                to_focused_app: AtomicBool::new(true),
                focused_app: Arc::new(Mutex::new(None)),
            });

            // Tray / menu bar.
            let open = MenuItem::with_id(app, "open", "Open Humming", true, None::<&str>)?;
            let history_item = MenuItem::with_id(app, "history", "History", true, None::<&str>)?;
            let settings_item = MenuItem::with_id(app, "settings", "Settings", true, None::<&str>)?;
            let sep = PredefinedMenuItem::separator(app)?;
            let quit = MenuItem::with_id(app, "quit", "Quit Humming", true, None::<&str>)?;
            let menu = Menu::with_items(app, &[&open, &history_item, &settings_item, &sep, &quit])?;
            TrayIconBuilder::with_id("main")
                .icon(Image::from_bytes(TRAY_IDLE)?)
                .tooltip("Humming")
                .menu(&menu)
                .show_menu_on_left_click(false)
                .on_menu_event(|app, event| match event.id.as_ref() {
                    "open" => show_main(app, Some("home")),
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

            if let Err(e) = register_hotkey(&handle, &settings.hotkey) {
                set_status(&handle, "error", e);
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
}
