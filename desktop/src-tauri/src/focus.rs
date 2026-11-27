//! The name of the app that has keyboard focus, for per-app dictionary entries (1.5). Best effort: `None` when the
//! platform will not say (a pure Wayland session, a sandbox); per-app entries are then skipped and global ones apply.

#[cfg(target_os = "windows")]
pub fn focused_app() -> Option<String> {
    use windows_sys::Win32::Foundation::CloseHandle;
    use windows_sys::Win32::System::Threading::{
        OpenProcess, QueryFullProcessImageNameW, PROCESS_NAME_WIN32, PROCESS_QUERY_LIMITED_INFORMATION,
    };
    use windows_sys::Win32::UI::WindowsAndMessaging::{GetForegroundWindow, GetWindowThreadProcessId};
    unsafe {
        let hwnd = GetForegroundWindow();
        if hwnd.is_null() {
            return None;
        }
        let mut pid = 0u32;
        GetWindowThreadProcessId(hwnd, &mut pid);
        if pid == 0 {
            return None;
        }
        let h = OpenProcess(PROCESS_QUERY_LIMITED_INFORMATION, 0, pid);
        if h.is_null() {
            return None;
        }
        let mut buf = [0u16; 1024];
        let mut len = buf.len() as u32;
        let ok = QueryFullProcessImageNameW(h, PROCESS_NAME_WIN32, buf.as_mut_ptr(), &mut len);
        CloseHandle(h);
        if ok == 0 {
            return None;
        }
        let path = String::from_utf16_lossy(&buf[..len as usize]);
        std::path::Path::new(&path).file_stem().map(|s| s.to_string_lossy().into_owned())
    }
}

#[cfg(target_os = "macos")]
pub fn focused_app() -> Option<String> {
    // lsappinfo ships with macOS and needs no Automation or Accessibility permission.
    let asn = run("lsappinfo", &["front"])?;
    let info = run("lsappinfo", &["info", "-only", "name", asn.trim()])?;
    parse_lsappinfo_name(&info)
}

#[cfg(all(unix, not(target_os = "macos")))]
pub fn focused_app() -> Option<String> {
    // X11 / XWayland: xdotool when installed. Wayland compositors do not expose the focused app.
    let class = run("xdotool", &["getactivewindow", "getwindowclassname"])?;
    let class = class.trim();
    (!class.is_empty()).then(|| class.to_string())
}

/// Normalizes and caps a window title before sending it as optional AI context.
pub fn clean_window_title(raw: &str) -> Option<String> {
    let collapsed = raw.split_whitespace().collect::<Vec<_>>().join(" ");
    let trimmed = collapsed.trim();
    if trimmed.is_empty() {
        return None;
    }
    Some(trimmed.chars().take(120).collect())
}

#[cfg(target_os = "windows")]
pub fn focused_window_title() -> Option<String> {
    use windows_sys::Win32::UI::WindowsAndMessaging::{GetForegroundWindow, GetWindowTextW};
    unsafe {
        let hwnd = GetForegroundWindow();
        if hwnd.is_null() {
            return None;
        }
        let mut buf = [0u16; 512];
        let len = GetWindowTextW(hwnd, buf.as_mut_ptr(), buf.len() as i32);
        if len <= 0 {
            return None;
        }
        clean_window_title(&String::from_utf16_lossy(&buf[..len as usize]))
    }
}

#[cfg(target_os = "macos")]
pub fn focused_window_title() -> Option<String> {
    None
}

#[cfg(all(unix, not(target_os = "macos")))]
pub fn focused_window_title() -> Option<String> {
    let title = run("xdotool", &["getactivewindow", "getwindowname"])?;
    clean_window_title(&title)
}

#[cfg(unix)]
fn run(cmd: &str, args: &[&str]) -> Option<String> {
    let out = std::process::Command::new(cmd).args(args).output().ok()?;
    out.status.success().then(|| String::from_utf8_lossy(&out.stdout).into_owned())
}

/// `"LSDisplayName"="Slack"` -> `Slack`.
#[allow(dead_code)]
pub fn parse_lsappinfo_name(s: &str) -> Option<String> {
    let v = s.split('=').nth(1)?.trim().trim_matches('"').trim();
    (!v.is_empty()).then(|| v.to_string())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_lsappinfo_output() {
        assert_eq!(parse_lsappinfo_name("\"LSDisplayName\"=\"Slack\"\n").as_deref(), Some("Slack"));
        assert_eq!(parse_lsappinfo_name("").as_deref(), None);
    }

    #[test]
    fn cleans_and_caps_window_title() {
        assert_eq!(clean_window_title("  PR #31   - Visual Studio Code  ").as_deref(), Some("PR #31 - Visual Studio Code"));
        assert_eq!(clean_window_title("   \n\t "), None);
        let long = "a".repeat(200);
        assert_eq!(clean_window_title(&long).unwrap().chars().count(), 120);
    }
}
