-- Zeytin Defteri / Zeytin Ligi — KATILIM İZNİ V4
--
-- Önce V1 (ADLI-SIRALAMA), V2 (CINS-AYLIK), V3 (ORT-FIYAT) ve GÜVEN V4 çalıştırılmış olmalı.
-- Supabase SQL Editor'de BİR KEZ çalıştır (tekrar çalıştırmak zararsız).
--
-- Neden?
--   "Piyasaya Anonim Katkı" varsayılan olarak açıktır ve bölge İSTATİSTİKLERİ (toplu, en az 5 üretici)
--   içindir. Bölge SIRALAMASI ise üretici başına satır yayınlar (sıra, puan, en çok çıkan numara,
--   ortalama TL/kg). Kişi başına satır yayınlamak ayrı bir amaçtır; bu yüzden ayrı ve varsayılan
--   KAPALI bir izne ("Zeytin Ligi'nde yer al") bağlanır.
--
-- Ne yapar?
--   * private.league_participants: sıralamada yer almayı seçen üreticilerin anahtarı (producer_hash).
--   * Adını ligde göstermeyi zaten onaylamış olanlar katılımcı sayılır (onları zaten sıralamaya
--     girmeyi kabul etmişlerdir). Adı açan herkes de bundan sonra kendiliğinden katılımcı olur.
--   * public.league_set_participation(katıl): katılır / ayrılır. Ayrılınca ligdeki ad da SİLİNİR.
--   * public.league_get_participation(): kendi katılım durumun.
--   * public.league_region_ranking_v3: aynı imza, GÜVEN V4 hesabı; yalnız katılımcıların satırları döner.
--     Sıra numaraları katılımcılar arasında verilir. Gizlilik eşiği (dilimde en az 5 farklı üretici)
--     tüm katkılar üzerinden aranmaya devam eder.
--   * Eski league_region_ranking (V1) ve league_region_ranking_v2 dışarıya kapatılır; uygulamanın
--     tüm sürümleri önce V3'ü çağırdığı için hiçbir sürüm bozulmaz.
--
-- Mevcut tabloların verisine DOKUNMAZ.
--
-- KURAL: Ligde yer almayı seçmeyen üreticinin satış kayıtları ve anonim bölge istatistiklerine katkısı
-- KAYBOLMAZ. private.market_contributions'a ve istatistik fonksiyonlarına hiç dokunulmaz; izin yalnız
-- kişi bazlı sıralamada (league_region_ranking_v3) satırın görünüp görünmeyeceğini belirler.
-- Ligden ayrılmak da yalnız katılım kaydını ve ligdeki adı siler, katkıları silmez.

begin;

-- ÖN KONTROL: beklenen tablo ve fonksiyonlar tam bu imzalarla yoksa hiçbir şey yapmadan durur.
do $$
declare
    missing text := '';
begin
    if to_regclass('private.market_contributions') is null then missing := missing || ' private.market_contributions'; end if;
    if to_regclass('private.league_profiles') is null then missing := missing || ' private.league_profiles'; end if;
    if to_regprocedure('private.market_producer_hash()') is null then missing := missing || ' private.market_producer_hash()'; end if;
    if to_regprocedure('public.league_region_ranking(integer, text)') is null then missing := missing || ' league_region_ranking(integer, text)'; end if;
    if to_regprocedure('public.league_region_ranking_v2(integer, text, text, integer)') is null then missing := missing || ' league_region_ranking_v2(integer, text, text, integer)'; end if;
    if to_regprocedure('public.league_region_ranking_v3(integer, text, text, integer)') is null then missing := missing || ' league_region_ranking_v3(integer, text, text, integer)'; end if;
    if missing <> '' then
        raise exception 'V4 durduruldu, beklenen nesneler yok:%', missing;
    end if;
end $$;

create table if not exists private.league_participants (
    producer_hash text primary key,
    joined_at     timestamptz not null default now(),
    constraint league_participants_hash_len check (char_length(producer_hash) = 64)
);

alter table private.league_participants enable row level security;
revoke all on table private.league_participants from public;
do $$
declare r text;
begin
    foreach r in array array['anon', 'authenticated'] loop
        if exists (select 1 from pg_roles where rolname = r) then
            execute format('revoke all on table private.league_participants from %I', r);
        end if;
    end loop;
end $$;

-- Adını göstermeyi zaten onaylamış olanlar katılımcıdır.
insert into private.league_participants (producer_hash)
select p.producer_hash
from private.league_profiles p
where p.show_name
on conflict (producer_hash) do nothing;

-- Bundan sonra adını açan herkes de katılımcı olur (eski uygulama sürümleri dahil).
create or replace function private.league_join_on_name()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
    if new.show_name then
        insert into private.league_participants (producer_hash)
        values (new.producer_hash)
        on conflict (producer_hash) do nothing;
    end if;
    return new;
end;
$$;

