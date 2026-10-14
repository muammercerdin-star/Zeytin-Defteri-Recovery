#!/bin/sh
# Host-only: compiles the real pure classes and runs the Home league card checks.
set -e
cd "$(dirname "$0")/../.."
D=$(mktemp -d); J=app/src/main/java/com/zeytindefteri/app
export JAVA_TOOL_OPTIONS=
java -m jdk.compiler/com.sun.tools.javac.Main -nowarn -encoding UTF-8 -d "$D" $J/HomeLeagueModel.java $J/LeagueBoardModel.java $J/MarketBackendConfig.java $J/ZeytinLeagueScore.java tests/home_league/HomeLeagueModelTest.java
java -cp "$D" com.zeytindefteri.app.HomeLeagueModelTest
