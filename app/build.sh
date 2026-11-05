#!/usr/bin/env bash
# PoHW / Android TV 答题守护 —— 零 Gradle 构建链: aapt2 -> javac -> d8 -> zipalign -> apksigner
# Requirements: JDK 17 + Android SDK (platform android-34 / build-tools 34.0.0)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP="$ROOT/app"
BUILD="$ROOT/.build"
DIST="$ROOT/dist"
VERSION_NAME="6.0.0"
VERSION_CODE="10"
APK_NAME="PoHW-v${VERSION_NAME}.apk"

: "${JAVA_HOME:=/opt/jdk17}"
: "${ANDROID_HOME:=$HOME/Android/Sdk}"
export JAVA_HOME ANDROID_HOME
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/build-tools/34.0.0:$PATH"
ANDROID_JAR="$ANDROID_HOME/platforms/android-34/android.jar"

for tool in aapt2 javac d8 zipalign apksigner keytool; do
  command -v "$tool" >/dev/null || { echo "MISSING TOOL: $tool"; exit 1; }
done
[ -f "$ANDROID_JAR" ] || { echo "MISSING android.jar"; exit 1; }
[ -f "$APP/assets/quiz.db" ] || { echo "MISSING assets/quiz.db"; exit 1; }
[ -f "$APP/assets/quiz.db.version" ] || { echo "MISSING assets/quiz.db.version"; exit 1; }
[ -f "$APP/res/drawable-hdpi/ic_launcher.png" ] || { echo "MISSING launcher icon"; exit 1; }
mkdir -p "$BUILD" "$DIST"
cd "$ROOT"

echo "[0/9] static guards"
grep -q "versionCode=\"$VERSION_CODE\"" "$APP/AndroidManifest.xml"
grep -q "versionName=\"$VERSION_NAME\"" "$APP/AndroidManifest.xml"
grep -q 'getPackageInfo' "$APP/src/com/jager/tvquiz/MainActivity.java"
grep -q 'GuardPolicy.allowStop' "$APP/src/com/jager/tvquiz/QuizOverlayService.java"
grep -q 'setAllSubjects' "$APP/src/com/jager/tvquiz/QuizEngine.java"
# v6 regression guards
grep -q 'AnswerPolicy.shouldSwap' "$APP/src/com/jager/tvquiz/QuizOverlayService.java"
grep -q 'WrongBook.recordAsync' "$APP/src/com/jager/tvquiz/QuizOverlayService.java"
grep -q 'MediaPauseController' "$APP/src/com/jager/tvquiz/QuizOverlayService.java"
grep -q 'BootPolicy.shouldStartGuard' "$APP/src/com/jager/tvquiz/BootReceiver.java"
grep -q 'final OverlayQuizView view = new OverlayQuizView' "$APP/src/com/jager/tvquiz/QuizOverlayService.java"
grep -q 'setFocusable(false)' "$APP/src/com/jager/tvquiz/OverlayQuizView.java"
grep -q 'lp.flags &= ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE' \
  "$APP/src/com/jager/tvquiz/QuizOverlayService.java"
grep -q 'WrongBookActivity' "$APP/AndroidManifest.xml"
for f in Hashes Subjects AnswerPolicy BootPolicy BootSettings MediaPauseController \
         WrongBook WrongBookActivity OverlayQuizView; do
  [ -f "$APP/src/com/jager/tvquiz/$f.java" ] || { echo "MISSING v6 source: $f.java"; exit 1; }
done
if grep -rq -E 'v4[.]1 控制台|DeviceAdmin|startLockTask|DELETE_PACKAGES|pack_hh_|huohua|HH_API_KEY' "$APP/src"; then
  echo 'FORBIDDEN legacy / third-party content in sources'; exit 1
fi
if ls "$APP/assets"/pack_hh_*.json >/dev/null 2>&1; then
  echo 'FORBIDDEN third-party question packs in assets'; exit 1
fi
if [ -f "$APP/assets/index.json" ]; then
  echo 'FORBIDDEN third-party bank index in assets'; exit 1
fi
if grep -q 'allowBackup="true"' "$APP/AndroidManifest.xml"; then
  echo 'allowBackup must stay false'; exit 1
fi

echo "[1/9] verify built-in question bank"
python3 tools/quizbank/verify.py "$APP/assets/quiz.db"

