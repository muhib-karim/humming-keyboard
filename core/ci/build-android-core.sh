#!/usr/bin/env bash
# Builds the humming-core Android libraries (UniFFI/JNA cdylib, whisper.cpp inside) in four CPU variants plus the
# Kotlin bindings, laid out as android/app/core-drop/ expects:
#   jniLibs/arm64-v8a/libhumming_core.so       armv8-a baseline (NEON)            -- any arm64 phone
#   jniLibs/arm64-v8a/libhumming_core_fast.so  armv8.2-a+dotprod+fp16             -- picked at runtime when the CPU has asimddp+asimdhp
#   jniLibs/x86_64/libhumming_core.so          x86-64 + SSE4.2 baseline           -- any x86_64 device
#   jniLibs/x86_64/libhumming_core_fast.so     AVX2+FMA+F16C+BMI2                 -- picked at runtime when the CPU has them
# Usage: core/ci/build-android-core.sh <out-dir>   (needs ANDROID_NDK_HOME, rustup, cargo-ndk, cmake)
# Builds run under /opt so no home-directory path is embedded in the binaries (the APK content scan rejects them).
set -euo pipefail
OUT="$(realpath -m "$1")"
SRC="$(cd "$(dirname "$0")/.." && pwd)"
WORK=/opt/hb
sudo mkdir -p "$WORK" /opt/cargo && sudo chown -R "$(id -u)":"$(id -g)" "$WORK" /opt/cargo
rm -rf "$WORK/core" && cp -r "$SRC" "$WORK/core"
export CARGO_HOME=/opt/cargo
export PATH="$CARGO_HOME/bin:$HOME/.cargo/bin:$PATH"
rustup target add aarch64-linux-android x86_64-linux-android >/dev/null
command -v cargo-ndk >/dev/null || cargo install cargo-ndk --locked --version 3.5.4 >/dev/null
cd "$WORK/core"
mkdir -p "$OUT/jniLibs/arm64-v8a" "$OUT/jniLibs/x86_64" "$OUT/kotlin" "$OUT/logs"
export GGML_NATIVE=OFF
export WHISPER_DONT_GENERATE_BINDINGS=1
export CARGO_PROFILE_RELEASE_OPT_LEVEL=3

build() { # variant abi rust-target outname [extra env...]
  local variant=$1 abi=$2 triple=$3 name=$4; shift 4
  local tdir="$WORK/target-$variant"
  local soname="lib${name}.so"
  local upper; upper=$(echo "$triple" | tr 'a-z-' 'A-Z_')
  echo "::group::core $variant ($abi) -> $soname $*"
  local t0; t0=$(date +%s)
  env "$@" "CARGO_TARGET_${upper}_RUSTFLAGS=-C link-arg=-Wl,-soname,${soname} --remap-path-prefix=$CARGO_HOME=/cargo --remap-path-prefix=$WORK=/src" \
    CARGO_TARGET_DIR="$tdir" \
    cargo ndk -t "$abi" --platform 26 build --release --lib --features whisper,ffi 2>&1 | tail -n 40 | tee "$OUT/logs/build-$variant.log"
  cp "$tdir/$triple/release/libhumming_core.so" "$OUT/jniLibs/$abi/$soname"
  grep -m1 -o 'ARCH_FLAGS[^"]*\|-march=[^ ;"]*' -r "$tdir/$triple/release/build/"whisper-rs-sys-*/output 2>/dev/null | head -3 >> "$OUT/logs/build-$variant.log" || true
  echo "$variant $abi $soname $(( $(date +%s) - t0 ))s flags: $*" | tee -a "$OUT/logs/variants.txt"
  echo "::endgroup::"
}

build arm64-base arm64-v8a aarch64-linux-android humming_core
build arm64-fast arm64-v8a aarch64-linux-android humming_core_fast GGML_CPU_ARM_ARCH=armv8.2-a+dotprod+fp16
build x86_64-base x86_64 x86_64-linux-android humming_core GGML_SSE42=ON
build x86_64-fast x86_64 x86_64-linux-android humming_core_fast GGML_SSE42=ON GGML_AVX=ON GGML_AVX2=ON GGML_FMA=ON GGML_F16C=ON GGML_BMI2=ON

# libc++ from the NDK that built the libraries.
for pair in "arm64-v8a aarch64-linux-android" "x86_64 x86_64-linux-android"; do
  set -- $pair
  cp "$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/sysroot/usr/lib/$2/libc++_shared.so" "$OUT/jniLibs/$1/"
done

# Kotlin bindings from the built library's UniFFI metadata (host build of the bindgen only, no whisper).
CARGO_TARGET_DIR="$WORK/target-host" cargo run -q --release --no-default-features --features bindgen-cli --bin uniffi-bindgen -- \
  generate --library "$OUT/jniLibs/arm64-v8a/libhumming_core.so" --language kotlin --out-dir "$WORK/kt" --no-format
cp -r "$WORK/kt/app" "$OUT/kotlin/"

NDKV=$(sed -n 's/^Pkg.Revision *= *//p' "$ANDROID_NDK_HOME/source.properties")
{
  echo "humming-core Android artifact (built in this repository's CI from core/)"
  echo "source: core/ @ ${GITHUB_SHA:-local}  run: ${GITHUB_RUN_ID:-local}"
  echo "ndk: $NDKV  api: 26  rustc: $(rustc --version)"
  echo "variants (the app loads *_fast when /proc/cpuinfo has the listed features, else the baseline):"
  sed 's/^/  /' "$OUT/logs/variants.txt"
  echo "  arm64 fast needs: asimddp asimdhp   x86_64 fast needs: avx2 fma f16c bmi2"
  echo
  (cd "$OUT" && find jniLibs kotlin -type f | sort | xargs sha256sum | sed 's/^/  /')
} > "$OUT/MANIFEST.txt"
echo "${GITHUB_RUN_ID:-local}" > "$OUT/RUN_ID"
cat "$OUT/MANIFEST.txt"
ls -la "$OUT"/jniLibs/*
