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
