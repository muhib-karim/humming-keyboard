# Design audit, 1.7.0-rc1 (Impeccable + UI/UX Pro Max)

17 October 2026, branch `feat/v1.7-premium`. Skills: **impeccable** (`audit`, its mechanical `detect` scan, and the
craft floor), **ui-ux-pro-max** (searches: `landing` "mobile app product landing download" → *App Store Style
Landing*; `ux` "focus visible keyboard" → *Focus States*; `ux` "touch target size" → *48 dp Android / 44 pt iOS /
24 px web*; its `--design-system` palette suggestion (green accent, Google Fonts) was rejected as off-brand and
against the no-CDN rule). No Chromium or Playwright browser is installed on this machine, so no rendered screenshots
were taken; contrast was computed from the colour values, and visual checks are left to CI (`screens.yml` for desktop).
Impeccable had no PRODUCT.md and no way to ask questions in this session, so product context was taken from the
owner's brief.

## Audit health score (before → after this pass)

| # | Dimension | Before | After | Key finding |
|---|---|---|---|---|
| 1 | Accessibility | 2 | 3 | White on `#E5486B` (3.84:1) on desktop and site buttons; pink, green and amber pill text 2.6-3.1:1; Clean chip touch target 28 dp |
| 2 | Performance | 3 | 3 | Voice orb draws 40 bars per frame while listening; acceptable, see `docs/ci/ANDROID_REVIEW.md` |
| 3 | Theming | 2 | 3 | Desktop pills hard-coded light colours in dark mode; tokens now cover both |
| 4 | Responsive | 3 | 3 | Desktop is a fixed-size window by design; site rebuilt for 360 px and up |
| 5 | Implementation integrity | 3 | 3 | Coherent pink/ink identity on every platform |
| | **Total** | **13/20** | **15/20** | Acceptable → Good |

## Findings and what was done

| Sev | Finding | Where | Fix (commit) |
|---|---|---|---|
| P1 | White text on `#E5486B` = 3.84:1 (detector: 4 hits) | `desktop/src/style.css` `button.primary`, site buttons | Filled buttons use `--accent-strong` `#C93A5B` (4.95:1); detector now reports 0 |
| P1 | `#C62828` danger text on dark card = 3.0:1 (detector) | desktop dark mode | `--danger` dark `#FF8A80` (7.5:1) |
| P1 | Pill text below 4.5:1 (recording 3.14, ready 3.05, warn 3.04) | desktop pills | Token pairs per theme: 5.76 / 5.37 / 5.31 light, 7.0+ dark |
| P1 | Clean chip text `#E5486B` on its 14 % tint = 2.84:1 | Android voice panel, light | `accentText` `#B03050` (4.59:1) |
| P1 | "Offline" pill `#1E9E5A` on its tint = 2.64:1; incognito pill 4.14:1 | Android voice panel, light | `ok` `#136B3D` (4.80:1), `secondary` `#545A6A` (5.03:1) |
| P1 | Clean/Verbatim chip touch target ~28 dp, no role or state for TalkBack | Android voice panel | 48 dp target, `Role.Switch`, state description |
| P2 | Orb and round actions not announced as buttons | Android voice panel | `Role.Button` |
| P2 | No visible focus style, no reduced-motion handling, default selection colour | desktop | `:focus-visible` ring, `prefers-reduced-motion`, themed `::selection` |
| P2 | iOS accent text and filled capsules use `#E5486B` (below 4.5:1 for white or accent text) | `ios/App`, `ios/Shared` | `Brand.accentText`, `Brand.accentStrong` |
| P3 | Smartbar mic button is 38 dp by default | Android Smartbar | Not changed: its size is a user setting (Settings > Smartbar > voice button size) and enlarging it shifts the Smartbar layout without a device to check; open item |

## Contrast script

```python
import sys
def L(h):
    h=h.lstrip('#'); r,g,b=[int(h[i:i+2],16)/255 for i in (0,2,4)]
    f=lambda c: c/12.92 if c<=0.03928 else ((c+0.055)/1.055)**2.4
    return 0.2126*f(r)+0.7152*f(g)+0.0722*f(b)
def cr(a,b):
    x,y=sorted([L(a),L(b)],reverse=True); return (x+0.05)/(y+0.05)
for pair in sys.argv[1:]:
    a,b=pair.split(':'); print(f"{a} on {b}: {cr(a,b):.2f}")
```

Usage: `python3 contrast.py ffffff:c93a5b c93a5b:f6f6f8` prints each ratio.

## Website (play/site), rebuilt in this pass

- Direction (UI/UX Pro Max *App Store Style Landing*, adapted): real captures in device frames in the hero, feature rows
  with screenshots instead of a card grid, an honest comparison table drawn from `COMPETITORS.md`, per-platform downloads,
  FAQ. No reviews or ratings section: Humming has no verified store ratings to show.
- No frameworks, no web fonts, no third-party requests; a Content-Security-Policy meta tag (`default-src 'self'`) on
  every page. Dark and light from `prefers-color-scheme`, tokens as in `docs/BRAND.md`.
- `scripts/check_site.py` (run by `pages.yml`): every local link, image and anchor resolves, tags balance, every image
  has alt text and dimensions, nothing loads from another origin. Local result: 3 pages, 0 problems.
- Impeccable `detect` on the assembled pages: one real finding fixed (footer column titles were 14 px `h2`s, flattening
  the heading scale; now labelled `nav` lists). Remaining: `cramped-padding` on `.table-wrap`, a false positive (the
  table's cells carry 12-16 px padding; the detector sees only the table flush with its wrapper).
- Layout checked by arithmetic at 360 px: hero phones and feature phones size as percentages of their container; wide
  tables scroll inside their own region. A rendered check at 360/768/1024/1440 px is pending (no browser on this machine).
- Craft-floor ruling: the brief asks for system or self-hosted fonts, and no suitable font is installed to self-host, so
  the site uses the platform UI font stack with weight and size carrying the hierarchy.
