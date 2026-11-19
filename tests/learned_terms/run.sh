#!/bin/bash
# Öğrenilen sözler (İP4): birim testleri + bağlantıların statik denetimi. Masaüstü (JVM) testidir.
set -e
DIR=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$DIR/../.." && pwd)
A="$ROOT/app/src/main/java/com/zeytindefteri/app"
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
source "$ROOT/tests/sentence_bank/brain_build.sh"
# Eski sürümün (cea13a1, İP4 sonu) LearnedTerms'i olduğu gibi, yalnız adı değiştirilerek: yedek dosyası uyumluluğu (İP5).
# Depoda değil (git show) — dal geçmişi yoksa (sığ kopya) dondurulmuş kopya kullanılır.
if git -C "$ROOT" cat-file -e cea13a1:app/src/main/java/com/zeytindefteri/app/LearnedTerms.java 2>/dev/null; then
  git -C "$ROOT" show cea13a1:app/src/main/java/com/zeytindefteri/app/LearnedTerms.java > "$T/LearnedTermsOld.src"
else
  cp "$DIR/LearnedTermsOld.cea13a1.txt" "$T/LearnedTermsOld.src"
fi
sed 's/\bLearnedTerms\b/LearnedTermsOld/g' "$T/LearnedTermsOld.src" > "$T/LearnedTermsOld.java"
brain_build "$T" "$A/ActionGate.java" "$A/Approval.java" "$A/OperationVoiceDialog.java" "$A/AssistantOutcome.java" \
  "$DIR/LearnedTermsTest.java" "$DIR/NewJobsTest.java" "$T/LearnedTermsOld.java"
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dbank="$ROOT/tests/sentence_bank/cumleler.tsv" -cp "$BRAIN_CP" com.zeytindefteri.app.LearnedTermsTest
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$BRAIN_CP" com.zeytindefteri.app.NewJobsTest
python3 "$DIR/check_wiring.py"
