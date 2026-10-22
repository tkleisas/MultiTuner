#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="${TMPDIR:-/tmp}"

g++ -std=c++17 -O2 \
    -I "$ROOT/app/src/main/cpp" \
    "$ROOT/native-tests/yin_test.cpp" \
    "$ROOT/app/src/main/cpp/yin.cpp" \
    -o "$OUT_DIR/multituner_yin_test"
"$OUT_DIR/multituner_yin_test"

g++ -std=c++17 -O2 \
    -I "$ROOT/app/src/main/cpp" \
    "$ROOT/native-tests/metronome_test.cpp" \
    "$ROOT/app/src/main/cpp/audio/Transport.cpp" \
    "$ROOT/app/src/main/cpp/audio/Metronome.cpp" \
    -o "$OUT_DIR/multituner_metronome_test"
"$OUT_DIR/multituner_metronome_test"

g++ -std=c++17 -O2 \
    -I "$ROOT/app/src/main/cpp" \
    "$ROOT/native-tests/drum_test.cpp" \
    "$ROOT/app/src/main/cpp/audio/DrumSynth.cpp" \
    "$ROOT/app/src/main/cpp/audio/DrumMachine.cpp" \
    -o "$OUT_DIR/multituner_drum_test"
"$OUT_DIR/multituner_drum_test"

g++ -std=c++17 -O2 \
    -I "$ROOT/app/src/main/cpp" \
    "$ROOT/native-tests/cassette_test.cpp" \
    "$ROOT/app/src/main/cpp/audio/CassettePlayer.cpp" \
    "$ROOT/app/src/main/cpp/audio/TapeEmulation.cpp" \
    "$ROOT/app/src/main/cpp/audio/Reverb.cpp" \
    "$ROOT/app/src/main/cpp/audio/Delay.cpp" \
    -o "$OUT_DIR/multituner_cassette_test"
"$OUT_DIR/multituner_cassette_test"

g++ -std=c++17 -O2 \
    -I "$ROOT/app/src/main/cpp" \
    "$ROOT/native-tests/effects_test.cpp" \
    "$ROOT/app/src/main/cpp/audio/Reverb.cpp" \
    "$ROOT/app/src/main/cpp/audio/Delay.cpp" \
    -o "$OUT_DIR/multituner_effects_test"
"$OUT_DIR/multituner_effects_test"
