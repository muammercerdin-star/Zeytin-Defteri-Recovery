#!/bin/sh
set -e
cd "$(dirname "$0")/../.."
D=$(mktemp -d); trap 'rm -rf "$D"' EXIT
java -m jdk.compiler/com.sun.tools.javac.Main -nowarn -encoding UTF-8 -d "$D" \
  app/src/main/java/com/zeytindefteri/app/LeagueNameAsk.java tests/league_name_ask/LeagueNameAskTest.java
java -cp "$D" com.zeytindefteri.app.LeagueNameAskTest
python3 tests/league_name_ask/check_wiring.py
