# Licences and attribution

Humming Keyboard is released under the **Apache License 2.0** (see `LICENSE`). It is a fork of FlorisBoard and
ships third-party components under their own permissive licences. Apache-2.0 allows commercial distribution,
including a paid store listing, as long as the obligations below are kept.

## What Humming must keep doing (licence obligations)

| Component | Licence | Obligation |
|---|---|---|
| FlorisBoard v0.5.2 (keyboard base) | Apache-2.0 | Keep `LICENSE` and the upstream copyright headers; state our changes (`NOTICE-HUMMING.md`); don't use the FlorisBoard name or logo for our product. |
| sherpa-onnx 1.13.8 (speech runtime, downloaded at build time) | Apache-2.0 | Credit in the app (Settings > Voice, About > Open-source licences) and here. |
| ONNX Runtime (inside the sherpa-onnx AAR) | MIT | Keep its copyright notice (in the AAR's licence list and here). |
| NVIDIA Parakeet TDT-CTC 110M and TDT 0.6B v2 (speech model weights, downloaded by the app on first use) | CC-BY-4.0 | **Attribution**: "Speech recognition model: NVIDIA Parakeet, licensed under CC-BY-4.0" shown in Settings > Voice and in this file; we distribute the ONNX export by the sherpa-onnx project unmodified (int8 quantised by them). |
| Silero VAD (voice activity model, bundled asset) | MIT | Keep the copyright notice (below). |
| Apache Commons Compress 1.27.1 (model archive reader) | Apache-2.0 | Credit here. |
| AOSP LatinIME word lists: English (156k words) plus German, Spanish, French, Italian and Brazilian Portuguese (60k each), for suggestions and autocorrect | Apache-2.0 | Credit here and in `assets/predict/NOTICE.txt`. |
| Tatoeba English sentences (only aggregated next-word counts ship, no sentences) | CC BY 2.0 FR | **Attribution**: "Next-word statistics counted from sentences of the Tatoeba Project (tatoeba.org), CC BY 2.0 FR", here and in `assets/predict/NOTICE.txt`. |
| Tatoeba German, Spanish, French, Italian and Portuguese sentences (1.2: next-word tables; the English neural model's training data) | CC BY 2.0 FR | **Attribution** as above; only aggregated counts and trained weights ship, no sentences (the unit-test benchmark keeps 1 % held-out sentences in `app/src/test/resources/predict_bench`, attributed there by source). |
| Mozilla Common Voice sentence lists (1.2: next-word tables and the English neural model's training data) | CC0 1.0 | None required; credited here and in `assets/predict/NOTICE.txt`. |
| Humming English neural next-word model `hlm_en.bin` (1.2; trained by `keyboard/lm/train.py`, run by our own Kotlin code, no runtime library) | Apache-2.0 (Humming's own weights) | Keep this entry; its training data's attribution (Tatoeba, above). |
| Noto Emoji images (1.2: the 32 built-in stickers are rendered from them) | Apache-2.0 | Credit here, in `assets/stickers` notes and in Settings > Stickers & GIFs. |
| Bangers font by The Bangers Project Authors (1.2: sticker captions are drawn with it; the font itself is not shipped) | SIL OFL 1.1 | Credit here. Rendered images are not subject to the OFL. |
| Openverse (1.2: optional online GIF search, off by default; no key, no account) | Service; results limited to CC0, public domain and CC BY | Each GIF result shows its licence, creator and source in the panel so it can be credited. Nothing from Openverse ships in the app. |
| Unicode CLDR emoji annotations (emoji in the suggestion strip, already bundled by FlorisBoard) | Unicode License v3 | Keep the notice (below). |

Nothing GPL is included. Gboard, Google voice typing ("Rambler") and FUTO Keyboard are separate apps: Humming only
switches to Gboard through Android's input-method API when the user asks, and FUTO's models were used only in our
benchmark, never shipped.

## Notices

- FlorisBoard: Copyright (C) 2020-2025 The FlorisBoard Contributors. Apache License 2.0.
- sherpa-onnx: Copyright (c) Xiaomi Corporation and the k2-fsa/sherpa-onnx contributors. Apache License 2.0.
- ONNX Runtime: Copyright (c) Microsoft Corporation. MIT License.
- NVIDIA Parakeet TDT-CTC 110M, Parakeet TDT 0.6B v2: Copyright (c) NVIDIA Corporation. Creative Commons Attribution 4.0
  International (https://creativecommons.org/licenses/by/4.0/). Converted to ONNX and quantised to int8 by the sherpa-onnx project.
- Silero VAD: Copyright (c) 2020-present Silero Team. MIT License.
- Apache Commons Compress: Copyright The Apache Software Foundation. Apache License 2.0.
- AOSP LatinIME dictionaries (word lists): Copyright (C) The Android Open Source Project. Apache License 2.0.
- Tatoeba Project sentences (tatoeba.org): Creative Commons Attribution 2.0 France. Only word-pair counts derived from
  them are distributed.
- Mozilla Common Voice sentence lists (github.com/common-voice/common-voice, server/data): CC0 1.0 Universal.
- Noto Emoji images: Copyright 2013 Google LLC. Apache License 2.0 (github.com/googlefonts/noto-emoji).
- Bangers font: Copyright 2010 The Bangers Project Authors (github.com/googlefonts/bangers). SIL Open Font License 1.1.
- Humming neural next-word model (hlm_en.bin): Humming project. Apache License 2.0.
- Unicode CLDR emoji annotations: Copyright (c) Unicode, Inc. Unicode License v3 (https://www.unicode.org/license.txt).
