-- Zeytin Defteri / Zeytin Ligi
-- Bölge Raporu V10.1 - üretici dengeli elek trendi + RPC runtime fix.
-- Anonimlik eşiği: 2 farklı üretici.
-- Trend: aynı elek için son 7 gün medyanı / önceki 7 gün medyanı.
-- Her üretici dönem içinde önce tek fiyata indirgenir; çok kayıt girerek trendi domine edemez.
-- Ham UID, üretici adı, bahçe adı veya tekil ham satış satırı istemciye dönmez.

begin;

create or replace function public.market_league_region_report(
    p_season_year integer,
    p_region_key text,
    p_days integer default 30
)
returns table(
    region_key text,
    region_label text,
    published boolean,
    producer_count bigint,
    sale_count bigint,
    total_kg numeric,
    average_price numeric,
    median_price numeric,
    min_price numeric,
    max_price numeric,
    dominant_grade text,
    trend_pct numeric,
    trust_label text,
    grade_breakdown jsonb,
    chart jsonb
)
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_threshold integer := 2;
begin
    if coalesce(auth.jwt() ->> 'sub', '') = '' then
        raise exception 'authentication required' using errcode = '42501';
    end if;

    if p_season_year < 2000 or p_season_year > 2100 then
        raise exception 'invalid season year' using errcode = '22023';
    end if;

    if p_region_key is null
       or length(trim(p_region_key)) < 3
       or length(trim(p_region_key)) > 100 then
        raise exception 'invalid region key' using errcode = '22023';
    end if;

    if p_days < 14 or p_days > 180 then
        raise exception 'invalid day window' using errcode = '22023';
    end if;

    return query
    with raw as (
        select to_jsonb(c) as j
        from private.market_contributions c
    ),
    normalized as (
        select
            coalesce(nullif(j ->> 'region_key', ''), nullif(j ->> 'region_id', '')) as rk,
            coalesce(
                nullif(j ->> 'region_label', ''),
                nullif(j ->> 'region_name', ''),
                nullif(j ->> 'region_key', ''),
                nullif(j ->> 'region_id', '')
            ) as rl,
            coalesce(nullif(j ->> 'producer_hash', ''), '') as ph,
            coalesce(nullif(j ->> 'source_record_id', ''), nullif(j ->> 'source_key', ''), '') as sk,
            case upper(trim(coalesce(j ->> 'grade', '')))
                when 'DOUBLE' then 'Double'
                when '1' then '1 No'
                when '1 NO' then '1 No'
                when '2' then '2 No'
                when '2 NO' then '2 No'
                when '3' then '3 No'
                when '3 NO' then '3 No'
                when '4' then '4 No'
                when '4 NO' then '4 No'
                when 'YAGLIK' then 'Yağlık'
                when 'YAĞLIK' then 'Yağlık'
                else trim(coalesce(j ->> 'grade', ''))
            end as gr,
            case
                when coalesce(j ->> 'kg', '') ~ '^[0-9]+([.][0-9]+)?$'
                    then (j ->> 'kg')::numeric
                else 0::numeric
            end as kg_value,
            case
                when coalesce(j ->> 'price_per_kg', j ->> 'price', '') ~ '^[0-9]+([.][0-9]+)?$'
                    then coalesce(j ->> 'price_per_kg', j ->> 'price')::numeric
                else 0::numeric
            end as price_value,
            case
                when coalesce(j ->> 'sale_date', j ->> 'record_date', '') ~ '^[0-9]{4}-[0-9]{2}-[0-9]{2}$'
                    then coalesce(j ->> 'sale_date', j ->> 'record_date')::date
                else null::date
            end as sd,
            case
                when coalesce(j ->> 'season_year', '') ~ '^[0-9]{4}$'
                    then (j ->> 'season_year')::integer
                else 0
            end as season_value
        from raw
    ),
    valid0 as (
        select *
        from normalized
        where season_value = p_season_year
          and rk = trim(p_region_key)
          and rk <> ''
          and rl is not null and rl <> ''
          and ph <> ''
          and gr in ('Double','1 No','2 No','3 No','4 No','Yağlık')
          and kg_value > 0
          and price_value > 0
    ),
    valid as (
        select
            *,
            coalesce(
                nullif(sk, ''),
                md5(ph || '|' || coalesce(sd::text, '') || '|' || kg_value::text || '|' || price_value::text)
            ) as sale_key
        from valid0
    ),

    -- Genel özet: fiyat tarafında her üretici önce tek bir ağırlıklı fiyata indirgenir.
    -- Böylece bir üreticinin çok sayıda kayıt girmesi fiyatı orantısız biçimde oynatmaz.
    producer_all as (
        select
            ph,
            sum(kg_value)::numeric as kg_sum,
            sum(kg_value * price_value)::numeric as amount_sum,
            count(distinct sale_key)::bigint as sale_count
        from valid
        group by ph
    ),
    base as (
        select
            (select max(rk) from valid) as rk,
            (select max(rl) from valid) as rl,
            count(*)::bigint as pc,
            (select count(distinct sale_key)::bigint from valid) as sc,
            sum(pa.kg_sum)::numeric as kg_sum,
            avg(pa.amount_sum / nullif(pa.kg_sum, 0))::numeric as producer_balanced_average,
            (select count(distinct sd)::integer from valid where sd is not null) as day_count
        from producer_all pa
        having count(*) > 0
    ),

    grade_catalog(gr, ord) as (
        values
            ('Double'::text, 1),
            ('1 No'::text, 2),
            ('2 No'::text, 3),
            ('3 No'::text, 4),
            ('4 No'::text, 5),
            ('Yağlık'::text, 6)
    ),

    -- Elek özeti de üretici dengeli fiyat kullanır.
    grade_producer as (
        select
            gr,
            ph,
            sum(kg_value)::numeric as kg_sum,
            sum(kg_value * price_value)::numeric as amount_sum,
            count(distinct sale_key)::bigint as sale_count
        from valid
        group by gr, ph
    ),
    grade_stats as (
        select
            gp.gr,
            count(*)::bigint as pc,
            sum(gp.sale_count)::bigint as sc,
            sum(gp.kg_sum)::numeric as kg_sum,
            avg(gp.amount_sum / nullif(gp.kg_sum, 0))::numeric as balanced_average,
            percentile_cont(0.5) within group (
                order by (gp.amount_sum / nullif(gp.kg_sum, 0))
            )::numeric as balanced_median
        from grade_producer gp
        group by gp.gr
    ),
    dominant as (
        select gr
        from grade_stats
        order by kg_sum desc nulls last, gr asc
        limit 1
    ),
    dominant_stats as (
        select gs.*
        from grade_stats gs
        join dominant d on d.gr = gs.gr
    ),

    -- Aynı üreticinin aynı gün aynı elekte kaç kayıt girdiğinden bağımsız tek günlük fiyatı.
    producer_day_grade as (
        select
            gr,
            ph,
            sd,
            percentile_cont(0.5) within group (order by price_value)::numeric as daily_price
        from valid
        where sd is not null
          and sd >= current_date - greatest(p_days - 1, 29)
          and sd <= current_date
        group by gr, ph, sd
    ),

    -- Son 7 gün ve önceki 7 gün: üretici önce dönem içinde tek medyana indirgenir.
    period_producer as (
        select
            gr,
            ph,
            case
                when sd >= current_date - 6 then 'current'
                when sd >= current_date - 13 and sd <= current_date - 7 then 'previous'
                else null
            end as period_key,
            percentile_cont(0.5) within group (order by daily_price)::numeric as producer_period_price
        from producer_day_grade
        where sd >= current_date - 13
        group by gr, ph,
            case
                when sd >= current_date - 6 then 'current'
                when sd >= current_date - 13 and sd <= current_date - 7 then 'previous'
                else null
            end
    ),
    period_grade as (
        select
            gr,
            period_key,
            count(*)::bigint as producer_count,
            percentile_cont(0.5) within group (order by producer_period_price)::numeric as period_median
        from period_producer
        where period_key is not null
        group by gr, period_key
    ),
    period_days as (
        select
            gr,
            case
                when sd >= current_date - 6 then 'current'
                when sd >= current_date - 13 and sd <= current_date - 7 then 'previous'
                else null
            end as period_key,
            count(distinct sd)::integer as day_count
        from producer_day_grade
        where sd >= current_date - 13
        group by gr,
            case
                when sd >= current_date - 6 then 'current'
                when sd >= current_date - 13 and sd <= current_date - 7 then 'previous'
                else null
            end
    ),
    grade_trend as (
        select
            gc.gr,
            coalesce(cur.producer_count, 0)::bigint as current_producer_count,
            coalesce(prev.producer_count, 0)::bigint as previous_producer_count,
            coalesce(cd.day_count, 0)::integer as current_day_count,
            coalesce(pd.day_count, 0)::integer as previous_day_count,
            cur.period_median::numeric as current_median,
            prev.period_median::numeric as previous_median,
            (
                coalesce(cur.producer_count, 0) >= v_threshold
                and coalesce(prev.producer_count, 0) >= v_threshold
                and coalesce(cd.day_count, 0) >= 2
                and coalesce(pd.day_count, 0) >= 2
                and coalesce(prev.period_median, 0) > 0
            ) as trend_available,
            case
                when coalesce(cur.producer_count, 0) >= v_threshold
                 and coalesce(prev.producer_count, 0) >= v_threshold
                 and coalesce(cd.day_count, 0) >= 2
                 and coalesce(pd.day_count, 0) >= 2
                 and coalesce(prev.period_median, 0) > 0
                    then round(((cur.period_median - prev.period_median) * 100.0 / prev.period_median), 2)
                else null::numeric
            end as trend_pct
        from grade_catalog gc
        left join period_grade cur on cur.gr = gc.gr and cur.period_key = 'current'
        left join period_grade prev on prev.gr = gc.gr and prev.period_key = 'previous'
        left join period_days cd on cd.gr = gc.gr and cd.period_key = 'current'
        left join period_days pd on pd.gr = gc.gr and pd.period_key = 'previous'
    ),

    grade_json as (
        select jsonb_agg(
            jsonb_build_object(
                'grade', gc.gr,
                'producer_count', coalesce(gs.pc, 0),
                'sale_count', case when coalesce(gs.pc, 0) >= v_threshold then coalesce(gs.sc, 0) else 0 end,
                'kg', case when coalesce(gs.pc, 0) >= v_threshold then gs.kg_sum else null end,
                'average_price', case
                    when coalesce(gs.pc, 0) >= 1 then round(gs.balanced_average, 2)
                    else null
                end,
                'median_price', case
                    when coalesce(gs.pc, 0) >= 1 then round(gs.balanced_median, 2)
                    else null
                end,
                'trend_available', coalesce(gt.trend_available, false),
                'trend_pct', gt.trend_pct,
                'trend_current_median', case when gt.trend_available then round(gt.current_median, 2) else null end,
                'trend_previous_median', case when gt.trend_available then round(gt.previous_median, 2) else null end,
                'trend_current_producers', gt.current_producer_count,
                'trend_previous_producers', gt.previous_producer_count,
                'trend_current_days', gt.current_day_count,
                'trend_previous_days', gt.previous_day_count
            ) order by gc.ord
        ) as value
        from grade_catalog gc
        left join grade_stats gs on gs.gr = gc.gr
        left join grade_trend gt on gt.gr = gc.gr
    ),

    -- Min/medyan/max artık farklı elekleri karıştırmaz; baskın eleğin son 30 günü kullanılır.
    dominant_recent_producer as (
        select
            pdg.ph,
            percentile_cont(0.5) within group (order by pdg.daily_price)::numeric as producer_price
        from producer_day_grade pdg
        join dominant d on d.gr = pdg.gr
        where pdg.sd >= current_date - 29
        group by pdg.ph
    ),
    dominant_recent_summary as (
        select
            percentile_cont(0.5) within group (order by producer_price)::numeric as med,
            min(producer_price)::numeric as min_p,
            max(producer_price)::numeric as max_p
        from dominant_recent_producer
    ),

    -- Grafik baskın eleğe aittir. Haftalık noktada her üretici tek oy taşır.
    dominant_week_producer as (
        select
            date_trunc('week', pdg.sd)::date as week_start,
            pdg.ph,
            percentile_cont(0.5) within group (order by pdg.daily_price)::numeric as producer_week_price
        from producer_day_grade pdg
        join dominant d on d.gr = pdg.gr
        where pdg.sd >= current_date - 29
        group by date_trunc('week', pdg.sd)::date, pdg.ph
    ),
    safe_weekly as (
        select
            week_start,
            count(*)::bigint as producer_count,
            percentile_cont(0.5) within group (order by producer_week_price)::numeric as price
        from dominant_week_producer
        group by week_start
        having count(*) >= v_threshold
        order by week_start
    ),
    chart_json as (
        select coalesce(
            jsonb_agg(
                jsonb_build_object(
                    'market_date', sw.week_start::text,
                    'price', round(sw.price, 2),
                    'producer_count', sw.producer_count
                ) order by sw.week_start
            ),
            '[]'::jsonb
        ) as value
        from safe_weekly sw
    ),
    dominant_trend as (
        select gt.*
        from grade_trend gt
        join dominant d on d.gr = gt.gr
    )

    select
        b.rk::text as region_key,
        b.rl::text as region_label,
        (b.pc >= v_threshold) as published,
        b.pc as producer_count,
        case when b.pc >= v_threshold then b.sc else null::bigint end as sale_count,
        case when b.pc >= v_threshold then b.kg_sum else null::numeric end as total_kg,
        case
            when b.pc >= v_threshold then round(b.producer_balanced_average, 2)
            else null::numeric
        end as average_price,
        case
            when b.pc >= v_threshold and ds.pc >= v_threshold then round(drs.med, 2)
            else null::numeric
        end as median_price,
        case
            when b.pc >= v_threshold and ds.pc >= v_threshold then drs.min_p
            else null::numeric
        end as min_price,
        case
            when b.pc >= v_threshold and ds.pc >= v_threshold then drs.max_p
            else null::numeric
        end as max_price,
        case when b.pc >= v_threshold then d.gr::text else null::text end as dominant_grade,
        case
            when b.pc >= v_threshold and dt.trend_available then dt.trend_pct
            else null::numeric
        end as trend_pct,
        case
            when dt.trend_available
             and dt.current_producer_count >= 5
             and dt.previous_producer_count >= 5
             and (dt.current_day_count + dt.previous_day_count) >= 7 then 'Yüksek'
            when dt.trend_available then 'Orta'
            when b.pc >= 1 then 'Düşük'
            else 'Veri yok'
        end::text as trust_label,
        gj.value as grade_breakdown,
        cj.value as chart
    from base b
    cross join grade_json gj
    cross join dominant d
    cross join dominant_stats ds
    cross join dominant_recent_summary drs
    cross join dominant_trend dt
    cross join chart_json cj;
end;
$$;

revoke all on function public.market_league_region_report(integer, text, integer) from public;
grant execute on function public.market_league_region_report(integer, text, integer) to anon, authenticated;

commit;

-- V10 davranışı:
-- * Trend yalnız aynı elek içinde hesaplanır.
-- * Son 7 gün / önceki 7 gün üretici-dengeli medyan karşılaştırılır.
-- * Aynı üreticinin çok sayıda kaydı trendde tek üretici ağırlığına indirgenir.
-- * Trend için her iki 7 günlük pencerede anonimlik eşiği + en az 2 farklı gün gerekir.
-- * Genel min/medyan/max baskın eleğin son 30 gününden gelir; farklı elek fiyatları karışmaz.
-- * Elek dağılımındaki her elek kendi trend_pct değerini taşır.
-- * V10.1: PL/pgSQL RETURNS TABLE isim çakışmaları giderildi; rapor RPC artık fallback
--   yerine toplam satış + elek satış adedi + trend alanlarını doğrudan döndürür.
