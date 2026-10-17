# GitHub repository metadata (proposed)

For the repository's **About** box and social preview. Nothing here is applied automatically: the owner sets these in
the repository's settings (or with `gh repo edit`, which needs write access). Every claim matches the README.

## About (description, 327 of 350 characters)

> Private keyboard with offline voice typing. Talk and it types, on your device: Android keyboard (FlorisBoard-based,
> NVIDIA Parakeet on sherpa-onnx) plus iPhone, Windows, macOS and Linux apps. Lists, "scratch that" and word
> replacements by voice. No account, no analytics, no word caps. Bangla phonetic typing. Free, Apache-2.0.

Plain text to paste:

```
Private keyboard with offline voice typing. Talk and it types, on your device: Android keyboard (FlorisBoard-based, NVIDIA Parakeet on sherpa-onnx) plus iPhone, Windows, macOS and Linux apps. Lists, "scratch that" and word replacements by voice. No account, no analytics, no word caps. Bangla phonetic typing. Free, Apache-2.0.
```

## Website

```
https://muhib-karim.github.io/humming-keyboard/
```

## Topics (20)

```
keyboard android-keyboard voice-typing speech-to-text dictation offline-first privacy on-device-ai parakeet
sherpa-onnx florisboard jetpack-compose tauri rust swiftui bangla bangla-keyboard avro-phonetic asr open-source
```

All lowercase, hyphenated, under 50 characters each (GitHub's limits), 20 topics (GitHub allows up to 20).

## Social preview

Upload `docs/social-preview.png` (1280 x 640, real Android and iPhone captures; source `docs/social-preview.svg`,
rendered by `scripts/render_social_preview.py`) under Settings > General > Social preview. The website already uses
the same image for OpenGraph and Twitter cards.

## Command (owner, with write access)

```sh
gh repo edit muhib-karim/humming-keyboard \
  --description 'Private keyboard with offline voice typing. Talk and it types, on your device: Android keyboard (FlorisBoard-based, NVIDIA Parakeet on sherpa-onnx) plus iPhone, Windows, macOS and Linux apps. Lists, "scratch that" and word replacements by voice. No account, no analytics, no word caps. Bangla phonetic typing. Free, Apache-2.0.' \
  --homepage "https://muhib-karim.github.io/humming-keyboard/" \
  --add-topic keyboard,android-keyboard,voice-typing,speech-to-text,dictation,offline-first,privacy,on-device-ai \
  --add-topic parakeet,sherpa-onnx,florisboard,jetpack-compose,tauri,rust,swiftui,bangla,bangla-keyboard \
  --add-topic avro-phonetic,asr,open-source
```
