-- Ortak döngü (İP8-A) — 01 ÖN KONTROL. Hiçbir şey değiştirmez; uygun değilse "ortak döngü ön kontrolü durdu" ile durur.
-- CANLIDA ÇALIŞTIRMA: İP8-A'da yalnız yerel PostgreSQL'de denenir (tests/ortak_dongu/run.sh).
-- Sunucu Türkiye'de olmalı; yurt dışındaki hiçbir sunucuya (Supabase dahil) kurulmaz.
-- Çalıştırma: psql -v ON_ERROR_STOP=1 -f 01-on-kontrol.sql && psql -v ON_ERROR_STOP=1 -f 02-uygula.sql

do $$
declare
    v int := current_setting('server_version_num')::int;
begin
    if v < 130000 then
        raise exception 'ortak döngü ön kontrolü durdu: PostgreSQL 13 ya da üstü gerekli (bulunan %)', current_setting('server_version');
    end if;
    -- Şema varsa bu kurulumun şeması olmalı (başka bir "ortak" şemasının üzerine yazılmaz).
    if exists (select 1 from pg_namespace where nspname = 'ortak')
       and not exists (select 1 from pg_tables where schemaname = 'ortak' and tablename = 'ayar') then
        raise exception 'ortak döngü ön kontrolü durdu: "ortak" şeması var ama bu kurulumun değil';
    end if;
    -- Rollerin oturum açabilen kullanıcılar olmaması gerekir: API kendi kullanıcısına bu rolü verir.
    if exists (select 1 from pg_roles where rolname in ('zd_katki_yazar', 'zd_paket_yonetici') and (rolcanlogin or rolsuper)) then
        raise exception 'ortak döngü ön kontrolü durdu: zd_katki_yazar / zd_paket_yonetici giriş yapabilen ya da süper kullanıcı olmamalı';
    end if;
    raise notice 'ortak döngü ön kontrolü geçti (PostgreSQL %)', current_setting('server_version');
end
$$;
