# Codebase map (Understand-Anything method)

Generated 2026-10-17 on branch `feat/v1.7-premium` at `fff17dc`, for the v1.7.0-rc1 pass.

**How this was made.** The `understand` skill (Understand-Anything) was invoked. Its scripted pipeline imports
`@understand-anything/core`, which is not built on this machine (`ERR_MODULE_NOT_FOUND .../packages/core/dist/index.js`),
and building it needs a `pnpm install` that the low-memory rules in [CLAUDE.md](../../CLAUDE.md) forbid. So the
skill's phases were followed by hand: **Phase 1 scan** (a deterministic `git ls-files` inventory by area and
language, script below), **Phase 4 layers** (architectural layers with the files in each), **Phase 5 tour** (a
guided reading order). No `knowledge-graph.json` was written. Line counts include comments and blank lines.

## Phase 1: inventory

1163 tracked files. Areas, by language (JSON and TSV are counted as files, not lines):

| Area | Files by language | Lines (code+docs, excl. JSON/TSV) |
|---|---|---:|
| `keyboard (FlorisBoard upstream app)` | Kotlin 229 | 46598 |
| `keyboard/app` | XML 87, JSON 10, Kotlin script 1, Kotlin 1 | 28020 |
| `keyboard/lib (FlorisBoard libs)` | Kotlin 97, Kotlin script 6, XML 3, TOML 2, Rust 2, JSON 1 | 13110 |
| `keyboard (Humming code)` | Kotlin 45 | 4536 |
| `core` | Rust 11, Python 3, Shell 3, TOML 2, Markdown 1 | 2708 |
| `desktop/src-tauri` | Rust 12, TOML 2, JSON 2 | 2636 |
| `keyboard (unit tests)` | Kotlin 30, Data (TSV) 1 | 1967 |
| `.github/workflows` | YAML 8 | 787 |
| `README.md` | Markdown 1 | 669 |
| `ios/App` | Swift 6, JSON 2 | 481 |
| `keyboard/lm` | Python 5 | 438 |
| `desktop/src` | TypeScript 2, CSS 2 | 360 |
| `ios/HummingCore` | Swift 3 | 349 |
| `keyboard/utils` | Python 3 | 319 |
| `CHANGELOG.md` | Markdown 1 | 288 |
| `keyboard/benchmark` | Kotlin 2, Kotlin script 1, XML 1 | 209 |
| `AUDIT.md` | Markdown 1 | 201 |
| `scripts` | Python 2 | 196 |
| `keyboard/ci` | Python 2 | 182 |
| `docs/USER_GUIDE.md` | Markdown 1 | 164 |
| `keyboard/README.md` | Markdown 1 | 160 |
| `play/site` | HTML 2 | 155 |
| `.github/ISSUE_TEMPLATE` | YAML 3 | 153 |
| `docs/ARCHITECTURE.md` | Markdown 1 | 151 |
| `landing` | JSON 1, Markdown 1, HTML 1 | 145 |
| `keyboard/LANGUAGEPACKS-CHINESE.md` | Markdown 1 | 143 |
| `desktop/index.html` | HTML 1 | 142 |
| `CODE_OF_CONDUCT.md` | Markdown 1 | 135 |
| `docs/AUDIT-1.6.2.md` | Markdown 1 | 134 |
| `keyboard/CODE_OF_CONDUCT.md` | Markdown 1 | 133 |
| `docs/RELEASING.md` | Markdown 1 | 130 |
| `ios/Shared` | Swift 2 | 130 |
| `desktop/README.md` | Markdown 1 | 122 |
| `play/DATA_SAFETY.md` | Markdown 1 | 115 |
| `docs/ci` | JavaScript 1 | 113 |
| `docs/RELEASE_SIGNING.md` | Markdown 1 | 108 |
| `keyboard/gradle` | TOML 2 | 108 |
| `ios/project.yml` | YAML 1 | 84 |
| `CONTRIBUTING.md` | Markdown 1 | 78 |
| `keyboard/CONTRIBUTING.md` | Markdown 1 | 72 |
| `SECURITY.md` | Markdown 1 | 58 |
| `docs/IOS_SIDELOAD.md` | Markdown 1 | 57 |
| `play/PRIVACY_POLICY.md` | Markdown 1 | 55 |
| `keyboard/LICENSES.md` | Markdown 1 | 54 |
| `keyboard/settings.gradle.kts` | Kotlin script 1 | 52 |
| `docs/BRAND.md` | Markdown 1 | 48 |
| `docs/README.md` | Markdown 1 | 48 |
| `TASKS.md` | Markdown 1 | 47 |
| `ios/Keyboard` | Swift 1 | 47 |
| `keyboard/crowdin.yml` | YAML 1 | 47 |
| `docs/audit-1.6.2` | Markdown 2 | 46 |
| `play/PLAY_CHECKLIST.md` | Markdown 1 | 41 |
| `keyboard/tools` | Python 1 | 37 |
| `.github/PULL_REQUEST_TEMPLATE.md` | Markdown 1 | 33 |
| `play/CONTENT_RATING.md` | Markdown 1 | 32 |
| `docs/FIREBASE_SETUP.md` | Markdown 1 | 28 |
| `keyboard/build.gradle.kts` | Kotlin script 1 | 28 |
| `keyboard/AI_POLICY.md` | Markdown 1 | 25 |
| `keyboard/NOTICE-HUMMING.md` | Markdown 1 | 24 |
| `keyboard/LANGUAGEPACKS.md` | Markdown 1 | 16 |
| `desktop/overlay.html` | HTML 1 | 15 |
| `desktop/vite.config.ts` | TypeScript 1 | 13 |
| `CLAUDE.md` | Markdown 1 | 10 |
| `desktop/package-lock.json` | JSON 1 | 0 |
| `desktop/package.json` | JSON 1 | 0 |
| `desktop/tsconfig.json` | JSON 1 | 0 |
| `docs/bench` | JSON 2 | 0 |
| `keyboard (assets)` | JSON 216, Data (TSV) 14 | 0 |

