#!/usr/bin/env bash
# Download a whisper.cpp ggml model. Usage: scripts/fetch-model.sh [base.en|small.en|tiny.en|base|small|tiny] [dest_dir]
# The ".en" models are English-only; base/small/tiny are multilingual (needed for language auto-detect).
set -euo pipefail
name="${1:-base.en}"
case "$name" in
  tiny.en)  min=70000000 ;;
  base.en)  min=140000000 ;;
  small.en) min=450000000 ;;
  tiny)     min=70000000 ;;
  base)     min=140000000 ;;
  small)    min=450000000 ;;
  *) echo "unknown model $name (tiny.en|base.en|small.en|tiny|base|small)" >&2; exit 1 ;;
esac
if [[ -n "${2:-}" ]]; then dest="$2"
elif [[ -n "${XDG_DATA_HOME:-}" ]]; then dest="$XDG_DATA_HOME/humming/models"
elif [[ "$(uname)" == "Darwin" ]]; then dest="$HOME/Library/Application Support/humming/models"
else dest="$HOME/.local/share/humming/models"; fi
mkdir -p "$dest"
out="$dest/ggml-$name.bin"
if [[ -f "$out" ]] && [[ $(wc -c < "$out") -ge $min ]]; then echo "$out"; exit 0; fi
url="https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-$name.bin"
curl -fL --retry 3 --retry-delay 5 -o "$out.part" "$url"
size=$(wc -c < "$out.part")
if [[ "$size" -lt "$min" ]]; then echo "download too small ($size bytes)" >&2; rm -f "$out.part"; exit 1; fi
mv "$out.part" "$out"
echo "$out"
