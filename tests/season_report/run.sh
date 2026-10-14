#!/bin/sh
set -e
cd "$(dirname "$0")/../.."
D=$(mktemp -d); J=app/src/main/java/com/zeytindefteri/app
export JAVA_TOOL_OPTIONS=
java -m jdk.compiler/com.sun.tools.javac.Main -nowarn -encoding UTF-8 -d "$D" $J/SeasonReportModel.java tests/season_report/SeasonReportModelTest.java
java -cp "$D" com.zeytindefteri.app.SeasonReportModelTest
