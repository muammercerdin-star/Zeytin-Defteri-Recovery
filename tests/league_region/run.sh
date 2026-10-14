#!/bin/sh
set -e
cd "$(dirname "$0")/../.."
P=app/src/main/java/com/zeytindefteri/app
D=$(mktemp -d); trap 'rm -rf "$D"' EXIT
javac -nowarn -encoding UTF-8 -d "$D" $P/RegionGradePrice.java $P/MarketBackendConfig.java tests/league_region/RegionGradePriceTest.java
java -cp "$D" com.zeytindefteri.app.RegionGradePriceTest
python3 tests/league_region/check_wiring.py
