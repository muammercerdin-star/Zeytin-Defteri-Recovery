#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
D=$(mktemp -d);trap 'rm -rf "$D"' EXIT
JSON_JAR=${JSON_JAR:-/usr/share/java/json.jar}
python3 - "$D" "$JSON_JAR" <<'PY'
from pathlib import Path
import subprocess,sys
out,jar=sys.argv[1:]
stubs=list(Path('tests/buyers/stubs').rglob('*.java'))
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-encoding','UTF-8','-d',out,*map(str,stubs)],check=True)
names=['TezgahModel','TezgahSearch','TezgahWizardFlow','TezgahCrates','TezgahPurchases','TezgahPayments','TezgahClosing','TezgahZ','TezgahWebCrypto','TezgahWebProtocol','TezgahWebChanges','TezgahWebDto','TezgahWebRepository','TezgahWebPhone']
src=[f'app/src/main/java/com/zeytindefteri/app/{x}.java' for x in names]+['tests/web_phone/PhoneTest.java','tests/web_phone/PhoneBridge.java']
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','--release','8','-encoding','UTF-8','-cp',out+':'+jar,'-d',out,*src],check=True)
PY
java -cp "$D:$JSON_JAR" com.zeytindefteri.app.PhoneTest "$D/phone.sqlite" tests/buyers/sql_helper.py
node tests/web_phone/flow.mjs "$D:$JSON_JAR"
python3 tests/web_phone/check_wiring.py
