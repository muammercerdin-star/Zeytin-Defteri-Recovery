#!/bin/bash
# Sezon satış dökümü: saf model + gerçek şemayla sorgu.
set -e
DIR=$(cd "$(dirname "$0")" && pwd); ROOT=$(cd "$DIR/../.." && pwd)
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
javac -encoding UTF-8 -d "$T" "$ROOT/app/src/main/java/com/zeytindefteri/app/SeasonSalesModel.java" "$DIR/SeasonSalesModelTest.java"
java -cp "$T" com.zeytindefteri.app.SeasonSalesModelTest
python3 "$DIR/check_query.py"
