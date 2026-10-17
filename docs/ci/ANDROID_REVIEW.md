# Android review (skills pass)

Read-only review of the Android app in `keyboard/` on **2026-10-17**. Nothing was built, run or installed: no Gradle,
emulator, adb or `android` CLI. Each finding below comes from reading the source, and none has been checked on a device
or in CI. Paths are relative to `keyboard/app/src/main/` unless they start with `keyboard/` or `docs/`.
Ratings: **High** = user-visible cost or a real exploit path, **Medium** = worth fixing before the next release,
**Low** = hygiene or hardening.

---

## 1. jetpack-compose-performance

**Checked:** `kotlin/dev/patrickgold/florisboard/ime/voice/HummingVoice.kt` (state publishing 408-430, UI 551-895),
and where it is used: `kotlin/dev/patrickgold/florisboard/FlorisImeService.kt:836-863`, `ime/smartbar/Smartbar.kt:291`.
The Compose compiler is Kotlin 2.2.20, so strong skipping is on by default.

What is already right: in `MicOrb`, `level`, `breath` and `spin` are `by` delegates that are only read inside the
`Canvas` draw lambda (809, 810, 814-838), so the 120 ms level animation and the infinite transitions redraw without
recomposing. Collecting an in-memory `StateFlow` with `collectAsState` is fine here.

- **High: the blinking rec dot recomposes the whole voice panel every frame while listening.**
  `HummingVoice.kt:685-687`: `val a by blink.animateFloat(...)` is read in composition in
  `.background(p.rec.copy(alpha = a))`. `Row` and `Box` are inline, so the nearest restart scope is `VoiceInputLayout`
  itself. That means the full panel (chips, timer, transcript `Text`, bottom row) re-runs at 60-120 Hz for the whole
  dictation. **Fix (compile-safe):** move the read into the draw phase, for example
  `Box(Modifier.size(8.dp).graphicsLayer { alpha = a }.clip(CircleShape).background(p.rec))`
  (import `androidx.compose.ui.graphics.graphicsLayer`), or `Modifier.size(8.dp).drawBehind { drawCircle(p.rec.copy(alpha = a)) }`.
- **Medium: the whole panel also recomposes about 14 times a second from level updates and the timer.**
  `HummingVoice.kt:624` collects the whole `VoiceState`. `pushLevel` (425-429) publishes a new state about 10 times a
  second with new `level`/`levels`, and the 250 ms ticker (629-633) writes `now`, which is read in composition at 633.
  Each of these restarts `VoiceInputLayout`. `FlorisImeService.kt:838,844` also collects the full state just to read
  `voice.open`, so the enclosing `CompositionLocalProvider` content restarts at the same rate.
  **Fix:** (a) move the timer into a small child composable, e.g. `ElapsedTime(startedAt, listening, color)`, so only
  its `Text` recomposes; (b) give `MicOrb` lambda providers (`level: () -> Float`, `levels: () -> List<Float>`) that
  read `HummingVoice.state.value` inside the `Canvas`, and keep the slow fields (phase, transcript, flags) in the
  parent; (c) in `FlorisImeService` collect a derived flag, e.g.
  `remember { HummingVoice.state.map { it.open }.distinctUntilChanged() }.collectAsState(false)`.
- **Low: the `MicOrb` infinite transition keeps ticking when nothing reads it.** `HummingVoice.kt:797-799` creates
  `breath` and `spin` unconditionally. They are only drawn while LISTENING or FINISHING, but the transition still
  asks for a frame every vsync whenever the panel is open (idle, error, model download). **Fix:** create the
  transition only inside `if (listening || finishing)`, or in a child composable shown only in those phases.
- **Low: press-scale animations are read in composition.** `HummingVoice.kt:590/595` (`HummingMicButton`) and
  `778/781` (`RoundAction`) use `.scale(scale)`. During the spring, each frame recomposes the button.
  **Fix (compile-safe):** `.graphicsLayer { scaleX = scale; scaleY = scale }`.
- **Low: draw-time allocations.** `HummingVoice.kt:837` builds a new `Brush.linearGradient` on every orb draw (every
  frame while listening). `HummingPalette.accentBrush` (565) is a getter that allocates on every access (598, 867).
  **Fix:** use `Modifier.drawWithCache { val b = Brush.linearGradient(...); onDrawBehind { ... } }` for the orb, and
  make `accentBrush` a stored `val` (palettes are fixed objects).
