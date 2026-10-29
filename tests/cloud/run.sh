#!/bin/sh
# Bulut yedeği: sıkıştırma, parçalama, değişiklik özeti.
set -e
cd "$(dirname "$0")/../.."
export JAVA_TOOL_OPTIONS=
D=$(mktemp -d)
python3 - "$D" <<'P'
import pathlib,subprocess,sys
out=sys.argv[1]; stubs=[str(p) for p in pathlib.Path('tests/buyers/stubs').rglob('*.java')]
A='app/src/main/java/com/zeytindefteri/app/'
src=[A+x for x in ['TezgahCloudPack.java','TezgahBackup.java','TezgahZ.java','TezgahPurchases.java','TezgahPayments.java','TezgahClosing.java',
  'TezgahModel.java','TezgahCrates.java','TezgahLedger.java','TezgahSearch.java','TezgahWizardFlow.java','SalePayments.java','SalePayStatus.java',
  'ElekSaleModel.java','OilPressings.java','TezgahQr.java']]
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-nowarn','-encoding','UTF-8','-d',out,*stubs,*src,'tests/cloud/TezgahCloudPackTest.java'],check=True)
subprocess.run(['java','-Dfile.encoding=UTF-8','-cp',out,'com.zeytindefteri.app.TezgahCloudPackTest'],check=True)
P
# Eşitleme turu: yazmalar bitmeden "tamam" denmez.
java -m jdk.compiler/com.sun.tools.javac.Main -nowarn -encoding UTF-8 -d "$D" app/src/main/java/com/zeytindefteri/app/CloudWriteBatch.java tests/cloud/CloudWriteBatchTest.java
java -cp "$D" com.zeytindefteri.app.CloudWriteBatchTest
