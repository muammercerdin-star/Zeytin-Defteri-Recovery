#!/bin/bash
# Yönlendiren asistan ve Tanecik sahneleri: saf sınıfların birim testleri ve bağlantı denetimi (JVM).
set -e
DIR=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$DIR/../.." && pwd)
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
source "$ROOT/tests/sentence_bank/brain_build.sh"
brain_build "$T" $(ls "$DIR"/*Test.java)
for t in "$DIR"/*Test.java; do
  java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$BRAIN_CP" com.zeytindefteri.app.$(basename "$t" .java) "$ROOT"
done
python3 "$DIR/check_wiring.py"
