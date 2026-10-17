#!/usr/bin/env python3
"""Dry-run tests for assemble_release.py: fake build artifacts in a temp folder, no signing keys, no network.

  python3 -m unittest scripts/test_assemble_release.py       (release.yml runs this before assembling)

HUMMING_ROOT points the script at a fake repository (versions, changelog); HUMMING_RELEASE_DRY_RUN=1 skips the minisign
check, which needs real signatures. Neither is ever set in CI's real assembly step.
"""
import json, os, subprocess, sys, tempfile, unittest

SCRIPT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "assemble_release.py")


def write(path, text="x"):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(text)


class AssembleReleaseTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        t = self.tmp.name
        self.root, self.art, self.out = os.path.join(t, "repo"), os.path.join(t, "art"), os.path.join(t, "rel", "out")
        write(os.path.join(self.root, "desktop/src-tauri/tauri.conf.json"), json.dumps({"version": "1.7.0"}))
        write(os.path.join(self.root, "keyboard/gradle.properties"), "projectVersionCode=1700\nprojectVersionName=1.7.0\n")
        write(os.path.join(self.root, "CHANGELOG.md"),
              "# Changelog\n\n## 1.7.0-rc1 (release candidate): premium pass\n- Bangla phonetic typing.\n\n## 1.6.3: older\n- old\n")

    def tearDown(self):
        self.tmp.cleanup()

    def artifacts(self, skip=()):
        v, a = "1.7.0", self.art
        files = {
            "android": ["humming-keyboard-signed-release/humming-keyboard-release.apk",
                        "humming-keyboard-signed-release/humming-keyboard-release.aab"],
            "ios": ["humming-ios-ipa/Humming_%s_unsigned.ipa" % v],
            "macos": ["humming-desktop-macos-universal/Humming_%s_universal.dmg" % v,
                      "humming-desktop-macos-universal/Humming_%s_universal.app.tar.gz" % v,
                      "humming-desktop-macos-universal/Humming_%s_universal.app.tar.gz.sig" % v],
            "windows": ["humming-desktop-windows-x64/Humming_%s_x64-setup.exe" % v,
                        "humming-desktop-windows-x64/Humming_%s_x64-setup.exe.sig" % v,
                        "humming-desktop-windows-x64/Humming_%s_x64_en-US.msi" % v,
                        "humming-desktop-windows-x64/Humming_%s_x64_en-US.msi.sig" % v],
            "linux": ["humming-desktop-linux-x64/Humming_%s_amd64.AppImage" % v,
                      "humming-desktop-linux-x64/Humming_%s_amd64.AppImage.sig" % v,
                      "humming-desktop-linux-x64/Humming_%s_amd64.deb" % v],
        }
        for platform, names in files.items():
            if platform in skip:
                continue
            for n in names:
                write(os.path.join(a, n), "sig-%s" % n if n.endswith(".sig") else "data-%s" % n)

    def run_script(self, version, *extra):
        env = dict(os.environ, HUMMING_ROOT=self.root, HUMMING_RELEASE_DRY_RUN="1")
        env.pop("GITHUB_OUTPUT", None)
        return subprocess.run([sys.executable, SCRIPT, self.art, self.out, version, *extra],
                              capture_output=True, text=True, env=env)

    def release_env(self):
        lines = open(os.path.join(os.path.dirname(self.out), "release.env")).read().split()
        return dict(l.split("=", 1) for l in lines)

    def test_rc_tag_accepted_and_marked_prerelease(self):
        self.artifacts()
        r = self.run_script("1.7.0-rc1")
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        env = self.release_env()
        self.assertEqual(env["PRERELEASE"], "true")
        self.assertEqual(env["VERSION"], "1.7.0-rc1")
        self.assertEqual(env["BASE_VERSION"], "1.7.0")
        names = set(os.listdir(self.out))
        for n in ["Humming-1.7.0-rc1.apk", "Humming-1.7.0-rc1.aab", "Humming_1.7.0-rc1_unsigned.ipa",
                  "Humming_1.7.0-rc1_universal.dmg", "Humming_1.7.0-rc1_x64-setup.exe", "Humming_1.7.0-rc1_x64_en-US.msi",
                  "Humming_1.7.0-rc1_amd64.AppImage", "Humming_1.7.0-rc1_amd64.deb", "SHA256SUMS.txt"]:
            self.assertIn(n, names)
        # A pre-release is never mirrored, so it carries no update feeds pointing at mirror files that won't exist.
        self.assertNotIn("latest.json", names)
        self.assertNotIn("android.json", names)
        sums = open(os.path.join(self.out, "SHA256SUMS.txt")).read()
        self.assertEqual(len(sums.strip().splitlines()), len(names) - 1)
        notes = open(os.path.join(os.path.dirname(self.out), "NOTES.md")).read()
        self.assertIn("Bangla phonetic typing", notes)
        self.assertIn("release candidate", notes.lower())
        self.assertIn("| Android |", notes)

    def test_stable_tag_is_not_prerelease_and_keeps_names(self):
        self.artifacts()
        r = self.run_script("1.7.0")
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        self.assertEqual(self.release_env()["PRERELEASE"], "false")
        self.assertIn("Humming-1.7.0.apk", os.listdir(self.out))
        android = json.load(open(os.path.join(self.out, "android.json")))
        self.assertEqual(android["versionCode"], 1700)
        self.assertEqual(android["apk"], "Humming-1.7.0.apk")
        latest = json.load(open(os.path.join(self.out, "latest.json")))
        self.assertTrue(latest["platforms"]["windows-x86_64"]["url"].endswith("Humming_1.7.0_x64-setup.exe"))

    def test_version_mismatch_fails(self):
        self.artifacts()
        r = self.run_script("1.8.0-rc1")
        self.assertNotEqual(r.returncode, 0)
        self.assertIn("tauri.conf.json says 1.7.0", r.stdout)

    def test_missing_platform_fails_loudly_with_every_missing_file(self):
        self.artifacts(skip=("ios", "windows"))
        r = self.run_script("1.7.0-rc1")
        self.assertNotEqual(r.returncode, 0)
        self.assertIn("ios", r.stdout)
        self.assertIn("windows", r.stdout)
        self.assertIn("Humming_1.7.0_unsigned.ipa", r.stdout)
        self.assertIn("Humming_1.7.0_x64-setup.exe", r.stdout)
        self.assertIn("--allow-missing", r.stdout)

    def test_allow_missing_publishes_the_rest_and_says_so(self):
        self.artifacts(skip=("ios",))
        r = self.run_script("1.7.0-rc1", "--allow-missing", "ios")
        self.assertEqual(r.returncode, 0, r.stdout + r.stderr)
        self.assertFalse(any(n.endswith(".ipa") for n in os.listdir(self.out)))
        notes = open(os.path.join(os.path.dirname(self.out), "NOTES.md")).read()
        self.assertIn("Not in this release: iOS", notes)

    def test_missing_updater_signature_fails(self):
        self.artifacts()
        os.remove(os.path.join(self.art, "humming-desktop-linux-x64/Humming_1.7.0_amd64.AppImage.sig"))
        r = self.run_script("1.7.0-rc1")
        self.assertNotEqual(r.returncode, 0)
        self.assertIn("Humming_1.7.0_amd64.AppImage", r.stdout)
        self.assertIn("signature", r.stdout)

    def test_duplicate_artifact_fails(self):
        self.artifacts()
        write(os.path.join(self.art, "other/humming-keyboard-release.apk"))
        r = self.run_script("1.7.0-rc1")
        self.assertNotEqual(r.returncode, 0)
        self.assertIn("more than one", r.stdout)


if __name__ == "__main__":
    unittest.main()
