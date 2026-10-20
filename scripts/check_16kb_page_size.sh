#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -lt 1 ] || [ "$#" -gt 2 ]; then
    echo "usage: $0 path/to/app.apk [path/to/app.aab]"
    exit 2
fi

ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
NDK="${ANDROID_NDK_HOME:-$ANDROID_HOME/ndk/27.2.12479018}"
READELF="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf"

if [ ! -x "$READELF" ]; then
    echo "llvm-readelf not found at $READELF"
    exit 1
fi

check_library() {
    local library="$1"
    local abi="$2"

    if [ ! -f "$library" ]; then
        echo "missing $abi library: $library"
        exit 1
    fi

    local aligns
    aligns="$("$READELF" -lW "$library" | awk '$1 == "LOAD" {print $NF}' | sort -u)"
    if [ "$aligns" != "0x4000" ]; then
        echo "$abi is not 16 KB aligned (LOAD align: $aligns)"
        exit 1
    fi
    echo "$abi: 16 KB aligned"
}

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

APK="$1"
unzip -q "$APK" 'lib/*' -d "$TMP/apk"
for abi in arm64-v8a x86_64; do
    check_library "$TMP/apk/lib/$abi/libmultituner.so" "$abi"
done

ZIPALIGN="$ANDROID_HOME/build-tools/35.0.0/zipalign"
if [ -x "$ZIPALIGN" ]; then
    "$ZIPALIGN" -c -P 16 -v 4 "$APK" > /dev/null
    echo "APK zip alignment: 16 KB"
fi

if [ "$#" -eq 2 ]; then
    AAB="$2"
    unzip -q "$AAB" 'base/lib/*' -d "$TMP/aab"
    for abi in arm64-v8a x86_64; do
        check_library "$TMP/aab/base/lib/$abi/libmultituner.so" "$abi (AAB)"
    done
fi

echo "16 KB page size check passed"
