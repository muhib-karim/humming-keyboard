#!/usr/bin/env python3
"""Line coverage from a JaCoCo XML report, per package and in total (keyboard.yml, docs/ci/COVERAGE.md).

usage: coverage_summary.py <jacoco.xml> [--prefix com/humming/keyboard] [--json out.json]

Prints a Markdown table. --prefix also sums every package under that path ("prefix" in the JSON).
The DTD line in JaCoCo reports is ignored; no network or file access beyond the two paths given.
"""
import argparse
import json
import re
import xml.etree.ElementTree as ET


def _line(elem):
    for c in elem.findall("counter"):
        if c.get("type") == "LINE":
            return int(c.get("covered")), int(c.get("missed"))
    return 0, 0


def _entry(covered, missed):
    total = covered + missed
    return {"covered": covered, "missed": missed, "pct": round(100.0 * covered / total, 1) if total else 0.0}


def summarise(xml_text, prefix=None):
    root = ET.fromstring(re.sub(r"<!DOCTYPE[^>]*>", "", xml_text))
    packages = {p.get("name"): _entry(*_line(p)) for p in root.findall("package")}
    out = {"packages": packages, "total": _entry(*_line(root))}
    if prefix:
        cov = sum(v["covered"] for k, v in packages.items() if k == prefix or k.startswith(prefix + "/"))
        mis = sum(v["missed"] for k, v in packages.items() if k == prefix or k.startswith(prefix + "/"))
        out["prefix_name"] = prefix
        out["prefix"] = _entry(cov, mis)
    return out


def markdown(s):
    rows = ["| Package | Lines covered | Lines | Coverage |", "|---|---:|---:|---:|"]

    def row(name, e):
        rows.append("| %s | %d | %d | %.1f %% |" % (name, e["covered"], e["covered"] + e["missed"], e["pct"]))

    prefix = s.get("prefix_name")
    for name, e in sorted(s["packages"].items()):
        if prefix is None or name == prefix or name.startswith(prefix + "/"):
            row(name, e)
    if prefix:
        row(prefix + " (all)", s["prefix"])
    row("whole app module", s["total"])
    return "\n".join(rows)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("xml")
    ap.add_argument("--prefix")
    ap.add_argument("--json")
    a = ap.parse_args()
    with open(a.xml, encoding="utf-8") as f:
        s = summarise(f.read(), a.prefix)
    print(markdown(s))
    if a.json:
        with open(a.json, "w", encoding="utf-8") as f:
            json.dump(s, f, indent=2, sort_keys=True)


if __name__ == "__main__":
    main()
