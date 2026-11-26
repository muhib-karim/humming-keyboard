#!/usr/bin/env python3
"""Render docs/banner.png (1600x640) and docs/social-preview.png (1280x640) from real brand-compliant captures.

Usage:
  render_social_preview.py [repo_root]   (defaults to repo root; needs Pillow + Liberation Sans or DejaVu Sans)
"""
import argparse
import math
import os
from PIL import Image, ImageDraw, ImageFilter, ImageFont

FONT_BOLD_CANDIDATES = (
    "/usr/share/fonts/truetype/liberation2/LiberationSans-Bold.ttf",
    "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
)
FONT_REG_CANDIDATES = (
    "/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf",
    "/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
)


def pick_font(candidates):
    for p in candidates:
        if os.path.isfile(p):
            return p
    raise FileNotFoundError("no suitable sans-serif font found in: " + ", ".join(candidates))


def make_bg(w, h):
    # Brand ink (#0F0F13) with a subtle radial magenta-wine glow behind the devices (docs/BRAND.md).
    base = Image.new("RGBA", (w, h), (15, 15, 19, 255))
    glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = glow.load()
    cx, cy = int(w * 0.78), int(h * 0.52)
    rx, ry = w * 0.44, h * 0.85
    for y in range(h):
        dy = (y - cy) / ry
        dy2 = dy * dy
        for x in range(w):
            dx = (x - cx) / rx
            r2 = dx * dx + dy2
            if r2 < 1.0:
                t = (1.0 - r2) ** 1.7
                px[x, y] = (202, 52, 156, int(74 * t))
    base.alpha_composite(glow)
    # Subtle top-left pink ambient bloom
    bloom = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    bpx = bloom.load()
    bx, by = int(w * 0.12), int(h * 0.16)
    brx, bry = w * 0.30, h * 0.55
    for y in range(h):
        dy = (y - by) / bry
        dy2 = dy * dy
        for x in range(w):
            dx = (x - bx) / brx
            r2 = dx * dx + dy2
            if r2 < 1.0:
                t = (1.0 - r2) ** 2.0
                bpx[x, y] = (229, 72, 107, int(36 * t))
    base.alpha_composite(bloom)
    return base


def rounded(im, r):
    m = Image.new("L", im.size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, im.width - 1, im.height - 1), r, fill=255)
    im = im.convert("RGBA")
    im.putalpha(m)
    return im


