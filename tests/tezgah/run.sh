#!/bin/sh
# Tezgah Modu: kasa darası, numara toplamları, saklama, sayı okuma, fiş metni.
set -e
cd "$(dirname "$0")/../.."
export JAVA_TOOL_OPTIONS=
D=$(mktemp -d)
java -m jdk.compiler/com.sun.tools.javac.Main -nowarn -encoding UTF-8 -d "$D" app/src/main/java/com/zeytindefteri/app/TezgahModel.java tests/tezgah/TezgahModelTest.java
java -cp "$D" com.zeytindefteri.app.TezgahModelTest
# Tüccar alımları: gerçek SQLite dosyasında (alıcı testlerinin Android stub'ları ile).
python3 - "$D" <<'P'
import pathlib,subprocess,sys
out=sys.argv[1]; stubs=[str(p) for p in pathlib.Path('tests/buyers/stubs').rglob('*.java')]
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-nowarn','-encoding','UTF-8','-d',out,*stubs,
  'app/src/main/java/com/zeytindefteri/app/TezgahModel.java','app/src/main/java/com/zeytindefteri/app/TezgahPurchases.java',
  'tests/tezgah/TezgahPurchasesTest.java'],check=True)
db=pathlib.Path(out)/'t.sqlite'
subprocess.run(['java','-cp',out,'com.zeytindefteri.app.TezgahPurchasesTest',str(db),'tests/buyers/sql_helper.py'],check=True)
P
