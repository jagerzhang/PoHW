#!/usr/bin/env bash
# JVM unit tests: pure-Java logic + tiny Android SharedPreferences/Context stubs.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
: "${JAVA_HOME:=/opt/jdk17}"
OUT="$ROOT/.build/test-classes"
cd "$ROOT"

STAMP="$(date +%s)"
if [ -d "$OUT" ]; then mv "$OUT" "$OUT.old.$STAMP"; fi
mkdir -p "$OUT"

"$JAVA_HOME/bin/javac" -encoding UTF-8 -source 8 -target 8 -nowarn -d "$OUT" \
  tests/src/android/content/SharedPreferences.java \
  tests/src/android/content/MemPrefs.java \
  tests/src/android/content/Context.java \
  app/src/com/jager/tvquiz/Hashes.java \
  app/src/com/jager/tvquiz/Subjects.java \
  app/src/com/jager/tvquiz/Json.java \
  app/src/com/jager/tvquiz/Syllabus.java \
  app/src/com/jager/tvquiz/SubjectWeights.java \
  app/src/com/jager/tvquiz/AnswerPolicy.java \
  app/src/com/jager/tvquiz/BootPolicy.java \
  app/src/com/jager/tvquiz/LaunchPolicy.java \
  app/src/com/jager/tvquiz/KeyRouter.java \
  app/src/com/jager/tvquiz/QuizFlow.java \
  app/src/com/jager/tvquiz/PinGate.java \
  app/src/com/jager/tvquiz/SettingsStore.java \
  app/src/com/jager/tvquiz/QuizTextSizer.java \
  app/src/com/jager/tvquiz/GuardPolicy.java \
  app/src/com/jager/tvquiz/GuardState.java \
  app/src/com/jager/tvquiz/UnlockPolicy.java \
  app/src/com/jager/tvquiz/RoundPolicy.java \
  app/src/com/jager/tvquiz/QrEncoder.java \
  app/src/com/jager/tvquiz/CooldownPolicy.java \
  app/src/com/jager/tvquiz/DifficultyPolicy.java \
  app/src/com/jager/tvquiz/PracticeConfig.java \
  app/src/com/jager/tvquiz/UpdateConfig.java \
  app/src/com/jager/tvquiz/UpdateState.java \
  tests/src/PinGateTest.java \
  tests/src/QuizLayoutTest.java \
  tests/src/StopGuardTest.java \
  tests/src/StopStateTest.java \
  tests/src/GradeScopeTest.java \
  tests/src/FeatureV6Test.java \
  tests/src/BehaviorV601Test.java \
  tests/src/SubjectWeightsTest.java \
  tests/src/SyllabusTest.java \
  tests/src/UnlockPolicyTest.java \
  tests/src/CooldownPolicyTest.java \
  tests/src/DifficultyPolicyTest.java \
  tests/src/PracticeConfigTest.java \
  tests/src/UpdateConfigTest.java \
  tests/src/RoundPolicyTest.java \
  tests/src/QuizTextSizerTest.java \
  tests/src/QrEncoderTest.java

TOTAL=0
for t in PinGateTest QuizLayoutTest StopGuardTest StopStateTest GradeScopeTest \
         FeatureV6Test BehaviorV601Test SubjectWeightsTest SyllabusTest \
         UnlockPolicyTest CooldownPolicyTest DifficultyPolicyTest PracticeConfigTest \
         UpdateConfigTest RoundPolicyTest QuizTextSizerTest QrEncoderTest; do
  LOG="$ROOT/.build/$t.log"
  "$JAVA_HOME/bin/java" -Dfile.encoding=UTF-8 -cp "$OUT" "$t" > "$LOG"
  grep -q 'VERIFY=PASS' "$LOG" || { cat "$LOG"; echo "FAILED: $t"; exit 1; }
  N="$(grep -c '^\[PASS\]' "$LOG" || true)"
  TOTAL=$((TOTAL + N))
  tail -2 "$LOG"
done
echo "JVM_TESTS_OK total=$TOTAL"
