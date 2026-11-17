-- Zeytin Defteri / Zeytin Ligi — KATILIM V4.1 (canlı düzeltme, 17 Kasım 2026)
--
-- KATILIM V4 zaten çalıştırıldıysa BUNU çalıştır (tekrar çalıştırmak zararsız).
-- Neden: KATILIM V4, sıralama fonksiyonunu eski V3 hesabı üzerine kurmuştu; canlıdaki GÜVEN V4 süzgeçleri
-- (haftalık piyasa kıyası, şüpheli fiyat ve yuvarlak kilo süzgeci) bu yüzden devre dışı kaldı.
-- Bu dosya league_region_ranking_v3'ü GÜVEN V4 hesabı + katılım süzgeciyle yeniden kurar.
-- Katılım tablosuna, profillere ve katkılara DOKUNMAZ; yalnız sıralama hesabını değiştirir.

begin;

do $$
begin
    if to_regclass('private.league_participants') is null then
        raise exception 'V4.1 durduruldu: önce ZEYTIN-LIGI-KATILIM-V4.sql çalıştırılmalı';
    end if;
end $$;

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

revoke all on function public.league_region_ranking_v3(integer, text, text, integer) from public;
do $$
declare r text;
begin
    foreach r in array array['anon', 'authenticated'] loop
        if exists (select 1 from pg_roles where rolname = r) then
            execute format('grant execute on function public.league_region_ranking_v3(integer, text, text, integer) to %I', r);
        end if;
    end loop;
end $$;

commit;