def phone_frame(path, h):
    s = Image.open(path).convert("RGB")
    pad = 9
    inner_h = h - pad * 2
    inner_w = round(s.width * inner_h / s.height)
    s = rounded(s.resize((inner_w, inner_h), Image.LANCZOS), 28)
    frame = Image.new("RGBA", (inner_w + pad * 2, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(frame)
    d.rounded_rectangle(
        (0, 0, frame.width - 1, frame.height - 1),
        36,
        fill=(26, 26, 33, 255),
        outline=(78, 78, 92, 255),
        width=2,
    )
    frame.alpha_composite(s, (pad, pad))
    return frame


def desktop_frame(path, w):
    s = Image.open(path).convert("RGB")
    pad = 8
    inner_w = w - pad * 2
    inner_h = round(s.height * inner_w / s.width)
    s = rounded(s.resize((inner_w, inner_h), Image.LANCZOS), 12)
    frame = Image.new("RGBA", (w, inner_h + pad * 2), (0, 0, 0, 0))
    d = ImageDraw.Draw(frame)
    d.rounded_rectangle(
        (0, 0, frame.width - 1, frame.height - 1),
        18,
        fill=(26, 26, 33, 255),
        outline=(78, 78, 92, 255),
        width=2,
    )
    frame.alpha_composite(s, (pad, pad))
    return frame


def composite_shadowed(canvas, fr, pos):
    sh = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    m = Image.new("RGBA", fr.size, (0, 0, 0, 175))
    m.putalpha(fr.getchannel("A").point(lambda a: 175 if a else 0))
    sh.alpha_composite(m, (pos[0] + 10, pos[1] + 18))
    canvas.alpha_composite(sh.filter(ImageFilter.GaussianBlur(20)))
    canvas.alpha_composite(fr, pos)


def draw_waveform(d, x0, y_mid, bars=18, step=9, color=(229, 72, 107)):
    heights = [8, 14, 24, 12, 30, 18, 10, 22, 14, 28, 16, 8, 20, 12, 24, 10, 16, 8]
    for i in range(bars):
        bh = heights[i % len(heights)]
        x = x0 + i * step
        d.rounded_rectangle((x, y_mid - bh // 2, x + 4, y_mid + bh // 2), 2, fill=color)


def draw_left_column(canvas, root, font_b, font_r, *, x, wide):
    d = ImageDraw.Draw(canvas)
    # Icon + brand wordmark + Offline status pill
    icon_sz = 84 if wide else 78
    icon = rounded(Image.open(f"{root}/play/graphics/icon-512.png").resize((icon_sz, icon_sz), Image.LANCZOS), 20)
    canvas.alpha_composite(icon, (x, 74))
    d.text((x + icon_sz + 22, 82), "Humming", font=ImageFont.truetype(font_b, 58 if wide else 54), fill=(244, 244, 247))

    # Offline green pill aligned next to wordmark on wide banner, or below on social preview
    f_pill = ImageFont.truetype(font_b, 17)
    pill_txt = "OFFLINE BY DEFAULT"
    pw = d.textlength(pill_txt, font=f_pill) + 34
    px = x + icon_sz + 22 + d.textlength("Humming", font=ImageFont.truetype(font_b, 58 if wide else 54)) + 20
    py = 102
    if not wide:
        px, py = x, 178
    d.rounded_rectangle((px, py, px + pw, py + 32), 16, fill=(23, 50, 37, 255), outline=(61, 214, 140, 160), width=1)
    d.ellipse((px + 11, py + 12, px + 19, py + 20), fill=(126, 226, 168))
    d.text((px + 26, py + 6), pill_txt, font=f_pill, fill=(126, 226, 168))

    # Headline
    hy = 200 if wide else 232
    f_h = ImageFont.truetype(font_b, 52 if wide else 46)
    d.text((x, hy), "Talk, and it types.", font=f_h, fill=(244, 244, 247))
    d.text((x, hy + (64 if wide else 56)), "Your voice stays on device.", font=f_h, fill=(240, 96, 127))

    # Subcopy (verified facts from docs/BENCHMARKS.md, COMPETITORS.md, and README.md)
    sy = hy + (146 if wide else 130)
    f_s = ImageFont.truetype(font_r, 22 if wide else 20)
    d.text((x, sy), "Parakeet offline ASR · Command Mode · Botless Meetings · Local API", font=f_s, fill=(196, 196, 208))
    d.text((x, sy + 34), "8.8 MB Win · No account · No analytics · No word caps · Apache-2.0", font=f_s, fill=(163, 163, 176))

    # Platform chips
    cy = sy + 90
    f_c = ImageFont.truetype(font_b, 19)
    cx = x
    for name in ("Android", "iOS", "Windows", "macOS", "Linux"):
        cw = d.textlength(name, font=f_c) + 30
        d.rounded_rectangle(
            (cx, cy, cx + cw, cy + 38),
            19,
            fill=(28, 22, 29, 255),
            outline=(229, 72, 107, 215),
            width=2,
        )
        d.text((cx + 15, cy + 8), name, font=f_c, fill=(244, 244, 247))
        cx += cw + 10
    if wide:
        draw_waveform(d, cx + 12, cy + 19, bars=16, step=9)

    # Bottom verified benchmark strip (sized so it never collides with the device frames)
    by = cy + 62
    f_b = ImageFont.truetype(font_r, 17 if wide else 16)
    bench_line = (
        "Measured WER: 1.77 % Best · 2.24 % Multilingual (25 EU) · 2.72 % Fast (LibriSpeech test-clean, 200 clips)"
        if wide
        else "Measured WER: 1.77 % Best · 2.24 % Multilingual · 2.72 % Fast (LibriSpeech)"
    )
    d.text((x, by), bench_line, font=f_b, fill=(146, 146, 160))


def render_banner(root, font_b, font_r):
    canvas = make_bg(1600, 640)
    draw_left_column(canvas, root, font_b, font_r, x=76, wide=True)

    # Real brand-pink captures: desktop dark behind, iOS dark middle, Android 14 pink-branded keyboard in front
    desk = desktop_frame(f"{root}/docs/screenshots/desktop_home_dark.png", 500)
    ios = phone_frame(f"{root}/docs/screenshots/ios_dictate_dark.png", 480)
    and_kb = phone_frame(f"{root}/docs/screenshots/emulator_keyboard.png", 520)

    composite_shadowed(canvas, desk, (1030, 56))
    composite_shadowed(canvas, ios, (1325, 118))
    composite_shadowed(canvas, and_kb, (1095, 92))

    out = f"{root}/docs/banner.png"
    canvas.convert("RGB").save(out, optimize=True)
    return out, canvas.size


def render_social(root, font_b, font_r):
    canvas = make_bg(1280, 640)
    draw_left_column(canvas, root, font_b, font_r, x=68, wide=False)

    ios = phone_frame(f"{root}/docs/screenshots/ios_dictate_dark.png", 495)
    and_kb = phone_frame(f"{root}/docs/screenshots/emulator_keyboard.png", 540)

    composite_shadowed(canvas, ios, (996, 78))
    composite_shadowed(canvas, and_kb, (780, 54))

    out = f"{root}/docs/social-preview.png"
    canvas.convert("RGB").save(out, optimize=True)
    return out, canvas.size


def render_poster(root, font_b, font_r):
    """Vertical launch poster (1600x2000) for GitHub README / releases / launch posts."""
    w, h = 1600, 2000
    canvas = make_bg(w, h)
    d = ImageDraw.Draw(canvas)

    # Top header
    icon = rounded(Image.open(f"{root}/play/graphics/icon-512.png").resize((96, 96), Image.LANCZOS), 24)
    canvas.alpha_composite(icon, (88, 78))
    f_brand = ImageFont.truetype(font_b, 64)
    d.text((206, 90), "Humming", font=f_brand, fill=(244, 244, 247))

    # Version + offline badge
    f_pill = ImageFont.truetype(font_b, 19)
    pill_txt = "v1.9  ·  OFFLINE BY DEFAULT  ·  APACHE-2.0"
    pw = d.textlength(pill_txt, font=f_pill) + 40
    px, py = w - 88 - pw, 108
    d.rounded_rectangle((px, py, px + pw, py + 38), 19, fill=(23, 50, 37, 255), outline=(61, 214, 140, 180), width=2)
    d.ellipse((px + 14, py + 14, px + 24, py + 24), fill=(126, 226, 168))
    d.text((px + 32, py + 8), pill_txt, font=f_pill, fill=(126, 226, 168))

    # Hero headline
    f_h1 = ImageFont.truetype(font_b, 78)
    d.text((88, 224), "Talk, and it types.", font=f_h1, fill=(244, 244, 247))
    d.text((88, 316), "Your voice stays on device.", font=f_h1, fill=(240, 96, 127))
    f_sub = ImageFont.truetype(font_r, 27)
    d.text((88, 422), "Private voice keyboard, Command Mode, botless Meeting Notepad & local HTTP API.", font=f_sub, fill=(186, 186, 200))

    # Platform chips + waveform
    f_c = ImageFont.truetype(font_b, 20)
    cx, cy = 88, 482
    for name in ("Android", "iOS", "Windows", "macOS", "Linux"):
        cw = d.textlength(name, font=f_c) + 36
        d.rounded_rectangle((cx, cy, cx + cw, cy + 42), 21, fill=(28, 22, 29, 255), outline=(229, 72, 107, 220), width=2)
        d.text((cx + 18, cy + 9), name, font=f_c, fill=(244, 244, 247))
        cx += cw + 12
    draw_waveform(d, cx + 22, cy + 21, bars=24, step=11)

    # Multi-device real captures showcase
    mtg = desktop_frame(f"{root}/docs/screenshots/desktop_meetings_dark.png", 860)
    and_kb = phone_frame(f"{root}/docs/screenshots/emulator_keyboard.png", 610)
    ios = phone_frame(f"{root}/docs/screenshots/ios_dictate_dark.png", 560)
    composite_shadowed(canvas, mtg, (88, 586))
    composite_shadowed(canvas, ios, (1240, 628))
    composite_shadowed(canvas, and_kb, (978, 570))

    # 4 feature cards (2x2 grid)
    cards = [
        ("Command Mode & Per-App Tone", "Select text anywhere, press Ctrl/Cmd+Shift+E, speak your edit. Adapts tone per app (Slack, Mail, Code) via 100% offline rules, local Ollama, or BYOK."),
        ("Offline Botless Meeting Notepad", "Zero call bots, zero cloud audio. 12s local Parakeet chunks + manual Markdown notes + template synthesis + Ask Across All Meetings."),
        ("Local HTTP API & GPU Provider", "Opt-in 127.0.0.1 Bearer-auth /v1/transcribe & /v1/cleanup API for scripts & agents. CoreML / DirectML / CUDA toggle with CPU fallback."),
        ("Full Android Keyboard + Bangla", "Built on FlorisBoard: 6-language prediction, glide typing, Avro-style Bangla phonetic, clipboard history, edit-by-voice across 36 shared vectors."),
    ]
    f_ct = ImageFont.truetype(font_b, 26)
    f_cb = ImageFont.truetype(font_r, 21)
    card_w, card_h = 696, 196
    for idx, (title, body) in enumerate(cards):
        col, row = idx % 2, idx // 2
        x0 = 88 + col * (card_w + 32)
        y0 = 1252 + row * (card_h + 26)
        d.rounded_rectangle((x0, y0, x0 + card_w, y0 + card_h), 20, fill=(23, 23, 30, 245), outline=(62, 62, 76, 220), width=2)
        d.rounded_rectangle((x0 + 26, y0 + 26, x0 + 34, y0 + 56), 4, fill=(229, 72, 107))
        d.text((x0 + 48, y0 + 24), title, font=f_ct, fill=(244, 244, 247))
        # Wrap body text cleanly
        words, lines, cur = body.split(), [], ""
        for wd in words:
            cand = (cur + " " + wd).strip()
            if d.textlength(cand, font=f_cb) <= card_w - 56:
                cur = cand
            else:
                lines.append(cur)
                cur = wd
        if cur:
            lines.append(cur)
        for li, line in enumerate(lines[:3]):
            d.text((x0 + 28, y0 + 74 + li * 32), line, font=f_cb, fill=(176, 176, 190))

    # Bottom verified metrics & binary size bar
    my = 1714
    d.rounded_rectangle((88, my, w - 88, my + 178), 22, fill=(26, 20, 27, 250), outline=(229, 72, 107, 170), width=2)
    metrics = [
        ("1.77 % WER", "Parakeet 0.6B Best (test-clean)"),
        ("2.24 % / 25 EU", "Multilingual offline model"),
        ("8.8 MB Win", "19.5x smaller than Granola"),
        ("$0 · Apache-2.0", "No account · No word caps"),
    ]
    f_mv = ImageFont.truetype(font_b, 33)
    f_ml = ImageFont.truetype(font_r, 19)
    mw = (w - 176) // 4
    for i, (val, lbl) in enumerate(metrics):
        mx = 88 + i * mw + 24
        d.text((mx, my + 40), val, font=f_mv, fill=(240, 96, 127) if i % 2 == 0 else (126, 226, 168))
        d.text((mx, my + 98), lbl, font=f_ml, fill=(186, 186, 200))

    # Footer
    f_ft = ImageFont.truetype(font_r, 22)
    d.text((88, 1924), "muhib-karim.github.io/humming-keyboard   ·   github.com/muhib-karim/humming-keyboard", font=f_ft, fill=(154, 154, 168))

    out = f"{root}/docs/poster.png"
    canvas.convert("RGB").save(out, optimize=True)
    return out, canvas.size


def render_feature_graphic(root, font_b, font_r):
    """Google Play feature graphic (1024x500) using brand gradient and real Android 14 capture."""
    w, h = 1024, 500
    canvas = Image.new("RGBA", (w, h), (15, 15, 19, 255))
    px = canvas.load()
    for y in range(h):
        ty = y / max(h - 1, 1)
        for x in range(w):
            tx = x / max(w - 1, 1)
            t = 0.6 * tx + 0.4 * ty
            r = int(235 + (202 - 235) * t)
            g = int(76 + (52 - 76) * t)
            b = int(123 + (156 - 123) * t)
            px[x, y] = (r, g, b, 255)

    d = ImageDraw.Draw(canvas)
    icon = rounded(Image.open(f"{root}/play/graphics/icon-512.png").resize((92, 92), Image.LANCZOS), 22)
    canvas.alpha_composite(icon, (64, 72))
    d.text((174, 86), "Humming", font=ImageFont.truetype(font_b, 56), fill=(255, 255, 255))
    d.text((64, 196), "Offline voice typing.", font=ImageFont.truetype(font_b, 48), fill=(255, 255, 255))
    d.text((64, 254), "Nothing leaves your phone.", font=ImageFont.truetype(font_b, 38), fill=(255, 232, 240))
    d.text((64, 330), "NVIDIA Parakeet · Edit by voice · Bangla phonetic", font=ImageFont.truetype(font_r, 22), fill=(255, 240, 246))
    d.text((64, 368), "Free · No ads · No telemetry · Apache-2.0", font=ImageFont.truetype(font_r, 22), fill=(255, 224, 235))

    and_kb = phone_frame(f"{root}/docs/screenshots/emulator_keyboard.png", 446)
    composite_shadowed(canvas, and_kb, (746, 28))

    out = f"{root}/play/graphics/feature-graphic-1024x500.png"
    canvas.convert("RGB").save(out, optimize=True)
    return out, canvas.size


def main():
    default_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    ap = argparse.ArgumentParser(description="Render Humming banner, social preview, launch poster, and Play feature graphic.")
    ap.add_argument("root", nargs="?", default=default_root, help="Repository root (default: auto-detected)")
    args = ap.parse_args()
    font_b = pick_font(FONT_BOLD_CANDIDATES)
    font_r = pick_font(FONT_REG_CANDIDATES)
    for fn in (render_banner, render_social, render_poster, render_feature_graphic):
        path, size = fn(args.root, font_b, font_r)
        print(f"wrote {os.path.relpath(path, args.root)} ({size[0]}x{size[1]})")


if __name__ == "__main__":
    main()
