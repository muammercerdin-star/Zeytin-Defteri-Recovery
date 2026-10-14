#!/bin/sh
# Aşama 2 ekranları: saf model testleri + kaynak/düzen bağlantı kontrolleri. Android derlemesi değildir.
set -e
cd "$(dirname "$0")/../.."
D=$(mktemp -d); J=app/src/main/java/com/zeytindefteri/app
export JAVA_TOOL_OPTIONS=
java -m jdk.compiler/com.sun.tools.javac.Main -nowarn -encoding UTF-8 -d "$D" $J/GardenDetailModel.java $J/OperationKinds.java tests/stage2/GardenDetailModelTest.java
java -cp "$D" com.zeytindefteri.app.GardenDetailModelTest
java -m jdk.compiler/com.sun.tools.javac.Main -nowarn -encoding UTF-8 -d "$D" $J/GradeFormModel.java tests/stage2/GradeFormModelTest.java
java -cp "$D" com.zeytindefteri.app.GradeFormModelTest
python3 tests/stage2/check_wiring.py
