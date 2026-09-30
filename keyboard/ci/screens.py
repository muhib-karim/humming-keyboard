#!/usr/bin/env python3
"""Humming M4 (added): drive the debug APK on an emulator and capture keyboard screenshots.

usage: screens.py <debug.apk> <out_dir> [clip.wav]
Light and dark: empty field, typing with suggestions, swipe-typing trail, voice panel (preview state), and a real
dictation (a WAV file played as the microphone through the on-device engine) with its result committed on Done.
"""
import json, os, subprocess, sys, time

PKG = "com.humming.keyboard.debug"
IME = PKG + "/dev.patrickgold.florisboard.FlorisImeService"
ACT = PKG + "/com.humming.keyboard.debug.TestFieldActivity"
RCV = PKG + "/com.humming.keyboard.debug.DebugVoiceReceiver"
apk, out = sys.argv[1], sys.argv[2]
clip = sys.argv[3] if len(sys.argv) > 3 else None
os.makedirs(out, exist_ok=True)
log = open(os.path.join(out, "screens.log"), "w")
results = {}


def sh(cmd, check=False, timeout=120):
    r = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout)
    log.write(f"$ {cmd}\n{r.stdout}{r.stderr}\n"); log.flush()
    if check and r.returncode != 0:
        raise SystemExit(f"failed: {cmd}\n{r.stderr}")
    return r.stdout.strip()


def adb(c, timeout=120):
    # Pass the device command as one argument so its own quoting reaches the device shell intact.
    r = subprocess.run(["adb", "shell", c], capture_output=True, text=True, timeout=timeout)
    log.write(f"$ adb shell {c}\n{r.stdout}{r.stderr}\n"); log.flush()
    return r.stdout.strip()


def shot(name):
    path = os.path.join(out, name + ".png")
    sh(f"adb exec-out screencap -p > {path}")
    results[name] = os.path.getsize(path) if os.path.exists(path) else 0
    print("shot", name, results[name], flush=True)
    return path


def open_field(wait_keys=False):
    # A fresh task (NEW_TASK|CLEAR_TASK) instead of "am start -S": force-stopping the package would also kill the
    # keyboard's own process, so every shot would catch the input method cold-starting.
    adb(f"am start -W -f 0x10008000 -n {ACT}")
    time.sleep(3)
    if wait_keys:
        wait_keyboard()


def wait_keyboard(limit=45):
    """Poll until the key rows are drawn (the first open after install loads layouts and the dictionary)."""
    t0 = time.time()
    tmp = os.path.join(out, "_probe.png")
    while time.time() - t0 < limit:
        sh(f"adb exec-out screencap -p > {tmp}")
        lay = key_centers(tmp)
        if lay and len(lay["rows"]) >= 4:
            results.setdefault("keyboard_ready_s", []).append(round(time.time() - t0, 1))
            time.sleep(1.0)
            break
        time.sleep(2)
    if os.path.exists(tmp):
        os.remove(tmp)


def wait_log(tag, limit=10):
    t0 = time.time()
    while time.time() - t0 < limit:
        if tag in sh("adb logcat -d -s HUMMING:I"):
            return True
        time.sleep(1)
    return False


def broadcast(action, extra=""):
    adb(f"am broadcast -a com.humming.keyboard.debug.{action} -n {RCV} {extra}")


def key_centers(png):
    """Find the letter keys from a screenshot: rows of key-coloured rectangles in the keyboard area."""
    try:
        from PIL import Image
    except ImportError:
        return None
    im = Image.open(png).convert("RGB")
    w, h = im.size
    px = im.load()
    def is_key(c, bgc):
        return abs(c[0] - bgc[0]) + abs(c[1] - bgc[1]) + abs(c[2] - bgc[2]) > 24
    # keyboard background: sample near the bottom-left gap
    rows = []
    x_probe = int(w * 0.055)  # centre of the first letter key ("q")
    bgc = px[2, int(h * 0.97)] if h else (0, 0, 0)
    y = int(h * 0.55)
    inside = False
    start = 0
    while y < h - 2:
        c = px[x_probe, y]
        k = is_key(c, bgc)
        if k and not inside:
            inside, start = True, y
        elif not k and inside:
            inside = False
            if y - start > h * 0.025:
                rows.append((start, y))
        y += 1
    return {"size": [w, h], "rows": rows}


def glide(word_keys, png_name):
    # word_keys: list of (x, y) px
    x, y = word_keys[0]
    adb(f"input motionevent DOWN {x} {y}")
    for (nx, ny) in word_keys[1:]:
        steps = 6
        for i in range(1, steps + 1):
            ix = int(x + (nx - x) * i / steps); iy = int(y + (ny - y) * i / steps)
            adb(f"input motionevent MOVE {ix} {iy}")
        x, y = nx, ny
    shot(png_name)
    adb(f"input motionevent UP {x} {y}")


