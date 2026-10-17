#!/usr/bin/env python3
"""Assemble a Humming GitHub Release from the build artifacts of release.yml.

  assemble_release.py <artifacts dir> <out dir> <version> [--allow-missing ios,macos,...]

<version> is the tag without its "v": "1.7.0" or a release candidate such as "1.7.0-rc1". The apps carry the base
version (1.7.0: MSI and the app stores accept no "-rc" suffix), so the "-rcN" part is stripped when comparing with
tauri.conf.json and keyboard/gradle.properties and when finding the build files; the release files are named with the
full version so a release candidate is never mistaken for the final build.

Platforms: android (signed APK + AAB), ios (unsigned .ipa), macos (.dmg + signed update bundle), windows (setup .exe +
.msi, both signed for updates), linux (.AppImage signed for updates + .deb). Every platform is required: a missing or
ambiguous file stops the release with a per-platform report naming each missing file. --allow-missing names platforms
the release may go out without (said in the release notes). Each self-update bundle's minisign signature is checked
against the public key in desktop/src-tauri/tauri.conf.json.

Writes into <out>: the release files, latest.json (desktop updater feed) and android.json (Android update feed) for a
final release only, and SHA256SUMS.txt over every file. Next to <out>: NOTES.md (release notes from CHANGELOG.md plus the platform table) and
release.env (VERSION, BASE_VERSION, PRERELEASE=true for "-rc" and other pre-release tags), also appended to
$GITHUB_OUTPUT when set. Tests: scripts/test_assemble_release.py (HUMMING_ROOT and HUMMING_RELEASE_DRY_RUN are for those
tests only).
"""
import argparse, datetime, glob, hashlib, json, os, re, shutil, subprocess, sys

ROOT = os.environ.get("HUMMING_ROOT") or os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SCRIPTS = os.path.dirname(os.path.abspath(__file__))
FEED = "https://humming-keyboard.pages.dev/updates/"
DRY_RUN = os.environ.get("HUMMING_RELEASE_DRY_RUN") == "1"

PLATFORM_NAMES = {"android": "Android", "ios": "iOS", "macos": "macOS", "windows": "Windows", "linux": "Linux"}
# key: (platform, build file pattern with {v} = base version, release name with {V} = full version, needs .sig)
FILES = [
    ("apk", "android", "humming-keyboard-release.apk", "Humming-{V}.apk", False),
    ("aab", "android", "humming-keyboard-release.aab", "Humming-{V}.aab", False),
    ("ipa", "ios", "Humming_{v}_unsigned.ipa", "Humming_{V}_unsigned.ipa", False),
    ("dmg", "macos", "Humming_{v}_universal.dmg", "Humming_{V}_universal.dmg", False),
    ("app", "macos", "Humming_{v}_universal.app.tar.gz", "Humming_{V}_universal.app.tar.gz", True),
    ("nsis", "windows", "Humming_{v}_x64-setup.exe", "Humming_{V}_x64-setup.exe", True),
    ("msi", "windows", "Humming_{v}_x64_en-US.msi", "Humming_{V}_x64_en-US.msi", True),
    ("appimage", "linux", "Humming_{v}_amd64.AppImage", "Humming_{V}_amd64.AppImage", True),
    ("deb", "linux", "Humming_{v}_amd64.deb", "Humming_{V}_amd64.deb", False),  # a .deb cannot self-update
]


def die(msg):
    print("::error::" + msg)
    sys.exit(1)


def find(art, name):
    """The single build file called <name> under <art>: (path, problem)."""
    hits = sorted(glob.glob(os.path.join(art, "**", name), recursive=True))
    if not hits:
        return None, "%s not found under %s" % (name, art)
    if len(hits) > 1:
        return None, "more than one %s: %s" % (name, ", ".join(hits))
    return hits[0], None


