#!/bin/sh
# Güvenlik sertleştirmesi testleri: masaüstü Java testleri + statik kontroller.
# Firestore/Storage kural testleri emülatör gerektirir ve ayrı çalışır:
#   firebase emulators:exec --only firestore,storage "npm --prefix functions test"
set -e
cd "$(dirname "$0")/../.."
J=app/src/main/java
P=$J/com/zeytindefteri/app
D=$(mktemp -d)
trap 'rm -rf "$D"' EXIT
javac -nowarn -encoding UTF-8 -d "$D" $J/MeydanPostPolicy.java tests/security/MeydanPostPolicyTest.java
java -cp "$D" com.zeytindefteri.app.MeydanPostPolicyTest
javac -nowarn -encoding UTF-8 -d "$D" $P/ModerationTransport.java $P/ModerationBackendException.java \
  $P/MarketBackendConfig.java tests/security/ModerationTransportTest.java tests/security/MarketBackendConfigTest.java
java -cp "$D" com.zeytindefteri.app.ModerationTransportTest
# Supabase ayarı: BuildConfig yok / boş / dolu (uydurma deneme değerleri)
java -cp "$D" com.zeytindefteri.app.MarketBackendConfigTest none
for MODE in empty filled; do
  B="$D/buildconfig-$MODE"; mkdir -p "$B/src/com/zeytindefteri/app"
  if [ "$MODE" = filled ]; then U="https://abcdefghijklmnopqrst.supabase.co"; K="sb_publishable_TEST_ONLY_not_a_real_key"; else U=""; K=""; fi
  printf 'package com.zeytindefteri.app;\npublic final class BuildConfig {\n  public static final String SUPABASE_URL = "%s";\n  public static final String SUPABASE_PUBLISHABLE_KEY = "%s";\n}\n' "$U" "$K" \
    > "$B/src/com/zeytindefteri/app/BuildConfig.java"
  javac -nowarn -encoding UTF-8 -d "$B" "$B/src/com/zeytindefteri/app/BuildConfig.java"
  java -cp "$B:$D" com.zeytindefteri.app.MarketBackendConfigTest $MODE
done
python3 tests/security/check_static.py "$@"
