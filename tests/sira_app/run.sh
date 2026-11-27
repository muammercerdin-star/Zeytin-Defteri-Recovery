#!/bin/bash
set -e
DIR=$(cd "$(dirname "$0")" && pwd); ROOT=$(cd "$DIR/../.." && pwd); A="$ROOT/app/src/main/java/com/zeytindefteri/app"
JSON_JAR=${JSON_JAR:-/usr/share/java/json.jar}
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
# SiraBulut.java saf JDK + org.json: ağ testi yapılamaz (sunucuya erişim yok) ama derlenerek tip/sözdizimi denetlenir.
javac -encoding UTF-8 -nowarn -cp "$JSON_JAR" -d "$T" \
  "$A/SiraDurumu.java" "$A/SiraDefteri.java" "$A/SiraBildirim.java" "$A/SiraBulut.java" "$A/SiraYoklama.java" \
  "$DIR/SiraDurumuTest.java" "$DIR/SiraDefteriTest.java" "$DIR/SiraBildirimTest.java" "$DIR/SiraYoklamaTest.java" 2>&1 | grep -v "^Note:" || true
java -Dfile.encoding=UTF-8 -cp "$T:$JSON_JAR" com.zeytindefteri.app.SiraDurumuTest
java -Dfile.encoding=UTF-8 -cp "$T:$JSON_JAR" com.zeytindefteri.app.SiraDefteriTest
java -Dfile.encoding=UTF-8 -cp "$T:$JSON_JAR" com.zeytindefteri.app.SiraBildirimTest
java -Dfile.encoding=UTF-8 -cp "$T:$JSON_JAR" com.zeytindefteri.app.SiraYoklamaTest
