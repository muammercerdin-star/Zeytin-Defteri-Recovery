-- Zeytin Defteri / Zeytin Ligi
-- PRODUCTION GİZLİLİK MODU: bölgeler 1 üreticiden itibaren dizinde görünür,
-- fakat kg / ortalama fiyat / baskın elek yalnız 5 farklı üreticiye ulaştığında yayınlanır.

begin;

create or replace function public.market_league_regions(p_season_year integer)
returns table(
    region_key text,
    region_label text,
    producer_count bigint,
    published boolean,
    total_kg numeric,
    average_price numeric,
    dominant_grade text
)
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
    if coalesce(auth.jwt() ->> 'sub', '') = '' then
        raise exception 'authentication required' using errcode = '42501';
    end if;

    if p_season_year < 2000 or p_season_year > 2100 then
        raise exception 'invalid season year' using errcode = '22023';
    end if;

    return query
    with raw as (
        select to_jsonb(c) as j
        from private.market_contributions c
    ),
    normalized as (
        select
            coalesce(nullif(j ->> 'region_key', ''), nullif(j ->> 'region_id', '')) as rk,
            coalesce(nullif(j ->> 'region_label', ''), nullif(j ->> 'region_name', ''),
                     nullif(j ->> 'region_key', ''), nullif(j ->> 'region_id', '')) as rl,
            coalesce(nullif(j ->> 'producer_hash', ''), '') as ph,
            coalesce(nullif(j ->> 'grade', ''), '') as gr,
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
                when coalesce(j ->> 'season_year', '') ~ '^[0-9]{4}$'
                    then (j ->> 'season_year')::integer
                else 0
            end as season_value
        from raw
    ),
    valid as (
        select *
        from normalized
        where season_value = p_season_year
          and rk is not null and rk <> ''
          and rl is not null and rl <> ''
          and ph <> ''
          and kg_value > 0
          and price_value > 0
    ),
    region_base as (
        select
            rk,
            max(rl) as rl,
            count(distinct ph)::bigint as pc,
            sum(kg_value)::numeric as kg_sum,
            sum(kg_value * price_value)::numeric as amount_sum
        from valid
        group by rk
    ),
    grade_totals as (
        select rk, gr, sum(kg_value)::numeric as grade_kg
        from valid
        where gr <> ''
        group by rk, gr
    ),
    dominant as (
        select x.rk, x.gr
        from (
            select
                gt.*,
                row_number() over (
                    partition by gt.rk
                    order by gt.grade_kg desc, gt.gr asc
                ) as rn
            from grade_totals gt
        ) x
        where x.rn = 1
    )
    select
        rb.rk::text as region_key,
        rb.rl::text as region_label,
        rb.pc as producer_count,
        (rb.pc >= 5) as published,
        case when rb.pc >= 5 then rb.kg_sum else null::numeric end as total_kg,
        case
            when rb.pc >= 5 and rb.kg_sum > 0
                then round(rb.amount_sum / rb.kg_sum, 2)
            else null::numeric
        end as average_price,
        case when rb.pc >= 5 then d.gr::text else null::text end as dominant_grade
    from region_base rb
    left join dominant d on d.rk = rb.rk
    order by rb.rl asc;
end;
$$;

revoke all on function public.market_league_regions(integer) from public;
grant execute on function public.market_league_regions(integer) to anon, authenticated;

commit;