Most of the Android line count is the FlorisBoard v0.5.2 base (`dev.patrickgold.florisboard`, `org.florisboard.lib`).
Humming's own Android code is the ~4.5k lines in `com.humming.keyboard` plus the voice panel
`ime/voice/HummingVoice.kt` and edits to upstream files listed in [keyboard/NOTICE-HUMMING.md](../../keyboard/NOTICE-HUMMING.md).

## Phase 4: layers

| Layer | What belongs here | Key files |
|---|---|---|
| **1. Android IME shell** (upstream) | Input method service, keyboard layouts, key rendering, gestures, Smartbar, theme engine (Snygg stylesheets), settings UI | `FlorisImeService.kt`, `ime/text/**`, `ime/smartbar/**`, `ime/theme/**`, `app/settings/**`, `assets/ime/**` |
| **2. Voice capture and recognition** (Android) | Mic permission, foreground service, recorder, VAD segmenting, sherpa-onnx engines, online fallback | `PermissionActivity`, `DictationService`, `WavRecorder`, `Segmenter`, `StreamingSession`, `LocalEngine`, `Transcriber`, `OnlineSession`, `VoiceModels`, `ModelDownload` |
| **3. Text post-processing** (pure Kotlin, JVM-tested) | Clean mode, Accuracy mode, spoken punctuation, self-corrections, voice commands, custom words | `TextCleanup`, `VoiceCommands`, `AppVocabulary`, `DictationPipeline`, `DictationOutput` |
| **4. Prediction** (pure Kotlin, JVM-tested) | Lexicons, n-grams, int8 LSTM, user model, strip order, text shortcuts | `predict/Predictor`, `predict/HummingLm`, `predict/Lexicon`, `predict/Ngrams`, `predict/UserModel`, `predict/StripOrder`, `predict/TextShortcuts` |
| **5. Voice UI** (Compose) | Voice panel, mic button, listening state, long-press menu, Gboard hand-off | `ime/voice/HummingVoice.kt`, `GboardHandoff`, `HummingA11y` |
| **6. Device features** | Clipboard time, stickers/GIF, floating and split keyboard, background photo, backup, settings search, updater | `ClipTime`, `Stickers`, `GifSearch`, `HummingFloat`, `SplitLayout`, `KeyboardBackground`, `HummingBackup`, `SettingsIndex`, `AppUpdate`, `UpdateInstallReceiver` |
| **7. Desktop app** (Rust + TS) | Tauri back end: shortcut, audio, STT windows, clean-up port, paste, models, settings, history, updater; vanilla TS UI | `desktop/src-tauri/src/*.rs`, `desktop/src/main.ts`, `desktop/index.html` |
| **8. Shared Rust core** | VAD, audio helpers, clean-up, spoken lists, optional whisper.cpp, UniFFI | `core/src/*.rs` |
| **9. iOS app** (Swift) | SwiftUI app, dictation with on-device Apple Speech, keyboard extension, shared store, `HummingCore` clean-up package | `ios/App/*.swift`, `ios/Keyboard/`, `ios/Shared/`, `ios/HummingCore/` |
| **10. Release and CI** | Workflows, release assembly, update-feed verification | `.github/workflows/*.yml`, `scripts/assemble_release.py`, `scripts/verify_updater.py` |
| **11. Web and store** | GitHub Pages site, privacy policy, Play listing, landing page | `play/site/`, `play/*.md`, `landing/` |
| **12. Docs and evidence** | User guide, architecture, releasing, audits, benchmarks, screenshots | `docs/**`, `AUDIT.md`, `CHANGELOG.md` |

### Cross-layer edges that matter

- Layer 2 → 3: every recognised segment goes through `TextCleanup.clean(...)` then `VoiceCommands`, then
  `DictationOutput` shows it as composing text (layer 5 drives it).
- Layer 3 is ported three times: Kotlin (`TextCleanup.kt`), Rust (`desktop/src-tauri/src/text_cleanup.rs`), Swift
  (`ios/HummingCore/.../TextCleanup.swift`). A behaviour change in one should be mirrored, with tests, in the others.
