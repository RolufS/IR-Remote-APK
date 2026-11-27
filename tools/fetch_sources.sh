#!/usr/bin/env bash
# Holt die Quelldaten nach tools/sources/{flipper,irdb,smartir} auf feste Commits (idempotent).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/sources"
FLIPPER_URL="https://github.com/Lucaslhm/Flipper-IRDB"
FLIPPER_SHA="d126fb1b6f1e114c52b4a8c19839ea65e3a9c24d"
IRDB_URL="https://github.com/probonopd/irdb"
IRDB_SHA="a3e34000f952795aa86a7a26fac7d463cc0eeccd"
SMARTIR_URL="https://github.com/smartHomeHub/SmartIR"
SMARTIR_SHA="3dca407e3c475b57c02c08b97ae521a4ef0bbf43"

fetch() {
  local url="$1" dir="$2" folder="$3" sha="$4"
  if [ -d "$dir/.git" ]; then
    git -C "$dir" cat-file -e "$sha^{commit}" 2>/dev/null || git -C "$dir" fetch origin
  else
    mkdir -p "$(dirname "$dir")"
    git clone --filter=blob:none --no-checkout "$url" "$dir"
  fi
  git -C "$dir" sparse-checkout set "$folder"
  git -C "$dir" checkout "$sha"
}

fetch "$FLIPPER_URL" "$ROOT/flipper" TVs "$FLIPPER_SHA"
fetch "$IRDB_URL" "$ROOT/irdb" codes "$IRDB_SHA"
fetch "$SMARTIR_URL" "$ROOT/smartir" codes/climate "$SMARTIR_SHA"
