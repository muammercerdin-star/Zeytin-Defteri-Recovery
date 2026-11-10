#!/bin/sh
set -e
cd "$(dirname "$0")/../.."
D=$(mktemp -d); trap 'rm -rf "$D"' EXIT
J=app/src/main/java/com/zeytindefteri/app
java -m jdk.compiler/com.sun.tools.javac.Main -nowarn -encoding UTF-8 -d "$D" \
  $J/ReceiptPrint.java tests/receipt_print/ReceiptPrintTest.java
java -cp "$D" com.zeytindefteri.app.ReceiptPrintTest
python3 tests/receipt_print/check_wiring.py
