#!/usr/bin/env python3
"""Humming M5: Gboard voice hand-off on a Google Play emulator image (Gboard preinstalled).

usage: handoff.py <debug.apk> <out_dir>
Long-press Humming's mic -> the voice menu -> "Gboard voice typing" -> Gboard is the active keyboard (its voice
subtype when it declares one) -> Gboard's mic -> back to Humming. Everything observed goes to handoff.json.
"""
import json, os, re, subprocess, sys, time

PKG = "com.humming.keyboard.debug"
IME = PKG + "/dev.patrickgold.florisboard.FlorisImeService"
ACT = PKG + "/com.humming.keyboard.debug.TestFieldActivity"
RCV = PKG + "/com.humming.keyboard.debug.DebugVoiceReceiver"
GB = "com.google.android.inputmethod.latin"
apk, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)
log = open(os.path.join(out, "handoff.log"), "w")
res = {}


def sh(cmd, timeout=120):
    r = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=timeout)
    log.write(f"$ {cmd}\n{r.stdout}{r.stderr}\n"); log.flush()
    return r.stdout.strip()


def adb(c, timeout=120):
    r = subprocess.run(["adb", "shell", c], capture_output=True, text=True, timeout=timeout)
    log.write(f"$ adb shell {c}\n{r.stdout}{r.stderr}\n"); log.flush()
    return r.stdout.strip()


def shot(name):
    p = os.path.join(out, name + ".png")
    sh(f"adb exec-out screencap -p > {p}")
    res.setdefault("shots", {})[name] = os.path.getsize(p) if os.path.exists(p) else 0


def dump(name):
    """uiautomator view of every window (IME windows included where the platform allows)."""
    adb("uiautomator dump --windows /sdcard/ui.xml || uiautomator dump /sdcard/ui.xml")
    x = adb("cat /sdcard/ui.xml")
    open(os.path.join(out, name + ".xml"), "w").write(x)
    return x


def find(xml, pattern):
    for m in re.finditer(r'<node [^>]*>', xml):
        n = m.group(0)
        txt = " ".join(re.findall(r'(?:text|content-desc)="([^"]*)"', n))
        if re.search(pattern, txt, re.I):
            b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', n)
            if b:
                x1, y1, x2, y2 = map(int, b.groups())
                return (x1 + x2) // 2, (y1 + y2) // 2, txt
    return None


def cur_ime():
    return adb("settings get secure default_input_method")


def step(name, fn):
    try:
        fn()
    except Exception as e:  # keep going: each step is evidence on its own
        res.setdefault("errors", {})[name] = repr(e)


sh("adb wait-for-device")
res["device"] = adb("getprop ro.build.fingerprint")
res["gboard_package"] = adb(f"pm list packages {GB}")
res["gboard_version"] = adb(f"dumpsys package {GB} | grep -m1 versionName")
sh(f"adb install -r -g {apk}", timeout=300)
for _ in range(30):
    if IME in adb("ime list -a -s"):
        break
    time.sleep(2)
gb_ids = [i for i in adb("ime list -a -s").split() if i.startswith(GB + "/")]
res["gboard_ime"] = gb_ids
adb(f"ime enable {IME}")
for g in gb_ids:
    adb(f"ime enable {g}")
adb(f"ime set {IME}")
res["enabled_imes"] = adb("ime list -s").split()
dm = adb("dumpsys input_method")
res["gboard_voice_subtype_declared"] = bool(re.search(r"mode=voice", dm))
open(os.path.join(out, "dumpsys_input_method.txt"), "w").write(dm)

# Dismiss any first-run screens, then open the test field with Humming.
adb("input keyevent KEYCODE_HOME")
time.sleep(2)
adb(f"am start -W -f 0x10008000 -n {ACT}")
for _ in range(10):  # wait for Humming's keys (the first open loads layouts)
    time.sleep(3)
    if "Dictate with Humming" in dump("h1_ui"):
        break
res["ime_start"] = cur_ime()
shot("h1_humming")
sh("adb logcat -c")

