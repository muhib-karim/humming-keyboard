#!/usr/bin/env python3
"""Check an assembled site: every local link and image resolves to a file, every #anchor exists, tags balance, every
<img> has alt text and size, and no page loads anything from another origin.

  check_site.py <site dir>

Links under updates/ are skipped: pages.yml adds that mirror after this check. Exits non-zero on any problem.
"""
import html.parser, os, sys, urllib.parse

VOID = {"area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "source", "track", "wbr"}
OPTIONAL_END = {"p", "li", "dt", "dd", "tr", "td", "th", "thead", "tbody", "option"}
SITE_PREFIX = "/humming-keyboard/"


class Page(html.parser.HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.refs, self.ids, self.stack, self.problems, self.external = [], set(), [], [], []

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        if "id" in a:
            if a["id"] in self.ids:
                self.problems.append("duplicate id %s" % a["id"])
            self.ids.add(a["id"])
        for key in ("href", "src", "srcset"):
            if key in a and a[key] is not None:
                value = a[key].split()[0] if key == "srcset" else a[key]
                self.refs.append((tag, key, value))
        if tag in ("script", "img", "source") or (tag == "link" and a.get("rel") in ("stylesheet", "icon", "manifest", "apple-touch-icon")):
            ref = a.get("src") or a.get("href") or a.get("srcset") or ""
            if ref.startswith(("http://", "https://", "//")):
                self.external.append(ref)
        if tag == "img":
            if a.get("alt") is None:
                self.problems.append("img without alt: %s" % a.get("src"))
            if not (a.get("width") and a.get("height")):
                self.problems.append("img without width/height: %s" % a.get("src"))
        if tag not in VOID:
            self.stack.append((tag, self.getpos()[0]))

    def handle_startendtag(self, tag, attrs):
        self.handle_starttag(tag, attrs)
        if tag not in VOID and self.stack and self.stack[-1][0] == tag:
            self.stack.pop()

    def handle_endtag(self, tag):
        if tag in VOID:
            return
        while self.stack and self.stack[-1][0] != tag and self.stack[-1][0] in OPTIONAL_END:
            self.stack.pop()
        if not self.stack or self.stack[-1][0] != tag:
            self.problems.append("unexpected </%s> at line %d" % (tag, self.getpos()[0]))
            return
        self.stack.pop()


def main():
    site = os.path.abspath(sys.argv[1])
    pages, problems = {}, []
    for dirpath, _, files in os.walk(site):
        for f in files:
            if f.endswith(".html"):
                path = os.path.join(dirpath, f)
                p = Page()
                p.feed(open(path, encoding="utf-8").read())
                p.close()
                rest = [t for t, _ in p.stack if t not in OPTIONAL_END]
                if rest:
                    p.problems.append("unclosed tags: %s" % rest)
                pages[path] = p
    for path, p in pages.items():
        rel = os.path.relpath(path, site)
        problems += ["%s: %s" % (rel, m) for m in p.problems]
        problems += ["%s: loads from another origin: %s" % (rel, u) for u in p.external]
        for tag, key, ref in p.refs:
            u = urllib.parse.urlparse(ref)
            if u.scheme in ("http", "https", "mailto") or ref.startswith("//"):
                continue
            target_path = u.path
            if target_path.startswith(SITE_PREFIX):
                target = os.path.join(site, target_path[len(SITE_PREFIX):])
            elif target_path.startswith("/"):
                problems.append("%s: root-relative link outside the site: %s" % (rel, ref))
                continue
            else:
                target = os.path.normpath(os.path.join(os.path.dirname(path), target_path)) if target_path else path
            if os.path.relpath(target, site).startswith("updates"):
                continue
            if os.path.isdir(target):
                target = os.path.join(target, "index.html")
            if not os.path.isfile(target):
                problems.append("%s: %s=%s does not exist" % (rel, key, ref))
                continue
            if u.fragment and target.endswith(".html"):
                tp = pages.get(target)
                if tp is not None and u.fragment not in tp.ids:
                    problems.append("%s: anchor #%s missing in %s" % (rel, u.fragment, os.path.relpath(target, site)))
    for p in problems:
        print("::error::" + p)
    print("checked %d pages, %d problems" % (len(pages), len(problems)))
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
