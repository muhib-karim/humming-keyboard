---
name: humming-site-brand
description: Edit the GitHub Pages site (play/site/), desktop/banner/poster graphics, or README showcase assets. Use only when changing website HTML/CSS/JS, brand tokens (docs/BRAND.md), or rendered PNGs.
---

# Humming Site & Brand Workflow

1. **Brand tokens (`docs/BRAND.md`)**:
   - Ink `#0F0F13`, Brand Pink `#E5486B`, Dark/Button Pink `#C93A5B` (4.95:1 white text), Night Pink `#F0607F`, Magenta `#CA349C`, Success `#136B3D` / `#7EE2A8`. Never reintroduce FlorisBoard green/orange or legacy purple/blue gradients.
2. **Website CSP & static checks (`play/site/`)**:
   - Zero inline `<style>`, zero `style=""` attributes, zero inline `<script>`, zero external origins. Every `<img>` must include `alt`, `width`, and `height`.
   - Assemble & validate:
     `python3 scripts/assemble_site.py /tmp/humming-site && python3 scripts/check_site.py /tmp/humming-site`
3. **Brand & launch graphics**:
   - Regenerate `docs/banner.png`, `docs/social-preview.png`, `docs/poster.png`, and `play/graphics/feature-graphic-1024x500.png` via `python3 scripts/render_social_preview.py`.
4. **Mandatory headless browser visual QA**:
   - Serve `/tmp/humming-site` locally and screenshot `1440x900` (dark & light), `768x1024`, `390x844` (dark & light), interactive demo tabs, and `privacy.html`. Verify zero horizontal overflow, zero broken images, and inspect screenshots directly before committing.