- **Low: the auto-scroll effect is relaunched on every partial.** `HummingVoice.kt:703`
  `LaunchedEffect(st.transcript)` cancels and relaunches a coroutine for every partial result, and reads
  `scroll.maxValue` before the new text has been laid out. **Fix:** a single
  `LaunchedEffect(Unit) { snapshotFlow { scroll.maxValue }.collect { scroll.animateScrollTo(it) } }`.
- **Low: unused state collection.** `HummingVoice.kt:852` `val st by HummingVoice.state.collectAsState()` in
  `VoiceEngineMenu` is never read. Because a `by` delegate that is never read does not subscribe, this costs only a
  collector. **Fix (compile-safe):** delete the line.
- **Low:** `FlorisImeService.kt:851` uses `onGloballyPositioned` to store the keyboard height in state.
  `Modifier.onSizeChanged` or `onPlaced` does the same job more cheaply.

## 2. edge-to-edge

**Checked:** all activities in `AndroidManifest.xml` (60-172); `kotlin/dev/patrickgold/florisboard/app/FlorisAppActivity.kt:94,188-195`;
`app/apptheme/Theme.kt:81-87`; `lib/compose/FlorisScreen.kt:133-166`; `res/values/themes.xml:29-34`;
`FlorisImeService.kt:329,620,815`; `ime/clipboard/FlorisCopyToClipboardActivity.kt:177`; `res/layout/crash_dialog.xml:6`.
The skill's prerequisite (Compose, targetSdk 35 or higher) is met: targetSdk is 36 (`keyboard/gradle.properties`).

- **No issue found: the IME window.** `FlorisImeService` sets `setDecorFitsSystemWindows(false)` (329, 620), the IME
  theme has a transparent navigation bar (`themes.xml:33`), and the keyboard row applies `safeDrawingPadding()`
  after drawing its background (815). The floating card is the one exception, and it sits above the bar on purpose.
  `VoiceInputLayout` is drawn inside that row (845), so the voice panel's bottom hint stays clear of the navigation
  bar. The IME panels `QuickActionsEditorPanel.kt:246` and `SelectSubtypePanel.kt:57` use `safeDrawingPadding()`.
- **No issue found: soft keyboard over settings fields.** `FlorisAppActivity` has `adjustResize` (manifest 114) and
  `imePadding()` on the root column (`FlorisAppActivity.kt:195`). That covers the `OutlinedTextField`s in
  `app/settings/voice/VoiceScreen.kt:283-319`. `CrashDialogActivity` (View-based) uses
  `fitsSystemWindows="true"`. `FlorisCopyToClipboardActivity` pads its bottom sheet with `navigationBarsPadding()`.
- **Low: manual edge-to-edge instead of `enableEdgeToEdge()`, and nav-bar icon colour is never set.**
  `FlorisAppActivity.kt:94` calls `WindowCompat.setDecorFitsSystemWindows(window, false)`, and `Theme.kt:85` sets
  only `isAppearanceLightStatusBars`. The skill requires both status and navigation icon appearance when
  `ComponentActivity.enableEdgeToEdge()` is not used. `FlorisScreen.kt:136-141` also writes
  `window.statusBarColor`/`navigationBarColor`, which are deprecated and ignored on Android 15+ at targetSdk 36. On
  API 29+ the contrast scrim (139) keeps 3-button navigation legible, which is why this is only Low. **Fix:** call
  `enableEdgeToEdge()` before `setContent` in `FlorisAppActivity` (it is a `ComponentActivity`) and remove the
  manual bar colours. Otherwise, also set `isAppearanceLightNavigationBars = !darkTheme` at `Theme.kt:85`.
- **Low: lists stop above the navigation bar instead of scrolling behind it.** `FlorisAppActivity.kt:191` applies
  `navigationBarsPadding()` to the root column, so no settings list draws behind the bar. The skill's pattern is to
  pass the inset as `contentPadding`. Cutout padding is applied only in landscape (192); `safeDrawing` would cover
  every orientation. This works as is; treat it as polish.

