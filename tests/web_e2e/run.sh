#!/bin/sh
# Uçtan uca eşleşme: gerçek relay + gerçek web sayfası + gerçek Java telefon motoru.
set -eu
cd "$(dirname "$0")/../.."
D=$(mktemp -d)
trap 'rm -rf "$D"' EXIT
JSON_JAR=${JSON_JAR:-/usr/share/java/json.jar}
cp -r web "$D/web"
mkdir -p "$D/cloud" "$D/tests/buyers"
cp -r cloud/tezgah-relay "$D/cloud/"
cp tests/buyers/sql_helper.py "$D/tests/buyers/"
echo '{"type":"module"}' > "$D/package.json"
cp tests/web_e2e/e2e.mjs "$D/e2e.mjs"
java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -d "$D/cls" $(find tests/buyers/stubs -name '*.java')
SRC=""
for n in TezgahModel TezgahSearch TezgahWizardFlow TezgahCrates TezgahPurchases TezgahPayments TezgahClosing TezgahZ TezgahWebCrypto TezgahWebProtocol TezgahWebChanges TezgahWebDto TezgahWebRepository TezgahWebPhone; do
  SRC="$SRC app/src/main/java/com/zeytindefteri/app/$n.java"
done
java -m jdk.compiler/com.sun.tools.javac.Main --release 8 -nowarn -encoding UTF-8 -cp "$D/cls:$JSON_JAR" -d "$D/cls" $SRC tests/web_e2e/E2EPhone.java
cd "$D"
CP="$D/cls:$JSON_JAR"
# Telefon saati ±5 dk, tarayıcı saati ±2 dk kayık; QR'ın her yaşında bağlanmalı.
node e2e.mjs "$CP" -300,-60,-20,-5,0,5,20,60,300 1,30,62,85 -120,0,120 connect
# Süresi dolmuş QR saat kayması ne olursa olsun reddedilmeli (relay 410).
node e2e.mjs "$CP" -300,0,300 91,600 0 reject
