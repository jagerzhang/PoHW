#!/usr/bin/env bash
# PoHW / Android TV 答题守护 —— 零 Gradle 构建链: aapt2 -> javac -> d8 -> zipalign -> apksigner
# Requirements: JDK 17 + Android SDK (platform android-34 / build-tools 34.0.0)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP="$ROOT/app"
BUILD="$ROOT/.build"
DIST="$ROOT/dist"
VERSION_NAME="6.0.1"
VERSION_CODE="11"
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
# v6.0.1 semantic gates: assert behaviour symbols, not exact line spellings.
# (Old gates assumed clearing FLAG_NOT_FOCUSABLE guarantees the WMS focused
#  window; it does not. The focus state machine below is the real safeguard.)
SVC="$APP/src/com/jager/tvquiz/QuizOverlayService.java"
VIEW="$APP/src/com/jager/tvquiz/OverlayQuizView.java"
grep -q 'QuizFlow' "$SVC"                       # 判题状态机由服务推进
grep -q 'flow.submit' "$SVC"
grep -q 'KeyRouter.decide' "$VIEW"              # 遥控器按键自主路由
grep -q 'WrongBook.recordAsync' "$SVC"
grep -q 'MediaPauseController' "$SVC"
grep -q 'BootPolicy.shouldStartGuard' "$APP/src/com/jager/tvquiz/BootReceiver.java"
grep -q 'new OverlayQuizView' "$SVC"             # 每次弹题不复用 View
grep -q 'setFocusable(false)' "$VIEW"            # 子按钮不参与焦点树
grep -q 'WrongBookActivity' "$APP/AndroidManifest.xml"
# P0: FGS 契约覆盖所有分支（startForeground 必须在 onStartCommand 入口）
grep -q 'fgReady = enterForeground()' "$SVC"
grep -q 'startForeground(NOTIF_ID' "$SVC"
# P0: 窗口焦点状态机（失焦重抢 + 有限重试 + 一次窗口重建）
grep -q 'onQuizWindowFocus' "$SVC"
grep -q 'updateViewLayout' "$SVC"
grep -q 'rebuildOverlay' "$SVC"
grep -q 'onWindowFocusChanged' "$VIEW"
grep -q 'requestContinue' "$VIEW"               # 不在按键分发栈内同步摘窗口
# P1: 一次性 STOP 令牌，客户端 boolean 不再是授权依据
grep -q 'issueStopToken' "$SVC"
grep -q 'consumeStopToken' "$SVC"
grep -q 'EXTRA_STOP_TOKEN' "$SVC"
if grep -q 'EXTRA_STOP_VERIFIED' "$SVC" "$VIEW"; then
  echo 'FORBIDDEN: STOP verified boolean must not be trusted'; exit 1
fi
# P1: QUICKBOOT 与 BOOT_COMPLETED 拆成两个 receiver
grep -q 'QuickBootReceiver' "$APP/AndroidManifest.xml"
[ -f "$APP/src/com/jager/tvquiz/QuickBootReceiver.java" ] || {
  echo 'MISSING QuickBootReceiver.java'; exit 1; }
# 行为级回归必须真的被编译、真的被执行
grep -q 'BehaviorV601Test' tests/run.sh
grep -q 'KeyRouter.java' tests/run.sh
grep -q 'QuizFlow.java' tests/run.sh
for f in Hashes Subjects AnswerPolicy BootPolicy BootSettings MediaPauseController          WrongBook WrongBookActivity OverlayQuizView KeyRouter QuizFlow; do
  [ -f "$APP/src/com/jager/tvquiz/$f.java" ] || { echo "MISSING source: $f.java"; exit 1; }
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

echo "[2/9] JVM unit tests (174 assertions incl. v6.0.1 behaviour regressions)"
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
# Dev builds generate a random local signing key (gitignored), so a fresh clone
# never ships a hardcoded private key. IMPORTANT for side-load upgrades: point
# POHW_KEYSTORE + POHW_KEYSTORE_PASS at the SAME keystore as the previous
# release, otherwise Android refuses to install over the old APK. Release
# artifacts keep a stable certificate only because that keystore is passed
# explicitly; the default (no env vars) intentionally does NOT guarantee it.
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
