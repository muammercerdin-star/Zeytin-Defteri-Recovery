#!/bin/sh
set -e
cd "$(dirname "$0")/../.."
export JAVA_TOOL_OPTIONS=
D=$(mktemp -d); trap 'rm -rf "$D"' EXIT
M=app/src/main/java/com/zeytindefteri/app
java -m jdk.compiler/com.sun.tools.javac.Main -nowarn -encoding UTF-8 -d "$D" $M/ElekDateFix.java $M/ElekSaleModel.java tests/elek_date/ElekDateFixTest.java
java -cp "$D" com.zeytindefteri.app.ElekDateFixTest
python3 tests/elek_date/check_wiring.py
