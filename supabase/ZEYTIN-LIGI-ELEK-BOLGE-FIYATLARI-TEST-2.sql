-- Zeytin Defteri / Zeytin Ligi
-- Elek bazli bolgesel fiyat kartlari - TEST modu
-- Fiyat 1+ anonim ureticide gorunur; toplam kg yalniz 2+ ureticide acilir.
-- Isim, UID, bahce adi ve tekil ham kayit DONMEZ.

begin;

create or replace function public.market_league_grade_regions(
    p_season_year integer,
    p_grade text
)
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
declare
    v_grade text;
begin
    if coalesce(auth.jwt() ->> 'sub', '') = '' then
        raise exception 'authentication required' using errcode = '42501';
    end if;

    if p_season_year < 2000 or p_season_year > 2100 then
        raise exception 'invalid season year' using errcode = '22023';
    end if;

    v_grade := case upper(trim(coalesce(p_grade, '')))
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
        else null
    end;

    if v_grade is null then
        raise exception 'invalid grade' using errcode = '22023';
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
          and gr = v_grade
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
    )
    select
        rb.rk::text as region_key,
        rb.rl::text as region_label,
        rb.pc as producer_count,
        true as published,
        case when rb.pc >= 2 then rb.kg_sum else null::numeric end as total_kg,
        case
            when rb.kg_sum > 0 then round(rb.amount_sum / rb.kg_sum, 2)
            else null::numeric
        end as average_price,
        v_grade::text as dominant_grade
    from region_base rb
    where rb.pc >= 1
    order by rb.rl asc;
end;
$$;

revoke all on function public.market_league_grade_regions(integer, text) from public;
grant execute on function public.market_league_grade_regions(integer, text) to anon, authenticated;

commit;

-- Beklenen davranis:
-- 1 uretici: fiyat gorunur, toplam kg NULL (dusuk guven on izlemesi)
-- 2+ uretici: fiyat + toplam kg gorunur
