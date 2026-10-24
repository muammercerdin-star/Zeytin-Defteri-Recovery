#!/bin/sh
# Yağhane sıkım defteri: "kaç kilodan", yağhane karşılaştırması, ücret, kayıt.
set -e
cd "$(dirname "$0")/../.."
export JAVA_TOOL_OPTIONS=
D=$(mktemp -d)
python3 - "$D" <<'P'
import pathlib,subprocess,sys
out=sys.argv[1]; stubs=[str(p) for p in pathlib.Path('tests/buyers/stubs').rglob('*.java')]
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-nowarn','-encoding','UTF-8','-d',out,*stubs,
  'app/src/main/java/com/zeytindefteri/app/OilPressings.java','app/src/main/java/com/zeytindefteri/app/TezgahSearch.java',
  'app/src/main/java/com/zeytindefteri/app/TezgahWizardFlow.java','app/src/main/java/com/zeytindefteri/app/TezgahModel.java',
  'app/src/main/java/com/zeytindefteri/app/TezgahPurchases.java','app/src/main/java/com/zeytindefteri/app/TezgahPayments.java','tests/oil/OilPressingsTest.java'],check=True)
subprocess.run(['java','-cp',out,'com.zeytindefteri.app.OilPressingsTest',str(pathlib.Path(out)/'o.sqlite'),'tests/buyers/sql_helper.py'],check=True)
P
