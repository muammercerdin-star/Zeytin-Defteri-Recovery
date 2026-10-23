#!/bin/sh
# Satış tahsilatı: durum etiketleri, söz verilen gün, ödeme kaydı ve Alacaklarım özeti.
set -e
cd "$(dirname "$0")/../.."
export JAVA_TOOL_OPTIONS=
D=$(mktemp -d)
java -m jdk.compiler/com.sun.tools.javac.Main -nowarn -encoding UTF-8 -d "$D" app/src/main/java/com/zeytindefteri/app/SalePayStatus.java app/src/main/java/com/zeytindefteri/app/ElekSaleModel.java tests/payments/SalePayStatusTest.java
java -cp "$D" com.zeytindefteri.app.SalePayStatusTest
python3 - "$D" <<'P'
import pathlib,subprocess,sys
out=sys.argv[1]; stubs=[str(p) for p in pathlib.Path('tests/buyers/stubs').rglob('*.java')]
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-nowarn','-encoding','UTF-8','-d',out,*stubs,
  'app/src/main/java/com/zeytindefteri/app/SalePayStatus.java','app/src/main/java/com/zeytindefteri/app/ElekSaleModel.java',
  'app/src/main/java/com/zeytindefteri/app/SalePayments.java','tests/payments/SalePaymentsTest.java'],check=True)
subprocess.run(['java','-cp',out,'com.zeytindefteri.app.SalePaymentsTest',str(pathlib.Path(out)/'p.sqlite'),'tests/buyers/sql_helper.py'],check=True)
P
