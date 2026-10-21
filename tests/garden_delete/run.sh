#!/bin/sh
# Bahçe silme: onay adı eşleşmesi ve özet metni (veritabanı işi DatabaseHelper ile cihazda).
set -e
cd "$(dirname "$0")/../.."
export JAVA_TOOL_OPTIONS=
D=$(mktemp -d); S=$(mktemp -d); mkdir -p $S/com/zeytindefteri/app
python3 - "$S" <<'P'
import re,sys
src=open('app/src/main/java/com/zeytindefteri/app/GardenDeletion.java',encoding='utf-8').read()
parts=[re.search(r'(    public static String summary\(.*?\n    \})',src,re.S).group(1),
       re.search(r'(    public static boolean nameMatches\(.*?\n    \})',src,re.S).group(1)]
open(sys.argv[1]+'/com/zeytindefteri/app/GardenDeletion.java','w').write('package com.zeytindefteri.app;\npublic final class GardenDeletion {\n'+'\n'.join(parts)+'\n}\n')
P
java -m jdk.compiler/com.sun.tools.javac.Main -nowarn -encoding UTF-8 -d "$D" $S/com/zeytindefteri/app/GardenDeletion.java tests/garden_delete/GardenDeletionTest.java
java -cp "$D" com.zeytindefteri.app.GardenDeletionTest
