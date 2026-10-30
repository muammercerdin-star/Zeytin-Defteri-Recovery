#!/bin/sh
set -e
cd "$(dirname "$0")/../.."
export JAVA_TOOL_OPTIONS=
D=$(mktemp -d)
javac -nowarn -encoding UTF-8 -d "$D" app/src/main/java/com/zeytindefteri/app/OliveVariety.java tests/variety/OliveVarietyTest.java
java -Dfile.encoding=UTF-8 -cp "$D" com.zeytindefteri.app.OliveVarietyTest
