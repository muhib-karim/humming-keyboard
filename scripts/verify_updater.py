#!/usr/bin/env python3
"""Verify Tauri updater signatures (minisign, Ed25519) against the public key embedded in tauri.conf.json.

Usage:
  verify_updater.py --config desktop/src-tauri/tauri.conf.json --file <bundle> --sig <bundle.sig>
  verify_updater.py --config desktop/src-tauri/tauri.conf.json --latest <latest.json path or URL> [--download]
      [--base-url <URL of the folder that serves the files>]

With --latest, every platform entry must carry a signature; with --download each entry's URL is fetched and its
signature checked against the bytes actually served. --base-url fetches each file by its name from that folder
instead (pages.yml: the copy this site serves, whatever host the feed of an older release lists). Exits non-zero on
any failure. Needs PyNaCl (imported where it is used, so the pure helpers can be tested without it:
scripts/test_verify_updater.py).
"""
import argparse, base64, hashlib, json, sys, urllib.request


def _b64text(s):
    return base64.b64decode(s.strip()).decode()


def load_pubkey(config_path):
    from nacl.signing import VerifyKey

    cfg = json.load(open(config_path))
    pub_b64 = cfg["plugins"]["updater"]["pubkey"]
    lines = _b64text(pub_b64).splitlines()
    raw = base64.b64decode(lines[1])
    assert raw[:2] == b"Ed" and len(raw) == 42, "not a minisign Ed25519 public key"
    return raw[2:10], VerifyKey(raw[10:]), cfg


def verify(data, sig_b64, keyid, vk):
    lines = _b64text(sig_b64).splitlines()
    sig = base64.b64decode(lines[1])
    alg, sig_keyid, sig64 = sig[:2], sig[2:10], sig[10:74]
    if sig_keyid != keyid:
        raise ValueError("signature key id %s != public key id %s" % (sig_keyid[::-1].hex(), keyid[::-1].hex()))
    msg = hashlib.blake2b(data, digest_size=64).digest() if alg == b"ED" else data
    vk.verify(msg, sig64)
    trusted = lines[2].split("trusted comment: ", 1)[1].encode()
    vk.verify(sig64 + trusted, base64.b64decode(lines[3]))
    return trusted.decode()


def bundle_url(entry_url, base_url=None):
    """Where a feed entry's file is fetched from: its own URL, or the same file name under base_url."""
    if not base_url:
        return entry_url
    return base_url.rstrip("/") + "/" + entry_url.rsplit("/", 1)[1]


def fetch(url):
    req = urllib.request.Request(url, headers={"User-Agent": "humming-verify"})
    with urllib.request.urlopen(req, timeout=120) as r:
        return r.read()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--config", required=True)
    ap.add_argument("--file")
    ap.add_argument("--sig")
    ap.add_argument("--latest")
    ap.add_argument("--download", action="store_true")
    ap.add_argument("--local-dir", help="verify latest.json entries against files in this directory (by URL basename)")
    ap.add_argument("--base-url", help="with --download: fetch each entry by its file name from this folder URL")
    a = ap.parse_args()
    from nacl.exceptions import BadSignatureError

    keyid, vk, cfg = load_pubkey(a.config)
    print("public key id:", keyid[::-1].hex().upper())
    ok = True
    if a.file:
        print(a.file, "->", verify(open(a.file, "rb").read(), open(a.sig).read(), keyid, vk))
    if a.latest:
        raw = fetch(a.latest) if a.latest.startswith("http") else open(a.latest, "rb").read()
        latest = json.loads(raw)
        want = cfg["version"]
        print("latest.json version", latest["version"], "| app config version", want)
        if not latest.get("platforms"):
            print("FAIL: no platforms"); sys.exit(1)
        for plat, e in sorted(latest["platforms"].items()):
            if not e.get("signature") or not e.get("url", "").startswith("https://"):
                print("FAIL", plat, "missing signature or non-https url"); ok = False; continue
            if a.download or a.local_dir:
                try:
                    if a.local_dir:
                        import os
                        data = open(os.path.join(a.local_dir, e["url"].rsplit("/", 1)[1]), "rb").read()
                    else:
                        data = fetch(bundle_url(e["url"], a.base_url))
                    tc = verify(data, e["signature"], keyid, vk)
                    print("OK  ", plat, e["url"].rsplit("/", 1)[1], len(data), "bytes |", tc)
                except (BadSignatureError, ValueError, OSError) as ex:
                    print("FAIL", plat, ex); ok = False
            else:
                print("sig ", plat, e["url"])
        if a.base_url and a.download:
            elsewhere = sorted({e["url"].rsplit("/", 1)[0] for e in latest["platforms"].values()
                                if e.get("url") and bundle_url(e["url"], a.base_url) != e["url"]})
            if elsewhere:
                print("NOTE: this feed lists its downloads under %s; the files checked are the copies under %s"
                      % (", ".join(elsewhere), a.base_url.rstrip("/")))
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
