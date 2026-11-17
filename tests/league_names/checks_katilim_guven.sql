\set QUIET on
-- V4.1 sonrası: sıralama hem GÜVEN V4 süzgeçlerini hem katılım süzgecini taşır.
do $$
declare d text;
begin
  d := pg_get_functiondef('public.league_region_ranking_v3(integer,text,text,integer)'::regprocedure);
  assert d like '%join private.league_participants lp on lp.producer_hash = s.ph%', 'katılım süzgeci yok';
  assert d like '%abs(s.d - r.d) <= 3%' and d like '%p.price > p.hi * 1.05%' and d like '%mod(p.kg, 50) = 0%', 'GÜVEN V4 süzgeçleri yok';
  assert has_function_privilege('authenticated', 'public.league_region_ranking_v3(integer,text,text,integer)', 'execute'), 'v3 açık';
  assert not has_function_privilege('authenticated', 'public.league_region_ranking_v2(integer,text,text,integer)', 'execute'), 'v2 kapalı kalmalı';
end $$;
select 'PASS: V4.1 GÜVEN + katılım kontrolleri' as sonuc;
