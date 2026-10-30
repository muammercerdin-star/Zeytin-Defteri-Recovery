-- Zeytin Defteri / Zeytin Ligi — ADLI ÖN SIRALAMA V1
--
-- Supabase SQL Editor'de BİR KEZ çalıştır (tekrar çalıştırmak zararsızdır).
--
-- Ne yapar?
--   * private.league_profiles: kullanıcının ligde görünecek adı ve "adımla görüneyim" izni.
--     Anahtar, mevcut private.market_producer_hash() ile üretilen anonim üretici kimliğidir;
--     Firebase UID saklanmaz.
--   * public.league_set_profile(ad, göster): izni açar/kapatır. Kapatınca ad SİLİNİR.
--   * public.league_get_profile(): kendi ayarın.
--   * public.league_region_ranking(sezon, bölge): bölgede en az 5 farklı üretici varsa ön sıralama.
--       - Ad yalnız izni açık olanlarda döner; diğerleri ad olmadan ("Anonim üretici").
--       - Kişinin kg'ı, TL/kg fiyatı ya da tutarı DÖNMEZ; yalnız sıra, puan, en çok çıkan numara.
--       - Puan uygulamadaki formülle: %50 elek kalitesi, %35 fiyat (bölge medyanına göre), %15 hacim.
--       - En az 100 kg olmayan ya da aşırı uç fiyatlı (bölge beklentisinin 1/4'ü altı / 3 katı üstü)
--         üretici sıralanmaz.
--       - Elek kayıtlarına dayanır; doğrulanmış satış değildir (uygulamada böyle etiketlenir).
--
-- Mevcut tablolara ve market_* fonksiyonlarına DOKUNMAZ.

begin;

create table if not exists private.league_profiles (
    producer_hash text primary key,
    display_name  text not null,
    show_name     boolean not null default true,
    updated_at    timestamptz not null default now(),
    constraint league_profiles_name_len check (char_length(display_name) between 2 and 40)
);

alter table private.league_profiles enable row level security;
revoke all on table private.league_profiles from public;
do $$
begin
    if exists (select 1 from pg_roles where rolname = 'anon') then
        execute 'revoke all on table private.league_profiles from anon';
    end if;
    if exists (select 1 from pg_roles where rolname = 'authenticated') then
        execute 'revoke all on table private.league_profiles from authenticated';
    end if;
end $$;

-- ------------------------------------------------------------------ ad / izin

create or replace function public.league_set_profile(p_display_name text, p_show_name boolean)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
    v_hash text;
    v_name text;
begin
    if coalesce(auth.jwt() ->> 'sub', '') = '' then
        raise exception 'authentication required' using errcode = '42501';
    end if;
    v_hash := private.market_producer_hash();

    if not coalesce(p_show_name, false) then
        delete from private.league_profiles where producer_hash = v_hash;
        return jsonb_build_object('ok', true, 'show_name', false);
    end if;

    -- Boşlukları sadeleştir; kontrol karakteri, link ve aşırı uzun ad kabul edilmez.
    v_name := regexp_replace(btrim(coalesce(p_display_name, '')), '\s+', ' ', 'g');
    if char_length(v_name) < 2 or char_length(v_name) > 40
       or v_name ~ '[[:cntrl:]]'
       or v_name ~* '(https?://|www\.|@)'
    then
        raise exception 'Invalid display name' using errcode = '22023';
    end if;

    insert into private.league_profiles (producer_hash, display_name, show_name, updated_at)
    values (v_hash, v_name, true, now())
    on conflict (producer_hash) do update
        set display_name = excluded.display_name, show_name = true, updated_at = now();

    return jsonb_build_object('ok', true, 'show_name', true, 'display_name', v_name);
end;
$$;

create or replace function public.league_get_profile()
returns table(display_name text, show_name boolean)
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
    return query
        select p.display_name, p.show_name
        from private.league_profiles p
        where p.producer_hash = v_hash;
end;
$$;

-- ------------------------------------------------------------------ bölge ön sıralaması

create or replace function public.league_region_ranking(p_season_year integer, p_region_key text)
returns table(
    rank           integer,
    display_name   text,
    is_me          boolean,
    score          numeric,
    dominant_grade text,
    producer_count integer,
    ranked_count   integer
)
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
    if p_season_year is null or p_season_year < 2000 or p_season_year > 2100 then
        raise exception 'invalid season year' using errcode = '22023';
    end if;
    if p_region_key is null or p_region_key !~ '^[a-z0-9_]{2,80}$' then
        raise exception 'invalid region key' using errcode = '22023';
    end if;
    v_hash := private.market_producer_hash();

    return query
    with rows as (
        select c.producer_hash as ph, c.grade as gr, c.kg::numeric as kg, c.price_per_kg::numeric as price
        from private.market_contributions c
        where c.season_year = p_season_year
          and c.region_key = p_region_key
          and c.kg > 0 and c.price_per_kg > 0
    ),
    region_size as (
        select count(distinct ph)::integer as n from rows
    ),
    medians as (
        -- Numara başına bölge medyan fiyatı; en az 3 farklı üretici yoksa o numara kıyaslanmaz.
        select gr, percentile_cont(0.5) within group (order by price)::numeric as med
        from rows
        group by gr
        having count(distinct ph) >= 3
    ),
    per_grade as (
        select r.ph, r.gr, sum(r.kg) as kg, sum(r.kg * r.price) as amount
        from rows r
        group by r.ph, r.gr
    ),
    producer as (
        select
            g.ph,
            sum(g.kg) as kg,
            sum(g.amount) as amount,
            sum(g.kg * case g.gr
                when 'Double' then 100 when '1 No' then 90 when '2 No' then 75
                when '3 No' then 55 when '4 No' then 30 when 'Yağlık' then 10 else 0 end) as quality_sum,
            -- Bölge medyanı olan numaralarda beklenen tutar ve gerçek tutar.
            sum(case when m.med is not null then g.kg * m.med else 0 end) as expected,
            sum(case when m.med is not null then g.amount else 0 end) as compared_amount
        from per_grade g
        left join medians m on m.gr = g.gr
        group by g.ph
    ),
    dominant as (
        select x.ph, x.gr
        from (
            select g.ph, g.gr, row_number() over (partition by g.ph order by g.kg desc, g.gr) as rn
            from per_grade g
        ) x
        where x.rn = 1
    ),
    scored as (
        select
            p.ph,
            p.kg,
            least(100, greatest(0, p.quality_sum / p.kg)) as quality,
            case when p.expected > 0
                 then least(100, greatest(0, 100 * (p.compared_amount / p.expected - 0.5)))
                 else 50 end as price_score,
            least(100, greatest(0, 100 * ln(1 + p.kg) / ln(1 + 10000::numeric))) as volume,
            case when p.expected > 0 then p.compared_amount / p.expected else 1 end as ratio
        from producer p
    ),
    eligible as (
        select s.ph,
               round(least(100, greatest(0, 0.5 * s.quality + 0.35 * s.price_score + 0.15 * s.volume)), 2) as sc,
               s.quality
        from scored s
        where s.kg >= 100 and s.ratio >= 0.25 and s.ratio <= 3
    ),
    ranked as (
        select e.ph, e.sc,
               row_number() over (order by e.sc desc, e.quality desc, e.ph) as rk
        from eligible e
    )
    select
        r.rk::integer as rank,
        case when pr.show_name then pr.display_name else null end as display_name,
        (r.ph = v_hash) as is_me,
        r.sc as score,
        d.gr as dominant_grade,
        (select n from region_size) as producer_count,
        (select count(*)::integer from ranked) as ranked_count
    from ranked r
    left join private.league_profiles pr on pr.producer_hash = r.ph
    left join dominant d on d.ph = r.ph
    where (select n from region_size) >= 5   -- gizlilik eşiği: 5 farklı üretici
      and r.rk <= 50
    order by r.rk;
end;
$$;

revoke all on function public.league_set_profile(text, boolean) from public;
revoke all on function public.league_get_profile() from public;
revoke all on function public.league_region_ranking(integer, text) from public;
do $$
begin
    if exists (select 1 from pg_roles where rolname = 'anon') then
        execute 'grant execute on function public.league_set_profile(text, boolean) to anon';
        execute 'grant execute on function public.league_get_profile() to anon';
        execute 'grant execute on function public.league_region_ranking(integer, text) to anon';
    end if;
    if exists (select 1 from pg_roles where rolname = 'authenticated') then
        execute 'grant execute on function public.league_set_profile(text, boolean) to authenticated';
        execute 'grant execute on function public.league_get_profile() to authenticated';
        execute 'grant execute on function public.league_region_ranking(integer, text) to authenticated';
    end if;
end $$;

commit;

-- Kontrol (isteğe bağlı): üç fonksiyon da listelenmeli.
-- select proname from pg_proc where proname in ('league_set_profile','league_get_profile','league_region_ranking');
