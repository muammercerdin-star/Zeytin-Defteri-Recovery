#!/bin/sh
# Güvenlik sertleştirmesi testleri: masaüstü Java testleri + statik kontroller.
# Firestore/Storage kural testleri emülatör gerektirir ve ayrı çalışır:
#   firebase emulators:exec --only firestore,storage "npm --prefix functions test"
set -e
cd "$(dirname "$0")/../.."
J=app/src/main/java
D=$(mktemp -d)
trap 'rm -rf "$D"' EXIT
javac -nowarn -encoding UTF-8 -d "$D" $J/MeydanPostPolicy.java tests/security/MeydanPostPolicyTest.java
java -cp "$D" com.zeytindefteri.app.MeydanPostPolicyTest
javac -nowarn -encoding UTF-8 -d "$D" $J/com/zeytindefteri/app/ModerationTransport.java \
  $J/com/zeytindefteri/app/ModerationBackendException.java $J/com/zeytindefteri/app/MarketBackendConfig.java \
  tests/security/ModerationTransportTest.java
java -cp "$D" com.zeytindefteri.app.ModerationTransportTest
python3 tests/security/check_static.py "$@"
