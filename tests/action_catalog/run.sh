#!/bin/bash
# Eylem kataloğu tutarlılığı (İP3). Masaüstü (JVM) testidir; Android derlemesi değildir.
set -e
DIR=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$DIR/../.." && pwd)
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
source "$ROOT/tests/sentence_bank/brain_build.sh"
brain_build "$T" "$ROOT/tests/brain_equivalence/LegacyRouter.java" "$ROOT/tests/brain_equivalence/BrainEquivalenceTest.java" "$DIR/ActionCatalogTest.java"
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$BRAIN_CP" com.zeytindefteri.app.ActionCatalogTest "$ROOT"
