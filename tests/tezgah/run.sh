#!/bin/sh
# Tezgah Modu: kasa darası, numara toplamları, saklama, sayı okuma, fiş metni.
set -e
cd "$(dirname "$0")/../.."
export JAVA_TOOL_OPTIONS=
D=$(mktemp -d)
java -m jdk.compiler/com.sun.tools.javac.Main -nowarn -encoding UTF-8 -d "$D" app/src/main/java/com/zeytindefteri/app/TezgahModel.java app/src/main/java/com/zeytindefteri/app/TezgahQr.java app/src/main/java/com/zeytindefteri/app/ElekSaleModel.java tests/tezgah/TezgahModelTest.java tests/tezgah/TezgahQrTest.java
java -cp "$D" com.zeytindefteri.app.TezgahModelTest
java -cp "$D" com.zeytindefteri.app.TezgahQrTest
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
# Excel (.xlsx) şablonu: zip yapısı, başlık, formüller, kaçış.
python3 - "$D" <<'P'
import pathlib,subprocess,sys,zipfile,tempfile
out=sys.argv[1]; stubs=[str(p) for p in pathlib.Path('tests/buyers/stubs').rglob('*.java')]
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-nowarn','-encoding','UTF-8','-d',out,*stubs,
  'app/src/main/java/com/zeytindefteri/app/TezgahModel.java','app/src/main/java/com/zeytindefteri/app/TezgahPurchases.java',
  'app/src/main/java/com/zeytindefteri/app/TezgahXlsx.java','tests/tezgah/TezgahXlsxTest.java'],check=True,capture_output=True)
t=pathlib.Path(out)/'t.xlsx'; e=pathlib.Path(out)/'e.xlsx'
subprocess.run(['java','-cp',out,'com.zeytindefteri.app.TezgahXlsxTest',str(t),str(e)],check=True,capture_output=True)
n=0
for f in (t,e):
    z=zipfile.ZipFile(f)
    names=set(z.namelist())
    assert {'[Content_Types].xml','_rels/.rels','xl/workbook.xml','xl/styles.xml','xl/worksheets/sheet1.xml'}<=names; n+=1
    import xml.dom.minidom
    for nm in names: xml.dom.minidom.parseString(z.read(nm)); n+=1
s=zipfile.ZipFile(t).read('xl/worksheets/sheet1.xml').decode()
assert 'SUM(K3:K6)' in s and 'IF(K3&gt;0' not in s and 'IF(K3>0,M3/K3,0)' in s, 'formüller'; n+=1
assert 'Adsız &lt;&amp;&gt; &quot;test&quot;' in s, 'XML kaçışı'; n+=1
assert 'state="frozen"' in s and 'autoFilter ref="A2:O6"' in s, 'dondurma ve filtre'; n+=1
assert 'Alım defteri · 20 Ekim 2026' in s, 'başlık'; n+=1
try:
    import openpyxl
    ws=openpyxl.load_workbook(t).active
    assert ws['B7'].value=='4 alım' and ws['M7'].value=='=SUM(M3:M6)'; n+=1
except ImportError: pass
print('PASS: %d Excel şablonu kontrolü'%n)
P
