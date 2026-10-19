#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="${TMPDIR:-/tmp}/multituner_yin_test"

g++ -std=c++17 -O2 \
    -I "$ROOT/app/src/main/cpp" \
    "$ROOT/native-tests/yin_test.cpp" \
    "$ROOT/app/src/main/cpp/yin.cpp" \
    -o "$OUT"

"$OUT"
