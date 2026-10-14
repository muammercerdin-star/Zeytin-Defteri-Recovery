#!/bin/sh
set -e
cd "$(dirname "$0")/../.."
P=app/src/main/java/com/zeytindefteri/app
D=$(mktemp -d); trap 'rm -rf "$D"' EXIT
javac -nowarn -encoding UTF-8 -d "$D" $P/MeydanMarketStrip.java $P/RegionGradePrice.java $P/MarketBackendConfig.java tests/meydan_redesign/MeydanMarketStripTest.java
java -cp "$D" com.zeytindefteri.app.MeydanMarketStripTest
python3 tests/meydan_redesign/check_wiring.py
