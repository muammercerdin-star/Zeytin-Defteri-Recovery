#!/bin/sh
# Zorunlu güncelleme karar kuralı + bağlantılar (ayar dosyası, manifest, uygulama kancası).
set -e
cd "$(dirname "$0")/../.."
export JAVA_TOOL_OPTIONS=
D=$(mktemp -d); S=$(mktemp -d); mkdir -p $S/com/zeytindefteri/app
# UpdateGate'in yalnız saf karar kısmı derlenir (Android sınıfları olmadan).
python3 - "$S" <<'P'
import re,sys
src=open('app/src/main/java/com/zeytindefteri/app/UpdateGate.java',encoding='utf-8').read()
body=re.search(r'(public static final int NONE.*?;)',src,re.S).group(1)
dec=re.search(r'(static int decide\(.*?\n    \})',src,re.S).group(1)
open(sys.argv[1]+'/com/zeytindefteri/app/UpdateGate.java','w').write('package com.zeytindefteri.app;\npublic final class UpdateGate {\n    '+body+'\n    '+dec+'\n}\n')
P
java -m jdk.compiler/com.sun.tools.javac.Main -nowarn -encoding UTF-8 -d "$D" $S/com/zeytindefteri/app/UpdateGate.java tests/update_gate/UpdateGateTest.java
java -cp "$D" com.zeytindefteri.app.UpdateGateTest
python3 - <<'P'
m=open('app/src/main/AndroidManifest.xml',encoding='utf-8').read()
a=open('app/src/main/java/ZeytinApplication.java',encoding='utf-8').read()
g=open('app/src/main/java/com/zeytindefteri/app/UpdateGate.java',encoding='utf-8').read()
assert '.UpdateRequiredActivity' in m and 'AD_ID" tools:node="remove"' in m
assert 'UpdateGate.onResume(activity)' in a
assert 'zeytin-defteri/app-config.json' in g and 'instanceof UpdateRequiredActivity' in g
print('PASS bağlantılar: manifest, uygulama kancası, ayar adresi, reklam kimliği kaldırıldı')
P
