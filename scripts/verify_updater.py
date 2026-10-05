#!/usr/bin/env python3
"""Verify Tauri updater signatures (minisign, Ed25519) against the public key embedded in tauri.conf.json.

Usage:
  verify_updater.py --config desktop/src-tauri/tauri.conf.json --file <bundle> --sig <bundle.sig>
  verify_updater.py --config desktop/src-tauri/tauri.conf.json --latest <latest.json path or URL> [--download]

With --latest, every platform entry must carry a signature; with --download each entry's URL is fetched and its
signature checked against the bytes actually served. Exits non-zero on any failure. Needs PyNaCl.
"""
import argparse, base64, hashlib, json, sys, urllib.request

from nacl.exceptions import BadSignatureError
from nacl.signing import VerifyKey


def _b64text(s):
    return base64.b64decode(s.strip()).decode()


def load_pubkey(config_path):
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
    a = ap.parse_args()
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
                        data = fetch(e["url"])
                    tc = verify(data, e["signature"], keyid, vk)
                    print("OK  ", plat, e["url"].rsplit("/", 1)[1], len(data), "bytes |", tc)
                except (BadSignatureError, ValueError, OSError) as ex:
                    print("FAIL", plat, ex); ok = False
            else:
                print("sig ", plat, e["url"])
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
