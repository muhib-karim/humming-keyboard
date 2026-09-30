# Humming sticker pack builder (Apache-2.0). Renders the built-in stickers in
# keyboard/app/src/main/assets/stickers/ from Noto Emoji images (googlefonts/noto-emoji 2D/png/512,
# Apache-2.0) and the Bangers font (google/fonts ofl/bangers, SIL OFL 1.1): each emoji gets a white die-cut
# outline, a soft shadow and a caption. Usage: python build.py NOTO_PNG_DIR BANGERS_TTF OUT_DIR
import sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont

STICKERS = [('1f602', 'LOL'), ('2764', 'LOVE YOU'), ('1f44d', 'OK!'), ('1f389', 'YAY!'), ('1f62d', 'NOOO'), ('1f60d', 'WOW'), ('1f64f', 'THANK YOU'), ('1f634', 'NIGHT NIGHT'), ('2615', 'COFFEE?'), ('1f355', 'PIZZA?'), ('1f525', 'FIRE'), ('1f4af', '100'), ('1f914', 'HMM'), ('1f60e', 'COOL'), ('1f973', 'PARTY!'), ('1f618', 'XOXO'), ('1f44b', 'HI!'), ('1f917', 'HUGS'), ('1f631', 'OMG'), ('1f644', 'UGH'), ('1f605', 'OOPS'), ('1f923', 'HAHA'), ('1f382', 'HAPPY BDAY'), ('1f3c3', 'ON MY WAY'), ('1f440', 'LOOK'), ('1f91e', 'FINGERS CROSSED'), ('1f4aa', 'YOU GOT THIS'), ('1f37b', 'CHEERS'), ('1f436', 'WOOF'), ('1f431', 'MEOW'), ('1f31e', 'GOOD MORNING'), ('1f97a', 'PLEASE')]
KEYWORDS = {'LOL': 'laugh funny', 'LOVE YOU': 'heart love', 'OK!': 'yes thumbs agree', 'YAY!': 'celebrate party', 'NOOO': 'cry sad', 'WOW': 'love eyes amazing', 'THANK YOU': 'thanks please pray', 'NIGHT NIGHT': 'sleep bed tired', 'COFFEE?': 'coffee morning', 'PIZZA?': 'food dinner', 'FIRE': 'hot lit', '100': 'perfect agree', 'HMM': 'think wonder', 'COOL': 'sunglasses chill', 'PARTY!': 'party celebrate birthday', 'XOXO': 'kiss love', 'HI!': 'hello wave bye', 'HUGS': 'hug support', 'OMG': 'shock scream', 'UGH': 'eyeroll annoyed', 'OOPS': 'sorry sweat', 'HAHA': 'laugh rofl', 'HAPPY BDAY': 'birthday cake', 'ON MY WAY': 'omw run late', 'LOOK': 'eyes see', 'FINGERS CROSSED': 'luck hope', 'YOU GOT THIS': 'strong support', 'CHEERS': 'beer drinks', 'WOOF': 'dog puppy', 'MEOW': 'cat kitty', 'GOOD MORNING': 'sun morning', 'PLEASE': 'please beg'}
src, font_path, out = sys.argv[1], sys.argv[2], sys.argv[3]
S = 320
index = []
for i, (cp, label) in enumerate(STICKERS):
    em = Image.open(f"{src}/{cp}.png").convert("RGBA").resize((236, 236), Image.LANCZOS)
    canvas = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ox, oy = (S - 236) // 2, 10
    a = Image.new("L", (S, S), 0); a.paste(em.split()[3], (ox, oy))
    outline = a.filter(ImageFilter.MaxFilter(15))
    shadow = outline.filter(ImageFilter.GaussianBlur(6)).point(lambda v: v * 0.35)
    canvas.paste(Image.new("RGBA", (S, S), (0, 0, 0, 255)), (3, 5), shadow)
    canvas.paste(Image.new("RGBA", (S, S), (255, 255, 255, 255)), (0, 0), outline)
    canvas.alpha_composite(em, (ox, oy))
    d = ImageDraw.Draw(canvas)
    size = 64
    while True:
        f = ImageFont.truetype(font_path, size)
        w = d.textlength(label, font=f)
        if w <= S - 24 or size <= 30:
            break
        size -= 2
    y = S - size - 18
    d.text(((S - w) / 2, y), label, font=f, fill=(255, 255, 255, 255), stroke_width=7, stroke_fill=(30, 30, 46, 255))
    name = f"s{i + 1:02d}.png"
    canvas.quantize(colors=200, method=Image.FASTOCTREE, dither=Image.NONE).save(f"{out}/{name}", optimize=True)
    index.append(f"{name}\t{label}\t{KEYWORDS.get(label, '')}")
open(f"{out}/index.tsv", "w").write("\n".join(index) + "\n")
print(len(index), "stickers")
