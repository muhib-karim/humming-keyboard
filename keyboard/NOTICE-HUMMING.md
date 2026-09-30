# Humming Keyboard, based on FlorisBoard

This directory is a fork of FlorisBoard (https://github.com/florisboard/florisboard), upstream release
v0.5.2 (commit 2e82060251897226c0739b9f52d1d051b02305fb, 2025-11-28), licensed under the Apache License 2.0
(see `LICENSE` in this directory, kept unchanged). Copyright (C) 2020-2026 The FlorisBoard Contributors.

Changes made by the Humming project (Apache-2.0 section 4(b) notice):
- Removed the `lib:native` module (an upstream placeholder Rust library that the app did not use).
- Added the Humming voice engine and its UI: on-device dictation with NVIDIA Parakeet models (CC-BY-4.0) run by
  sherpa-onnx (Apache-2.0), Silero VAD, a live transcript, Clean mode, a model manager, and a Gboard voice hand-off.
- Typing additions: word suggestions and autocorrect from the AOSP word list, a clipboard grid with timestamps,
  long-press edit shortcuts, a vertical space-bar trackpad, a split layout, and incognito for voice and clipboard.
- New default look (Humming light/dark themes), app name, icon and application id.
- M6: a new word-prediction engine (`com.humming.keyboard.predict`) replacing the upstream stub: dictionaries with
  frequencies for six languages, next-word statistics, keyboard-aware autocorrect and on-device learning; a three-slot
  suggestion strip with the best guess in the middle; emoji and text shortcuts in the strip; an AMOLED theme, a
  background photo, a floating keyboard, Settings search, a Privacy screen, glide typing settings re-enabled,
  TalkBack labels on every key and contrast fixes.
Every modified upstream file keeps its original licence header. Files added by Humming are marked as such.
