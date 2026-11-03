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
import shutil
root = Path(out)
(root / 'package.json').write_text('{"type":"module"}')
shutil.copytree('web/tezgah', root / 'web/tezgah')
(root / 'tests/web_commands').mkdir(parents=True)
shutil.copy2('tests/web_commands/flow.mjs', root / 'tests/web_commands/flow.mjs')
stubs = [p for p in Path('tests/buyers/stubs').rglob('*.java') if p.name != 'SQLiteDatabase.java']
stubs += list(Path('tests/web_commands/stubs').rglob('*.java'))
names = ['TezgahModel','TezgahSearch','TezgahWizardFlow','TezgahCrates','TezgahPurchases','TezgahPayments','TezgahClosing','TezgahZ','TezgahLedger','TezgahStatement','TezgahProducers','TezgahWebCrypto','TezgahWebProtocol','TezgahWebChanges','TezgahWebDto','TezgahWebRepository','TezgahWebPhone','TezgahWebCommands']
sources = [Path('app/src/main/java/com/zeytindefteri/app') / (name + '.java') for name in names]
sources += list(Path('tests/web_commands').glob('*.java'))
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','--release','8','-encoding','UTF-8','-cp',jar,'-d',out,*map(str,stubs + sources)],check=True)
PY
if [ -f "$D/com/zeytindefteri/app/CommandsTest.class" ]; then
java -cp "$D:$JSON_JAR" com.zeytindefteri/app/CommandsTest "$D/test.sqlite" tests/web_commands/sql_bridge.py
fi
java -cp "$D:$JSON_JAR" com.zeytindefteri.app.SharedPathsTest "$D/shared.sqlite" tests/web_commands/sql_bridge.py
node "$D/tests/web_commands/flow.mjs" "$D:$JSON_JAR"
