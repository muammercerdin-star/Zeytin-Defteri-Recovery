#!/bin/sh
# Tezgâh soruları (sesli/yazılı): anlama, ad eşleştirme, cevaplar.
set -e
cd "$(dirname "$0")/../.."
export JAVA_TOOL_OPTIONS=
D=$(mktemp -d)
A=app/src/main/java/com/zeytindefteri/app
python3 - "$D" <<'P'
import pathlib,subprocess,sys
out=sys.argv[1]; stubs=[str(p) for p in pathlib.Path('tests/buyers/stubs').rglob('*.java')]
A='app/src/main/java/com/zeytindefteri/app/'
src=[A+x for x in ['TezgahVoice.java','TezgahPurchases.java','TezgahPayments.java','TezgahCrates.java',
  'TezgahLedger.java','TezgahModel.java','TezgahSearch.java','TezgahWizardFlow.java','SalePayStatus.java','ElekSaleModel.java']]
subprocess.run(['java','-m','jdk.compiler/com.sun.tools.javac.Main','-nowarn','-encoding','UTF-8','-d',out,*stubs,*src,'tests/tezgah_voice/TezgahVoiceTest.java'],check=True)
subprocess.run(['java','-Dfile.encoding=UTF-8','-cp',out,'com.zeytindefteri.app.TezgahVoiceTest'],check=True)
P
