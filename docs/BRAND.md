# Humming brand

One look on every platform: pink, white sound-wave bars, dark or light neutral surfaces.

## Colours

| Name | Hex | Use |
|---|---|---|
| Brand pink | `#E5486B` | The accent everywhere: buttons, links, toggles, focus, badges, notification tint, splash background, the default accent colour on Android |
| Gradient top | `#EB4C7B` | App icon and hero gradients, top / start |
| Gradient bottom | `#CA349C` | App icon and hero gradients, bottom / end (magenta) |
| Pink, dark | `#C93A5B` | Pressed states and text on light surfaces |
| Pink, light (night) | `#F0607F` | Accent on dark surfaces where `#E5486B` is too dim |
| Magenta, night | `#D9479F` | Gradient end on dark surfaces |
| Pink tint | `#FDE3EA` | Selected / focused backgrounds on light surfaces |
| Ink | `#0F0F13` | Dark backgrounds (site, Play graphics, iOS) |
| Success | `#1E9E5A` | Status only (installed, done); never as an accent |
| Recording | `#FF3B5C` / `#FF4D6A` | The live-recording dot only |

The gradient always runs top to bottom (or top-left to bottom-right), pink to magenta.

## Design tokens

One token set, three layers, five implementations. **Primitives** are raw values; **semantic** tokens name a job
(accent text, filled button); **component** tokens bind a semantic token to one part. Change a primitive once and
every platform follows; never put a raw hex in a component when a semantic token exists. Every text/background pair
listed meets WCAG AA (4.5:1 for text under 18pt, 3:1 for icons and large text), measured with the WCAG relative
luminance formula (script in the 1.7 pass: `docs/ci/DESIGN_AUDIT.md`).

### Primitives

| Token | Light | Dark | Notes |
|---|---|---|---|
| `pink-500` | `#E5486B` | `#F0607F` | Brand pink. Fills only on light (3.84:1 with white text) |
| `pink-600` | `#C93A5B` | — | Filled buttons with white text (4.95:1); accent text on light (4.6:1 on `#F6F6F8`) |
| `pink-700` | `#B03050` | — | Pressed state; accent text on pink-tinted light surfaces (4.59:1 on 14 % pink tint) |
| `magenta-600` | `#CA349C` | `#D9479F` | Gradient end |
| `ink-950` | `#0F0F13` | | Site and Play background |
| `neutral-*` | `#F6F6F8` bg, `#FFFFFF` card, `#E4E4EA` line, `#6B6B76` muted, `#16161A` text | `#121216` bg, `#1B1B21` card, `#2B2B33` line, `#9A9AA6` muted, `#ECECF1` text | Desktop; the site and Compose use close neighbours listed below |
| `green-700` | `#136B3D` / `#13703F` | `#3DD68C` / `#7EE2A8` | Success and "Offline" status only |

### Semantic tokens

| Role | Android IME (Snygg) | Android voice panel (Compose) | Desktop (CSS) | iOS (SwiftUI) | Website (CSS) |
|---|---|---|---|---|---|
| Accent fill | `--primary` / `--primary-variant` | `accentStart` → `accentEnd` gradient | `--accent` | `Brand.accent` | `--accent` |
| Filled control (white text) | `--primary-strong` (Enter key) | gradient orb, icon only | `--accent-strong` | `Brand.accentStrong` | `--accent-strong` |
| Accent text | — | `accentText` (`#B03050` light, `#F0607F` dark) | `--accent-ink` | `Brand.accentText` | `--accent-ink` |
| Background | `--background` | `background` | `--bg` | system | `--bg` |
| Surface / card | `--surface` | `surface` | `--card` | system grouped | `--surface` |
| Text | `--on-surface` | `onBackground` | `--fg` | `.primary` | `--fg` |
| Secondary text | `--on-surface-variant` | `secondary` (`#545A6A` light) | `--muted` | `.secondary` | `--muted` |
| Success | — | `ok` (`#136B3D` light) | `--ok` | — | `--ok` |
| Recording dot | — | `rec` | `.pill.recording` | red dot | — |

Sources of truth: `keyboard/app/src/main/assets/ime/theme/org.florisboard.themes/stylesheets/humming_{light,dark,amoled}.json`
(guarded by `HummingThemeTest` and `ThemeContrastTest`), `HummingPalette` in
`keyboard/app/src/main/kotlin/dev/patrickgold/florisboard/ime/voice/HummingVoice.kt`, `:root` in `desktop/src/style.css`,
`Brand` in `ios/Shared/SharedStore.swift`, `:root` in `play/site/assets/site.css`.

