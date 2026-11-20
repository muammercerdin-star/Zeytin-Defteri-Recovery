-- Ortak döngü (İP8-A) — 03 GERİ AL. 02-uygula.sql'in kurduğu her şeyi kaldırır: "ortak" şeması (katkılar, onaylar,
-- hız sayaçları dahil) ve iki rol. Katkı verisi geri gelmez; ortak döngü yeni bir özellik olduğu için geri almanın
-- anlamı budur. Uygulamadaki telefonlar etkilenmez (adres boşken zaten ağa çıkmazlar).
-- CANLIDA ÇALIŞTIRMA (İP8-A'da yalnız yerel test).

\set ON_ERROR_STOP on
begin;

do $$
begin
    if exists (select 1 from pg_extension where extname = 'pg_cron') then
        execute $c$select cron.unschedule(jobid) from cron.job where jobname = 'zd-ortak-temizle'$c$;
    end if;
end
$$;

drop schema if exists ortak cascade;

do $$
declare
    r text;
begin
    foreach r in array array['zd_katki_yazar', 'zd_paket_yonetici'] loop
        if exists (select 1 from pg_roles where rolname = r) then
            execute format('drop owned by %I', r);
            execute format('drop role %I', r);
        end if;
    end loop;
end
$$;

commit;