drop trigger if exists league_join_on_name on private.league_profiles;
create trigger league_join_on_name
after insert or update of show_name on private.league_profiles
for each row execute function private.league_join_on_name();

create or replace function public.league_set_participation(p_join boolean)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_hash text;
begin
    if coalesce(auth.jwt() ->> 'sub', '') = '' then
        raise exception 'authentication required' using errcode = '42501';
    end if;
    v_hash := private.market_producer_hash();
    if coalesce(p_join, false) then
        insert into private.league_participants (producer_hash)
        values (v_hash)
        on conflict (producer_hash) do nothing;
        return jsonb_build_object('ok', true, 'participate', true);
    end if;
    delete from private.league_participants where producer_hash = v_hash;
    delete from private.league_profiles where producer_hash = v_hash;
    return jsonb_build_object('ok', true, 'participate', false);
end;
$$;

create or replace function public.league_get_participation()
returns jsonb
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_hash text;
begin
    if coalesce(auth.jwt() ->> 'sub', '') = '' then
        raise exception 'authentication required' using errcode = '42501';
    end if;
    v_hash := private.market_producer_hash();
    return jsonb_build_object(
        'participate',
        exists (select 1 from private.league_participants lp where lp.producer_hash = v_hash));
end;
$$;

-- GÜVEN V4 hesabının aynısı (ZEYTIN-LIGI-GUVEN-V4.sql); tek fark "eligible" adımında yalnız katılımcılar.
create or replace function public.league_region_ranking_v3(
    p_season_year integer, p_region_key text, p_variety text, p_month integer)
returns table(
    rank           integer,
    display_name   text,
    is_me          boolean,
    score          numeric,
    dominant_grade text,
    producer_count integer,
    ranked_count   integer,
    avg_price      numeric
)
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_hash text;
    v_variety text := lower(coalesce(p_variety, ''));
    v_month integer := coalesce(p_month, 0);