def notes_for(*versions):
    text = open(os.path.join(ROOT, "CHANGELOG.md"), encoding="utf-8").read()
    for version in versions:
        m = re.search(r"^## \[?v?%s\]?(?![\w.-]).*?$(.*?)(?=^## |\Z)" % re.escape(version), text, re.M | re.S)
        if m:
            return m.group(1).strip()
    return "Humming %s" % versions[0]


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("art"), ap.add_argument("out"), ap.add_argument("version")
    ap.add_argument("--allow-missing", default="", help="comma-separated platforms the release may go out without")
    a = ap.parse_args()
    version = a.version[1:] if a.version.startswith("v") else a.version
    m = re.fullmatch(r"(\d+\.\d+\.\d+)(?:-([0-9A-Za-z.-]+))?", version)
    if not m:
        die("version %r is not X.Y.Z or X.Y.Z-suffix (for example 1.7.0-rc1)" % version)
    base, prerelease = m.group(1), m.group(2) is not None
    allowed = {p.strip().lower() for p in a.allow_missing.split(",") if p.strip()}
    unknown = allowed - set(PLATFORM_NAMES)
    if unknown:
        die("--allow-missing: unknown platform(s) %s (known: %s)" % (", ".join(sorted(unknown)), ", ".join(PLATFORM_NAMES)))

    conf = json.load(open(os.path.join(ROOT, "desktop/src-tauri/tauri.conf.json")))
    if conf["version"] != base:
        die("tag %s but tauri.conf.json says %s (the tag's X.Y.Z must equal it)" % (version, conf["version"]))
    props = open(os.path.join(ROOT, "keyboard/gradle.properties")).read()
    vname = re.search(r"^projectVersionName=(.+)$", props, re.M).group(1).strip()
    vcode = int(re.search(r"^projectVersionCode=(\d+)$", props, re.M).group(1))
    if vname.split("-")[0] != base:
        die("tag %s but keyboard/gradle.properties says %s" % (version, vname))

    # Find every file first, so one run reports everything that is missing.
    found, problems = {}, {}
    for key, platform, pattern, _, needs_sig in FILES:
        path, problem = find(a.art, pattern.format(v=base))
        if problem is None and needs_sig:
            sig, sig_problem = find(a.art, os.path.basename(path) + ".sig")
            if sig_problem:
                problem = "no updater signature for %s (%s)" % (os.path.basename(path), sig_problem)
            else:
                found[key + ".sig"] = sig
        if problem:
            problems.setdefault(platform, []).append(problem)
        else:
            found[key] = path
    print("Humming %s (%s): build files by platform" % (version, "pre-release" if prerelease else "release"))
    for platform, label in PLATFORM_NAMES.items():
        status = "ok" if platform not in problems else ("MISSING, allowed" if platform in allowed else "MISSING")
        print("  %-8s %s" % (label, status))
        for p in problems.get(platform, []):
            print("           - " + p)
    blocking = sorted(set(problems) - allowed)
    if blocking:
        die("release %s is missing platform(s): %s. Details above. Re-run the failed build job, or pass "
            "--allow-missing %s to publish without them." % (version, ", ".join(blocking), ",".join(blocking)))
    skipped = [p for p in PLATFORM_NAMES if p in problems]

    if os.path.exists(a.out):
        shutil.rmtree(a.out)
    os.makedirs(a.out)
    names = {}
    for key, platform, _, release_name, needs_sig in FILES:
        if platform in skipped:
            continue
        names[key] = release_name.format(V=version)
        shutil.copyfile(found[key], os.path.join(a.out, names[key]))
        if needs_sig:
            shutil.copyfile(found[key + ".sig"], os.path.join(a.out, names[key] + ".sig"))

    sigs = {}
    for key, _, _, _, needs_sig in FILES:
        if not needs_sig or key not in names:
            continue
        f = os.path.join(a.out, names[key])
        sigs[key] = open(f + ".sig").read().strip()
        if DRY_RUN:
            continue
        # Every signature must verify against the public key the app ships with.
        r = subprocess.run([sys.executable, os.path.join(SCRIPTS, "verify_updater.py"),
                            "--config", os.path.join(ROOT, "desktop/src-tauri/tauri.conf.json"), "--file", f, "--sig", f + ".sig"])
        if r.returncode != 0:
            die("updater signature of %s does not verify against tauri.conf.json's public key" % names[key])

    notes = notes_for(version, base)
    sha = lambda f: hashlib.sha256(open(os.path.join(a.out, f), "rb").read()).hexdigest()
    entry = lambda key: {"signature": sigs[key], "url": FEED + names[key]}
    platforms = {}
    if "app" in names:
        for k in ("darwin-aarch64", "darwin-x86_64", "darwin-aarch64-app", "darwin-x86_64-app"):
            platforms[k] = entry("app")
    if "nsis" in names:
        platforms.update({"windows-x86_64": entry("nsis"), "windows-x86_64-nsis": entry("nsis"), "windows-x86_64-msi": entry("msi")})
    if "appimage" in names:
        platforms.update({"linux-x86_64": entry("appimage"), "linux-x86_64-appimage": entry("appimage")})
    # Update feeds only for final releases: pages.yml mirrors the latest final release, so a pre-release's feeds would
    # point at mirror files that never exist.
    if platforms and not prerelease:
        latest = {"version": version, "notes": notes,
                  "pub_date": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
                  "platforms": platforms}
        json.dump(latest, open(os.path.join(a.out, "latest.json"), "w"), indent=2)
    if "apk" in names and not prerelease:
        json.dump({"versionCode": vcode, "versionName": version, "apk": names["apk"], "sha256": sha(names["apk"]),
                   "notes": notes[:2000]}, open(os.path.join(a.out, "android.json"), "w"), indent=2)
    files = sorted(f for f in os.listdir(a.out) if f != "SHA256SUMS.txt")
    with open(os.path.join(a.out, "SHA256SUMS.txt"), "w") as fh:
        for f in files:
            fh.write("%s  %s\n" % (sha(f), f))

    rows = [("Android", "apk", "Signed APK (sideload, self-updating)"), ("Android", "aab", "Signed AAB (Google Play)"),
            ("iOS", "ipa", "Unsigned .ipa: sideload with your own Apple ID (docs/IOS_SIDELOAD.md)"),
            ("macOS", "dmg", "Universal .dmg (not notarised)"), ("Windows", "nsis", "Setup .exe (unsigned; self-updating)"),
            ("Windows", "msi", "MSI installer (unsigned)"), ("Linux", "appimage", "AppImage (self-updating)"),
            ("Linux", "deb", "Debian package")]
    table = "| Platform | File | What it is |\n|---|---|---|\n" + "".join(
        "| %s | `%s` | %s |\n" % (label, names[key], what) for label, key, what in rows if key in names)
    head = ""
    if prerelease:
        head = ("**Release candidate.** Humming %s is a pre-release for testing; the final %s replaces it. "
                "It carries no update feeds, so installed apps do not update to it.\n\n" % (version, base))
    if skipped:
        head += "**Not in this release: %s** (build not available; use the previous release for it).\n\n" % \
            ", ".join(PLATFORM_NAMES[p] for p in skipped)
    parent = os.path.dirname(os.path.abspath(a.out))
    with open(os.path.join(parent, "NOTES.md"), "w", encoding="utf-8") as fh:
        fh.write(head + notes + "\n\n### Files\n\n" + table + "\nCheck downloads with `sha256sum -c SHA256SUMS.txt "
                 "--ignore-missing`. The APK's signing certificate SHA-256 is "
                 "`8C:8B:68:BB:E6:3A:A4:2A:85:61:59:AA:A5:B0:C9:0C:D9:54:EE:F7:8A:98:0D:39:BC:B6:69:41:75:3E:1F:C6`.\n\n"
                 "---\nIn-app updates: desktop installs read `latest.json` (signed, minisign key id in `tauri.conf.json`); "
                 "the Android APK reads `android.json` and checks `SHA256SUMS.txt` and the signing certificate. Both feeds "
                 "of the latest final release are mirrored to " + FEED + "\n")
    env = "VERSION=%s\nBASE_VERSION=%s\nPRERELEASE=%s\n" % (version, base, "true" if prerelease else "false")
    open(os.path.join(parent, "release.env"), "w").write(env)
    if os.environ.get("GITHUB_OUTPUT"):
        with open(os.environ["GITHUB_OUTPUT"], "a") as fh:
            fh.write(env)
    print(open(os.path.join(a.out, "SHA256SUMS.txt")).read())


if __name__ == "__main__":
    main()
