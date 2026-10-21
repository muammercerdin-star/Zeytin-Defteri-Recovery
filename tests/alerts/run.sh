#!/bin/sh
# Telefonun ürettiği bildirimler: hava (don, hasatta yağmur, fırtına, ilaçlama), takvim, alacak, gün sonu.
set -e
cd "$(dirname "$0")/../.."
export JAVA_TOOL_OPTIONS=
D=$(mktemp -d)
java -m jdk.compiler/com.sun.tools.javac.Main -nowarn -encoding UTF-8 -d "$D" app/src/main/java/com/zeytindefteri/app/AlertRules.java tests/alerts/AlertRulesTest.java
java -Dfile.encoding=UTF-8 -cp "$D" com.zeytindefteri.app.AlertRulesTest
python3 - "$D" <<'P'
import pathlib,subprocess,sys
out=sys.argv[1]; stubs=[str(p) for p in pathlib.Path('tests/buyers/stubs').rglob('*.java')]
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-nowarn','-encoding','UTF-8','-d',out,*stubs,
  'app/src/main/java/com/zeytindefteri/app/AlertRules.java','app/src/main/java/com/zeytindefteri/app/NotificationInbox.java',
  'tests/alerts/NotificationInboxTest.java'],check=True,capture_output=True)
subprocess.run(['java','-cp',out,'com.zeytindefteri.app.NotificationInboxTest',str(pathlib.Path(out)/'n.sqlite'),'tests/buyers/sql_helper.py'],check=True)
P
