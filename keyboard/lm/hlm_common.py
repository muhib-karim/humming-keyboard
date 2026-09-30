# Humming language-model tooling (Apache-2.0). Shared tokenizer and corpus helpers.
# The tokenizer matches Predictor.context() in the app: letter runs with inner apostrophes, lowercase,
# a sentence ends at . ! ? or a newline; everything else (commas, digits, quotes) is dropped.
import bz2, gzip, hashlib, io, os, re, urllib.request

TOKEN = re.compile(r"[^\W\d_]+(?:'[^\W\d_]+)*|[.!?\n]")
TATOEBA = {"en": "eng", "de": "deu", "es": "spa", "fr": "fra", "it": "ita", "pt": "por"}
CV_BASE = "https://raw.githubusercontent.com/common-voice/common-voice/main/server/data/"
CV_FILES = {  # Common Voice sentence files, CC0-1.0 (each directory carries the CC0 LICENSE)
    "en": ["sentence-collector.txt"],
    "de": ["sentence-collector.txt"],
    "es": ["sentence-collector.txt"],
    "fr": ["sentence-collector.txt"],
    "it": ["frasi.txt"],
    "pt": ["sentence-collector.txt"],
}
# Placeholder names that fill translation corpora; they are context but never a prediction.
NAMES = {
    "en": "tom mary john ken jack mike bob alice jane tony jim layla sami muiriel mennad baya yanni ziri skura rima sonia ricardo mark lucy dan linda fadil dania salima boston tom's mary's sami's layla's john's mennad's yanni's",
    "de": "tom maria mary john jack ken layla sami yanni ziri mennad baya toms marias",
    "es": "tom mary maría john jack ken layla sami yanni ziri mennad baya",
    "fr": "tom marie mary john jack ken layla sami yanni ziri mennad baya",
    "it": "tom mary maria marie john jack ken layla sami yanni ziri mennad baya",
    "pt": "tom mary maria john jack ken layla sami yanni ziri mennad baya",
}


def names(lang):
    return set(NAMES.get(lang, "").split())


def fetch(url, path):
    if not os.path.exists(path):
        os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
        req = urllib.request.Request(url, headers={"User-Agent": "humming-lm-build"})
        with urllib.request.urlopen(req, timeout=120) as r, open(path + ".part", "wb") as f:
            while True:
                b = r.read(1 << 20)
                if not b:
                    break
                f.write(b)
        os.replace(path + ".part", path)
    return path


def raw_sentences(lang, cache="corpus"):
    """Yields (source, sentence) pairs: Tatoeba (CC BY 2.0 FR) then Common Voice (CC0)."""
    t = TATOEBA[lang]
    p = fetch(f"https://downloads.tatoeba.org/exports/per_language/{t}/{t}_sentences.tsv.bz2", f"{cache}/{t}_sentences.tsv.bz2")
    with bz2.open(p, "rt", encoding="utf-8") as f:
        for line in f:
            parts = line.rstrip("\n").split("\t")
            if len(parts) >= 3:
                yield "tatoeba", parts[2]
    for name in CV_FILES.get(lang, []):
        p = fetch(CV_BASE + lang + "/" + name, f"{cache}/cv_{lang}_{name}")
        with open(p, encoding="utf-8", errors="replace") as f:
            for line in f:
                s = line.strip()
                if s:
                    yield "cv", s


def is_test(sentence):
    """A stable 1 % held-out split by sentence hash (never trained on by the new models)."""
    return int(hashlib.md5(sentence.encode("utf-8")).hexdigest()[:8], 16) % 100 == 0


def clauses(sentence):
    """Token lists, one per sentence-like clause, lowercase, as the app sees them."""
    out, cur = [], []
    for m in TOKEN.finditer(sentence.replace("\u2019", "'")):
        t = m.group(0)
        if len(t) == 1 and t in ".!?\n":
            if cur:
                out.append(cur)
            cur = []
        else:
            cur.append(t.lower())
    if cur:
        out.append(cur)
    return out


def load_lexicon(path):
    """word(lowercase) -> (logP, offensive) from the app's lexicon_xx.tsv."""
    lex = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            p = line.rstrip("\n").split("\t")
            if len(p) < 2:
                continue
            try:
                q = int(p[1])
            except ValueError:
                continue
            w = p[0].lower()
            if w not in lex:
                lex[w] = (q / 10.0 - 24.0, len(p) > 2 and "o" in p[2])
    return lex
