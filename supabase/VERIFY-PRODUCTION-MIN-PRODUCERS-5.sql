-- READ ONLY. SQL Editor: inspect active V7 read RPCs and their helper definitions.
-- This prints server SQL definitions, not service-account credentials or user data.
-- Verify all overloads, DISTINCT producer grouping, 5 threshold, current/previous
-- windows and chart buckets; definition text alone is not an end-to-end PASS.
select n.nspname as schema_name, p.proname as function_name,
       pg_get_function_identity_arguments(p.oid) as identity_args,
       pg_get_functiondef(p.oid) as definition
from pg_proc p join pg_namespace n on n.oid=p.pronamespace
where n.nspname in ('public','private') and p.prokind='f'
  and p.proname ~ '^(market_|zd_market_)'
order by n.nspname,p.proname,identity_args;
