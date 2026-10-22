#!/usr/bin/env bash
# Humming 1.7.1: everything the CI emulator does (android-emulator.yml), from the repository root, with the APKs built:
#   bash keyboard/ci/emulator_run.sh <out dir>
# 1. ModelLoadBenchTest: downloads the Fast voice model through the app and times three loads (model_load.json).
# 2. ImeSmokeTest: makes Humming the keyboard, opens the keyboard settings, types "hello" with Humming's keys
#    (screenshots in screens/; on failure also failure_hierarchy.xml).
# 3. Cold start of the release-like "benchmark" build: one launch after install that is not counted, then ten
#    `am start -W` launches, each after a force-stop (cold_start.txt).
# Exits non-zero when a test fails; the measurements and screenshots are kept either way.
set -euo pipefail

OUT=${1:-emulator-out}
mkdir -p "$OUT"
APKS=keyboard/app/build/outputs/apk
PKG=com.humming.keyboard.debug
RUNNER=$PKG.test/androidx.test.runner.AndroidJUnitRunner
BENCH_PKG=com.humming.keyboard
LAUNCHER=$BENCH_PKG/dev.patrickgold.florisboard.SettingsLauncherAlias
COLD_RUNS=10

adb wait-for-device
adb shell getprop ro.build.fingerprint | tee "$OUT/device.txt"
adb install -r -g "$(ls $APKS/debug/*.apk | head -1)"
adb install -r -g "$(ls $APKS/androidTest/debug/*.apk | head -1)"
adb install -r -g "$(ls $APKS/benchmark/*.apk | head -1)"

instrument() { # name, am instrument arguments...
  local name=$1; shift
  adb shell am instrument -w -r "$@" "$RUNNER" | tee "$OUT/$name.txt"
  grep -q '^OK (' "$OUT/$name.txt" && ! grep -q 'FAILURES!!!' "$OUT/$name.txt"
}
private_file() { # path inside the debug app's files dir, destination
  adb exec-out run-as "$PKG" cat "files/$1" > "$2" 2>/dev/null && [ -s "$2" ] || rm -f "$2"
}

status=0
instrument model_load -e humming.modelBench true -e class com.humming.keyboard.ModelLoadBenchTest || status=1
private_file ci/model_load.json "$OUT/model_load.json"
instrument smoke -e class com.humming.keyboard.ImeSmokeTest || status=1
private_file ci/failure_hierarchy.xml "$OUT/failure_hierarchy.xml"
adb pull /data/local/tmp/humming "$OUT/screens" || status=1

adb shell am start -W -n "$LAUNCHER" > /dev/null
sleep 5
: > "$OUT/cold_start.txt"
for i in $(seq 1 "$COLD_RUNS"); do
  adb shell am force-stop "$BENCH_PKG"
  sleep 2
  adb shell am start -W -n "$LAUNCHER" | tee -a "$OUT/cold_start.txt"
  sleep 3
done
adb shell am force-stop "$BENCH_PKG"
adb logcat -d -s HUMMING:V > "$OUT/logcat-humming.txt" || true
exit $status
