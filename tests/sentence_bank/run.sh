#!/bin/bash
# Cümle bankası: cumleler.tsv'deki her cümle uygulamanın beynine (RuleBrain) verilir.
# "dogru" bir cümle bozulursa test KALIR; "beklenen_hata" bir cümle düzelirse UYARI yazar.
# Masaüstü (JVM) testidir; Android derlemesi ya da cihaz testi değildir.
set -e
DIR=$(cd "$(dirname "$0")" && pwd)
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
source "$DIR/brain_build.sh"
brain_build "$T" "$DIR/SentenceBankRunner.java" "$DIR/ScenarioRunner.java"
if [ "$1" = "--dump" ]; then
  java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$BRAIN_CP" com.zeytindefteri.app.SentenceBankRunner --dump
  exit 0
fi
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$BRAIN_CP" com.zeytindefteri.app.SentenceBankRunner "$DIR/cumleler.tsv"
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$BRAIN_CP" com.zeytindefteri.app.ScenarioRunner "$DIR/senaryolar.tsv"
