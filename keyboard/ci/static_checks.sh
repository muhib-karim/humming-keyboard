#!/usr/bin/env bash
# Humming 1.7.1: ktlint and detekt gates for the Android keyboard's Kotlin (quality.yml). Run from the repository root:
#   bash keyboard/ci/static_checks.sh ktlint|detekt
# Both tools are the projects' own release files, pinned by SHA-256, run from the command line (no Gradle plugin, so
# they cannot affect the build). Pre-existing findings live in a committed baseline (keyboard/config/); anything new
# fails. A missing baseline is an error, not a free pass: the job writes a fresh one to build/static/ (uploaded as an
# artifact) so a maintainer can review it and commit it on purpose. docs/ci/STATIC_ANALYSIS.md explains the gates.
set -euo pipefail

tool=${1:?usage: static_checks.sh ktlint|detekt}
cfg=keyboard/config
out=build/static
bin=${RUNNER_TEMP:-/tmp}/humming-static
mkdir -p "$out" "$bin"

KTLINT_VERSION=1.7.1
KTLINT_SHA256=b596a05f09364e1997b644152683af12fef58944f3acfabdf78071f5a490eb98
DETEKT_VERSION=1.23.8
DETEKT_SHA256=2ce2ff952e150baf28a29cda70a363b0340b3e81a55f43e51ec5edffc3d066c1

fetch() { # url sha256 dest
  if [ ! -f "$3" ] || ! echo "$2  $3" | sha256sum -c --quiet - 2>/dev/null; then
    curl -fsSL --retry 3 -o "$3" "$1"
    echo "$2  $3" | sha256sum -c --quiet - || { echo "::error::checksum mismatch for $1"; exit 1; }
  fi
}

missing_baseline() { # name fresh-file committed-path
  echo "::error::$1 baseline $3 is missing. A fresh one is in the static-analysis artifact ($2): review it, then commit it."
  exit 1
}

case "$tool" in
  ktlint)
    fetch "https://github.com/pinterest/ktlint/releases/download/$KTLINT_VERSION/ktlint" "$KTLINT_SHA256" "$bin/ktlint"
    chmod +x "$bin/ktlint"
    baseline=$cfg/ktlint-baseline.xml
    globs=("keyboard/app/src/**/*.kt" "keyboard/app/src/**/*.kts" "keyboard/app/build.gradle.kts")
    if [ ! -f "$baseline" ]; then
      "$bin/ktlint" --relative "${globs[@]}" --reporter=baseline,output="$out/ktlint-baseline.xml" > /dev/null || true
      missing_baseline ktlint "$out/ktlint-baseline.xml" "$baseline"
    fi
    "$bin/ktlint" --relative --baseline="$baseline" "${globs[@]}" \
      --reporter=plain --reporter=checkstyle,output="$out/ktlint-checkstyle.xml"
    echo "ktlint $KTLINT_VERSION: no findings outside $baseline"
    ;;
  detekt)
    fetch "https://github.com/detekt/detekt/releases/download/v$DETEKT_VERSION/detekt-cli-$DETEKT_VERSION-all.jar" \
      "$DETEKT_SHA256" "$bin/detekt.jar"
    baseline=$cfg/detekt-baseline.xml
    input=keyboard/app/src/main/kotlin,keyboard/app/src/test/kotlin,keyboard/app/src/androidTest/kotlin
    run_detekt() { java -jar "$bin/detekt.jar" --input "$input" --config "$cfg/detekt.yml" --build-upon-default-config "$@"; }
    if [ ! -f "$baseline" ]; then
      run_detekt --baseline "$out/detekt-baseline.xml" --create-baseline > /dev/null || true
      missing_baseline detekt "$out/detekt-baseline.xml" "$baseline"
    fi
    run_detekt --baseline "$baseline" --report "txt:$out/detekt.txt" --report "sarif:$out/detekt.sarif"
    echo "detekt $DETEKT_VERSION: no findings outside $baseline"
    ;;
  *)
    echo "unknown tool: $tool" >&2
    exit 2
    ;;
esac