- Layer 1 ↔ 4: the Smartbar candidate strip asks `PredictionEngine` / `Predictor` for suggestions; `StripOrder`
  decides the slot order.
- Layer 10 → all: `release.yml` calls `android-release.yml`, `desktop.yml`, `ios.yml`, then `assemble_release.py`,
  then `pages.yml` (update mirror).

### Test setup per module

| Module | Test runner | Where |
|---|---|---|
| Android Humming code | JUnit (JVM), `./gradlew :app:testDebugUnitTest` in `keyboard.yml` | `keyboard/app/src/test/kotlin/com/humming/keyboard/` (23 files) |
| Snygg theme lib | JUnit | `keyboard/lib/snygg/src/test/` |
| Desktop | `cargo test` in `desktop.yml` (`#[cfg(test)]` modules in `text_cleanup.rs`, `settings.rs`, `stt.rs`, `models.rs`, `audio.rs`, `focus.rs`, `lib.rs`) | `desktop/src-tauri/src/` |
| Core | `cargo test` | `core/src/` |
| iOS | `swift test` on Linux and the simulator in `ios.yml` | `ios/HummingCore/Tests/` |
| Release script | none yet (this pass adds a dry-run test) | `scripts/` |

## Phase 5: guided tour

1. **What it is**: [README.md](../../README.md), then [docs/ARCHITECTURE.md](../ARCHITECTURE.md).
2. **Tap the mic**: `keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/ime/voice/HummingVoice.kt` (panel,
   states, long-press menu).
3. **Keep listening**: `DictationService.kt` → `WavRecorder.kt` → `StreamingSession.kt` → `Segmenter.kt`.
4. **Recognise**: `LocalEngine.kt`, `VoiceModels.kt`, `ModelDownload.kt` (pinned SHA-256 downloads).
5. **Clean up**: `TextCleanup.kt` and `VoiceCommands.kt` with `TextCleanupTest` / `VoiceCommandsTest`.
6. **Type it**: `DictationOutput.kt` (composing draft, single commit).
7. **Typing intelligence**: `predict/Predictor.kt`, `predict/HummingLm.kt`, `predict/StripOrder.kt`.
8. **Look and feel**: `assets/ime/theme/org.florisboard.themes/stylesheets/humming_*.json`, `HummingThemeTest`,
   `ThemeContrastTest`.
9. **Privacy guarantees**: `NetworkAuditTest.kt` (source scan of network APIs), `play/PRIVACY_POLICY.md`.
10. **Desktop**: `desktop/src-tauri/src/lib.rs` → `stt.rs` → `text_cleanup.rs` → `insert.rs`.
11. **iOS**: `ios/App/Dictation.swift` → `ios/Shared/SharedStore.swift` → `ios/Keyboard/KeyboardViewController.swift`.
12. **Shipping**: `.github/workflows/release.yml` → `scripts/assemble_release.py` → `pages.yml`.

## Scan script

```python
import subprocess, collections, os, sys, re
root = sys.argv[1]
files = subprocess.check_output(['git','-C',root,'ls-files'],text=True).split()
ext_lang = {'.kt':'Kotlin','.kts':'Kotlin script','.java':'Java','.rs':'Rust','.swift':'Swift','.ts':'TypeScript','.js':'JavaScript','.mjs':'JavaScript','.py':'Python','.html':'HTML','.css':'CSS','.yml':'YAML','.yaml':'YAML','.json':'JSON','.toml':'TOML','.md':'Markdown','.xml':'XML','.sh':'Shell','.tsv':'Data (TSV)'}
by_area = collections.defaultdict(lambda: collections.Counter())
loc_area = collections.Counter()
for f in files:
    ext = os.path.splitext(f)[1]
    lang = ext_lang.get(ext)
    if not lang: continue
    parts = f.split('/')
    area = '/'.join(parts[:2]) if parts[0] in ('keyboard','ios','desktop','play','docs','.github') else parts[0]
    if f.startswith('keyboard/app/src/main/kotlin/com/humming'): area='keyboard (Humming code)'
    elif f.startswith('keyboard/app/src/test'): area='keyboard (unit tests)'
    elif f.startswith('keyboard/app/src/main/kotlin/dev'): area='keyboard (FlorisBoard upstream app)'
    elif f.startswith('keyboard/app/src/main/assets'): area='keyboard (assets)'
    elif f.startswith('keyboard/lib'): area='keyboard/lib (FlorisBoard libs)'
    by_area[area][lang]+=1
    if lang not in ('JSON','Data (TSV)'):
        try: loc_area[area]+=sum(1 for _ in open(os.path.join(root,f),errors='ignore'))
        except Exception: pass
print('| Area | Files by language | Lines (code+docs, excl. JSON/TSV) |\n|---|---|---:|')
for a in sorted(by_area, key=lambda a:-loc_area[a]):
    langs = ', '.join(f'{l} {n}' for l,n in by_area[a].most_common())
    print(f'| `{a}` | {langs} | {loc_area[a]} |')
```
