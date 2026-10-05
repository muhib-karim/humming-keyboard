//! Self-update (1.6): the signed Tauri updater.
//!
//! Humming checks its update feed (`plugins.updater.endpoints` in tauri.conf.json: the latest.json that CI publishes
//! with every GitHub Release) 45 seconds after launch and every 4 hours after that, and whenever the user presses
//! "Check for updates". Every download is checked against the minisign public key embedded in tauri.conf.json before
//! it is installed; an unsigned or tampered file is refused by the updater plugin.
//!
//! With "Automatically install updates" on (the default) a newer version is downloaded, installed and Humming
//! relaunches, but never in the middle of a dictation or a model download: it waits until both are finished. With the
//! switch off, Humming only shows "Update available" and installs when the user clicks "Install now".
//! A Linux .deb install cannot replace itself (the AppImage can), so there Humming only reports the new version.

use crate::AppState;
use once_cell::sync::Lazy;
use serde::Serialize;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Mutex;
use std::time::{Duration, SystemTime, UNIX_EPOCH};
use tauri::{AppHandle, Emitter, Manager};
use tauri_plugin_updater::UpdaterExt;

/// First check after launch, then the interval between background checks.
pub const FIRST_CHECK: Duration = Duration::from_secs(45);
pub const CHECK_EVERY: Duration = Duration::from_secs(4 * 60 * 60);
/// Where a user can download a build by hand (shown when the app cannot replace itself).
pub const RELEASES_PAGE: &str = "https://humming-keyboard.pages.dev/";

#[derive(Debug, Clone, Serialize, PartialEq, Default)]
pub struct UpdateStatus {
    /// idle | checking | up_to_date | available | downloading | installing | restarting | error
    pub state: String,
    pub current: String,
    pub latest: Option<String>,
    pub notes: Option<String>,
    pub message: String,
    /// Bytes downloaded so far and the total, while downloading.
    pub done: u64,
    pub total: u64,
    /// Unix seconds of the last finished check (0 = never).
    pub last_checked: u64,
    /// False where this install cannot replace itself (Linux .deb): the update is shown, not installed.
    pub can_install: bool,
}

static STATUS: Lazy<Mutex<UpdateStatus>> = Lazy::new(|| Mutex::new(UpdateStatus::default()));
static RUNNING: AtomicBool = AtomicBool::new(false);

/// Can this build replace itself? Windows (NSIS/MSI) and macOS (.app) can; on Linux only the AppImage can.
pub fn can_self_install() -> bool {
    if cfg!(target_os = "linux") {
        std::env::var_os("APPIMAGE").is_some()
    } else {
        true
    }
}

/// Pure decision, unit-tested: what to do once a check has found `latest` (None = nothing newer).
#[derive(Debug, PartialEq)]
pub enum Plan {
    UpToDate,
    Notify,
    Install,
}

pub fn plan(newer_found: bool, auto_install: bool, user_asked_install: bool, can_install: bool) -> Plan {
    if !newer_found {
        Plan::UpToDate
    } else if can_install && (auto_install || user_asked_install) {
        Plan::Install
    } else {
        Plan::Notify
    }
}

fn now() -> u64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_secs()).unwrap_or(0)
}

fn publish(app: &AppHandle, f: impl FnOnce(&mut UpdateStatus)) -> UpdateStatus {
    let snapshot = {
        let mut g = STATUS.lock().unwrap_or_else(|p| p.into_inner());
        if g.current.is_empty() {
            g.current = app.package_info().version.to_string();
            g.can_install = can_self_install();
            g.state = "idle".into();
        }
        f(&mut g);
        g.clone()
    };
    let _ = app.emit("update-status", snapshot.clone());
    snapshot
}

pub fn status(app: &AppHandle) -> UpdateStatus {
    publish(app, |_| {})
}

fn auto_install_on(app: &AppHandle) -> bool {
    app.state::<AppState>().settings.lock().map(|g| g.auto_update).unwrap_or(true)
}

/// Dictating or downloading a model right now? Then a relaunch waits.
fn busy(app: &AppHandle) -> bool {
    let st = app.state::<AppState>();
    st.recorder.is_recording() || st.busy.load(Ordering::SeqCst) || st.downloading.load(Ordering::SeqCst)
}

/// One check, and (per [plan]) the download, install and relaunch. `user_asked_install` = the "Install now" button.
pub async fn run_once(app: AppHandle, user_asked_install: bool) -> UpdateStatus {
    if RUNNING.swap(true, Ordering::SeqCst) {
        return status(&app);
    }
    let out = run_inner(&app, user_asked_install).await;
    RUNNING.store(false, Ordering::SeqCst);
    match out {
        Ok(s) => s,
        Err(e) => publish(&app, |s| {
            s.state = "error".into();
            s.message = e;
            s.last_checked = now();
        }),
    }
}

