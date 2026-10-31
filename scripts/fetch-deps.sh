#!/usr/bin/env bash
# Downloads the pinned build tools and libraries listed in scripts/deps.txt into .deps/
# and checks every file's SHA-256. Nothing here is committed to the repository.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"; DEPS="$ROOT/.deps"; mkdir -p "$DEPS"
grep -v '^#' "$ROOT/scripts/deps.txt" | while IFS='|' read -r name url sha; do
  [ -z "$name" ] && continue
  out="$DEPS/$name"; mkdir -p "$(dirname "$out")"
  if [ ! -f "$out" ] || ! echo "$sha  $out" | sha256sum -c --status; then
    echo "fetching $name"; curl -fL --retry 3 --max-time 600 -o "$out.part" "$url"; mv "$out.part" "$out"
  fi
  echo "$sha  $out" | sha256sum -c --quiet || { echo "checksum mismatch: $name" >&2; exit 1; }
done
cp "$ROOT/module/libs/"*.jar "$DEPS/libs/"
echo "deps ok -> $DEPS"
