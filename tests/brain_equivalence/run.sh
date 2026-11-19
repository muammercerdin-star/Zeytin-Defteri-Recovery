#!/bin/bash
# Birebir aynılık (İP3): RuleBrain ↔ eski asistan zinciri (dondurulmuş kopya). Tek fark varsa KALIR.
# Masaüstü (JVM) testidir; Android derlemesi değildir.
set -e
DIR=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$DIR/../.." && pwd)
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
source "$ROOT/tests/sentence_bank/brain_build.sh"
brain_build "$T" "$DIR/LegacyRouter.java" "$DIR/BrainEquivalenceTest.java"
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$BRAIN_CP" com.zeytindefteri.app.BrainEquivalenceTest "$ROOT"
