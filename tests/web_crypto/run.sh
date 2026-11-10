#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
D=$(mktemp -d)
trap 'rm -rf "$D"' EXIT
python3 - "$D/vectors.properties" <<'PY'
import json,sys
v=json.load(open('tests/web_crypto/vectors.json'))
# Java Properties için sadece test girdilerini düzleştir; UTF-8 Reader kullanılır.
def flatten(o,p=''):
 if isinstance(o,dict):
  for k,v in o.items():yield from flatten(v,p+k+'.')
 elif isinstance(o,list):
  for k,v in enumerate(o):yield from flatten(v,p+str(k)+'.')
 else:yield p[:-1],str(o)
with open(sys.argv[1],'w') as f:
 for k,v in flatten(v):f.write(k+'='+v.replace('\\','\\\\').replace('\n','\\n').replace('\r','\\r')+'\n')
PY
# --release 8 API uyumunu kontrol eder; Android bootclasspath/cihaz testi değildir.
java -m jdk.compiler/com.sun.tools.javac.Main --release 8 -encoding UTF-8 -d "$D" app/src/main/java/com/zeytindefteri/app/TezgahWebCrypto.java app/src/main/java/com/zeytindefteri/app/TezgahWebProtocol.java tests/web_crypto/TezgahWebCryptoTest.java
java -cp "$D" com.zeytindefteri.app.TezgahWebCryptoTest "$D/vectors.properties" "$D/java.frame"
node tests/web_crypto/test.mjs "$D/java.frame" "$D/js.frame"
java -cp "$D" com.zeytindefteri.app.TezgahWebCryptoTest "$D/vectors.properties" "$D/java.frame" "$D/js.frame"
