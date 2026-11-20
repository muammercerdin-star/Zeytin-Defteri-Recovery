#!/bin/bash
# Ortak döngü, sunucusuz hazırlık (İP8-A): temizleyici, izin, kuyruk, ağ kapalı, bilgi paketi + bağlantı denetimi.
# Masaüstü (JVM) testidir; telefondan hiçbir veri gönderilmez (adres boş).
set -e
DIR=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$DIR/../.." && pwd)
A="$ROOT/app/src/main/java/com/zeytindefteri/app"
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
source "$ROOT/tests/sentence_bank/brain_build.sh"
EXTRA=()
for f in "$DIR"/*Test.java; do EXTRA+=("$f"); done
brain_build "$T" "$A/ContributionCleaner.java" "$A/CommunityQueue.java" "$A/CommunitySender.java" \
  "$A/MarketBackendConfig.java" "${EXTRA[@]}"
for f in "$DIR"/*Test.java; do
  cls=$(basename "$f" .java)
  java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$BRAIN_CP" "com.zeytindefteri.app.$cls" "$ROOT"
done
if [ -f "$DIR/check_wiring.py" ]; then python3 "$DIR/check_wiring.py"; fi
