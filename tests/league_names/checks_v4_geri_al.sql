\set QUIET on
do $$
declare n int;
begin
  perform set_config('test.sub', 'g5', false);
  select count(*) into n from public.league_region_ranking_v3(2026,'alasehir','',0);
  assert n > 3, 'geri alınca sıralama herkesi gösterir: ' || n;
  assert has_function_privilege('authenticated', 'public.league_region_ranking_v2(integer,text,text,integer)', 'execute'), 'v2 geri açıldı';
  assert to_regprocedure('public.league_set_participation(boolean)') is null, 'katılım fonksiyonu kaldırıldı';
  select count(*) into n from private.league_participants; assert n >= 2, 'katılım kayıtları korunur: ' || n;
end $$;
select 'PASS: V4 geri alma kontrolleri' as sonuc;
