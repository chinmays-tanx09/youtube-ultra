#!/usr/bin/env bash
# Builds the MP3 encoder (LAME 3.100 + module/jni/paradox_mp3.c) as libparadoxmp3.so.
#   scripts/build-native.sh            -> build/native/{arm64-v8a,armeabi-v7a}/libparadoxmp3.so  (needs the Android NDK, r27c tested)
#   scripts/build-native.sh host       -> build/native/host/libparadoxmp3.so  (for module/test on a desktop JVM)
# LAME is LGPL-2.0-or-later; it is downloaded unmodified (pinned SHA-256 below) and linked into this one library.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; DEPS="$ROOT/.deps"; OUT="$ROOT/build/native"
LAME_URL="https://downloads.sourceforge.net/project/lame/lame/3.100/lame-3.100.tar.gz"
LAME_SHA="ddfe36cab873794038ae2c1210557ad34857a4b6bdc515785d1da9e175b1da1e"
mkdir -p "$DEPS"; TGZ="$DEPS/lame-3.100.tar.gz"
if [ ! -f "$TGZ" ] || ! echo "$LAME_SHA  $TGZ" | sha256sum -c --status; then curl -fL --retry 3 -o "$TGZ.part" "$LAME_URL"; mv "$TGZ.part" "$TGZ"; fi
echo "$LAME_SHA  $TGZ" | sha256sum -c --quiet
[ -d "$DEPS/lame-3.100" ] || tar -xzf "$TGZ" -C "$DEPS"
L="$DEPS/lame-3.100"
SRCS="$(ls "$L"/libmp3lame/*.c) $ROOT/module/jni/paradox_mp3.c"
CFLAGS="-O2 -fPIC -fvisibility=hidden -DSTDC_HEADERS -DHAVE_STDINT_H -DHAVE_LIMITS_H -Dieee754_float32_t=float -w -I$L/include -I$L/libmp3lame"
build() { # $1 out dir, $2 cc, $3 extra flags, $4 strip
  mkdir -p "$1"; $2 $CFLAGS $3 -shared -o "$1/libparadoxmp3.so" $SRCS -lm -Wl,--gc-sections -Wl,-z,max-page-size=16384 -Wl,--build-id=none
  [ -n "${4:-}" ] && "$4" --strip-unneeded "$1/libparadoxmp3.so"; ls -la "$1/libparadoxmp3.so"; }
if [ "${1:-}" = host ]; then
  JH="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")}"
  build "$OUT/host" "${CC:-cc}" "-I$JH/include -I$JH/include/linux"
  exit 0
fi
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-$HOME/Android/Sdk/ndk/27.2.12479018}}"
TC="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"; [ -x "$TC/clang" ] || { echo "NDK not found (set ANDROID_NDK_HOME)" >&2; exit 1; }
build "$OUT/arm64-v8a"   "$TC/aarch64-linux-android26-clang"   "" "$TC/llvm-strip"
build "$OUT/armeabi-v7a" "$TC/armv7a-linux-androideabi26-clang" "-mfpu=neon -mfloat-abi=softfp" "$TC/llvm-strip"
