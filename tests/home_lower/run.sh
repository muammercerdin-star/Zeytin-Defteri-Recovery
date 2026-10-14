#!/bin/sh
set -e
cd "$(dirname "$0")/../.."
D=$(mktemp -d); J=app/src/main/java/com/zeytindefteri/app
export JAVA_TOOL_OPTIONS=
java -m jdk.compiler/com.sun.tools.javac.Main -nowarn -encoding UTF-8 -d "$D" $J/HomeSummaryModel.java $J/OperationKinds.java tests/home_lower/HomeSummaryModelTest.java
java -cp "$D" com.zeytindefteri.app.HomeSummaryModelTest
python3 tests/home_lower/check_wiring.py
