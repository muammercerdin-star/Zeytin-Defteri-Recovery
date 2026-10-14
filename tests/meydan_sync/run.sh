#!/bin/sh
set -e
cd "$(dirname "$0")/../.."
P=app/src/main/java/com/zeytindefteri/app
D=$(mktemp -d); trap 'rm -rf "$D"' EXIT
javac -nowarn -encoding UTF-8 -d "$D" $P/MeydanPostSync.java tests/meydan_sync/MeydanPostSyncTest.java
java -cp "$D" com.zeytindefteri.app.MeydanPostSyncTest
python3 tests/meydan_sync/check_sql.py
python3 tests/meydan_sync/check_wiring.py
