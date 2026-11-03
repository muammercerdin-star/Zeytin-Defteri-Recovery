#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
D=$(mktemp -d)
trap 'rm -rf "$D"' EXIT
JSON_JAR=${JSON_JAR:-/usr/share/java/json.jar}
python3 - "$D" "$JSON_JAR" <<'PY'
from pathlib import Path
import subprocess
import sys
out, jar = sys.argv[1:]
# Node 20'de de ES module davranışı: yalnız geçici dizinde type=module.
# Üretim dosyaları değiştirilmeden aynen kopyalanır.
import shutil
root = Path(out)
(root / 'package.json').write_text('{"type":"module"}')
shutil.copytree('web/tezgah', root / 'web/tezgah')
(root / 'tests/web_page').mkdir(parents=True)
for name in ['test.mjs', 'flow.mjs', 'operations.mjs']:
    shutil.copy2('tests/web_page/' + name, root / 'tests/web_page' / name)
stubs = list(Path('tests/buyers/stubs').rglob('*.java'))
subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-encoding', 'UTF-8', '-d', out, *map(str, stubs)], check=True)
names = ['TezgahModel', 'TezgahSearch', 'TezgahWizardFlow', 'TezgahCrates', 'TezgahPurchases', 'TezgahPayments', 'TezgahClosing', 'TezgahZ', 'TezgahWebCrypto', 'TezgahWebProtocol', 'TezgahWebChanges', 'TezgahWebDto', 'TezgahWebRepository', 'TezgahWebPhone']
src = [f'app/src/main/java/com/zeytindefteri/app/{name}.java' for name in names]
src.append('tests/web_phone/PhoneBridge.java')
subprocess.run(['java', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '--release', '8', '-encoding', 'UTF-8', '-cp', out + ':' + jar, '-d', out, *src], check=True)
PY
node "$D/tests/web_page/test.mjs"
node "$D/tests/web_page/operations.mjs"
node "$D/tests/web_page/flow.mjs" "$D:$JSON_JAR"
