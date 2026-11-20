-- Ortak döngü (İP8-A) — 02 UYGULA. Tekrar çalıştırmak zararsızdır. CANLIDA ÇALIŞTIRMA (İP8-B'ye kadar yalnız yerel test).
-- Sunucu Türkiye'de; veri yurt dışına aktarılmaz.
--
-- Ne tutulur: telefonda temizlenmiş tek söz ve anlamı (kok, soz, eylem, tur, not, il, surum), sezonluk takma ad
-- (telefondaki gizli anahtardan HMAC; her 1 Ağustos'ta değişir) ve silme belgesinin yalnız SHA-256 özeti.
-- Ne tutulmaz: ad, telefon, hesap, konum, IP. Bağlantı başına hız sınırı, API'nin verdiği bağlantı özetiyle ayrı bir
-- tabloda sayılır, katkılarla bağlanmaz ve bir günde silinir.
--
-- Roller (giriş yapamaz; API kendi kullanıcısına verir):
--   zd_katki_yazar    : yalnız ortak.katki_ekle ve ortak.katki_geri_cek çağırır. Tabloları okuyamaz, doğrudan yazamaz.
--   zd_paket_yonetici : yalnız toplu aday listesini görür (tek tek katkıları değil), elle onaylar, paket üretir.
-- Paket imzası sunucuda ATILMAZ: özel anahtar sunucuda durmaz (docs/ortak-dongu-anahtar.md).

\set ON_ERROR_STOP on
\ir 01-on-kontrol.sql

begin;

do $$
begin
    if not exists (select 1 from pg_roles where rolname = 'zd_katki_yazar') then
        create role zd_katki_yazar nologin;
    end if;
    if not exists (select 1 from pg_roles where rolname = 'zd_paket_yonetici') then
        create role zd_paket_yonetici nologin;
    end if;
end
$$;

create schema if not exists ortak;
revoke all on schema ortak from public;
grant usage on schema ortak to zd_katki_yazar, zd_paket_yonetici;

-- Ayarlar: eşik TEK yerde (değiştirmek için: update ortak.ayar set deger = 25 where ad = 'esik';).
create table if not exists ortak.ayar (
    ad text primary key,
    deger integer not null check (deger > 0),
    aciklama text not null
);
insert into ortak.ayar (ad, deger, aciklama) values
    ('esik', 20, 'Bir sözün aday olması için onu öğreten farklı çiftçi (takma ad) sayısı'),
    ('saatlik_sinir', 30, 'Bir bağlantıdan bir saatte kabul edilen en çok katkı + geri çekme'),
    ('saklama_sezon', 2, 'Katkıların saklandığı sezon sayısı (içinde bulunulan dahil); eskisi temizlenir')
on conflict (ad) do nothing;

create or replace function ortak.ayar(p_ad text) returns integer
language sql stable security definer set search_path = ortak, pg_temp as $$
    select deger from ortak.ayar where ad = p_ad
$$;

-- Sezon: 1 Ağustos'ta başlar (telefondaki takma adla aynı), Türkiye saatine göre. 2026-08-01..2027-07-31 → '2026'.
create or replace function ortak.sezon(p_an timestamptz default now()) returns text
language sql stable as $$
    select case when extract(month from p_an at time zone 'Europe/Istanbul') >= 8
                then extract(year from p_an at time zone 'Europe/Istanbul')::int
                else extract(year from p_an at time zone 'Europe/Istanbul')::int - 1 end::text
$$;

create table if not exists ortak.katki (
    id bigint generated always as identity primary key,
    kok text not null check (kok ~ '^[a-z]{2,20}$'),
    soz text not null check (char_length(soz) between 4 and 20 and soz ~ '^[a-zçğıöşü]+$'),
    eylem text not null check (eylem in ('ISLEM', 'HASAT')),
    tur text not null check (char_length(tur) <= 30),
    "not" text not null default '' check (char_length("not") <= 30),
    il text not null default '' check (char_length(il) <= 20),
    surum text not null default '' check (surum = '' or surum ~ '^[0-9]{1,3}(\.[0-9]{1,3}){0,3}$'),
    takma_ad text not null check (takma_ad ~ '^[0-9a-f]{32}$'),
    silme_ozeti text not null unique check (silme_ozeti ~ '^[0-9a-f]{64}$'),
    sezon text not null default ortak.sezon(),
    created timestamptz not null default now(),
    -- Bir çiftçi (takma ad) bir söz-anlamı bir kez sayılır.
    unique (takma_ad, kok, eylem, tur, "not")
);
create index if not exists katki_anlam on ortak.katki (kok, eylem, tur, "not");
create index if not exists katki_sezon on ortak.katki (sezon);

-- Satır güvenliği: açık; okuma kuralı YOK, yalnız ekleme kuralı var. Roller tabloya doğrudan hiç yetki almaz;
-- ekleme yalnız ortak.katki_ekle üzerinden (doğrulama + hız sınırı) yapılır.
alter table ortak.katki enable row level security;
drop policy if exists katki_yalniz_ekle on ortak.katki;
create policy katki_yalniz_ekle on ortak.katki for insert to zd_katki_yazar with check (true);
revoke all on ortak.katki from public, zd_katki_yazar, zd_paket_yonetici;

create table if not exists ortak.hiz (
    baglanti text not null check (baglanti ~ '^[0-9a-f]{16,64}$'),
    saat timestamptz not null,
    sayi integer not null default 0,
    primary key (baglanti, saat)
);
alter table ortak.hiz enable row level security;
revoke all on ortak.hiz from public, zd_katki_yazar, zd_paket_yonetici;

create table if not exists ortak.onay (
    kok text not null,
    eylem text not null,
    tur text not null,
    "not" text not null,
    soz text not null,
    karar text not null check (karar in ('onaylandi', 'reddedildi')),
    karar_zamani timestamptz not null default now(),
    primary key (kok, eylem, tur, "not")
);
alter table ortak.onay enable row level security;
revoke all on ortak.onay from public, zd_katki_yazar, zd_paket_yonetici;

-- Bağlantı başına hız sınırı. p_baglanti: API'nin bağlantıdan (ör. IP) türettiği tuzlu özet; IP'nin kendisi gelmez.
create or replace function ortak.hiz_say(p_baglanti text) returns void
language plpgsql security definer set search_path = ortak, pg_temp as $$
declare
    n integer;
begin
    if p_baglanti is null or p_baglanti !~ '^[0-9a-f]{16,64}$' then
        raise exception 'gecersiz baglanti' using errcode = '22023';
    end if;
    insert into ortak.hiz as h (baglanti, saat, sayi) values (p_baglanti, date_trunc('hour', now()), 1)
    on conflict (baglanti, saat) do update set sayi = h.sayi + 1
    returning sayi into n;
    if n > ortak.ayar('saatlik_sinir') then
        raise exception 'hiz siniri' using errcode = '54000';
    end if;
end
$$;

-- Katkı ekle. Döner: true = yeni katkı, false = bu çiftçi bu söz-anlamı zaten göndermiş (yinelenen sayılmaz).
create or replace function ortak.katki_ekle(
    p_kok text, p_soz text, p_eylem text, p_tur text, p_not text, p_il text, p_surum text,
    p_takma_ad text, p_silme_ozeti text, p_baglanti text) returns boolean
language plpgsql security definer set search_path = ortak, pg_temp as $$
declare
    n integer;
begin
    perform ortak.hiz_say(p_baglanti);
    insert into ortak.katki (kok, soz, eylem, tur, "not", il, surum, takma_ad, silme_ozeti)
    values (p_kok, p_soz, p_eylem, coalesce(p_tur, ''), coalesce(p_not, ''), coalesce(p_il, ''), coalesce(p_surum, ''),
            p_takma_ad, p_silme_ozeti)
    on conflict do nothing;
    get diagnostics n = row_count;
    return n = 1;
exception
    when check_violation or not_null_violation then
        raise exception 'gecersiz katki' using errcode = '22023';
end
$$;

-- Geri çek: telefon yalnız kendinde duran rastgele silme belgesini gönderir; sunucu özetini alıp eşleşen katkıyı siler.
-- Döner: silinen katkı sayısı (0 ya da 1). Belge sunucuda hiç saklanmaz.
create or replace function ortak.katki_geri_cek(p_silme_belgesi text, p_baglanti text) returns integer
language plpgsql security definer set search_path = ortak, pg_temp as $$
declare
    n integer;
begin
    perform ortak.hiz_say(p_baglanti);
    if p_silme_belgesi is null or char_length(p_silme_belgesi) not between 16 and 128 then
        raise exception 'gecersiz belge' using errcode = '22023';
    end if;
    delete from ortak.katki where silme_ozeti = encode(sha256(convert_to(p_silme_belgesi, 'UTF8')), 'hex');
    get diagnostics n = row_count;
    return n;
end
$$;

-- Aday listesi: yalnız toplu sayılar; eşiğin altındaki sözler, takma adlar ve tek tek katkılar görünmez.
-- Söz: en çok yazılan biçim. Bölge: tek bir il en az eşik kadar çiftçiyle öğretmişse o il, değilse boş.
create or replace view ortak.adaylar with (security_barrier) as
with anlam as (
    select kok, eylem, tur, "not", count(distinct takma_ad) as ciftci,
           mode() within group (order by soz) as soz
    from ortak.katki
    group by kok, eylem, tur, "not"
    having count(distinct takma_ad) >= ortak.ayar('esik')
), bolge as (
    select kok, eylem, tur, "not", il, count(distinct takma_ad) as ciftci
    from ortak.katki
    where il <> ''
    group by kok, eylem, tur, "not", il
)
select a.kok, a.soz, a.eylem, a.tur, a."not", a.ciftci,
       coalesce((select b.il from bolge b
                 where (b.kok, b.eylem, b.tur, b."not") = (a.kok, a.eylem, a.tur, a."not") and b.ciftci >= ortak.ayar('esik')
                 order by b.ciftci desc, b.il limit 1), '') as bolge,
       -- Aynı köke başka anlam veren aday da varsa yönetici görsün (çakışma).
       (select count(*) from anlam x where x.kok = a.kok) > 1 as cakisma,
       o.karar
from anlam a
left join ortak.onay o on (o.kok, o.eylem, o.tur, o."not") = (a.kok, a.eylem, a.tur, a."not");

revoke all on ortak.adaylar from public, zd_katki_yazar;
grant select on ortak.adaylar to zd_paket_yonetici;

-- Elle onay: paket yöneticisi her adayı tek tek onaylar ya da reddeder. Eşiğin altındaki söz onaylanamaz.
create or replace function ortak.aday_karar(p_kok text, p_eylem text, p_tur text, p_not text, p_karar text) returns void
language plpgsql security definer set search_path = ortak, pg_temp as $$
declare
    s text;
begin
    select soz into s from ortak.adaylar
    where kok = p_kok and eylem = p_eylem and tur = coalesce(p_tur, '') and "not" = coalesce(p_not, '');
    if s is null then
        raise exception 'boyle bir aday yok (esigin altinda olabilir)' using errcode = '22023';
    end if;
    insert into ortak.onay (kok, eylem, tur, "not", soz, karar) values (p_kok, p_eylem, coalesce(p_tur, ''), coalesce(p_not, ''), s, p_karar)
    on conflict (kok, eylem, tur, "not") do update set soz = excluded.soz, karar = excluded.karar, karar_zamani = now();
end
$$;

-- Paket üret: onaylı VE bugün hâlâ eşiği geçen sözler (geri çekmeler düşmüşse girmez). Bir köke tek anlam: çakışan
-- köklerden yalnız onaylanmış olan girer; ikisi de onaylıysa hiçbiri girmez. İmzasız döner; "imzalanacak" metin
-- telefondaki KnowledgePack.canonical() ile birebir aynıdır ve imza sunucu dışında atılır.
create or replace function ortak.paket_uret(p_surum integer, p_tarih date default current_date) returns jsonb
language plpgsql security definer set search_path = ortak, pg_temp as $$
declare
    satirlar jsonb;
    metin text;
begin
    with secilen as (
        select a.kok, a.soz, a.eylem, a.tur, a."not", a.bolge
        from ortak.adaylar a
        where a.karar = 'onaylandi'
          and (select count(*) from ortak.adaylar b where b.kok = a.kok and b.karar = 'onaylandi') = 1
    )
    select coalesce(jsonb_agg(jsonb_build_object('kok', kok, 'soz', soz, 'eylem', eylem, 'tur', tur, 'not', "not", 'bolge', bolge)
                              order by kok, eylem, tur, "not"), '[]'::jsonb),
           'zd-bilgi-paketi|1|' || p_surum || '|' || to_char(p_tarih, 'YYYY-MM-DD') || '|indirilen'
               || coalesce(string_agg(E'\n' || concat_ws(E'\t', kok, soz, eylem, tur, "not", bolge), '' order by kok, eylem, tur, "not"), '')
    into satirlar, metin
    from secilen;
    return jsonb_build_object('bicim', 1, 'surum', p_surum, 'tarih', to_char(p_tarih, 'YYYY-MM-DD'), 'kaynak', 'indirilen',
                              'sozler', satirlar, 'ozet', encode(sha256(convert_to(metin, 'UTF8')), 'hex'),
                              'imzalanacak', metin);
end
$$;

-- Temizlik görevi (günde bir çalışır): saklama süresi dışındaki sezonların katkıları ve bir günden eski hız sayaçları silinir.
-- Döner: silinen katkı sayısı.
create or replace function ortak.temizle(p_an timestamptz default now()) returns integer
language plpgsql security definer set search_path = ortak, pg_temp as $$
declare
    n integer;
begin
    delete from ortak.katki where sezon::int <= ortak.sezon(p_an)::int - ortak.ayar('saklama_sezon');
    get diagnostics n = row_count;
    delete from ortak.hiz where saat < p_an - interval '1 day';
    return n;
end
$$;

revoke all on all functions in schema ortak from public;
grant execute on function ortak.katki_ekle(text, text, text, text, text, text, text, text, text, text) to zd_katki_yazar;
grant execute on function ortak.katki_geri_cek(text, text) to zd_katki_yazar;
grant execute on function ortak.ayar(text) to zd_paket_yonetici;
grant execute on function ortak.sezon(timestamptz) to zd_paket_yonetici;
grant execute on function ortak.aday_karar(text, text, text, text, text) to zd_paket_yonetici;
grant execute on function ortak.paket_uret(integer, date) to zd_paket_yonetici;

-- Zamanlama: pg_cron kuruluysa her gece 03:17'de temizlik; değilse sunucunun cron'u çalıştırır:
--   17 3 * * *  psql -d <veritabanı> -c "select ortak.temizle()"
do $$
begin
    if exists (select 1 from pg_extension where extname = 'pg_cron') then
        execute $c$select cron.schedule('zd-ortak-temizle', '17 3 * * *', 'select ortak.temizle()')$c$;
    end if;
end
$$;

commit;
