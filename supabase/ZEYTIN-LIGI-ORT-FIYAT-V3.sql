-- Zeytin Defteri / Zeytin Ligi — ORTALAMA FİYAT V3
--
-- Önce V1 (ADLI-SIRALAMA) ve V2 (CINS-AYLIK) çalıştırılmış olmalı. Supabase SQL Editor'de BİR KEZ çalıştır
-- (tekrar çalıştırmak zararsız). Önceki fonksiyonlar DEĞİŞMEZ; eski uygulama sürümleri aynen çalışır.
--
--   * private.league_profiles.consent_version: 1 = ad (fiyatsız onay), 2 = ad + ortalama kg fiyatı.
--   * public.league_set_profile_v2(ad, göster, onay_sürümü), public.league_get_profile_v2().
--   * public.league_region_ranking_v3(sezon, bölge, cins, ay): V2 sıralamasının aynısı + avg_price (TL/kg).
--       - Adı görünmeyen satırda ortalama fiyat görünür (ad yok; dilimde en az 5 üretici şartı).
--       - Adı görünen satırda fiyat yalnız onay sürümü 2 ise; değilse boş ("—").
--       - Kilogram ve toplam tutar DÖNMEZ.

begin;

alter table private.league_profiles add column if not exists consent_version integer not null default 1;

create or replace function public.league_set_profile_v2(p_display_name text, p_show_name boolean, p_consent_version integer)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_result jsonb;
    v_version integer := greatest(1, least(2, coalesce(p_consent_version, 1)));
begin
    v_result := public.league_set_profile(p_display_name, p_show_name);
    if coalesce(p_show_name, false) then
        update private.league_profiles set consent_version = v_version
         where producer_hash = private.market_producer_hash();
    end if;
    return v_result || jsonb_build_object('consent_version', case when coalesce(p_show_name, false) then v_version else 0 end);
end;
$$;

create or replace function public.league_get_profile_v2()
returns table(display_name text, show_name boolean, consent_version integer)
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
    if coalesce(auth.jwt() ->> 'sub', '') = '' then
        raise exception 'authentication required' using errcode = '42501';
    end if;
    return query
        select p.display_name, p.show_name, p.consent_version
        from private.league_profiles p
        where p.producer_hash = private.market_producer_hash();
end;
$$;

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
    with rows as (
        select c.producer_hash as ph, c.grade as gr, c.variety as va, c.kg::numeric as kg, c.price_per_kg::numeric as price
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
    -- Fiyat kıyası: önce aynı cins + aynı numara (en az 3 üretici), yoksa yalnız numara (en az 3 üretici).
    med_vg as (
        select va, gr, percentile_cont(0.5) within group (order by price)::numeric as med
        from rows where va <> ''
        group by va, gr having count(distinct ph) >= 3
    ),
    med_g as (
        select gr, percentile_cont(0.5) within group (order by price)::numeric as med
        from rows group by gr having count(distinct ph) >= 3
    ),
    per_line as (
        select r.ph, r.gr, r.va, sum(r.kg) as kg, sum(r.kg * r.price) as amount
        from rows r group by r.ph, r.gr, r.va
    ),
    with_med as (
        select l.*, coalesce(vg.med, g.med) as med
        from per_line l
        left join med_vg vg on vg.va = l.va and vg.gr = l.gr
        left join med_g g on g.gr = l.gr
    ),
    producer as (
        select
            w.ph,
            sum(w.kg) as kg,
            sum(w.amount) as amount,
            sum(w.kg * case w.gr
                when 'Double' then 100 when '1 No' then 90 when '2 No' then 75
                when '3 No' then 55 when '4 No' then 30 when 'Yağlık' then 10 else 0 end) as quality_sum,
            sum(case when w.med is not null then w.kg * w.med else 0 end) as expected,
            sum(case when w.med is not null then w.amount else 0 end) as compared_amount
        from with_med w group by w.ph
    ),
    per_grade as (
        select ph, gr, sum(kg) as kg from per_line group by ph, gr
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
            least(100, greatest(0, 100 * ln(1 + p.kg) / ln(1 + 10000::numeric))) as volume,
            case when p.expected > 0 then p.compared_amount / p.expected else 1 end as ratio
        from producer p
    ),
    eligible as (
        -- Aylık sıralamada en az 100 kg şartı sezonluk yerine ay içinde aranır (ay = kendi yarışı).
        select s.ph,
               round(least(100, greatest(0, 0.5 * s.quality + 0.35 * s.price_score + 0.15 * s.volume)), 2) as sc,
               s.quality, s.avg_price
        from scored s
        where s.kg >= 100 and s.ratio >= 0.25 and s.ratio <= 3
    ),
    ranked as (
        select e.ph, e.sc, e.avg_price, row_number() over (order by e.sc desc, e.quality desc, e.ph) as rk
        from eligible e
    )
    select
        r.rk::integer, case when pr.show_name then pr.display_name else null end,
        (r.ph = v_hash), r.sc, d.gr,
        (select n from slice_size), (select count(*)::integer from ranked),
        -- Ortalama TL/kg: adı görünmeyen satırda her zaman; adı görünen satırda yalnız onay sürümü 2 ise.
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


revoke all on function public.league_set_profile_v2(text, boolean, integer) from public;
revoke all on function public.league_get_profile_v2() from public;
revoke all on function public.league_region_ranking_v3(integer, text, text, integer) from public;
do $$
declare r text;
begin
    foreach r in array array['anon', 'authenticated'] loop
        if exists (select 1 from pg_roles where rolname = r) then
            execute format('grant execute on function public.league_set_profile_v2(text, boolean, integer) to %I', r);
            execute format('grant execute on function public.league_get_profile_v2() to %I', r);
            execute format('grant execute on function public.league_region_ranking_v3(integer, text, text, integer) to %I', r);
        end if;
    end loop;
end $$;

commit;
