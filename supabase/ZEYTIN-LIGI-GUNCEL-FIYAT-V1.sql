-- Zeytin Defteri / Piyasa
-- Güncel elek fiyatı V1: bölgedeki TÜM üreticilerin son 3 günlük elek satışlarının ortadaki fiyatı (medyan).
-- Son 3 günde o elekte satış yoksa 7 güne, o da yoksa 30 güne bakılır; hangi pencereden geldiği döner.
-- Kullanım: Piyasa kartının ana rakamı, yeni elek kaydının fiyat önerisi, sesli elek tutarı.
-- Bölge eşiği: bölgede sezon boyunca en az 5 farklı üretici (bölge raporuyla aynı; Alaşehir açık).
-- Pencere içinde ayrıca üretici sayısı şartı yok (ürün kararı, 2 Kasım 2026).
-- Ham UID, üretici adı, bahçe adı, kg ya da tekil satış satırı istemciye dönmez; yalnız elek başına fiyat.
-- Tekrar çalıştırmak zararsızdır (create or replace).

begin;

create or replace function public.market_region_current_prices(
    p_season_year integer,
    p_region_key text
)
returns table(
    grade text,
    price numeric,
    window_days integer,
    sale_count bigint,
    producer_count bigint,
    last_sale_date date
)
language plpgsql
stable
security definer
set search_path = ''
as $$
declare
    v_region_floor integer := 5;
    v_today date := (now() at time zone 'Europe/Istanbul')::date;
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

    return query
    with raw as (
        select to_jsonb(c) as j
        from private.market_contributions c
    ),
    normalized as (
        select
            coalesce(nullif(j ->> 'region_key', ''), nullif(j ->> 'region_id', '')) as rk,
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
            end as pv,
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
    region_rows as (
        select *
        from normalized
        where season_value = p_season_year
          and rk = trim(p_region_key)
          and ph <> ''
          and gr in ('Double', '1 No', '2 No', '3 No', '4 No', 'Yağlık')
          and kg_value > 0
          and pv > 0
    ),
    region_ok as (
        select count(distinct ph)::integer as n
        from region_rows
    ),
    -- Aynı satış iki kez gönderildiyse bir kez sayılır.
    sales as (
        select distinct on (
            coalesce(nullif(sk, ''), md5(ph || '|' || coalesce(sd::text, '') || '|' || kg_value::text || '|' || pv::text)),
            gr
        )
            ph, gr, pv, sd
        from region_rows
        where sd is not null
          and sd <= v_today
    ),
    windows(wd) as (
        values (3), (7), (30)
    ),
    per_window as (
        select
            w.wd,
            s.gr,
            percentile_cont(0.5) within group (order by s.pv)::numeric as med,
            count(*)::bigint as sc,
            count(distinct s.ph)::bigint as pc,
            max(s.sd) as ld
        from windows w
        join sales s on s.sd > v_today - w.wd
        group by w.wd, s.gr
    ),
    picked as (
        -- Her elek için satışı olan en kısa pencere (önce 3 gün).
        select distinct on (p.gr) p.*
        from per_window p
        order by p.gr, p.wd asc
    )
    select
        pk.gr::text,
        round(pk.med, 2),
        pk.wd::integer,
        pk.sc,
        pk.pc,
        pk.ld
    from picked pk
    cross join region_ok r
    where r.n >= v_region_floor
    order by array_position(array['Double', '1 No', '2 No', '3 No', '4 No', 'Yağlık'], pk.gr);
end;
$$;

revoke all on function public.market_region_current_prices(integer, text) from public;
grant execute on function public.market_region_current_prices(integer, text) to anon, authenticated;

commit;
