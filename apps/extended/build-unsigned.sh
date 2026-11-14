#!/usr/bin/env bash
set -euo pipefail
: "${ANDROID_JAR:?Set ANDROID_JAR to the Android SDK platform android.jar}"
: "${OUTPUT_APK:?Set OUTPUT_APK to a new unsigned APK path}"
root=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repo=$(CDPATH= cd -- "$root/../.." && pwd)
test -r "$ANDROID_JAR"
test ! -e "$OUTPUT_APK"
build=$(mktemp -d)
cleanup() { python3 -c 'import shutil,sys; shutil.rmtree(sys.argv[1])' "$build"; }
trap cleanup EXIT
mkdir -p "$build/classes" "$build/dex" "$build/lib/arm64-v8a"
python3 - "$root/src/main/AndroidManifest.xml" "$build/AndroidManifest.xml" <<'PYMANIFEST'
import sys
from pathlib import Path
s=Path(sys.argv[1]).read_text().replace('<manifest ','<manifest package="com.cifi.assistant" ',1)
Path(sys.argv[2]).write_text(s)
PYMANIFEST
aapt2 compile --dir "$root/src/main/res" -o "$build/resources.zip"
aapt2 link --auto-add-overlay -R "$build/resources.zip" -I "$ANDROID_JAR" --manifest "$build/AndroidManifest.xml" --min-sdk-version 29 --target-sdk-version 35 --version-code 1 --version-name 0.1.0 -o "$build/resources.apk"
mapfile -t sources < <(find "$root/src/main/java" -type f -name '*.java' -print)
javac -source 17 -target 17 -cp "$ANDROID_JAR" -d "$build/classes" "${sources[@]}"
jar --create --file "$build/classes.jar" -C "$build/classes" .
d8 --lib "$ANDROID_JAR" --min-api 29 --output "$build/dex" "$build/classes.jar"
rust_host=$(rustc -vV | sed -n 's/^host: //p')
rust_target=${RUST_TARGET:-$rust_host}
[[ "$rust_target" == aarch64-linux-android ]] || { echo 'Set RUST_TARGET=aarch64-linux-android and configure an Android linker' >&2; exit 1; }
rust_args=(); if [[ -n ${RUST_TARGET:-} ]]; then rust_args=(--target "$RUST_TARGET"); fi
if [[ -n ${HOME:-} ]]; then rust_args+=("--remap-path-prefix=$HOME=/cifi-home"); fi
if [[ -n ${PREFIX:-} ]]; then rust_args+=("--remap-path-prefix=$PREFIX=/cifi-prefix"); fi
rust_args+=("--remap-path-prefix=$repo=/cifi-source" "--remap-path-prefix=$build=/cifi-build")
# Termux clang otherwise injects its local library directory into the ELF.
# This driver option is specific to Termux, not an Android NDK option.
termux_native=false
if [[ "$rust_host" == aarch64-linux-android && -n ${PREFIX:-} ]]; then
    command -v clang >/dev/null && command -v readelf >/dev/null || { echo 'Termux native builds require clang and readelf' >&2; exit 1; }
    clang_help=$(clang --help)
    [[ "$clang_help" == *-fno-termux-rpath* ]] || { echo 'Termux clang does not support -fno-termux-rpath' >&2; exit 1; }
    rust_args+=(-C link-arg=-fno-termux-rpath)
    termux_native=true
fi
rustc "${rust_args[@]}" --edition=2021 --crate-type cdylib -C opt-level=2 -C debuginfo=0 "$repo/engine/native_bridge.rs" -o "$build/lib/arm64-v8a/libcifi_math.so"
if "$termux_native"; then
    dynamic=$(readelf -d "$build/lib/arm64-v8a/libcifi_math.so")
    [[ "$dynamic" != *'(RPATH)'* && "$dynamic" != *'(RUNPATH)'* ]] || { echo 'Native library contains a local runtime search path' >&2; exit 1; }
    python3 - "$build/lib/arm64-v8a/libcifi_math.so" "$repo" "$build" <<'PYPATH'
import os, sys
from pathlib import Path
data = Path(sys.argv[1]).read_bytes()
for value in (os.environ.get('HOME'), os.environ.get('PREFIX'), *sys.argv[2:]):
    if value and len(value) > 1 and os.fsencode(value) in data:
        raise SystemExit('Native library contains a local build path')
PYPATH
fi
python3 - "$build/lib/arm64-v8a/libcifi_math.so" <<'PYCHECK'
from pathlib import Path
import sys
b=Path(sys.argv[1]).read_bytes()[:20]
if len(b)!=20 or b[:4]!=b'\x7fELF' or b[4]!=2 or b[5]!=1 or int.from_bytes(b[18:20],'little')!=183:
    raise SystemExit('Rust output is not a little-endian ARM64 ELF; set RUST_TARGET and linker for Android ARM64')
PYCHECK
cp "$build/resources.apk" "$build/unsigned.apk"
python3 - "$build" "$root/src/main/assets" <<'PYZIP'
import sys,zipfile
from pathlib import Path
p=Path(sys.argv[1]); assets=Path(sys.argv[2])
with zipfile.ZipFile(p/'unsigned.apk','a',compression=zipfile.ZIP_DEFLATED) as z:
    for dex in sorted((p/'dex').glob('classes*.dex')): z.write(dex,dex.name)
    z.write(p/'lib/arm64-v8a/libcifi_math.so','lib/arm64-v8a/libcifi_math.so')
    for name in ('projection.js','site-transaction.js'): z.write(assets/name,'assets/'+name)
PYZIP
zipalign -f 4 "$build/unsigned.apk" "$build/aligned.apk"
python3 - "$build/aligned.apk" "$OUTPUT_APK" <<'PYCOPY'
import shutil,sys
with open(sys.argv[1],'rb') as src, open(sys.argv[2],'xb') as dst: shutil.copyfileobj(src,dst)
PYCOPY
printf '%s\n' 'Unsigned ARM64 APK created; sign it with your own Android signing key before installation.'
