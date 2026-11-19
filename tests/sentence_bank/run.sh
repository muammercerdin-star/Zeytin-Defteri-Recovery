#!/bin/bash
# Cümle bankası (İP1): cumleler.tsv'deki her cümle uygulamanın gerçek çözücülerinden geçer.
# "dogru" bir cümle bozulursa test KALIR; "beklenen_hata" bir cümle düzelirse UYARI yazar.
# Masaüstü (JVM) testidir; Android derlemesi ya da cihaz testi değildir.
set -e
DIR=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$DIR/../.." && pwd)
A="$ROOT/app/src/main/java/com/zeytindefteri/app"
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
mkdir -p "$T/src"
# ZeytinAssistantPeriod yalnız ZeytinAssistantData.date'i kullanır: Android'e bağlı veri sınıfı yerine küçük taklit.
cat > "$T/src/ZeytinAssistantData.java" <<'J'
package com.zeytindefteri.app;
public final class ZeytinAssistantData { public static Long date(String v){ try{return java.time.LocalDate.parse(v).toEpochDay();}catch(Exception e){return null;} } }
J
javac -encoding UTF-8 -nowarn -d "$T/out" \
  "$A/TurkishText.java" "$A/ElekVoiceEntry.java" "$A/ElekVoiceDialog.java" "$A/ElekVoiceBuyer.java" \
  "$A/OperationVoiceEntry.java" "$A/OperationKinds.java" "$A/HarvestVoiceEntry.java" "$A/VoiceRecordDialog.java" \
  "$A/ZeytinAssistantCommand.java" "$A/ZeytinAssistantPeriod.java" "$A/ZeytinAssistantText.java" "$A/ZeytinAssistantHints.java" \
  "$A/AssistantReceivables.java" "$A/AssistantKnowledge.java" "$A/AssistantPersona.java" \
  "$A/AssistantMisses.java" "$A/AssistantScorecard.java" \
  "$T/src/ZeytinAssistantData.java" "$DIR/SentenceBankRunner.java" 2>&1 | grep -v "^Note:" || true
if [ "$1" = "--dump" ]; then
  java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$T/out" com.zeytindefteri.app.SentenceBankRunner --dump
  exit 0
fi
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$T/out" com.zeytindefteri.app.SentenceBankRunner "$DIR/cumleler.tsv"
