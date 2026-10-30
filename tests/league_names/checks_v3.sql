\set QUIET on
do $$
declare n int; r record; a record;
begin
  -- Veriler checks_v2.sql'den: 6 Gemlik (g1..g6), 3 Ayvalık (a1..a3), x1.
  perform set_config('test.sub', 'g2', false);
  -- V3 sıralaması V2 ile birebir aynı (sıra, puan, ben)
  select count(*) into n from (
    select rank, score, is_me from public.league_region_ranking_v2(2026,'alasehir','',0)
    except select rank, score, is_me from public.league_region_ranking_v3(2026,'alasehir','',0)) d;
  assert n = 0, 'V3 sıralaması V2 ile aynı olmalı';
  -- adsız satırlarda fiyat görünür; g2'nin kendi fiyatı 70
  select * into r from public.league_region_ranking_v3(2026,'alasehir','',0) where is_me;
  assert r.avg_price = 70 and r.display_name is null, 'adsız: fiyat görünür ' || coalesce(r.avg_price::text,'null');
  -- g2 adını ESKİ onayla açar (v1 fonksiyonu) → ad görünür, fiyat gizli
  perform public.league_set_profile('Volkan Afşar', true);
  select * into r from public.league_region_ranking_v3(2026,'alasehir','',0) where is_me;
  assert r.display_name = 'Volkan Afşar' and r.avg_price is null, 'eski onay: fiyat gizli';
  -- yeni onay (v2) → fiyat görünür
  perform public.league_set_profile_v2('Volkan Afşar', true, 2);
  select * into r from public.league_region_ranking_v3(2026,'alasehir','',0) where is_me;
  assert r.avg_price = 70, 'yeni onay: fiyat görünür';
  select * into a from public.league_get_profile_v2();
  assert a.consent_version = 2 and a.show_name, 'profil v2';
  -- gizleyince ad silinir, satır yine adsız + fiyatlı
  perform public.league_set_profile_v2(null, false, 2);
  select * into r from public.league_region_ranking_v3(2026,'alasehir','',0) where is_me;
  assert r.display_name is null and r.avg_price = 70, 'gizleyince adsız';
  -- Gemlik dilimi: ortalama yalnız o dilimden
  select count(*) into n from public.league_region_ranking_v3(2026,'alasehir','gemlik',0) where avg_price is null;
  assert n = 0, 'gemlik dilimi fiyatlar dolu';
  -- eşik altı dilim boş
  select count(*) into n from public.league_region_ranking_v3(2026,'alasehir','',9); assert n = 0, 'eylül boş';
end $$;
do $$
declare cols text;
begin
  select string_agg(a, ',') into cols from (select unnest(proargnames) a from pg_proc where proname = 'league_region_ranking_v3') x;
  assert cols !~ '(\mkg\M|amount|total)', 'kg/tutar dönmemeli: ' || cols;
  assert has_function_privilege('authenticated','public.league_region_ranking_v3(integer,text,text,integer)','execute'), 'yetki';
end $$;
select 'PASS: ortalama fiyat V3 SQL kontrolleri' as sonuc;
