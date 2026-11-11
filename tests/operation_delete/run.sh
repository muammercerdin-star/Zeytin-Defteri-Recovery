#!/bin/sh
set -e
cd "$(dirname "$0")/../.."
D=$(mktemp -d); trap 'rm -rf "$D"' EXIT
java -m jdk.compiler/com.sun.tools.javac.Main -nowarn -encoding UTF-8 -d "$D" \
  app/src/main/java/com/zeytindefteri/app/OperationDeleteText.java \
  app/src/main/java/com/zeytindefteri/app/OperationHistoryModel.java \
  app/src/main/java/com/zeytindefteri/app/OperationKinds.java \
  app/src/main/java/com/zeytindefteri/app/GardenDetailModel.java \
  tests/operation_delete/OperationDeleteTextTest.java
java -cp "$D" com.zeytindefteri.app.OperationDeleteTextTest
python3 tests/operation_delete/check_wiring.py
