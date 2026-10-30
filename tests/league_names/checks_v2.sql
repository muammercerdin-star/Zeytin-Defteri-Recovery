\set QUIET on
create or replace function pg_temp.sub(s text, rec text, grade text, kg numeric, price numeric, d date, va text) returns void language plpgsql as $$
begin perform set_config('test.sub', s, false);
  perform public.market_submit_v2(rec, 'alasehir', 'Alaşehir / Manisa', grade, kg, price, d, 2026, va); end $$;
do $$
declare n int; r record;
begin
  -- 6 Gemlik üreticisi (Ekim), 2 Ayvalık üreticisi (Ekim, pahalı), 1 belirtilmemiş (Eylül)
  perform pg_temp.sub('g1','r1','2 No',300,72,'2026-10-05','gemlik');
  perform pg_temp.sub('g2','r1','2 No',300,70,'2026-10-06','gemlik');
  perform pg_temp.sub('g3','r1','2 No',300,68,'2026-10-07','gemlik');
  perform pg_temp.sub('g4','r1','2 No',300,66,'2026-10-08','gemlik');
  perform pg_temp.sub('g5','r1','2 No',300,64,'2026-10-09','gemlik');
  perform pg_temp.sub('g6','r1','2 No',300,62,'2026-10-10','gemlik');
  perform pg_temp.sub('a1','r1','2 No',300,140,'2026-10-11','ayvalik');
  perform pg_temp.sub('a2','r1','2 No',300,138,'2026-10-12','ayvalik');
  perform pg_temp.sub('x1','r1','2 No',300,70,'2026-09-20','');
  -- aynı kaydı yeniden gönderme: çoğalmaz, cins güncellenir
  perform pg_temp.sub('g1','r1','2 No',300,72,'2026-10-05','gemlik');
  select count(*) into n from private.market_contributions where producer_hash = md5('zd:g1')||md5('xg1');
  assert n = 1, 'yeniden gönderim çoğalmaz: ' || n;
  -- geçersiz cins
  perform set_config('test.sub', 'g1', false);
  begin perform public.market_submit_v2('r9','alasehir','Alaşehir','2 No',10,70,'2026-10-01',2026,'Gem lik'); raise exception 'BOZUK CİNS';
  exception when sqlstate '22023' then null; end;

  perform set_config('test.sub', 'g1', false);
  -- Tüm sezon, tüm cinsler: 9 üretici
  select count(*) into n from public.league_region_ranking_v2(2026,'alasehir','',0); assert n = 9, 'tümü: ' || n;
  -- Gemlik: 6 üretici → açık; birinci en pahalı Gemlik (g1)
  select count(*) into n from public.league_region_ranking_v2(2026,'alasehir','gemlik',0); assert n = 6, 'gemlik: ' || n;
  select * into r from public.league_region_ranking_v2(2026,'alasehir','gemlik',0) where rank = 1; assert r.is_me, 'g1 birinci';
  -- Ayvalık: 2 üretici → eşik altı, boş
  select count(*) into n from public.league_region_ranking_v2(2026,'alasehir','ayvalik',0); assert n = 0, 'ayvalık eşik altı';
  -- Ekim: 8 üretici; Eylül: 1 üretici → boş
  select count(*) into n from public.league_region_ranking_v2(2026,'alasehir','',10); assert n = 8, 'ekim: ' || n;
  select count(*) into n from public.league_region_ranking_v2(2026,'alasehir','',9); assert n = 0, 'eylül eşik altı';
  -- Cinse göre kıyas: pahalı Ayvalık (140) tüm cinsler listesinde aşırı fiyat diye ELENMEZ ve Gemlik'leri ezmez
  -- (Ayvalık'ta 3 üretici yok → numara medyanı ~70; 140/70=2 < 3, elenmez). Gemlik g1'in puanı kendi cinsindeki
  -- kıyasla hesaplanır.
  select count(*) into n from public.league_region_ranking_v2(2026,'alasehir','',0) where is_me; assert n = 1, 'sen satırı';
  -- filtre sayıları
  select producer_count into n from public.league_region_filters(2026,'alasehir') where kind='variety' and value='gemlik'; assert n = 6, 'gemlik sayısı';
  select producer_count into n from public.league_region_filters(2026,'alasehir') where kind='month' and value='10'; assert n = 8, 'ekim sayısı';
  select count(*) into n from public.league_region_filters(2026,'alasehir') where kind='variety' and value=''; assert n = 0, 'boş cins listelenmez';
  -- geçersiz ay
  begin perform * from public.league_region_ranking_v2(2026,'alasehir','',13); raise exception 'AY 13';
  exception when sqlstate '22023' then null; end;
  -- girişsiz
  perform set_config('test.sub', '', false);
  begin perform * from public.league_region_filters(2026,'alasehir'); raise exception 'GİRİŞSİZ';
  exception when insufficient_privilege then null; end;
end $$;
-- Cinse göre kıyas etkisi: aynı numarada pahalı cins, kendi cinsinde 3+ üretici varken kendi medyanıyla kıyaslanır.
do $$
declare s_before numeric; s_after numeric;
begin
  perform set_config('test.sub','a1',false);
  select score into s_before from public.league_region_ranking_v2(2026,'alasehir','',0) where is_me;
  perform pg_temp.sub('a3','r1','2 No',300,139,'2026-10-13','ayvalik');
  perform set_config('test.sub','a1',false);
  select score into s_after from public.league_region_ranking_v2(2026,'alasehir','',0) where is_me;
  assert s_after < s_before, 'Ayvalık 3 üreticiye ulaşınca kendi cinsiyle kıyaslanır (fiyat avantajı kalkar): ' || s_before || ' -> ' || s_after;
end $$;
select 'PASS: cins + aylık sıralama SQL kontrolleri' as sonuc;
