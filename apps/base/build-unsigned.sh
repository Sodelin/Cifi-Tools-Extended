#!/usr/bin/env bash
set -euo pipefail
: "${ANDROID_JAR:?Set ANDROID_JAR to the Android SDK platform android.jar}"
: "${OUTPUT_APK:?Set OUTPUT_APK to a new unsigned APK path}"
root=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
test -r "$ANDROID_JAR"
test ! -e "$OUTPUT_APK"
build=$(mktemp -d)
cleanup() { python3 -c 'import shutil,sys; shutil.rmtree(sys.argv[1])' "$build"; }
trap cleanup EXIT
mkdir -p "$build/classes" "$build/dex"
python3 - "$root/src/main/AndroidManifest.xml" "$build/AndroidManifest.xml" <<'PYMANIFEST'
import sys
from pathlib import Path
s=Path(sys.argv[1]).read_text().replace('<manifest ','<manifest package="com.cifi.tools.portal" ',1)
Path(sys.argv[2]).write_text(s)
PYMANIFEST
aapt2 compile --dir "$root/src/main/res" -o "$build/resources.zip"
aapt2 link -R "$build/resources.zip" -I "$ANDROID_JAR" --manifest "$build/AndroidManifest.xml" --min-sdk-version 29 --target-sdk-version 35 --version-code 1 --version-name 0.1.0 -o "$build/resources.apk"
javac -source 17 -target 17 -cp "$ANDROID_JAR" -d "$build/classes" "$root/src/main/java/com/cifi/tools/MainActivity.java" "$root/src/main/java/com/cifi/tools/BackupCompare.java"
jar --create --file "$build/classes.jar" -C "$build/classes" .
d8 --lib "$ANDROID_JAR" --min-api 29 --output "$build/dex" "$build/classes.jar"
cp "$build/resources.apk" "$build/unsigned.apk"
python3 - "$build" <<'PY'
import sys, zipfile
from pathlib import Path
p=Path(sys.argv[1])
with zipfile.ZipFile(p/'unsigned.apk','a',compression=zipfile.ZIP_DEFLATED) as z:
    z.write(p/'dex/classes.dex','classes.dex')
PY
zipalign -f 4 "$build/unsigned.apk" "$build/aligned.apk"
python3 - "$build/aligned.apk" "$OUTPUT_APK" <<'PY'
import shutil, sys
with open(sys.argv[1], 'rb') as src, open(sys.argv[2], 'xb') as dst:
    shutil.copyfileobj(src,dst)
PY
printf '%s\n' 'Unsigned APK created; sign it with your own Android signing key before installation.'
