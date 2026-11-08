#!/bin/bash
# Tanıtım modu: örnek veri hikâyesi (Java) + buluta/piyasaya sızmama güvencesi (statik denetim).
set -e
here="$(cd "$(dirname "$0")" && pwd)"; root="$(cd "$here/../.." && pwd)"
A="$root/app/src/main/java/com/zeytindefteri/app"
out="$(mktemp -d)"; trap 'rm -rf "$out"' EXIT
stubs=$(find "$root/tests/buyers/stubs" -name '*.java')
javac -encoding UTF-8 -nowarn -d "$out" $stubs "$A/TanitimVerisi.java" "$A/ElekSaleModel.java" "$A/BuyerFairCompare.java" \
  "$A/BuyerElekReport.java" "$A/BuyerDirectory.java" "$here/TanitimTest.java" 2>&1 | grep -v "^Note:" || true
java -Dstdout.encoding=UTF-8 -cp "$out" com.zeytindefteri.app.TanitimTest
python3 "$here/check_safety.py"
