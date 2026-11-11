\set QUIET on
-- GÜVEN V4: haftalık piyasa kıyası + şüpheli kayıt süzgeci. Alaşehir benzeri veri:
-- ekimde Double 130 / 1 No 100 / 2 No 70 / 3 No 40; kasımda 110 / 80 / 55 / 30.
-- 'x' 10 Kasım'da ekim fiyatlarını ve yuvarlak kiloları yazıyor (50, 300, 500, 40).
delete from private.market_contributions;
create or replace function pg_temp.put(sub text, rec text, grade text, kg numeric, price numeric, d date) returns void
language plpgsql as $$ begin
  perform set_config('test.sub', sub, false);
  perform public.market_submit_v2(rec, 'alasehir', 'Alaşehir / Manisa', grade, kg, price, d, 2026, '');
end $$;
do $$
declare p text; i int := 0;
begin
  foreach p in array array['p1','p2','p3','p4','p5','p6','p7'] loop
    i := i + 1;
    perform pg_temp.put(p, p||'o1', 'Double', 3, 130, ('2026-10-20'::date + i));
    perform pg_temp.put(p, p||'o2', '1 No', 20 + i, 100, ('2026-10-20'::date + i));
    perform pg_temp.put(p, p||'o3', '2 No', 40 + i, 70, ('2026-10-20'::date + i));
    perform pg_temp.put(p, p||'o4', '3 No', 30 + i, 40, ('2026-10-20'::date + i));
    perform pg_temp.put(p, p||'n1', 'Double', 2, 110, ('2026-11-05'::date + i));
    perform pg_temp.put(p, p||'n2', '1 No', 15 + i, 80, ('2026-11-05'::date + i));
    perform pg_temp.put(p, p||'n3', '2 No', 35 + i, 55, ('2026-11-05'::date + i));
    perform pg_temp.put(p, p||'n4', '3 No', 25 + i, 30, ('2026-11-05'::date + i));
  end loop;
  perform pg_temp.put('x', 'x1', 'Double', 50, 130, '2026-11-10');
  perform pg_temp.put('x', 'x2', '1 No', 300, 100, '2026-11-10');
  perform pg_temp.put('x', 'x3', '2 No', 500, 70, '2026-11-10');
  perform pg_temp.put('x', 'x4', '3 No', 40, 40, '2026-11-10');
  -- dürüst büyük satış: 500 kg 2 No, kasım fiyatı 55 (yuvarlak → yarım ağırlık, ama sıralamada)
  perform pg_temp.put('h', 'h1', '2 No', 500, 55, '2026-11-09');
end $$;
do $$
declare r record; n int; old_rank int;
begin
  perform set_config('test.sub', 'x', false);
  -- V3'teki eski hesap (karşılaştırma için, test içinde yeniden kurulmaz): x'in kayıtları 1 No/2 No/3 No'da
  -- piyasanın %25+ üstünde → süzgeçten çıkar; geriye 50 kg Double kalır (< 100 kg) → x sıralamada YOK.
  select count(*) into n from public.league_region_ranking_v3(2026,'alasehir','',0) where is_me;
  assert n = 0, 'x (ekim fiyatı + yuvarlak kilo) sıralamaya girmemeli';
  -- slice: 9 üretici görünür (p1..p7, x, h); sıralanan 8 (x hariç)
  select * into r from public.league_region_ranking_v3(2026,'alasehir','',0) limit 1;
  assert r.producer_count = 9 and r.ranked_count = 8, 'üretici/sıralanan: ' || r.producer_count || '/' || r.ranked_count;
  -- dürüst büyük satış (h) sıralamada; ortalaması piyasa fiyatı 55
  perform set_config('test.sub', 'h', false);
  select * into r from public.league_region_ranking_v3(2026,'alasehir','',0) where is_me;
  assert r.rank is not null and r.avg_price = 55, 'h sıralamada, 55: ' || coalesce(r.avg_price::text, 'yok');
  -- ekimde satan kayırılmaz: p'lerin fiyat puanı kendi haftasıyla kıyaslanır → hepsi piyasada (oran 1)
  perform set_config('test.sub', 'p1', false);
  select * into r from public.league_region_ranking_v3(2026,'alasehir','',0) where is_me;
  assert r.rank is not null, 'p1 sıralamada';
  -- kasım dilimi: x yine yok
  perform set_config('test.sub', 'x', false);
  select count(*) into n from public.league_region_ranking_v3(2026,'alasehir','',11) where is_me;
  assert n = 0, 'kasım diliminde de x yok';
  -- x fiyatlarını kasım piyasasına düzeltirse (110/80/55/30) geri gelir
  perform pg_temp.put('x', 'x1', 'Double', 50, 110, '2026-11-10');
  perform pg_temp.put('x', 'x2', '1 No', 300, 80, '2026-11-10');
  perform pg_temp.put('x', 'x3', '2 No', 500, 55, '2026-11-10');
  perform pg_temp.put('x', 'x4', '3 No', 40, 30, '2026-11-10');
  perform set_config('test.sub', 'x', false);
  select * into r from public.league_region_ranking_v3(2026,'alasehir','',0) where is_me;
  assert r.rank is not null, 'x düzeltince sıralamada';
end $$;
do $$
declare cols text;
begin
  select string_agg(a, ',') into cols from (select unnest(proargnames) a from pg_proc where proname = 'league_region_ranking_v3') x;
  assert cols !~ '(\mkg\M|amount|total)', 'kg/tutar dönmemeli: ' || cols;
  assert has_function_privilege('authenticated','public.league_region_ranking_v3(integer,text,text,integer)','execute'), 'yetki';
end $$;
select 'PASS: güven V4 SQL kontrolleri (haftalık piyasa, süzgeç, yuvarlak kilo)' as sonuc;