async fn run_inner(app: &AppHandle, user_asked_install: bool) -> Result<UpdateStatus, String> {
    publish(app, |s| {
        s.state = "checking".into();
        s.message = "Checking for updates".into();
    });
    let updater = app.updater().map_err(|e| format!("update check unavailable: {e}"))?;
    let found = updater.check().await.map_err(|e| format!("could not check for updates: {e}"))?;
    let Some(update) = found else {
        return Ok(publish(app, |s| {
            s.state = "up_to_date".into();
            s.latest = None;
            s.notes = None;
            s.message = "Humming is up to date".into();
            s.last_checked = now();
        }));
    };
    let version = update.version.clone();
    let can_install = can_self_install();
    let decision = plan(true, auto_install_on(app), user_asked_install, can_install);
    let st = publish(app, |s| {
        s.state = "available".into();
        s.latest = Some(version.clone());
        s.notes = update.body.clone();
        s.last_checked = now();
        s.message = if can_install {
            format!("Humming {version} is available")
        } else {
            format!("Humming {version} is available. This install cannot update itself: download it from {RELEASES_PAGE}")
        };
    });
    if decision != Plan::Install {
        return Ok(st);
    }
    // Never cut off a dictation or a model download: wait for both to finish (checked every 15 s).
    while busy(app) {
        tauri::async_runtime::spawn_blocking(|| std::thread::sleep(Duration::from_secs(15)))
            .await
            .map_err(|e| e.to_string())?;
    }
    publish(app, |s| {
        s.state = "downloading".into();
        s.message = format!("Downloading Humming {version}");
        s.done = 0;
        s.total = 0;
    });
    let app_dl = app.clone();
    let mut done: u64 = 0;
    update
        .download_and_install(
            move |chunk, total| {
                done += chunk as u64;
                let d = done;
                publish(&app_dl, |s| {
                    s.done = d;
                    s.total = total.unwrap_or(0);
                });
            },
            || {},
        )
        .await
        .map_err(|e| format!("update {version} was not installed: {e}"))?;
    publish(app, |s| {
        s.state = "restarting".into();
        s.message = format!("Humming {version} is installed. Restarting");
    });
    app.restart();
}

/// Background schedule: first check shortly after launch, then every [CHECK_EVERY].
pub fn start_background(app: &AppHandle) {
    if std::env::var_os("HUMMING_NO_UPDATE_CHECK").is_some() {
        return;
    }
    let app = app.clone();
    tauri::async_runtime::spawn(async move {
        let mut wait = FIRST_CHECK;
        loop {
            let _ = tauri::async_runtime::spawn_blocking(move || std::thread::sleep(wait)).await;
            let _ = run_once(app.clone(), false).await;
            wait = CHECK_EVERY;
        }
    });
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn nothing_newer_means_up_to_date() {
        assert_eq!(plan(false, true, true, true), Plan::UpToDate);
    }

    #[test]
    fn auto_install_on_installs() {
        assert_eq!(plan(true, true, false, true), Plan::Install);
    }

    #[test]
    fn auto_install_off_only_notifies_until_asked() {
        assert_eq!(plan(true, false, false, true), Plan::Notify);
        assert_eq!(plan(true, false, true, true), Plan::Install);
    }

    #[test]
    fn a_build_that_cannot_replace_itself_only_notifies() {
        assert_eq!(plan(true, true, true, false), Plan::Notify);
    }

    #[test]
    fn the_schedule_is_a_few_hours() {
        assert!(FIRST_CHECK.as_secs() <= 120);
        assert_eq!(CHECK_EVERY.as_secs(), 4 * 3600);
    }

    #[test]
    fn the_config_carries_the_updater_key_and_feed() {
        let cfg: serde_json::Value = serde_json::from_str(include_str!("../tauri.conf.json")).unwrap();
        let up = &cfg["plugins"]["updater"];
        assert!(up["pubkey"].as_str().unwrap().len() > 100, "embedded minisign public key");
        let eps: Vec<&str> = up["endpoints"].as_array().unwrap().iter().map(|e| e.as_str().unwrap()).collect();
        assert!(!eps.is_empty() && eps.iter().all(|e| e.starts_with("https://") && e.ends_with("latest.json")));
        assert_eq!(cfg["bundle"]["createUpdaterArtifacts"], serde_json::Value::Bool(true));
    }
}
