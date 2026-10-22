#!/usr/bin/env python3
"""Startup and model-load numbers from the CI emulator run (android-emulator.yml, keyboard/ci/emulator_run.sh).

usage: emulator_metrics.py <run dir> <out.json> --image <system image> --date YYYY-MM-DD [--run ID] [--commit SHA]

<run dir> holds cold_start.txt (the output of repeated `am start -W`) and, when the model test ran,
model_load.json (written by ModelLoadBenchTest). Only cold launches count. Nothing is estimated: a missing file is
reported as "not measured".
"""
import argparse
import json
import os
import re


def median(values):
    s = sorted(values)
    n = len(s)
    if n == 0:
        raise ValueError("no values")
    return s[n // 2] if n % 2 else round((s[n // 2 - 1] + s[n // 2]) / 2)


def cold_start(am_output):
    values = []
    state = None
    for line in am_output.splitlines():
        line = line.strip()
        if line.startswith("LaunchState:"):
            state = line.split(":", 1)[1].strip()
        m = re.match(r"TotalTime:\s*(\d+)", line)
        if m and state in (None, "COLD"):
            values.append(int(m.group(1)))
        if line == "Complete":
            state = None
    if not values:
        raise ValueError("no cold launches in the am start output")
    return {"n": len(values), "median": median(values), "min": min(values), "max": max(values), "values": values}


def model_load(j):
    loads = j["load_ms"]
    return {
        "model": j.get("model"),
        "first_load_ms": loads[0],
        "repeat_load_median_ms": median(loads[1:]) if len(loads) > 1 else None,
        "load_ms": loads,
        "warmup_decode_ms": j.get("warmup_decode_ms"),
        "download_ms": j.get("download_ms"),
        "threads": j.get("threads"),
        "abi": j.get("abi"),
        "sdk": j.get("sdk"),
    }


def collect(run_dir, image, date, run=None, commit=None):
    with open(os.path.join(run_dir, "cold_start.txt"), encoding="utf-8") as f:
        cs = cold_start(f.read())
    mpath = os.path.join(run_dir, "model_load.json")
    ml = None
    if os.path.isfile(mpath):
        with open(mpath, encoding="utf-8") as f:
            ml = model_load(json.load(f))
    return {"image": image, "date": date, "run": run, "commit": commit,
            "cold_start_build": "benchmark build type (release settings: R8, resource shrinking), debug-signed",
            "cold_start_ms": cs, "model_load": ml}


def markdown(j):
    rows = ["Emulator `%s`, %s%s" % (j["image"], j["date"], (", run %s" % j["run"]) if j.get("run") else ""), "",
            "| Measurement | Result |", "|---|---:|"]
    cs = j["cold_start_ms"]
    rows.append("| Cold start, median of %d | %d ms (min %d, max %d) |" % (cs["n"], cs["median"], cs["min"], cs["max"]))
    ml = j["model_load"]
    if ml:
        rows.append("| Voice model (%s) first load | %d ms |" % (ml["model"], ml["first_load_ms"]))
        if ml["repeat_load_median_ms"] is not None:
            rows.append("| Voice model reload, median of %d | %d ms |" % (len(ml["load_ms"]) - 1, ml["repeat_load_median_ms"]))
        if ml["warmup_decode_ms"] is not None:
            rows.append("| First decode (1 s of silence) | %d ms |" % ml["warmup_decode_ms"])
    else:
        rows.append("| Voice model load | not measured |")
    return "\n".join(rows)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("run_dir")
    ap.add_argument("out")
    ap.add_argument("--image", required=True)
    ap.add_argument("--date", required=True)
    ap.add_argument("--run")
    ap.add_argument("--commit")
    a = ap.parse_args()
    j = collect(a.run_dir, a.image, a.date, a.run, a.commit)
    with open(a.out, "w", encoding="utf-8") as f:
        json.dump(j, f, indent=2)
    print(markdown(j))


if __name__ == "__main__":
    main()
