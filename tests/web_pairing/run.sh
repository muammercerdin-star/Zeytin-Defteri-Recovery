#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
D=$(mktemp -d)
trap 'rm -rf "$D"' EXIT
JSON_JAR=${JSON_JAR:-/usr/share/java/json.jar}
ZXING_JAR=${ZXING_JAR:-/usr/share/java/core.jar}
if [ ! -f "$ZXING_JAR" ]; then
    echo 'Çalıştırılamadı: ZXING_JAR bağımsız ZXing core 3.5.3 JAR dosyasını göstermeli.' >&2
    exit 2
fi
python3 - "$D" "$JSON_JAR" "$ZXING_JAR" <<'PY'
from pathlib import Path
import shutil, subprocess, sys
out, jar, zxing = sys.argv[1:]
p = Path(out)
(p/'package.json').write_text('{"type":"module"}')
shutil.copytree('web',p/'web')
shutil.copytree('tests/web_pairing',p/'tests/web_pairing')
stubs=list(Path('tests/buyers/stubs').rglob('*.java'))
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-d',out,*map(str,stubs)],check=True)
names=['TezgahModel','TezgahSearch','TezgahWizardFlow','TezgahCrates','TezgahPurchases','TezgahPayments','TezgahClosing','TezgahZ','TezgahWebCrypto','TezgahWebProtocol','TezgahWebChanges','TezgahWebDto','TezgahWebRepository','TezgahWebPhone']
sources=[f'app/src/main/java/com/zeytindefteri/app/{n}.java' for n in names]
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','--release','8','-encoding','UTF-8','-cp',out+':'+jar+':'+zxing,'-d',out,*sources,'tests/web_phone/PhoneBridge.java','tests/web_pairing/Decode.java'],check=True)
PY
node "$D/tests/web_pairing/test.mjs" "$D:$JSON_JAR" "$D/qr.json"
java -cp "$D:$JSON_JAR:$ZXING_JAR" Decode "$D/qr.json"