## 3. styles (theming)

**Checked:** `res/values/themes.xml:4-44`, `res/values/colors.xml:3-15`, `res/values-night/colors.xml`,
`kotlin/dev/patrickgold/florisboard/app/apptheme/Theme.kt:36-94`,
`keyboard/lib/color/src/main/kotlin/org/florisboard/lib/color/ColorMappings.kt:36-37`,
`HummingVoice.kt:551-580`, `docs/BRAND.md`.

- **Styles API: not applicable yet.** The skill's API is experimental and needs compileSdk 37 and Compose foundation
  1.12.0-alpha01 (BOM 2026.04.01) or newer. The project is on compileSdk 36 and BOM 2025.11.00
  (`keyboard/gradle/libs.versions.toml:7`). Adopting it means moving to alpha Compose. This review does not
  recommend that now. The app UI already uses Material 3 (no `androidx.compose.material.MaterialTheme` imports were
  found).
- **No issue found: the brand accent is tokenised for the settings UI.** The Compose theme seeds
  `neutralDynamicColorScheme` from `systemAccentOrDefault(accentColor)` (`Theme.kt:43-61`), and the default is
  `HUMMING_PINK = Color(0xFFE5486B)` (`ColorMappings.kt:37`). The XML theme uses `@color/colorPrimary` = `#E5486B`
  (`colors.xml:3`).
- **Low: the raw brand hex is duplicated outside the tokens.** `0xFFE5486B` is hard-coded in
  `com/humming/keyboard/DictationService.kt:106`, `AppUpdate.kt:360`, `lib/crashutility/CrashUtility.kt:281` and
  `HummingVoice.kt:571`. `HummingPalette` (551-580) is a second, hand-maintained palette, separate from both
  `MaterialTheme` and the Snygg keyboard themes. `docs/BRAND.md` says "never put a raw hex in a component when a
  semantic token exists". **Fix:** notifications use `ContextCompat.getColor(ctx, R.color.colorPrimary)` (or
  `HUMMING_PINK.toArgb()`), and `HummingPalette.Light/Dark` reference named constants mirroring the BRAND.md
  primitives (`pink-500`, `magenta-600`, ...).
- **Low: possible brand drift.** BRAND.md lists the hero gradient top as `#EB4C7B` (line 10), but the hero mic and
  orb gradient start at `#E5486B` (`HummingVoice.kt:571`). Pick one and record it in BRAND.md.

## 4. adaptive

**Checked:** `kotlin/com/humming/keyboard/SplitLayout.kt:8-30`, `ime/text/keyboard/TextKeyboardLayout.kt:244-247`,
`ime/text/keyboard/TextKeyboard.kt:60-61`, `FlorisImeService.kt:800-830`, `lib/compose/FlorisScreen.kt:147-166`,
`app/FlorisAppActivity.kt:188-203`. A repo-wide search for `WindowSizeClass`, `currentWindowAdaptiveInfo`,
`FoldingFeature` and `material3-adaptive` found nothing. Navigation is `navigation-compose` 2.9.6, not Navigation 3,
so the skill's Nav3 scene steps are a later migration.

- **No issue found: split keyboard.** The split decision depends on the measured keyboard width (at least 600 dp,
  `SplitLayout.kt:10-14`), not on orientation. It therefore follows foldables, multi-window and freeform resizing.
  Touch and visible bounds are both remapped (`TextKeyboard.kt:60-61`). `SplitLayoutTest` exists, per the
  file's header.
- **Low: one-handed mode and bottom offset decide by orientation, not width.** In `FlorisImeService.kt:800-826`
  (upstream code), `configuration.isOrientationPortrait()/Landscape()` chooses the one-handed scaling and the bottom
  offset. On an unfolded foldable or a tablet in portrait (600 dp and wider), one-handed scaling still applies.
  **Fix:** gate it on the same width test as the split (`SplitLayout.MIN_WIDTH_DP`).
- **Low: settings stretch to full width on tablets.** `FlorisScreen.kt:158-163` lays out every preference screen
  with `fillMaxWidth()` and no maximum width or second pane. On a 1280 dp landscape tablet, rows and switches end up
  very wide. **Fix (small):** `widthIn(max = 840.dp)` plus centring on the `PreferenceLayout`. **Fix (larger):** a
  list-detail layout for Settings home, after a Navigation 3 migration.