def run_theme(theme):
    adb(f"cmd uimode night {'yes' if theme == 'dark' else 'no'}")
    time.sleep(2)
    open_field(wait_keys=True)
    empty = shot(f"{theme}_empty")
    # typing: suggestions strip
    sh("adb logcat -c")
    adb("input text 'Sounds%sgre'")
    wait_log("HUMMING_SUGGEST word=gre", 12)
    time.sleep(1.5)
    shot(f"{theme}_typing")
    # next-word predictions after a finished word, on a fresh field
    open_field(wait_keys=True)
    adb("input text 'See%syou%s'")
    time.sleep(4)
    shot(f"{theme}_next")
    results[f"{theme}_suggest_log"] = [l for l in sh("adb logcat -d -s HUMMING:I").splitlines() if "SUGGEST" in l][-6:]
    # swipe typing trail over h-e-l-l-o on a fresh field
    open_field(wait_keys=True)
    layout = key_centers(empty)
    results[f"{theme}_layout"] = layout
    probe = shot(f"{theme}_swipe_probe")
    visible = key_centers(probe)
    if layout and len(layout["rows"]) >= 4 and visible and len(visible["rows"]) >= 4:
        w = layout["size"][0]
        r = layout["rows"][-4:]  # last four key rows: qwerty, asdf, zxcv, bottom
        cy = [(a + b) // 2 for a, b in r]
        kw = w / 10.0
        # One continuous device-side swipe (separate adb calls per point are far slower than a finger, so the
        # glide detector never fires): t -> o along the top row, the word "to". Shot taken mid-gesture for the trail.
        tx, ty = int(kw * 4.5), cy[0]
        ox, oy = int(kw * 8.5), cy[0]
        subprocess.Popen(["adb", "shell", f"input swipe {tx} {ty} {ox} {oy} 900"])
        time.sleep(0.75)
        shot(f"{theme}_swipe")
        time.sleep(2.0)
        shot(f"{theme}_swipe_result")
    # voice panel (preview state; the emulator has no microphone)
    open_field()
    broadcast("VOICE_PREVIEW", "--es text 'Running ten minutes late, save me a seat and order the dumplings'")
    time.sleep(2)
    shot(f"{theme}_voice")
    broadcast("VOICE_CLOSE")
    time.sleep(1)


sh(f"adb install -r -g {apk}", check=True, timeout=300)
# A slow emulator can trip "isn't responding" dialogs on any app; keep them off the screenshots.
adb("settings put global hide_error_dialogs 1")
# Reference: the emulator's stock keyboard (Gboard on Google APIs images) on the same screen, before switching.
results["stock_ime"] = adb("settings get secure default_input_method")
for t in ("light", "dark"):
    adb(f"cmd uimode night {'yes' if t == 'dark' else 'no'}")
    time.sleep(2)
    open_field()
    shot(f"stock_{t}_empty")
    adb("input text 'Sounds%sgre'")
    time.sleep(2.5)
    shot(f"stock_{t}_typing")
adb("cmd uimode night no")
# The input method service is registered a moment after install; wait for it, then make it the only keyboard.
for _ in range(60):
    if IME in adb("ime list -a -s"):
        break
    time.sleep(2)
adb(f"ime enable {IME}")
adb(f"ime set {IME}")
for other in adb("ime list -s").split():
    if other != IME:
        adb(f"ime disable {other}")
results["default_ime"] = adb("settings get secure default_input_method")
results["enabled_imes"] = adb("ime list -s")
if results["default_ime"] != IME:
    log.write("WARNING: Humming is not the default keyboard\n")
adb(f"pm grant {PKG} android.permission.RECORD_AUDIO")
adb("settings put system screen_off_timeout 1800000")
adb("svc power stayon true")
# First open, then the one-time voice model download (Settings > Voice shows it), before any shot.
adb("cmd uimode night no")
sh("adb logcat -c")
open_field(wait_keys=True)
broadcast("MODEL_DOWNLOAD")
time.sleep(6)
adb(f"am start -W -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d ui://florisboard/settings/voice -p {PKG}")
time.sleep(4)
shot("settings_voice_downloading")
t0 = time.time()
while time.time() - t0 < 480:
    if "HUMMING_MODEL ready" in sh("adb logcat -d -s HUMMING:I"):
        break
    time.sleep(5)
results["prep_model_s"] = round(time.time() - t0)
results["model_log"] = [l for l in sh("adb logcat -d -s HUMMING:*").splitlines() if "HUMMING_MODEL" in l][-6:]
adb("input keyevent KEYCODE_BACK")
time.sleep(1)
adb(f"am start -W -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d ui://florisboard/settings/voice -p {PKG}")
time.sleep(4)
shot("settings_voice_ready")


def field_text():
    adb("uiautomator dump /sdcard/ui.xml")
    xml = adb("cat /sdcard/ui.xml")
    import re
    m = re.findall(r'class="android.widget.EditText"[^>]*?text="([^"]*)"|text="([^"]*)"[^>]*?class="android.widget.EditText"', xml)
    return [a or b for a, b in m]


# OFFLINE PROOF: airplane mode, Wi-Fi and mobile data off, the keyboard process killed and cold-started with no
# network, then a real dictation (a WAV file played as the microphone) committed into the field on Done.
if clip:
    sh(f"adb push {clip} /data/local/tmp/clip.wav")
    adb("chmod 644 /data/local/tmp/clip.wav")
    adb("cmd connectivity airplane-mode enable")
    adb("svc wifi disable")
    adb("svc data disable")
    time.sleep(8)
    results["offline_airplane"] = adb("settings get global airplane_mode_on")
    results["offline_wifi"] = adb("cmd wifi status")[:200]
    results["offline_ping"] = adb("ping -c 1 -W 3 8.8.8.8; echo EXIT=$?")[-200:]
    results["offline_default_network"] = [l for l in adb("dumpsys connectivity").splitlines() if "Active default network" in l][:2]
    adb(f"am force-stop {PKG}")
    time.sleep(2)
    adb("cmd uimode night no")
    open_field(wait_keys=True)
    results["offline_pid"] = adb(f"pidof {PKG}")
    sh("adb logcat -c")
    rec = subprocess.Popen(["adb", "shell", "screenrecord --size 720x1600 --bit-rate 6000000 --time-limit 45 /sdcard/demo.mp4"])
    time.sleep(1.5)
    shot("offline_empty")
    broadcast("VOICE_TEST", "--es wav /data/local/tmp/clip.wav")
    for i in range(8):
        time.sleep(1.5)
        shot(f"offline_live_{i}")
    broadcast("VOICE_DONE")
    t1 = time.time()
    while time.time() - t1 < 240:
        if "HUMMING_FINAL" in sh("adb logcat -d -s HUMMING:I"):
            break
        time.sleep(1)
    results["offline_stop_to_final_s"] = round(time.time() - t1, 1)
    time.sleep(2.5)
    shot("offline_committed")
    time.sleep(3)
    try:
        rec.wait(timeout=50)
    except Exception:
        rec.kill()
    time.sleep(2)
    sh(f"adb pull /sdcard/demo.mp4 {os.path.join(out, 'demo.mp4')}", timeout=120)
    results["offline_field_text"] = field_text()
    results["offline_log"] = [l for l in sh("adb logcat -d -s HUMMING:*").splitlines() if "HUMMING_" in l][-25:]
    results["offline_airplane_after"] = adb("settings get global airplane_mode_on")
    adb("cmd connectivity airplane-mode disable")
    adb("svc wifi enable")
    adb("svc data enable")
    time.sleep(5)

for t in ("light", "dark"):
    try:
        run_theme(t)
    except Exception as e:
        log.write(f"theme {t} failed: {e}\n")

# Clipboard history (grid, timestamps) and the long-press edit shortcuts.
try:
    adb("cmd uimode night no")
    time.sleep(2)
    open_field(wait_keys=True)
    broadcast("CLIP_SEED", "--es items 'Meet at 7:30 by the north entrance|https://example.com/menu|Order #48213 ships Friday|0412 555 019|Thanks, see you tomorrow!|Parakeet runs fully on the phone'")
    time.sleep(1)
    broadcast("UI_MODE", "--es mode clipboard")
    time.sleep(2.5)
    shot("light_clipboard")
    broadcast("UI_MODE", "--es mode text")
    time.sleep(1.5)
    probe = shot("edit_probe")
    lay = key_centers(probe)
    if lay and len(lay["rows"]) >= 4:
        w = lay["size"][0]
        r = lay["rows"][-4:]
        cy = [(a + b) // 2 for a, b in r]
        kw = w / 10.0
        cx, ccy = int(kw * 4.0), cy[2]
        subprocess.Popen(["adb", "shell", f"input swipe {cx} {ccy} {cx} {ccy} 1500"])
        time.sleep(1.0)
        shot("light_edit_popup")
        time.sleep(1.5)
except Exception as e:
    log.write(f"clipboard/edit failed: {e}\n")

# Long-press voice menu (the Gboard hand-off itself runs on a Google Play image: ci/handoff.py).
try:
    adb("cmd uimode night no")
    time.sleep(1)
    open_field(wait_keys=True)
    broadcast("VOICE_MENU", "--ez on true")
    time.sleep(2)
    shot("voice_menu")
    broadcast("VOICE_MENU", "--ez on false")
except Exception as e:
    log.write(f"voice menu failed: {e}\n")

# Split keyboard in landscape.
try:
    adb("settings put system accelerometer_rotation 0")
    adb("settings put system user_rotation 1")
    time.sleep(3)
    open_field(wait_keys=False)
    time.sleep(5)
    shot("landscape_split")
    adb("settings put system user_rotation 0")
    time.sleep(3)
except Exception as e:
    log.write(f"landscape failed: {e}\n")

sh("adb root", timeout=60)
time.sleep(3)
sh(f"adb pull /data/anr {os.path.join(out, 'anr')}", timeout=120)
results["anr_events"] = [l for l in sh("adb logcat -d -b events").splitlines() if "am_anr" in l][-10:]
crash = sh("adb logcat -d -b crash")
results["crash_log"] = crash[-4000:]
json.dump(results, open(os.path.join(out, "results.json"), "w"), indent=2)
print(json.dumps({k: v for k, v in results.items() if k != "crash_log"}, indent=1))
