# Changelog

## 1.0.0
Voice
- The whisper.cpp engine is replaced with NVIDIA Parakeet on sherpa-onnx 1.13.8, chosen by a 73-clip bake-off.
  - Fast (default): Parakeet TDT-CTC 110M int8, 3.7% WER.
  - Best: Parakeet TDT 0.6B v2 int8, 2.6% WER.
  - For comparison: FUTO small.en ACFT 5.4%, whisper base.en 6.1%.
- Silero VAD cuts phrases for the live preview. Text is committed once, on Done.
- Model manager (Settings > Voice):
  - choose Fast or Best, with each model's size on disk;
  - delete a model;
  - "Download on Wi-Fi only";
  - downloads pinned to SHA-256;
  - an Offline badge on the voice panel.
- Bluetooth headset microphone support.
- Long-press the mic to switch to Gboard voice typing. The option only shows when Gboard is enabled, and it uses
  Gboard's voice subtype when Gboard declares one (`GboardHandoff`, `GboardHandoffTest`).
- NetworkAuditTest: only `ModelDownload.kt` may use a network API, and the voice path has none.

Typing
- Clipboard history is on by default, shown as a 2-column grid with timestamps, and keeps 100 items for up to a week.
- Edit shortcuts on long-press: a select all, c copy, x cut, v paste, z undo, y redo. An edit toolbar sits in the
  suggestion bar.
- The space-bar trackpad also moves the cursor up and down.
- Split keyboard in landscape and on tablets.
- Incognito mode also pauses voice history and clipboard history.

CI
- Offline proof on an emulator: airplane mode on, Wi-Fi off, no default network, ping failing. Cold start, a real
  dictation committed, and the screen recorded.
- Gboard hand-off run on a Google Play emulator image.
- Release APK job (R8, development-key signed).
- 70 unit tests.