- **Low: the voice transcript is full width on large screens.** `HummingVoice.kt:636-640,719`: on a tablet the
  22 sp transcript runs very long lines. **Fix:** `widthIn(max = 640.dp)` on the transcript `Text`, centred.

## 5. agp-9-upgrade (readiness only, nothing changed)

**Current:** AGP 8.12.0 (`keyboard/gradle/libs.versions.toml:3`), Gradle 9.2.0 (`gradle/wrapper/gradle-wrapper.properties`),
Kotlin 2.2.20 (17), KSP 2.2.20-2.0.3 (20), Room 2.7.2 (14), AboutLibraries plugin 13.1.0 (22),
compile/targetSdk 36, build-tools 35.0.0, JDK 17 (`gradle/tools.versions.toml`). `gradle.properties` contains none of
the temporary AGP 9 flags.

The skill's rule: AGP is below 9, so the first step is to run the AGP Upgrade Assistant in Android Studio. CLAUDE.md
forbids local builds on this machine, so any upgrade can only be verified in CI. Blockers found by reading:

- **High (blocker): KSP is too old.** The skill requires KSP 2.3.6 or newer for AGP 9; the project has 2.2.20-2.0.3.
  Whether the KSP processors (Room 2.7.2, `jetpref-datastore-model-processor` 0.3.0-rc01) work with that KSP is
  **unverified**.
- **High (blocker): the legacy variant API is used.** `keyboard/lib/snygg/build.gradle.kts:113`
  `android.libraryVariants.first { ... }.javaCompileProvider` uses the old variant API, which the new AGP 9 DSL
  removes. It needs rewriting with `androidComponents`.
- **Medium: built-in Kotlin migration.** `org.jetbrains.kotlin.android` is applied in seven modules
  (`app/build.gradle.kts:23`, `lib/android:21`, `lib/color:5`, `lib/compose:21`, `lib/snygg:22`, `lib/native:21`,
  `benchmark:19`). AGP 9's built-in Kotlin requires removing it and moving `kotlin { compilerOptions }` accordingly.
- **Low: DSL spots to re-check.** In `app/build.gradle.kts:125-134`, `sourceSets { }` is nested inside
  `defaultConfig { }` (it resolves to `android.sourceSets` through the outer receiver) and adds `src/main/kotlin` as a
  Java dir. Also check `sourceSets.getByName("release").manifest.srcFile(...)` (201) and `buildToolsVersion` (91).
  AGP 9 compatibility of AboutLibraries 13.1.0 is **unverified**. `lib/native` builds Rust through CMake
  (`externalNativeBuild`) and should be re-tested.

## 6. android-permissions-security

**Checked:** `AndroidManifest.xml:5-27` (permissions), `46-202` (components); `src/play/AndroidManifest.xml`;
`kotlin/com/humming/keyboard/DictationService.kt`, `UpdateInstallReceiver.kt`, `PermissionActivity.kt`, `AppUpdate.kt:265-346`,
`SecretStore.kt`; `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`, `res/xml/file_paths.xml`.

- **No issue found: component exposure.** `PermissionActivity` (63), `DictationService` (71) and
  `UpdateInstallReceiver` (77) are all `exported="false"`. The IME and spell-checker services are exported but
  guarded by `BIND_INPUT_METHOD` / `BIND_TEXT_SERVICE` (86, 99). All three providers are `exported="false"` (179,
  187, 197). `file_paths.xml` exposes only two cache subfolders. No custom permissions are defined, so there is no
  weak protection level to fix.
- **No issue found: `DictationService`.** The type is `microphone` (manifest 72) together with
  `FOREGROUND_SERVICE_MICROPHONE` (18). `startForeground` with `FOREGROUND_SERVICE_TYPE_MICROPHONE` is wrapped in a
  try/catch, and on refusal the service stops itself (`DictationService.kt:37-45`). The Stop action is an explicit,
  immutable `PendingIntent` (101-103). The wake lock has a 12 h cap and is released in `onDestroy` (46-60).
