#!/bin/bash
# Ortak döngü sunucu SQL'i (server/ortak-dongu) gerçek PostgreSQL'de, geçici veritabanında.
# ZD_PG="-h /var/tmp/zdpg -p 55432 -U postgres". CANLIDA ÇALIŞTIRILMAZ.
set -e
set -o pipefail
cd "$(dirname "$0")/../.."
ROOT=$(pwd)
PG=${ZD_PG:-"-h /var/tmp/zdpg -p 55432 -U postgres"}
# JSON kütüphanesi: diğer testler gibi (brain_build.sh) Termux / Debian varsayılan yolu.
JSON_JAR=${JSON_JAR:-/usr/share/java/json.jar}
export JSON_JAR
S=server/ortak-dongu
DB=zd_ortak_test_$$
psql $PG -q -c "create database $DB" >/dev/null
cleanup() {
  psql $PG -q -c "drop database if exists $DB" >/dev/null
  psql $PG -q -c "drop database if exists ${DB}_b" >/dev/null
  psql $PG -q -c "drop role if exists zd_katki_yazar" >/dev/null 2>&1 || true
  psql $PG -q -c "drop role if exists zd_paket_yonetici" >/dev/null 2>&1 || true
}
trap cleanup EXIT
q() { psql $PG -d $DB -q -v ON_ERROR_STOP=1 "$@"; }
# Roller kümeye ait: önceki bir koşudan kalmışsa temizle.
psql $PG -q -c "drop role if exists zd_katki_yazar" >/dev/null 2>&1 || true
psql $PG -q -c "drop role if exists zd_paket_yonetici" >/dev/null 2>&1 || true

q -f $S/01-on-kontrol.sql 2>&1 | grep -q "ön kontrolü geçti" || { echo "FAIL ön kontrol boş veritabanında geçmedi"; exit 1; }
echo "PASS ön kontrol boş veritabanında geçer"
q -f $S/02-uygula.sql >/dev/null 2>&1
cd $S && psql $PG -d $DB -q -v ON_ERROR_STOP=1 -f 02-uygula.sql >/dev/null 2>&1 && cd "$ROOT" && echo "PASS uygula iki kez çalıştırılabilir"
q -f tests/ortak_dongu/checks.sql > /tmp/zd_ortak_c_$$.log 2>&1 || { sed -n 's/^.*\(NOTICE\|ERROR\):  //p' /tmp/zd_ortak_c_$$.log; echo "FAIL denetimler yarıda kaldı"; exit 1; }
sed -n 's/^.*NOTICE:  //p' /tmp/zd_ortak_c_$$.log
rm -f /tmp/zd_ortak_c_$$.log

# Telefonla aynı paket: SQL'in ürettiği "imzalanacak" metin ve özet, KnowledgePack.canonical() ile birebir;
# test anahtarıyla imzalanınca telefon paketi kabul eder.
q -tA -c "select ortak.paket_uret(7, '2027-03-05')" > /tmp/zd_ortak_paket_$$.json
T=$(mktemp -d)
. tests/sentence_bank/brain_build.sh
A=app/src/main/java/com/zeytindefteri/app
set +o pipefail
brain_build "$T" "$A/ContributionCleaner.java" "$A/CommunityQueue.java" "$A/KnowledgePack.java" \
  tests/ortak_dongu/SqlPackTest.java 2>&1 | grep -v "^Picked" || true
set -o pipefail
java -Dstdout.encoding=UTF-8 -Dfile.encoding=UTF-8 -cp "$T/classes:$JSON_JAR" com.zeytindefteri.app.SqlPackTest /tmp/zd_ortak_paket_$$.json 2>&1 | grep -v "^Picked"
rm -rf "$T" /tmp/zd_ortak_paket_$$.json

# Geri al: şema ve roller gider; yeniden kurulabilir.
q -f $S/03-geri-al.sql >/dev/null 2>&1
LEFT=$(psql $PG -d $DB -tAq -c "select count(*) from pg_namespace where nspname='ortak'")
ROLES=$(psql $PG -d $DB -tAq -c "select count(*) from pg_roles where rolname in ('zd_katki_yazar','zd_paket_yonetici')")
[ "$LEFT$ROLES" = "00" ] || { echo "FAIL geri al şemayı ya da rolleri bırakmadı ($LEFT/$ROLES)"; exit 1; }
echo "PASS geri al: şema ve roller kaldırıldı"
(cd $S && psql $PG -d $DB -q -v ON_ERROR_STOP=1 -f 02-uygula.sql >/dev/null 2>&1) && echo "PASS geri aldıktan sonra yeniden kurulur"

# Ön kontrol: başka bir "ortak" şeması varsa hiçbir şey yapmadan durur.
psql $PG -q -c "create database ${DB}_b" >/dev/null
psql $PG -d ${DB}_b -q -c "create schema ortak; create table ortak.baska(x int)"
if (cd $S && psql $PG -d ${DB}_b -q -v ON_ERROR_STOP=1 -f 02-uygula.sql >/tmp/zd_ortak_b.log 2>&1); then
  echo "FAIL yabancı şemada ön kontrol durmadı"; exit 1
fi
grep -q "ön kontrolü durdu" /tmp/zd_ortak_b.log || { echo "FAIL ön kontrol mesajı yok"; exit 1; }
N=$(psql $PG -d ${DB}_b -tAq -c "select count(*) from pg_tables where schemaname='ortak'")
[ "$N" = "1" ] || { echo "FAIL ön kontrol durduğu hâlde tablo oluştu"; exit 1; }
echo "PASS ön kontrol: başka bir ortak şemasında hiçbir şey yapmadan durur"
# Yazı denetimi: sunucu dosyalarında yurt dışı adres ya da Supabase yok.
if grep -iqE "supabase\.co|amazonaws|googleapis|azure|https?://" $S/*.sql; then
  echo "FAIL sunucu SQL'inde adres var"; exit 1
fi
echo "PASS sunucu SQL'inde hiçbir adres yok"
