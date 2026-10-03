#!/usr/bin/env bash
# Speak a test phrase into a 16 kHz mono WAV.
# Usage: scripts/make-test-wav.sh out.wav ["phrase"] [lang]   (lang: en default, or es)
set -euo pipefail
out="${1:-hello.wav}"
phrase="${2:-Hello, this is a dictation test. Please buy milk and bread tomorrow.}"
lang="${3:-en}"
case "$lang" in
  en) say_voice=""; espeak_voice="en" ;;
  es) espeak_voice="es"
      # A real Spanish voice, or none: 'say -v <missing>' falls back to an English voice reading Spanish.
      say_voice="$( (command -v say >/dev/null && say -v '?' 2>/dev/null) | sed -nE 's/^(.*[^ ]) +es_(ES|MX|US) .*/\1/p' | head -1 || true)"
      [[ -n "$say_voice" ]] || say_voice="__none__" ;;
  *) echo "unknown lang $lang" >&2; exit 2 ;;
esac
say_ok() {
  command -v say >/dev/null 2>&1 || return 1
  [[ "$say_voice" == "__none__" ]] && return 1
  if [[ -n "$say_voice" ]]; then
    say -v "$say_voice" -o "$out" --file-format=WAVE --data-format=LEI16@16000 "$phrase" 2>/dev/null
  else
    say -o "$out" --file-format=WAVE --data-format=LEI16@16000 "$phrase"
  fi
}
if say_ok; then
  :
elif command -v espeak-ng >/dev/null 2>&1; then
  espeak-ng -v "$espeak_voice" -s 150 -w "$out.tmp.wav" "$phrase"
  if command -v ffmpeg >/dev/null 2>&1; then
    ffmpeg -loglevel error -y -i "$out.tmp.wav" -ar 16000 -ac 1 "$out" && rm -f "$out.tmp.wav"
  else
    mv "$out.tmp.wav" "$out"   # 22.05 kHz; the engine resamples
  fi
else
  echo "need macOS 'say' (voice ${say_voice:-default}) or espeak-ng" >&2; exit 1
fi
echo "$out"
