#!/bin/sh
set -e
cd "$(dirname "$0")/../.."
D=$(mktemp -d); trap 'rm -rf "$D"' EXIT
J=app/src/main/java/com/zeytindefteri/app
JSON_JAR=${JSON_JAR:-/usr/share/java/json.jar}
java -m jdk.compiler/com.sun.tools.javac.Main -nowarn -encoding UTF-8 -cp "$JSON_JAR" -d "$D" \
  $J/ElekMixedEntry.java $J/CurrentPrices.java $J/ZeytinLeagueScore.java tests/elek_mixed/ElekMixedEntryTest.java
java -cp "$D:$JSON_JAR" com.zeytindefteri.app.ElekMixedEntryTest
python3 tests/elek_mixed/check_sql.py