- **No issue found: secrets in backup.** Backup uses include-lists, in both the pre-31 and the
  `data-extraction-rules` formats. `SecretStore`'s `humming_secrets` prefs are not included, and the value is
  AES-GCM ciphertext under an Android Keystore key anyway.
- **Low: the mic permission flow has no rationale or permanent-denial path.** `PermissionActivity.kt:13-31` calls
  `requestPermissions` directly and, on denial, only shows a toast. It never checks
  `shouldShowRequestPermissionRationale`, so after "Don't allow" twice the mic tap silently fails again.
  **Fix:** when denied and `!shouldShowRequestPermissionRationale(RECORD_AUDIO)`, open
  `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` for the package (or offer to).
- **Low (informational):** `<profileable android:shell="true"/>` (manifest 80) ships in release builds. It is meant
  for baseline profiles and allows shell-level profiling only. Leave it if the benchmark module needs it.

## 7. android-intent-security

**Checked:** every `getParcelableExtra`, `getStringExtra` and `PendingIntent` call in `kotlin/` (grep);
`FlorisAppActivity.kt:153-170,207-215`; `app/Routes.kt:85-97,108-276`; `HummingVoice.kt:766-772`;
`ime/clipboard/FlorisCopyToClipboardActivity.kt:96-134`; `UpdateInstallReceiver.kt:17-31`; `AppUpdate.kt:318-346`.

- **Medium: an exported "copy image to clipboard" activity trusts any `EXTRA_STREAM` URI (upstream code).**
  `FlorisCopyToClipboardActivity` is exported for `SEND image/*` (manifest 163-172). `handleIntent` takes any URI
  from `EXTRA_STREAM` (111-114), puts it on the clipboard with `ClipData.newUri` *before* decoding (125-126), then
  decodes it with the app's own `ContentResolver` (128-132). The caller controls the URI. It can therefore name
  Humming's own non-exported providers, e.g. `content://com.humming.keyboard.provider.file/backup/...` or the
  clipboard media provider. Android's clipboard grants read access to whoever later reads the clip, which can be the
  sending app once it is back in the foreground. That is a confused-deputy path to Humming's cached backups, stickers
  and clipboard images. **Not verified on a device.** **Fix:** before line 125, reject anything that is not
  `content://`, and reject URIs whose authority starts with `packageName`. Also catch decode exceptions (a
  `file://` URI on the clipboard throws `FileUriExposedException` at targetSdk 24 and above).
- **Low: the `ui://florisboard/...` deep links are `BROWSABLE`, so any web page can open any settings route.**
  The manifest filter (117-122) and `Routes.kt:95` cover all routes, including `devtools`, `export-debug-log`,
  `settings/other/backup|restore` and `ext/import`. They only navigate (every action still needs a tap), so the risk
  is phishing-style UI confusion. **Fix:** for internal navigation (`HummingVoice.kt:768`, `StickerPanel.kt:323`),
  use an explicit `Intent(ctx, FlorisAppActivity::class.java)` with the route as data. Then drop `BROWSABLE` from the
  filter, or skip `handleDeepLink` for `devtools*`/`export-debug-log` when the intent comes from outside.
  `openVoiceSettings` already sets `setPackage`, which is good.
- **Low: the update-confirm intent is relaunched without being checked.** `UpdateInstallReceiver.kt:21-26` takes
  the nested `EXTRA_INTENT` and `AppUpdate.kt:340-344` starts it, or wraps it in an immutable `PendingIntent`. The
  receiver is not exported and is reached only through the session's explicit `PendingIntent`, so only
  PackageInstaller can deliver it. **Hardening:** check that `confirm.resolveActivity(packageManager)` resolves to a
  system app (`ApplicationInfo.FLAG_SYSTEM`) and that `confirm.flags` has no URI-grant flags before launching it.
- **No issue found: `PendingIntent` mutability.** `AppUpdate.kt:330` is `FLAG_MUTABLE` with an explicit component
  (`Intent(ctx, UpdateInstallReceiver::class.java)`, 329), which PackageInstaller needs to fill in the status
  extras. The others are `FLAG_IMMUTABLE` (`AppUpdate.kt:344`, `DictationService.kt:103`, `CrashUtility.kt:282`).
  `PermissionActivity` reads no intent data. `FlorisAppActivity.onNewIntent` calls `setIntent` (155) before routing.
  Dynamic receivers listen only for protected system broadcasts (`USER_UNLOCKED`, `WALLPAPER_CHANGED`).

