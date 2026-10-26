#!/bin/sh
# Defterden oku: el yazısı elek satırlarının çözümlenmesi ve hesap kontrolü.
set -e
cd "$(dirname "$0")/../.."
export JAVA_TOOL_OPTIONS=
D=$(mktemp -d)
java -m jdk.compiler/com.sun.tools.javac.Main -nowarn -encoding UTF-8 -d "$D" \
  app/src/main/java/com/zeytindefteri/app/NotebookOcr.java tests/notebook/NotebookOcrTest.java
java -cp "$D" com.zeytindefteri.app.NotebookOcrTest
