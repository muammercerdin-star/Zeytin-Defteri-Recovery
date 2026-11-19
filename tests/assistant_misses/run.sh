#!/bin/bash
set -e
DIR=$(cd "$(dirname "$0")" && pwd); ROOT=$(cd "$DIR/../.." && pwd); A="$ROOT/app/src/main/java/com/zeytindefteri/app"
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
javac -encoding UTF-8 -nowarn -d "$T" "$A/TurkishText.java" "$A/ElekVoiceEntry.java" "$A/AssistantMisses.java" "$DIR/AssistantMissesTest.java" "$DIR/AssistantLogTest.java" 2>&1 | grep -v "^Note:" || true
java -Dfile.encoding=UTF-8 -cp "$T" com.zeytindefteri.app.AssistantMissesTest
java -Dfile.encoding=UTF-8 -cp "$T" com.zeytindefteri.app.AssistantLogTest
python3 - "$A/ZeytinAssistantController.java" "$A/RuleBrain.java" <<'P'
import sys
c=open(sys.argv[1],encoding="utf-8").read()
rb=open(sys.argv[2],encoding="utf-8").read()   # İP3: karar sırası beyinde
checks=[("anlamadigi cumle yalniz telefonda saklanir","getSharedPreferences(\"zd_assistant_misses\"" in c),
        ("konu kartlarina dusen cumle kaydedilir","rememberMiss(raw,owner);\n                    if(data.unchanged())postHint" in c),
        ("goster/sil komutu sohbetten once","missesCommand(d);return;" in c and rb.index("AssistantMisses.asksClear(in.raw)")<rb.index("c.persona.handle(in.raw)"))]
bad=0
for n,ok in checks: print(("PASS " if ok else "FAIL ")+n); bad+=0 if ok else 1
sys.exit(1 if bad else 0)
P
