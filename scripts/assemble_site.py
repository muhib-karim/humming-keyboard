#!/usr/bin/env python3
"""Assemble the GitHub Pages site: play/site/ plus the images it uses, copied from docs/.

  assemble_site.py <out dir>

pages.yml runs this, then scripts/check_site.py, then adds the /updates/ mirror. Run both locally to preview.
"""
import argparse
import os
import shutil

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# Every image the pages reference under img/ (and a few older names kept so old links keep working).
IMAGES = {
    "docs/banner.png": "banner.png",
    "docs/poster.png": "poster.png",
    "landing/icon.png": "icon.png",
    "docs/social-preview.png": "social-preview.png",
}
SCREENSHOTS = [
    "android_dictation.gif", "android_dictation_listening.png", "android_dictation_typed.png", "android_swipe.gif",
    "android_clipboard.png", "android_keyboard_dark.png", "android_themes.gif", "android_settings_voice.png",
    "ios_dictate.png", "ios_dictate_dark.png", "ios_keyboard.png", "ios_settings.png", "ios_walkthrough.gif",
    "desktop_home.png", "desktop_home_dark.png", "desktop_history.png", "desktop_settings.png", "desktop_settings_dark.png",
    "desktop_dictation.gif", "desktop_meetings.png", "desktop_meetings_dark.png",
    # 1.7.1: the CI emulator run (android-emulator.yml), unedited apart from scaling to 540x1200.
    "emulator_settings_home.png", "emulator_keyboard.png", "emulator_typed.png",
]


def assemble(out):
    if os.path.exists(out):
        shutil.rmtree(out)
    shutil.copytree(os.path.join(ROOT, "play/site"), out)
    img = os.path.join(out, "img")
    os.makedirs(img, exist_ok=True)
    for src, name in IMAGES.items():
        shutil.copyfile(os.path.join(ROOT, src), os.path.join(img, name))
    for name in SCREENSHOTS:
        shutil.copyfile(os.path.join(ROOT, "docs/screenshots", name), os.path.join(img, name))
    return sum(len(f) for _, _, f in os.walk(out))


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("out", help="Output directory for the assembled static site")
    args = ap.parse_args()
    count = assemble(args.out)
    print("site assembled in %s: %d files" % (args.out, count))


if __name__ == "__main__":
    main()