# 1. Long-press the mic (a real 900 ms press on the node); broadcast only if the node is not in the dump.
def long_press():
    x = dump("h1_ui")
    hit = find(x, r"Dictate with Humming")
    res["mic_node"] = hit
    if hit:
        adb(f"input swipe {hit[0]} {hit[1]} {hit[0]} {hit[1]} 900")
        res["menu_opened_by"] = "long-press"
    else:
        adb(f"am broadcast -a com.humming.keyboard.debug.VOICE_MENU -n {RCV} --ez on true")
        res["menu_opened_by"] = "debug broadcast (mic node not in the accessibility dump)"
    time.sleep(2)
    shot("h2_voice_menu")
step("long_press", long_press)

# 2. Tap "Gboard voice typing".
def pick_gboard():
    x = dump("h2_ui")
    hit = find(x, r"Gboard voice typing")
    res["menu_row"] = hit
    if hit:
        adb(f"input tap {hit[0]} {hit[1]}")
        res["handoff_by"] = "tap on the menu row"
    else:
        adb(f"am broadcast -a com.humming.keyboard.debug.HANDOFF_GBOARD -n {RCV}")
        res["handoff_by"] = "debug broadcast (menu row not in the dump)"
    time.sleep(6)
    res["ime_after_handoff"] = cur_ime()
    res["subtype_after_handoff"] = adb("settings get secure selected_input_method_subtype")
    res["handoff_log"] = [l for l in sh("adb logcat -d -s HUMMING:*").splitlines() if "HANDOFF" in l][-4:]
    shot("h3_gboard")
step("pick_gboard", pick_gboard)

# 3. Gboard's own mic (Google voice typing). On an emulator it may ask for the speech pack or a network.
def gboard_mic():
    # Gboard asks for the microphone on first use; grant it the way a user would tap "While using the app".
    adb(f"pm grant {GB} android.permission.RECORD_AUDIO")
    x = dump("h3_ui")
    hit = find(x, r"voice typing|Voice input|Use voice|microphone")
    res["gboard_mic_node"] = hit
    if hit:
        adb(f"input tap {hit[0]} {hit[1]}")
        time.sleep(3)
        shot("h4_gboard_voice")
        v = dump("h4_ui")
        allow = find(v, r"While using the app")
        if allow:  # permission dialog still shown: accept it, then tap the mic again
            adb(f"input tap {allow[0]} {allow[1]}")
            time.sleep(2)
            adb(f"input tap {hit[0]} {hit[1]}")
            time.sleep(3)
            shot("h4_gboard_voice")
        res["gboard_voice_ui"] = sorted(set(re.findall(r'(?:text|content-desc)="([^"]{3,60})"', dump("h4_ui"))))[:40]
        dump("h4_ui")
        adb("input keyevent KEYCODE_BACK")
        time.sleep(2)
step("gboard_mic", gboard_mic)

# 4. Back to Humming: Gboard's keyboard-switch key (globe / "Switch input method"), else the system picker.
def back():
    adb(f"am start -W -f 0x10008000 -n {ACT}")
    time.sleep(4)
    x = dump("h5_ui")
    hit = find(x, r"Switch input method|switch keyboard|Next keyboard|language")
    res["gboard_switch_node"] = hit
    if hit:
        adb(f"input tap {hit[0]} {hit[1]}")
        time.sleep(3)
        res["back_by"] = "Gboard switch key"
    if PKG not in cur_ime():
        adb(f"ime set {IME}")
        res["back_by"] = (res.get("back_by", "") + " + ime set (fallback)").strip(" +")
    time.sleep(4)
    res["ime_after_return"] = cur_ime()
    shot("h5_back_to_humming")
step("back", back)

res["pass"] = bool(res.get("gboard_ime")) and GB in res.get("ime_after_handoff", "") and PKG in res.get("ime_after_return", "")
json.dump(res, open(os.path.join(out, "handoff.json"), "w"), indent=2)
print(json.dumps(res, indent=2))
