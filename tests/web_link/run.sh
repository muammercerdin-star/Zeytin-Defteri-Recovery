#!/bin/sh
set -e
cd "$(dirname "$0")/../.."
D=$(mktemp -d); trap 'rm -rf "$D"' EXIT
java -m jdk.compiler/com.sun.tools.javac.Main -nowarn -encoding UTF-8 -d "$D" \
  app/src/main/java/com/zeytindefteri/app/TezgahWebLink.java tests/web_link/WebLinkTest.java
java -cp "$D" com.zeytindefteri.app.WebLinkTest
python3 tests/web_link/check_flow.py
