-- Supabase benzeri ortam: auth.jwt(), private.market_producer_hash(), anon/authenticated rolleri.
create schema if not exists auth;
create schema if not exists private;
do $$ begin
  if not exists (select 1 from pg_roles where rolname='anon') then create role anon; end if;
  if not exists (select 1 from pg_roles where rolname='authenticated') then create role authenticated; end if;
end $$;
create or replace function auth.jwt() returns jsonb language sql stable as $$
  select case when coalesce(current_setting('test.sub', true), '') = '' then '{}'::jsonb
              else jsonb_build_object('sub', current_setting('test.sub', true)) end $$;
create or replace function private.market_producer_hash() returns text language sql stable as $$
  select md5('zd:' || current_setting('test.sub', true)) || md5('x' || current_setting('test.sub', true)) $$;
create table if not exists private.market_contributions (
  id uuid default gen_random_uuid() primary key, producer_hash text, source_record_id text, region_key text,
  region_label text, grade text, kg numeric, price_per_kg numeric, sale_date date, season_year integer,
  created_at timestamptz default now(), updated_at timestamptz default now());
create unique index if not exists mc_upsert on private.market_contributions(producer_hash, source_record_id, grade);
-- Canlıdaki private.market_submit_internal'in sade taklidi (doğrulama + upsert + id).
create or replace function private.market_submit_internal(p_source_record_id text, p_region_key text, p_region_label text,
    p_grade text, p_kg numeric, p_price_per_kg numeric, p_sale_date date, p_season_year integer)
returns jsonb language plpgsql security definer as $$
declare v_id uuid;
begin
  if p_kg is null or p_kg <= 0 then raise exception 'Invalid kg'; end if;
  insert into private.market_contributions(producer_hash, source_record_id, region_key, region_label, grade, kg, price_per_kg, sale_date, season_year)
  values (private.market_producer_hash(), p_source_record_id, p_region_key, p_region_label, p_grade, p_kg, p_price_per_kg, p_sale_date, p_season_year)
  on conflict (producer_hash, source_record_id, grade) do update set kg = excluded.kg, price_per_kg = excluded.price_per_kg,
     sale_date = excluded.sale_date, updated_at = now()
  returning id into v_id;
  return jsonb_build_object('ok', true, 'id', v_id);
end $$;
