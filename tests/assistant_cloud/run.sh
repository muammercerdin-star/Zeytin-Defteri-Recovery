#!/bin/bash
set -e
DIR=$(cd "$(dirname "$0")" && pwd); ROOT=$(cd "$DIR/../.." && pwd); A="$ROOT/app/src/main/java/com/zeytindefteri/app"
JSON_JAR=${JSON_JAR:-/usr/share/java/json.jar}
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
javac -encoding UTF-8 -nowarn -cp "$JSON_JAR" -d "$T" "$A/ElekVoiceEntry.java" "$A/ElekVoiceDialog.java" "$A/ElekVoiceBuyer.java" "$A/OperationKinds.java" "$A/OperationVoiceEntry.java" "$A/AssistantCloud.java" "$DIR/AssistantCloudTest.java" 2>&1 | grep -v "^Note:" || true
java -Dfile.encoding=UTF-8 -cp "$T:$JSON_JAR" com.zeytindefteri.app.AssistantCloudTest
python3 - "$A" "$ROOT" <<'P'
import sys, os, re
A, ROOT = sys.argv[1], sys.argv[2]
c = open(os.path.join(A, "ZeytinAssistantController.java"), encoding="utf-8").read()
cl = open(os.path.join(A, "AssistantCloudClient.java"), encoding="utf-8").read()
g = open(os.path.join(ROOT, "app", "build.gradle"), encoding="utf-8").read()
i_cloud = c.index("AssistantCloudClient.ask(raw,names)")
checks = [
    ("kurallar once: elek/islem/hasat yollarindan sonra", c.index("final OperationVoiceEntry op=") < i_cloud and c.index("ElekVoiceEntry.parse(raw)") < i_cloud),
    ("cevap denetlenir (accept)", "AssistantCloud.accept(AssistantCloudClient.ask(raw,names),raw,names)" in c),
    ("sonuc okunur, evet beklenir (OperationVoiceDialog)", "opDialog=new OperationVoiceDialog(cloud.entry,names,cloud.garden)" in c),
    ("ilk seferde izin sorulur", "pendingCloud=raw;postDialog(new OperationVoiceDialog.Reply(AssistantCloud.ASK" in c),
    ("kapaliyken ve internetsiz sorulmaz", "!\"off\".equals(mode)&&AssistantCloudClient.online(activity)" in c),
    ("uygulamada anahtar yok (yalniz Firebase belgesi)", "Bearer \" + token" in cl and not re.search(r"(sk-|api[_-]?key\s*=)", cl, re.I)),
    ("adres derleme ayarindan (repoda degil)", "zdBuildString('ZEYTIN_AI_URL')" in g),
    ("duzeltmeler modele gitmez (opDialog once)", c.index("if(opDialog!=null&&opDialog.step()!=OperationVoiceDialog.Step.DONE){answerDialog(raw);return;}") < c.index("handleCloudCommand(raw))return;")),
]
bad = 0
for n, ok in checks:
    print(("PASS " if ok else "FAIL ") + n); bad += 0 if ok else 1
sys.exit(1 if bad else 0)
P
