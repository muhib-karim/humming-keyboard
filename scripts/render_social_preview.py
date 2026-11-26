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

    # Subcopy (verified facts from docs/BENCHMARKS.md and README.md)
    sy = hy + (146 if wide else 130)
    f_s = ImageFont.truetype(font_r, 23 if wide else 21)
    d.text((x, sy), "NVIDIA Parakeet offline ASR · Edit by voice · Bangla phonetic", font=f_s, fill=(192, 192, 204))
    d.text((x, sy + 34), "No account · No analytics · No word caps · Apache-2.0", font=f_s, fill=(163, 163, 176))

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


def main():
    default_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    ap = argparse.ArgumentParser(description="Render docs/banner.png and docs/social-preview.png.")
    ap.add_argument("root", nargs="?", default=default_root, help="Repository root (default: auto-detected)")
    args = ap.parse_args()
    font_b = pick_font(FONT_BOLD_CANDIDATES)
    font_r = pick_font(FONT_REG_CANDIDATES)
    for fn in (render_banner, render_social):
        path, size = fn(args.root, font_b, font_r)
        print(f"wrote {os.path.relpath(path, args.root)} ({size[0]}x{size[1]})")


if __name__ == "__main__":
    main()
