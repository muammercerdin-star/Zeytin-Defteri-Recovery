#!/bin/sh
# Aşama 3 ekranları: saf model testleri + kaynak/düzen bağlantı kontrolleri. Android derlemesi değildir.
set -e
cd "$(dirname "$0")/../.."
D=$(mktemp -d); J=app/src/main/java/com/zeytindefteri/app
export JAVA_TOOL_OPTIONS=
java -m jdk.compiler/com.sun.tools.javac.Main -nowarn -encoding UTF-8 -d "$D" $J/GardenFormModel.java tests/stage3/GardenFormModelTest.java
java -cp "$D" com.zeytindefteri.app.GardenFormModelTest
python3 tests/stage3/check_wiring.py
