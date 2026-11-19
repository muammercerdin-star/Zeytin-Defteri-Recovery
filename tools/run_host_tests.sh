#!/bin/bash
# Tüm host (masaüstü) testleri: her tests/<dizin> bir birimdir.
# Giriş sırası: run.sh > run*.py > dizindeki diğer *.py. Yalnız Java dosyası olan dizin (giriş yok) ATLA yazılır.
# Kullanım: bash tools/run_host_tests.sh [günlük_dizini]
# Sonuç: her birim için PASS/FAIL/ATLA ve sonda özet; günlükler günlük dizininde (varsayılan geçici dizin).
# Not: bazı birimler ortam ister (PostgreSQL: ZD_PG, org.json: JSON_JAR, ZXing: ZXING_JAR, git geçmişi).
ROOT=$(cd "$(dirname "$0")/.." && pwd)
OUT=${1:-$(mktemp -d)}
mkdir -p "$OUT"
cd "$ROOT"
pass=0
fail=0
skip=0
for d in tests/*/; do
  d=${d%/}
  n=$(basename "$d")
  log="$OUT/$n.log"
  if [ -f "$d/run.sh" ]; then
    cmd="bash $d/run.sh"
  elif ls "$d"/run*.py >/dev/null 2>&1; then
    cmd="python3 $(ls "$d"/run*.py | head -1)"
  elif ls "$d"/*.py >/dev/null 2>&1; then
    cmd="for f in $(ls "$d"/*.py | grep -v helper | tr '\n' ' '); do python3 \$f || exit 1; done"
  else
    echo "ATLA $n"
    skip=$((skip + 1))
    continue
  fi
  if timeout 600 bash -c "$cmd" >"$log" 2>&1; then
    echo "PASS $n"
    pass=$((pass + 1))
  else
    echo "FAIL $n  ($log)"
    fail=$((fail + 1))
  fi
done
echo "Özet: $pass geçti · $fail kaldı · $skip atlandı · günlükler: $OUT"
[ "$fail" -eq 0 ]