### Type scale

System faces everywhere (Roboto / Google Sans on Android, SF on Apple, Segoe UI on Windows, the platform UI font on
Linux and the web): no font downloads, no third-party font hosts.

| Step | Android (sp) | Desktop (px) | Website (rem, fluid) | Use |
|---|---|---|---|---|
| Caption | 12 | 11-12 | 0.8125 | Hints, pills, metadata |
| Label | 13 | 13-14 | 0.875 | Chips, buttons, timers |
| Body | 16 | 14 | 1-1.0625 | Settings, paragraphs |
| Transcript | 22 (18 when long) | 15 | — | Live dictation text |
| Title | 22 | 22 | 1.5-2 | Screen and section titles |
| Display | — | — | 2.5-4.25 (`clamp`) | Site hero only |

### Shape, elevation, motion, touch

| Token | Value | Where |
|---|---|---|
| Radius S / M / pill | 8 / 12 / 999 px (keys 10 dp, popups 14 dp) | Buttons and inputs / cards / chips and pills |
| Elevation 1 | `0 1px 2px` at 6 % ink (light), 40 % black (dark); keys `1dp` | Cards, active nav item, keys |
| Elevation 2 | 6-8 dp coloured shadow | The mic button and voice menu only |
| Motion: press | spring, damping 0.45-0.5, stiffness 600-700 | Mic, round actions (Compose) |
| Motion: UI | 140 ms (fast), 220 ms (base), `cubic-bezier(.16,1,.3,1)` | Desktop and site transitions |
| Motion: live level | 120 ms linear; halo breathes over 1.6 s | Voice orb |
| Reduced motion | `prefers-reduced-motion` turns CSS transitions off; Compose follows the system animator scale | Desktop, site, Android |
| Touch target | 48 dp Android, 44 pt iOS, 24 px minimum on the web (controls are 40-48 px) | Every control; the voice panel's Clean chip got a 48 dp target in 1.7 |
| Focus | 2 px `--accent` outline, 2 px offset | Desktop and site, `:focus-visible` |

### Rules

- Accent **text** uses accent-text tokens, never `pink-500` on a light surface.
- White text sits only on `pink-600` or darker (or on the gradient for icons, which need 3:1).
- Status colours (success, recording, warning) never act as the accent.
- Dark and light are both first-class: every semantic token has both values; check both before shipping.

## Icon

White rounded bars (5 bars, heights short-medium-tall-medium-short) on the pink gradient.

| Platform | Files |
|---|---|
| Android | `keyboard/app/src/main/res/drawable/ic_humming_background.xml` (gradient), `ic_humming_foreground.xml` (bars), `ic_app_icon_{stable,beta,debug}_{foreground,monochrome}.xml`, `mipmap-anydpi-v26/ic_humming.xml` and `ic_app_icon_*.xml`, the `mipmap-*dpi/ic_app_icon_*.png` fallbacks, `keyboard/app/src/main/ic_app_icon_*-playstore.png` |
| Desktop | `desktop/src-tauri/icons/` (`icon.png`, `.icns`, `.ico`, tray icons) |
| iOS | `ios/App/Assets.xcassets/AppIcon.appiconset/` |
| Play / web | `play/graphics/icon-512.png`, `play/graphics/feature-graphic-1024x500.png`, `landing/icon.png` |

The master is `play/graphics/icon-512.png`; the Android vector bars use the same geometry, scaled into the
adaptive-icon safe zone.

## Do

- Use `#E5486B` for every accent; use the gradient only for the icon and large hero surfaces.
- Keep the bars white and the icon background the gradient (themed/monochrome icons use the bars alone).
- Write the name as **Humming** (the app) or **Humming Keyboard** (the project).
- Credit the keyboard base as: "Android keyboard built on FlorisBoard (Apache 2.0)".

## Don't

- No FlorisBoard green (`#4CAF50`) or orange (`#FF9800`), and no older purple/blue Humming gradient (`#7B61FF` to `#3D8BFF`).
- Don't use the FlorisBoard name or lotus logo as Humming's branding. FlorisBoard's name, licence and copyright stay
  exactly as they are in `keyboard/LICENSE`, `keyboard/NOTICE-HUMMING.md`, file headers and the credit line.
- Don't recolour the bars, add outlines, or put the icon on a pink background without its rounded shape.
