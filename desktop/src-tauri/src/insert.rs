//! Puts text into whatever app has keyboard focus: clipboard + Ctrl/Cmd+V (default), or synthetic typing.

use enigo::{Direction, Enigo, Key, Keyboard, Settings};
use std::thread::sleep;
use std::time::Duration;

fn enigo() -> Result<Enigo, String> {
    Enigo::new(&Settings::default()).map_err(|e| {
        if cfg!(target_os = "macos") {
            format!("cannot send keystrokes ({e}). Allow Humming in System Settings > Privacy & Security > Accessibility")
        } else {
            format!("cannot send keystrokes ({e})")
        }
    })
}

fn paste_chord(e: &mut Enigo) -> Result<(), String> {
    #[cfg(target_os = "macos")]
    let (modifier, v) = (Key::Meta, Key::Unicode('v'));
    #[cfg(target_os = "windows")]
    let (modifier, v) = (Key::Control, Key::Other(0x56)); // VK_V: layout independent
    #[cfg(not(any(target_os = "macos", target_os = "windows")))]
    let (modifier, v) = (Key::Control, Key::Unicode('v'));
    e.key(modifier, Direction::Press).map_err(|x| x.to_string())?;
    let r = e.key(v, Direction::Click).map_err(|x| x.to_string());
    e.key(modifier, Direction::Release).map_err(|x| x.to_string())?;
    r
}

/// Edit by voice (1.8): delete the last `n` characters Humming itself typed, with Backspace presses.
pub fn erase(n: usize) -> Result<(), String> {
    if n == 0 {
        return Ok(());
    }
    let mut e = enigo()?;
    for _ in 0..n {
        e.key(Key::Backspace, Direction::Click).map_err(|x| x.to_string())?;
    }
    Ok(())
}

pub fn insert(text: &str, method: &str, restore_clipboard: bool, paste_delay_ms: u64) -> Result<(), String> {
    if text.is_empty() {
        return Ok(());
    }
    if method == "type" {
        let mut e = enigo()?;
        return e.text(text).map_err(|x| x.to_string());
    }
    let mut cb = arboard::Clipboard::new().map_err(|e| format!("clipboard unavailable: {e}"))?;
    let previous = if restore_clipboard { cb.get_text().ok() } else { None };
    cb.set_text(text.to_string()).map_err(|e| format!("clipboard write failed: {e}"))?;
    sleep(Duration::from_millis(40));
    let mut e = enigo()?;
    paste_chord(&mut e)?;
    // Give the target app time to read the clipboard before it is restored.
    sleep(Duration::from_millis(paste_delay_ms));
    if let Some(p) = previous {
        let _ = cb.set_text(p);
    }
    Ok(())
}

pub fn copy(text: &str) -> Result<(), String> {
    let mut cb = arboard::Clipboard::new().map_err(|e| format!("clipboard unavailable: {e}"))?;
    cb.set_text(text.to_string()).map_err(|e| format!("clipboard write failed: {e}"))
}
