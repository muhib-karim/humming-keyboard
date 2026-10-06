# Humming 1.6.2: emulator walk of the settings screens

Commit built: 46cea1f47addb0e863432c28e7c87401ecde9e4c (branch v1.6.2), x86_64 release build of the same source as the published APK.

Emulator: AVD h28, system-images;android-28;default;x86_64 (Android 9), software-rendered (-accel off, swiftshader_indirect), 720x1600 @320dpi, 1536MB RAM, 2 cores, animations disabled.

## Files (screens/*.png, text/*.txt same basename)
- settings_home: Settings root/home screen.
- settings_search: Settings search screen (empty search field).
- settings_voice / settings_voice_scrolled: Voice settings, top and scrolled down.
- settings_privacy: Privacy settings.
- settings_updates: Updates settings.
- settings_localization: Localization settings list.
- settings_localization_select-locale: Select-locale picker.
- settings_localization_language-pack-manage: Manage language packs.
- settings_localization_subtype_add: Add subtype screen.
- settings_theme / settings_theme_manage / settings_theme_background: Theme settings, manage themes, background picker.
- settings_stickers: Stickers settings.
- settings_keyboard / settings_keyboard_input-feedback: Keyboard settings, input feedback sub-screen.
- settings_smartbar: Smartbar settings.
- settings_typing / settings_typing_shortcuts: Typing settings, shortcuts sub-screen.
- settings_dictionary / settings_dictionary_user-dictionary: Dictionary settings, user dictionary sub-screen.
- settings_gestures: Gestures settings.
- settings_clipboard: Clipboard settings.
- settings_media: Media settings.
- settings_other / settings_other_physical-keyboard / settings_other_backup / settings_other_restore: Other settings and its sub-screens.
- settings_about / settings_about_bottom / settings_about_project-license / settings_about_third-party-licenses: About screen (top and scrolled to bottom), project license, third-party licenses.
- ext / ext_check-updates: Addons & Extensions root, Check for updates screen.
- ext_theme_list: Addons & Extensions > Themes list (manual tap from ext root).
- ext_keyboard_list / ext_keyboard_detail: Addons & Extensions > Keyboard extensions list and one item's details page (manual).

## What did NOT work / was skipped
- Addons & Extensions > Language packs list: attempted (ext_language_list target) but the capturing shell call timed out before this step ran; not captured this run.
- Keyboard open on the settings search field (tapping the search field to raise the IME) and the dictation panel (pink mic) screenshots were not captured (not taken in the time available).
- ext_keyboard_detail.png and ext_keyboard_list.png are identical in file size; the manual tap meant to open one extension's details may not have navigated to a distinct details page — worth a re-check if this run is redone with more time.

## ANR encountered and recovered
First pass of walk.sh (WAIT=6) ran into a "System UI isn't responding" ANR dialog that was already showing before the very first route (settings/home) opened — likely leftover from boot/install — so every screenshot in that first pass is the ANR dialog, not the real screens (confirmed by checking the first screenshot with attach_image per the brief's instruction). Recovered by: adb KEYCODE_BACK, wait 20s, tap "Wait" on the dialog, wait 20s more, confirmed the underlying real screen was showing again, then re-ran walk.sh fully (WAIT=10). The second run's screenshots were verified real (checked settings_home, settings_voice, settings_about, ext, ext_check-updates with attach_image) and walk.log shows all 31 routes completed. All shots/*.png and text/*.txt in this delivery are from the second (good) walk run plus the manual additions above.

## FlorisBoard text hits
See shots/FLORIS_HITS.md — only expected hit is in settings_about.txt ("Android keyboard built on FlorisBoard (Apache 2.0)").
