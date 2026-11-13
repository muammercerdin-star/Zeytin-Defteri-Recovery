#!/bin/sh
# Alıcıdan toplu tahsilat: dağıtım (en eskiden), gerçek SQLite ile kayıt, ekran bağlantısı.
set -e
cd "$(dirname "$0")/../.."
export JAVA_TOOL_OPTIONS=
D=$(mktemp -d); trap 'rm -rf "$D"' EXIT
M=app/src/main/java/com/zeytindefteri/app
java -m jdk.compiler/com.sun.tools.javac.Main -nowarn -encoding UTF-8 -d "$D" $M/BuyerPayPlan.java $M/ElekSaleModel.java tests/buyer_pay/BuyerPayPlanTest.java
java -cp "$D" com.zeytindefteri.app.BuyerPayPlanTest
python3 - "$D" <<'P'
import pathlib,subprocess,sys
out=sys.argv[1]; stubs=[str(p) for p in pathlib.Path('tests/buyers/stubs').rglob('*.java')]
M='app/src/main/java/com/zeytindefteri/app/'
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-nowarn','-encoding','UTF-8','-d',out,*stubs,
  M+'SalePayStatus.java',M+'ElekSaleModel.java',M+'SalePayments.java',M+'BuyerPayPlan.java',M+'BuyerDirectory.java',M+'BuyerFairCompare.java',
  'tests/buyer_pay/BuyerPayDbTest.java'],check=True)
subprocess.run(['java','-cp',out,'com.zeytindefteri.app.BuyerPayDbTest',str(pathlib.Path(out)/'b.sqlite'),'tests/buyers/sql_helper.py'],check=True)
P
python3 tests/buyer_pay/check_wiring.py
