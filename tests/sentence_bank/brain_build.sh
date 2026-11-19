#!/bin/bash
# Beyin (RuleBrain) ve çözücülerini masaüstü JVM için derler: cümle bankası, birebir aynılık ve onay testleri
# aynı listeyi kullanır. Kullanım: source brain_build.sh; brain_build <çıktı-dizini> [ek .java dosyaları...]
# İnternet yardımcısının komut sözleri (AssistantCloud) org.json ister: JSON_JAR (Termux'ta json.jar).
brain_build() {
  local out=$1
  shift
  local here
  here=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
  local root
  root=$(cd "$here/../.." && pwd)
  local A="$root/app/src/main/java/com/zeytindefteri/app"
  JSON_JAR=${JSON_JAR:-/usr/share/java/json.jar}
  mkdir -p "$out/src" "$out/classes"
  # ZeytinAssistantPeriod yalnız ZeytinAssistantData.date'i kullanır: Android'e bağlı veri sınıfı yerine küçük taklit.
  cat > "$out/src/ZeytinAssistantData.java" <<'J'
package com.zeytindefteri.app;
public final class ZeytinAssistantData { public static Long date(String v){ try{return java.time.LocalDate.parse(v).toEpochDay();}catch(Exception e){return null;} } }
J
  javac -encoding UTF-8 -nowarn -cp "$JSON_JAR" -d "$out/classes" \
    "$A/TurkishText.java" "$A/ElekVoiceEntry.java" "$A/ElekVoiceDialog.java" "$A/ElekVoiceBuyer.java" \
    "$A/OperationVoiceEntry.java" "$A/OperationKinds.java" "$A/HarvestVoiceEntry.java" "$A/VoiceRecordDialog.java" \
    "$A/ZeytinAssistantCommand.java" "$A/ZeytinAssistantPeriod.java" "$A/ZeytinAssistantText.java" "$A/ZeytinAssistantHints.java" \
    "$A/AssistantReceivables.java" "$A/AssistantKnowledge.java" "$A/AssistantPersona.java" \
    "$A/AssistantMisses.java" "$A/AssistantScorecard.java" "$A/AssistantCloud.java" \
    "$A/ActionCatalog.java" "$A/Decision.java" "$A/Brain.java" "$A/BrainContext.java" "$A/RuleBrain.java" \
    "$A/ElekRules.java" "$A/CloudBrain.java" "$A/LearnedTerms.java" "$A/LearnedBrain.java" \
    "$out/src/ZeytinAssistantData.java" "$@" 2>&1 | grep -v "^Note:"
  BRAIN_CP="$out/classes:$JSON_JAR"
}
