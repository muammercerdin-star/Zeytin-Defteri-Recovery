#!/bin/sh
set -eu
cd "$(dirname "$0")/../.."
D=$(mktemp -d)
trap 'rm -rf "$D"' EXIT
JSON_JAR=${JSON_JAR:-/usr/share/java/json.jar}
python3 - "$D" "$JSON_JAR" <<'PY'
from pathlib import Path
import shutil, subprocess, sys
out, jar = sys.argv[1:]
p=Path(out)
(p/'package.json').write_text('{"type":"module"}')
shutil.copytree('web/tezgah',p/'web/tezgah')
shutil.copytree('tests/web_devices',p/'tests/web_devices')
files=['TezgahWebProtocol','TezgahWebCrypto','TezgahWebDeviceCrypto','TezgahWebDeviceHandshake']
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','--release','8','-encoding','UTF-8','-cp',jar,'-d',out,*['app/src/main/java/com/zeytindefteri/app/'+f+'.java' for f in files],'tests/web_devices/DeviceBridge.java'],check=True)
PY
node "$D/tests/web_devices/test.mjs" "$D:$JSON_JAR"
