#!/bin/sh
set -e
cd "$(dirname "$0")/../.."
D=$(mktemp -d); trap 'rm -rf "$D"' EXIT
java -m jdk.compiler/com.sun.tools.javac.Main -nowarn -encoding UTF-8 -d "$D" \
  app/src/main/java/com/zeytindefteri/app/TanecikDismiss.java tests/tanecik_dismiss/TanecikDismissTest.java
java -cp "$D" com.zeytindefteri.app.TanecikDismissTest
python3 tests/tanecik_dismiss/check_wiring.py
