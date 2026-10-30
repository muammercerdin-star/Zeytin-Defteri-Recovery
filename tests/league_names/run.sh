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
