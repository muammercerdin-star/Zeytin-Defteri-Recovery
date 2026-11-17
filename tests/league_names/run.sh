#!/bin/sh
# Adlı ön sıralama SQL'i gerçek PostgreSQL'de (geçici sunucu): ZD_PG="-h /var/tmp/zdpg -p 55432 -U postgres"
set -e
cd "$(dirname "$0")/../.."
PG=${ZD_PG:-"-h /var/tmp/zdpg -p 55432 -U postgres"}
DB=zd_league_test_$$
psql $PG -q -c "create database $DB" >/dev/null
trap 'psql $PG -q -c "drop database $DB" >/dev/null' EXIT
psql $PG -d $DB -q -v ON_ERROR_STOP=1 -f tests/league_names/fixture.sql >/dev/null
psql $PG -d $DB -q -v ON_ERROR_STOP=1 -f supabase/ZEYTIN-LIGI-ADLI-SIRALAMA-V1.sql >/dev/null
psql $PG -d $DB -q -v ON_ERROR_STOP=1 -f supabase/ZEYTIN-LIGI-ADLI-SIRALAMA-V1.sql >/dev/null  # tekrar çalıştırmak zararsız
psql $PG -d $DB -q -v ON_ERROR_STOP=1 -f tests/league_names/checks.sql
psql $PG -d $DB -q -v ON_ERROR_STOP=1 -f supabase/ZEYTIN-LIGI-CINS-AYLIK-V2.sql >/dev/null
psql $PG -d $DB -q -v ON_ERROR_STOP=1 -f supabase/ZEYTIN-LIGI-CINS-AYLIK-V2.sql >/dev/null  # tekrar çalıştırmak zararsız
psql $PG -d $DB -q -v ON_ERROR_STOP=1 -c "delete from private.market_contributions"
psql $PG -d $DB -q -v ON_ERROR_STOP=1 -f tests/league_names/checks_v2.sql
psql $PG -d $DB -q -v ON_ERROR_STOP=1 -f supabase/ZEYTIN-LIGI-ORT-FIYAT-V3.sql >/dev/null
psql $PG -d $DB -q -v ON_ERROR_STOP=1 -f supabase/ZEYTIN-LIGI-ORT-FIYAT-V3.sql >/dev/null  # tekrar çalıştırmak zararsız
psql $PG -d $DB -q -v ON_ERROR_STOP=1 -f tests/league_names/checks_v3.sql
psql $PG -d $DB -q -v ON_ERROR_STOP=1 -f supabase/ZEYTIN-LIGI-GUVEN-V4.sql >/dev/null
psql $PG -d $DB -q -v ON_ERROR_STOP=1 -f supabase/ZEYTIN-LIGI-GUVEN-V4.sql >/dev/null  # tekrar çalıştırmak zararsız
psql $PG -d $DB -q -v ON_ERROR_STOP=1 -f tests/league_names/checks_v4.sql
psql $PG -d $DB -q -v ON_ERROR_STOP=1 -f supabase/ZEYTIN-LIGI-KATILIM-V4.sql >/dev/null
psql $PG -d $DB -q -v ON_ERROR_STOP=1 -f supabase/ZEYTIN-LIGI-KATILIM-V4.sql >/dev/null  # tekrar çalıştırmak zararsız
psql $PG -d $DB -q -v ON_ERROR_STOP=1 -f tests/league_names/checks_katilim.sql
# Geri alma: sıralama yeniden herkesi gösterir, eski izinler döner, katılım kayıtları korunur; V4 yeniden kurulabilir.
psql $PG -d $DB -q -v ON_ERROR_STOP=1 -f supabase/ZEYTIN-LIGI-KATILIM-V4-GERI-AL.sql >/dev/null
psql $PG -d $DB -q -v ON_ERROR_STOP=1 -f tests/league_names/checks_v4_geri_al.sql
psql $PG -d $DB -q -v ON_ERROR_STOP=1 -f supabase/ZEYTIN-LIGI-KATILIM-V4.sql >/dev/null
# Ön kontrol: V2/V3 kurulmamış bir veritabanında V4 hiçbir şey yapmadan durmalı.
DB2=zd_league_eksik_$$
psql $PG -q -c "create database $DB2" >/dev/null
psql $PG -d $DB2 -q -v ON_ERROR_STOP=1 -f tests/league_names/fixture.sql >/dev/null
psql $PG -d $DB2 -q -v ON_ERROR_STOP=1 -f supabase/ZEYTIN-LIGI-ADLI-SIRALAMA-V1.sql >/dev/null
if psql $PG -d $DB2 -q -v ON_ERROR_STOP=1 -f supabase/ZEYTIN-LIGI-KATILIM-V4.sql >/tmp/zd_v4_eksik.log 2>&1; then
  psql $PG -q -c "drop database $DB2" >/dev/null; echo "FAIL: eksik ortamda V4 durmadı"; exit 1
fi
grep -q "V4 durduruldu" /tmp/zd_v4_eksik.log || { psql $PG -q -c "drop database $DB2" >/dev/null; echo "FAIL: ön kontrol mesajı yok"; exit 1; }
LEFT=$(psql $PG -d $DB2 -tAq -c "select to_regclass('private.league_participants') is null")
psql $PG -q -c "drop database $DB2" >/dev/null
[ "$LEFT" = "t" ] || { echo "FAIL: V4 durduğu hâlde tablo oluşmuş"; exit 1; }
echo "PASS: V4 ön kontrolü eksik ortamda hiçbir şey yapmadan durdu"
# Canlı düzeltme V4.1: V4 kurulu bir veritabanında sıralamayı GÜVEN V4 hesabı + katılım süzgeciyle yeniden kurar.
psql $PG -d $DB -q -v ON_ERROR_STOP=1 -f supabase/ZEYTIN-LIGI-KATILIM-V4-1-GUVEN.sql >/dev/null
psql $PG -d $DB -q -v ON_ERROR_STOP=1 -f supabase/ZEYTIN-LIGI-KATILIM-V4-1-GUVEN.sql >/dev/null  # tekrar çalıştırmak zararsız
psql $PG -d $DB -q -v ON_ERROR_STOP=1 -f tests/league_names/checks_katilim_guven.sql