begin
    if coalesce(auth.jwt() ->> 'sub', '') = '' then
        raise exception 'authentication required' using errcode = '42501';
    end if;
    if p_season_year is null or p_season_year < 2000 or p_season_year > 2100 then
        raise exception 'invalid season year' using errcode = '22023';
    end if;
    if p_region_key is null or p_region_key !~ '^[a-z0-9_]{2,80}$' then
        raise exception 'invalid region key' using errcode = '22023';
    end if;
    if v_variety !~ '^[a-z0-9]{0,40}$' or v_month < 0 or v_month > 12 then
        raise exception 'invalid filter' using errcode = '22023';
    end if;
    v_hash := private.market_producer_hash();
    return query
    with season as (
        -- Piyasa fiyatı için bölgenin bütün sezonu (dilim filtresinden bağımsız; piyasa herkesin satışıdır).
        select c.producer_hash as ph, c.grade as gr, c.price_per_kg::numeric as price, c.sale_date as d
        from private.market_contributions c
        where c.season_year = p_season_year
          and c.region_key = p_region_key
          and c.kg > 0 and c.price_per_kg > 0
    ),
    rows as (
        select c.id as rid, c.producer_hash as ph, c.grade as gr, c.kg::numeric as kg,
               c.price_per_kg::numeric as price, c.sale_date as d
        from private.market_contributions c
        where c.season_year = p_season_year
          and c.region_key = p_region_key
          and c.kg > 0 and c.price_per_kg > 0
          and (v_variety = '' or c.variety = v_variety)
          and (v_month = 0 or extract(month from c.sale_date) = v_month)
    ),
    slice_size as (
        select count(distinct ph)::integer as n from rows
    ),
    priced as (
        select r.*,
               coalesce(w3.med, w7.med, w21.med, ws.med) as ref,
               hi.mx as hi
        from rows r
        left join lateral (
            select percentile_cont(0.5) within group (order by s.price)::numeric as med
            from season s
            where s.gr = r.gr and s.ph <> r.ph and abs(s.d - r.d) <= 3
            having count(distinct s.ph) >= 2
        ) w3 on true
        left join lateral (
            select max(s.price) as mx
            from season s
            where s.gr = r.gr and s.ph <> r.ph and abs(s.d - r.d) <= 7
            having count(distinct s.ph) >= 2
        ) hi on true
        left join lateral (
            select percentile_cont(0.5) within group (order by s.price)::numeric as med
            from season s
            where s.gr = r.gr and s.ph <> r.ph and abs(s.d - r.d) <= 7
            having count(distinct s.ph) >= 3
        ) w7 on true
        left join lateral (
            select percentile_cont(0.5) within group (order by s.price)::numeric as med
            from season s
            where s.gr = r.gr and s.ph <> r.ph and abs(s.d - r.d) <= 21
            having count(distinct s.ph) >= 3
        ) w21 on true
        left join lateral (
            select percentile_cont(0.5) within group (order by s.price)::numeric as med
            from season s
            where s.gr = r.gr and s.ph <> r.ph
            having count(distinct s.ph) >= 3
        ) ws on true
    ),
    weighted as (
        select p.*,
               case
                   when p.ref is not null and (p.price > p.ref * 1.20 or p.price < p.ref * 0.75) then 0::numeric
                   when p.hi is not null and p.price > p.hi * 1.05 then 0::numeric
                   when p.kg >= 200 and mod(p.kg, 50) = 0 and p.ref is not null and p.price > p.ref * 1.05 then 0::numeric
                   when p.kg >= 200 and mod(p.kg, 50) = 0 then 0.5::numeric
                   else 1::numeric
               end as w
        from priced p
    ),
    producer as (
        select
            w.ph,
            sum(w.kg * w.w) as kg,
            sum(w.kg * w.w * w.price) as amount,
            sum(w.kg * w.w * case w.gr
                when 'Double' then 100 when '1 No' then 90 when '2 No' then 75
                when '3 No' then 55 when '4 No' then 30 when 'Yağlık' then 10 else 0 end) as quality_sum,
            sum(case when w.ref is not null then w.kg * w.w * w.ref else 0 end) as expected,
            sum(case when w.ref is not null then w.kg * w.w * w.price else 0 end) as compared_amount
        from weighted w group by w.ph
    ),
    per_grade as (
        select w.ph, w.gr, sum(w.kg * w.w) as kg from weighted w where w.w > 0 group by w.ph, w.gr
    ),
    dominant as (
        select x.ph, x.gr from (
            select g.ph, g.gr, row_number() over (partition by g.ph order by g.kg desc, g.gr) as rn
            from per_grade g) x
        where x.rn = 1
    ),
    scored as (
        select
            p.ph, p.kg,
            p.amount / p.kg as avg_price,
            least(100, greatest(0, p.quality_sum / p.kg)) as quality,
            case when p.expected > 0
                 then least(100, greatest(0, 100 * (p.compared_amount / p.expected - 0.5))) else 50 end as price_score,
            least(100, greatest(0, 100 * ln(1 + p.kg) / ln(1 + 10000::numeric))) as volume
        from producer p
        where p.kg > 0
    ),
    eligible as (
        -- Süzgeçten geçen (ağırlıklı) en az 100 kg; aylık sıralamada ay içinde aranır.
        select s.ph,
               round(least(100, greatest(0, 0.5 * s.quality + 0.35 * s.price_score + 0.15 * s.volume)), 2) as sc,
               s.quality, s.avg_price
        from scored s
        -- Yalnız "Zeytin Ligi'nde yer al" iznini verenler (league_participants) sıralanır.
        join private.league_participants lp on lp.producer_hash = s.ph
        where s.kg >= 100
    ),
    ranked as (
        select e.ph, e.sc, e.avg_price, row_number() over (order by e.sc desc, e.quality desc, e.ph) as rk
        from eligible e
    )
    select
        r.rk::integer, case when pr.show_name then pr.display_name else null end,
        (r.ph = v_hash), r.sc, d.gr,
        (select n from slice_size), (select count(*)::integer from ranked),
        -- Ortalama TL/kg (süzgeçten geçen kayıtlardan): adsız satırda her zaman; adlı satırda yalnız onay sürümü 2 ise.
        case when pr.show_name is not true or coalesce(pr.consent_version, 1) >= 2
             then round(r.avg_price, 2) else null end
    from ranked r
    left join private.league_profiles pr on pr.producer_hash = r.ph
    left join dominant d on d.ph = r.ph
    where (select n from slice_size) >= 5
      and r.rk <= 50
    order by r.rk;
end;
$$;

-- PUBLIC (herkes) izinleri: Postgres yeni fonksiyonlara varsayılan olarak PUBLIC çalıştırma izni verir.
-- Eski süzgeçsiz sıralamalarda bu izin kalmışsa anon/authenticated'dan almak yetmez; önce PUBLIC'ten alınır.
revoke all on function public.league_region_ranking(integer, text) from public;
revoke all on function public.league_region_ranking_v2(integer, text, text, integer) from public;
revoke all on function private.league_join_on_name() from public;
revoke all on function public.league_set_participation(boolean) from public;
revoke all on function public.league_get_participation() from public;
revoke all on function public.league_region_ranking_v3(integer, text, text, integer) from public;
do $$
declare r text;
begin
    foreach r in array array['anon', 'authenticated'] loop
        if exists (select 1 from pg_roles where rolname = r) then
            execute format('grant execute on function public.league_set_participation(boolean) to %I', r);
            execute format('grant execute on function public.league_get_participation() to %I', r);
            execute format('grant execute on function public.league_region_ranking_v3(integer, text, text, integer) to %I', r);
            -- Katılım süzgeci olmayan eski sıralamalar kapatılır (uygulama önce V3'ü çağırır).
            execute format('revoke execute on function public.league_region_ranking(integer, text) from %I', r);
            execute format('revoke execute on function public.league_region_ranking_v2(integer, text, text, integer) from %I', r);
        end if;
    end loop;
end $$;

commit;
