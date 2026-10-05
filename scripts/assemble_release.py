#!/usr/bin/env python3
"""Assemble a Humming GitHub Release from the build artifacts of release.yml.

  assemble_release.py <artifacts dir> <out dir> <version>

Copies every installer under its release name, checks each self-update bundle's minisign signature against the public
key in desktop/src-tauri/tauri.conf.json, writes the desktop updater feed (latest.json), the Android update feed
(android.json), the release notes (NOTES.md, outside <out>) and SHA256SUMS.txt over every file. Exits non-zero if
anything is missing or does not verify.
"""
import datetime, glob, hashlib, json, os, re, shutil, subprocess, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
FEED = "https://humming-keyboard.pages.dev/updates/"


def die(msg):
    print("::error::" + msg)
    sys.exit(1)


def one(pattern):
    hits = sorted(glob.glob(pattern, recursive=True))
    if len(hits) != 1:
        die("expected exactly one file for %s, found %s" % (pattern, hits))
    return hits[0]


def notes_for(version):
    text = open(os.path.join(ROOT, "CHANGELOG.md"), encoding="utf-8").read()
    m = re.search(r"^## \[?v?%s\]?.*?$(.*?)(?=^## |\Z)" % re.escape(version), text, re.M | re.S)
    return (m.group(1).strip() if m else "Humming %s" % version)


def main():
    art, out, version = sys.argv[1], sys.argv[2], sys.argv[3]
    conf = json.load(open(os.path.join(ROOT, "desktop/src-tauri/tauri.conf.json")))
    if conf["version"] != version:
        die("tag %s but tauri.conf.json says %s" % (version, conf["version"]))
    props = open(os.path.join(ROOT, "keyboard/gradle.properties")).read()
    vname = re.search(r"^projectVersionName=(.+)$", props, re.M).group(1).strip()
    vcode = int(re.search(r"^projectVersionCode=(\d+)$", props, re.M).group(1))
    if vname.split("-")[0] != version:
        die("tag %s but keyboard/gradle.properties says %s" % (version, vname))
    os.makedirs(out, exist_ok=True)

    def put(src, name=None):
        dst = os.path.join(out, name or os.path.basename(src))
        shutil.copyfile(src, dst)
        return os.path.basename(dst)

    apk = put(one(art + "/**/humming-keyboard-release.apk"), "Humming-%s.apk" % version)
    put(one(art + "/**/humming-keyboard-release.aab"), "Humming-%s.aab" % version)
    put(one(art + "/**/Humming_%s_unsigned.ipa" % version))
    files = {}
    for key, pat in [("dmg", "Humming_%s_universal.dmg"), ("app", "Humming_%s_universal.app.tar.gz"),
                     ("nsis", "Humming_%s_x64-setup.exe"), ("msi", "Humming_%s_x64_en-US.msi"),
                     ("appimage", "Humming_%s_amd64.AppImage"), ("deb", "Humming_%s_amd64.deb")]:
        files[key] = put(one(art + "/**/" + pat % version))
    sigs = {}
    for key in ("app", "nsis", "msi", "appimage", "deb"):
        hits = glob.glob(art + "/**/" + files[key] + ".sig", recursive=True)
        if hits:
            put(hits[0])
            sigs[key] = open(hits[0]).read().strip()
        elif key != "deb":
            die("no updater signature for " + files[key])

    # Every signature must verify against the public key the app ships with.
    for key, sig in sigs.items():
        r = subprocess.run([sys.executable, os.path.join(ROOT, "scripts/verify_updater.py"),
                            "--config", os.path.join(ROOT, "desktop/src-tauri/tauri.conf.json"),
                            "--file", os.path.join(out, files[key]), "--sig", os.path.join(out, files[key] + ".sig")])
        if r.returncode != 0:
            die("signature of %s does not verify" % files[key])

    notes = notes_for(version)
    entry = lambda key: {"signature": sigs[key], "url": FEED + files[key]}
    platforms = {
        "darwin-aarch64": entry("app"), "darwin-x86_64": entry("app"),
        "darwin-aarch64-app": entry("app"), "darwin-x86_64-app": entry("app"),
        "windows-x86_64": entry("nsis"), "windows-x86_64-nsis": entry("nsis"), "windows-x86_64-msi": entry("msi"),
        "linux-x86_64": entry("appimage"), "linux-x86_64-appimage": entry("appimage"),
    }
    if "deb" in sigs:
        platforms["linux-x86_64-deb"] = entry("deb")
    latest = {"version": version, "notes": notes,
              "pub_date": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
              "platforms": platforms}
    json.dump(latest, open(os.path.join(out, "latest.json"), "w"), indent=2)
    sha = lambda f: hashlib.sha256(open(os.path.join(out, f), "rb").read()).hexdigest()
    json.dump({"versionCode": vcode, "versionName": version, "apk": apk, "sha256": sha(apk),
               "notes": notes[:2000]}, open(os.path.join(out, "android.json"), "w"), indent=2)
    names = sorted(f for f in os.listdir(out) if f != "SHA256SUMS.txt")
    with open(os.path.join(out, "SHA256SUMS.txt"), "w") as fh:
        for f in names:
            fh.write("%s  %s\n" % (sha(f), f))
    with open(os.path.join(os.path.dirname(os.path.abspath(out)), "NOTES.md"), "w", encoding="utf-8") as fh:
        fh.write(notes + "\n\n---\nIn-app updates: desktop installs read `latest.json` (signed, minisign key id in "
                 "`tauri.conf.json`); the Android APK reads `android.json` and checks `SHA256SUMS.txt` and the signing "
                 "certificate. Both feeds are mirrored to " + FEED + "\n")
    print(open(os.path.join(out, "SHA256SUMS.txt")).read())


if __name__ == "__main__":
    main()
