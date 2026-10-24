#!/bin/sh
# Tezgah Modu: kasa darası, numara toplamları, saklama, sayı okuma, fiş metni.
set -e
cd "$(dirname "$0")/../.."
export JAVA_TOOL_OPTIONS=
D=$(mktemp -d)
java -m jdk.compiler/com.sun.tools.javac.Main -nowarn -encoding UTF-8 -d "$D" app/src/main/java/com/zeytindefteri/app/TezgahModel.java app/src/main/java/com/zeytindefteri/app/TezgahQr.java app/src/main/java/com/zeytindefteri/app/ElekSaleModel.java app/src/main/java/com/zeytindefteri/app/TezgahWizardFlow.java tests/tezgah/TezgahModelTest.java tests/tezgah/TezgahQrTest.java tests/tezgah/TezgahWizardFlowTest.java
java -cp "$D" com.zeytindefteri.app.TezgahModelTest
java -cp "$D" com.zeytindefteri.app.TezgahQrTest
java -cp "$D" com.zeytindefteri.app.TezgahWizardFlowTest
# Tüccar alımları: gerçek SQLite dosyasında (alıcı testlerinin Android stub'ları ile).
python3 - "$D" <<'P'
import pathlib,subprocess,sys
out=sys.argv[1]; stubs=[str(p) for p in pathlib.Path('tests/buyers/stubs').rglob('*.java')]
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-nowarn','-encoding','UTF-8','-d',out,*stubs,
  'app/src/main/java/com/zeytindefteri/app/TezgahModel.java','app/src/main/java/com/zeytindefteri/app/TezgahPurchases.java',
  'app/src/main/java/com/zeytindefteri/app/TezgahClosing.java',
  'app/src/main/java/com/zeytindefteri/app/TezgahZ.java',
  'app/src/main/java/com/zeytindefteri/app/TezgahSearch.java','app/src/main/java/com/zeytindefteri/app/TezgahWizardFlow.java','tests/tezgah/TezgahSearchTest.java',
  'app/src/main/java/com/zeytindefteri/app/TezgahPayments.java','app/src/main/java/com/zeytindefteri/app/TezgahBackup.java',
  'app/src/main/java/com/zeytindefteri/app/SalePayments.java','app/src/main/java/com/zeytindefteri/app/SalePayStatus.java','app/src/main/java/com/zeytindefteri/app/ElekSaleModel.java','tests/tezgah/TezgahBackupTest.java',
  'tests/tezgah/TezgahPurchasesTest.java','tests/tezgah/TezgahClosingTest.java','tests/tezgah/TezgahZTest.java'],check=True)
db=pathlib.Path(out)/'t.sqlite'
subprocess.run(['java','-cp',out,'com.zeytindefteri.app.TezgahPurchasesTest',str(db),'tests/buyers/sql_helper.py'],check=True)
subprocess.run(['java','-cp',out,'com.zeytindefteri.app.TezgahClosingTest',str(pathlib.Path(out)/'c.sqlite'),'tests/buyers/sql_helper.py'],check=True)
subprocess.run(['java','-cp',out,'com.zeytindefteri.app.TezgahSearchTest'],check=True)
subprocess.run(['java','-cp',out,'com.zeytindefteri.app.TezgahBackupTest',str(pathlib.Path(out)/'b.sqlite'),'tests/buyers/sql_helper.py'],check=True)
subprocess.run(['java','-cp',out,'com.zeytindefteri.app.TezgahZTest',str(pathlib.Path(out)/'z.sqlite'),'tests/buyers/sql_helper.py'],check=True)
P