## 8. play-policy-insights

**Note:** this skill's own procedure runs `scripts/orchestrator.py`, which writes a `.scratch/` folder in the workspace.
That conflicts with this review's read-only brief, so it was **not run**. Its three policy areas (data safety, login
credentials, restricted permissions) were checked by hand. **Checked:** `AndroidManifest.xml:5-44`,
`src/play/AndroidManifest.xml`, `keyboard/app/build.gradle.kts:83-86,116,200-202`, `AppUpdate.kt:119,141-152`,
`.github/workflows/android-release.yml:73,105-110`, `play/DATA_SAFETY.md`.

- **No issue found: `REQUEST_INSTALL_PACKAGES` is out of the Play build.** With `-Phumming.play=true`, the release
  source set takes `src/play/AndroidManifest.xml`, which removes the permission (`tools:node="remove"`), and
  `SELF_UPDATE` becomes `false` (`build.gradle.kts:116,200-202`). CI fails if the AAB contains the permission
  (`android-release.yml:107-109`). Every updater entry point returns early when `!enabled` (`AppUpdate.kt:142,152`).
- **Low: updater code and its receiver still ship in the Play AAB.** `AppUpdate`'s `PackageInstaller` session code,
  the feed URL (`AppUpdate.kt:92`) and the `UpdateInstallReceiver` manifest entry are still in the Play build; they
  are disabled at runtime, not compiled out. Play's policy forbids self-updating, and dormant install code can draw
  reviewer questions. **Quick, compile-safe fix:** add
  `<receiver android:name="com.humming.keyboard.UpdateInstallReceiver" tools:node="remove"/>` to
  `src/play/AndroidManifest.xml`, and extend the CI check to grep the AAB manifest for `UpdateInstallReceiver`.
  **Fuller fix:** move `AppUpdate.kt`/`UpdateInstallReceiver.kt` into a sideload-only source set.
- **Medium (Play Console action, not code): the microphone foreground-service declaration.** Targeting SDK 34 or
  higher with `FOREGROUND_SERVICE_MICROPHONE` (manifest 18, 72) requires the Play Console foreground-service
  declaration: the use case, why it must run while the screen is off, and a demo video. The code supports the case:
  the service is user-started, runs only while dictating, shows an ongoing notification with Stop, and stops on
  user action. Prepare the declaration before submitting.
- **No issue found: package visibility.** There is no `QUERY_ALL_PACKAGES`. `<queries>` is limited to three intent
  actions (`InputMethod`, `SpellCheckerService`, `RecognitionService`; manifest 33-44). The Gboard check uses the
  enabled-IME list (`GboardHandoff.kt:35`).
- **No issue found: login credentials.** The app has no account or login, so no demo credentials are needed for
  review.
- **Medium (data safety, process): the form draft has open items.** `play/DATA_SAFETY.md` (1.6.3 draft, not
  submitted) matches the network paths found in code: Online voice through `SpeechRecognizer`, `LlmCleanup` to
  Gemini/OpenAI/Groq, `GifSearch` to Openverse, the model download from GitHub, and the update check (sideload
  only). It leaves "encrypted in transit" unresolved for Online voice audio and IP-address declaration open. Those
  must be settled before submission. The voice model download fetches model weights, not dex/so code; whether Play
  counts ONNX models as "executable code" is **unverified** (the native library is bundled in the APK).

---

### Quick, compile-safe fixes (all unverified until CI runs)

1. `HummingVoice.kt:687`: rec-dot alpha through `graphicsLayer { alpha = a }` or `drawBehind` (High perf item).
2. `HummingVoice.kt:595,781`: `.scale(x)` to `.graphicsLayer { scaleX = x; scaleY = x }`.
3. `HummingVoice.kt:852`: delete the unused `st` collector in `VoiceEngineMenu`.
4. `src/play/AndroidManifest.xml`: remove `UpdateInstallReceiver` from the Play manifest with `tools:node="remove"`.
5. `Theme.kt:85`: also set `isAppearanceLightNavigationBars = !darkTheme`.
