#!/usr/bin/env python3
"""Renders docs/social-preview.png (1280x640) from real screenshots; the layout mirrors docs/social-preview.svg.

  render_social_preview.py <repo root>     (needs Pillow and the Liberation Sans fonts)
"""
import sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter
root = sys.argv[1]
W, H = 1280, 640
B = "/usr/share/fonts/truetype/liberation2/LiberationSans-Bold.ttf"
R = "/usr/share/fonts/truetype/liberation2/LiberationSans-Regular.ttf"

def lerp(a, b, t): return tuple(round(x + (y - x) * t) for x, y in zip(a, b))
# Background: ink, warming to a deep wine at the lower right.
bg = Image.new("RGB", (W, H))
px = bg.load()
ink, wine = (15, 15, 19), (46, 16, 32)
for y in range(H):
    for x in range(W):
        t = max(0.0, min(1.0, (x / W) * 0.75 + (y / H) * 0.45 - 0.25))
        px[x, y] = lerp(ink, wine, t)
img = bg.convert("RGBA")
d = ImageDraw.Draw(img)

def rounded(im, r):
    m = Image.new("L", im.size, 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, im.width - 1, im.height - 1), r, fill=255)
    im = im.convert("RGBA"); im.putalpha(m); return im

# Icon
icon = rounded(Image.open(f"{root}/play/graphics/icon-512.png").resize((96, 96), Image.LANCZOS), 22)
img.alpha_composite(icon, (88, 96))
d.text((204, 104), "Humming", font=ImageFont.truetype(B, 64), fill=(244, 244, 247))
# Headline
f_h = ImageFont.truetype(B, 50)
d.text((88, 250), "Private voice typing", font=f_h, fill=(244, 244, 247))
d.text((88, 312), "that works offline.", font=f_h, fill=(240, 96, 127))
f_s = ImageFont.truetype(R, 25)
d.text((88, 398), "On-device speech recognition. No account, no word caps.", font=f_s, fill=(178, 178, 190))
# Platform chips
f_c = ImageFont.truetype(B, 21)
x = 88
for name in ["Android", "iPhone", "Windows", "macOS", "Linux"]:
    w = d.textlength(name, font=f_c) + 32
    d.rounded_rectangle((x, 470, x + w, 512), 21, outline=(229, 72, 107), width=2)
    d.text((x + 16, 480), name, font=f_c, fill=(244, 244, 247))
    x += w + 12
d.text((88, 548), "Free and open source (Apache-2.0)", font=ImageFont.truetype(R, 21), fill=(140, 140, 152))

def phone(path, h, rot=0):
    s = Image.open(path).convert("RGB")
    sw = round(s.width * (h - 20) / s.height)
    s = rounded(s.resize((sw, h - 20), Image.LANCZOS), 30)
    frame = Image.new("RGBA", (sw + 20, h), (0, 0, 0, 0))
    ImageDraw.Draw(frame).rounded_rectangle((0, 0, sw + 19, h - 1), 38, fill=(30, 30, 37), outline=(70, 70, 82), width=2)
    frame.alpha_composite(s, (10, 10))
    return frame

def shadowed(fr, pos):
    sh = Image.new("RGBA", img.size, (0, 0, 0, 0))
    m = Image.new("RGBA", fr.size, (0, 0, 0, 150)); m.putalpha(fr.getchannel("A").point(lambda a: 150 if a else 0))
    sh.alpha_composite(m, (pos[0] + 10, pos[1] + 18))
    img.alpha_composite(sh.filter(ImageFilter.GaussianBlur(18)))
    img.alpha_composite(fr, pos)

back = phone(f"{root}/docs/screenshots/ios_dictate_dark.png", 500)
front = phone(f"{root}/docs/images/hero_voice.png", 560)
shadowed(back, (1000, 50))
shadowed(front, (790, 60))
img.convert("RGB").save(f"{root}/docs/social-preview.png", optimize=True)
print("wrote", img.size)