echo "[2/9] JVM unit tests"
bash tests/run.sh

STAMP="$(date +%s)"
for d in gen classes dexout out; do
  if [ -d "$BUILD/$d" ]; then mv "$BUILD/$d" "$BUILD/$d.old.$STAMP"; fi
done
mkdir -p "$BUILD/gen" "$BUILD/classes" "$BUILD/dexout" "$BUILD/out"

echo "[3/9] aapt2 compile"
aapt2 compile --dir "$APP/res" -o "$BUILD/out/res.zip"

echo "[4/9] aapt2 link"
aapt2 link -o "$BUILD/out/base.apk" \
  -I "$ANDROID_JAR" \
  --manifest "$APP/AndroidManifest.xml" \
  -A "$APP/assets" \
  --java "$BUILD/gen" \
  --min-sdk-version 21 --target-sdk-version 33 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  "$BUILD/out/res.zip"

echo "[5/9] javac"
find "$APP/src" "$BUILD/gen" -name '*.java' > "$BUILD/sources.txt"
grep -q 'QuizDb.java' "$BUILD/sources.txt"
grep -q 'PinVerifyView.java' "$BUILD/sources.txt"
grep -q 'WrongBook.java' "$BUILD/sources.txt"
javac -source 8 -target 8 -encoding UTF-8 -nowarn \
  -classpath "$ANDROID_JAR" -d "$BUILD/classes" @"$BUILD/sources.txt"

echo "[6/9] d8"
find "$BUILD/classes" -name '*.class' > "$BUILD/classes.txt"
d8 --lib "$ANDROID_JAR" --min-api 21 --output "$BUILD/dexout" @"$BUILD/classes.txt"
if grep -aq 'v4.1 控制台' "$BUILD/dexout/classes.dex"; then
  echo 'OLD HARDCODED TITLE IN DEX'; exit 1
fi
grep -aq '应用版本' "$BUILD/dexout/classes.dex"
grep -aq '错题库' "$BUILD/dexout/classes.dex"

echo "[7/9] package + zipalign"
cp "$BUILD/dexout/classes.dex" "$BUILD/out/classes.dex"
(cd "$BUILD/out" && zip -q -j base.apk classes.dex)
zipalign -f 4 "$BUILD/out/base.apk" "$BUILD/out/aligned.apk"

echo "[8/9] sign + verify"
# Never ship a fixed debug key: generate a random local signing key (gitignored).
# For a release, point POHW_KEYSTORE / POHW_KEYSTORE_PASS at your own key.
KS="${POHW_KEYSTORE:-$BUILD/release.keystore}"
PASS_FILE="$BUILD/keystore.pass"
if [ -n "${POHW_KEYSTORE_PASS:-}" ]; then
  KS_PASS="$POHW_KEYSTORE_PASS"
elif [ -f "$PASS_FILE" ]; then
  KS_PASS="$(cat "$PASS_FILE")"
else
  KS_PASS="$(python3 -c 'import secrets; print(secrets.token_hex(16))')"
  printf '%s' "$KS_PASS" > "$PASS_FILE"
fi
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storepass "$KS_PASS" -keypass "$KS_PASS" \
    -alias pohw -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=PoHW, OU=OpenSource, O=PoHW, L=Shenzhen, ST=Guangdong, C=CN" \
    >/dev/null 2>&1
fi
apksigner sign --ks "$KS" --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
  --out "$DIST/$APK_NAME" "$BUILD/out/aligned.apk"
apksigner verify --verbose "$DIST/$APK_NAME"

echo "[9/9] artifact checks"
aapt dump badging "$DIST/$APK_NAME" 2>/dev/null | grep -q "versionName='$VERSION_NAME'"
unzip -l "$DIST/$APK_NAME" | grep -q 'assets/quiz.db'
unzip -l "$DIST/$APK_NAME" | grep -q 'assets/quiz.db.version'
(cd "$ROOT" && zip -q -r "$DIST/PoHW-src.zip" \
  README.md LICENSE .gitignore CHANGELOG.md \
  app/AndroidManifest.xml app/build.sh app/src app/res app/assets \
  tests docs tools)

echo "=== RESULT ==="
ls -lh "$DIST/$APK_NAME" "$DIST/PoHW-src.zip"
sha256sum "$DIST/$APK_NAME"
echo "BUILD_OK version=$VERSION_NAME code=$VERSION_CODE"
