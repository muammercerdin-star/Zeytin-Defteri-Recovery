#!/bin/sh
set -e
cd "$(dirname "$0")/../.."
D=$(mktemp -d); J=app/src/main/java/com/zeytindefteri/app
export JAVA_TOOL_OPTIONS=
java -m jdk.compiler/com.sun.tools.javac.Main -nowarn -encoding UTF-8 -d "$D" $J/ElekSaleModel.java tests/elek_sale/ElekSaleModelTest.java
java -cp "$D" com.zeytindefteri.app.ElekSaleModelTest
python3 tests/elek_sale/check_bridge.py
