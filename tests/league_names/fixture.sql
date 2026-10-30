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
