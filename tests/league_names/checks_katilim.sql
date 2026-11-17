\set QUIET on
-- V4 katılım izni: katılan, ayrılan, adıyla görünen, eski sürüm, izin vermeyen.
-- Kendi temiz verisi: Alaşehir'de g1..g6, GÜVEN V4 süzgeçlerinden geçen tutarlı kayıtlar
-- (aynı hafta, yakın fiyatlar, 200 kg altı kilolar → her biri sıralamaya uygun).
delete from private.league_participants;
delete from private.league_profiles;
delete from private.market_contributions;
create or replace function pg_temp.put(sub text, rec text, grade text, kg numeric, price numeric, d date) returns void
language plpgsql as $$ begin
  perform set_config('test.sub', sub, false);
  perform public.market_submit_v2(rec, 'alasehir', 'Alaşehir / Manisa', grade, kg, price, d, 2026, '');
end $$;
do $$
declare p text; i int := 0;
begin
  foreach p in array array['g1','g2','g3','g4','g5','g6'] loop
    i := i + 1;
    perform pg_temp.put(p, p||'a', '2 No', 110 + i, 68 + i, ('2026-11-03'::date + i));
    perform pg_temp.put(p, p||'b', '1 No', 30 + i, 98 + i, ('2026-11-03'::date + i));
  end loop;
end $$;
create temp table onceki as
select (select count(*) from private.market_contributions) as katki_sayisi,
       (select count(distinct producer_hash) from private.market_contributions) as uretici_sayisi;
grant all on onceki to public;

do $$
declare n int; r record; k int; once_k int; once_u int;
begin
  select katki_sayisi, uretici_sayisi into once_k, once_u from onceki;

  -- 1) V4 sonrası: kimse katılmadı → kişi bazlı sıralama boş, katkılar yerinde
  perform set_config('test.sub', 'g1', false);
  select count(*) into n from public.league_region_ranking_v3(2026,'alasehir','',0);
  assert n = 0, 'izin yokken sıralama boş olmalı: ' || n;
  assert (public.league_get_participation() ->> 'participate')::boolean = false, 'varsayılan kapalı';
  select count(*) into k from private.market_contributions; assert k = once_k, 'katkılar korunmalı';

  -- 2) KATILAN: g1 katılır → yalnız kendisi, sıra 1; bölge üretici sayısı tüm katkılardan
  perform public.league_set_participation(true);
  perform public.league_set_participation(true);  -- iki kez katılmak zararsız
  select count(*) into n from public.league_region_ranking_v3(2026,'alasehir','',0);
  assert n = 1, 'yalnız katılan görünür: ' || n;
  select * into r from public.league_region_ranking_v3(2026,'alasehir','',0);
  assert r.is_me and r.rank = 1 and r.display_name is null and r.ranked_count = 1, 'g1 adsız, 1. sıra';
  assert r.producer_count = (select count(distinct producer_hash) from private.market_contributions where region_key = 'alasehir'),
         'bölge sayısı tüm katkılardan: ' || r.producer_count;

  -- 3) ESKİ SÜRÜM: g3 yalnız eski ad fonksiyonuyla adını açar → tetikleyiciyle katılımcı olur
  perform set_config('test.sub', 'g3', false);
  perform public.league_set_profile('Eski Sürüm', true);
  assert (public.league_get_participation() ->> 'participate')::boolean, 'eski sürüm ad açınca katılır';
  select * into r from public.league_region_ranking_v3(2026,'alasehir','',0) where is_me;
  assert r.display_name = 'Eski Sürüm', 'eski sürüm adıyla görünür';

  -- 4) ADIYLA GÖRÜNEN (yeni onay): g4
  perform set_config('test.sub', 'g4', false);
  perform public.league_set_profile_v2('Adlı Üretici', true, 2);
  select * into r from public.league_region_ranking_v3(2026,'alasehir','',0) where is_me;
  assert r.display_name = 'Adlı Üretici' and r.avg_price is not null, 'adlı ve fiyatlı';

  -- sıra numaraları katılımcılar arasında boşluksuz (1..3)
  select count(*) into n from public.league_region_ranking_v3(2026,'alasehir','',0);
  assert n = 3, 'üç katılımcı: ' || n;
  select count(*) into n from (
    select rank, row_number() over (order by rank) rn from public.league_region_ranking_v3(2026,'alasehir','',0)) x
    where rank <> rn;
  assert n = 0, 'sıralar boşluksuz';

  -- 5) ADINI KAPATAN: g4 adını kapatır → adsız olarak sıralamada kalır
  perform public.league_set_profile_v2(null, false, 2);
  select * into r from public.league_region_ranking_v3(2026,'alasehir','',0) where is_me;
  assert r.display_name is null, 'adı kapatan adsız kalır';

  -- 6) AYRILAN: g3 ayrılır → sıralamadan çıkar, adı silinir, katkıları SİLİNMEZ
  perform set_config('test.sub', 'g3', false);
  perform public.league_set_participation(false);
  select count(*) into n from public.league_region_ranking_v3(2026,'alasehir','',0) where is_me;
  assert n = 0, 'ayrılan görünmez';
  select count(*) into n from private.league_profiles where producer_hash = private.market_producer_hash();
  assert n = 0, 'ayrılanın adı silinir';
  select count(*) into k from private.market_contributions where producer_hash = private.market_producer_hash();
  assert k > 0, 'ayrılanın katkıları durur';

  -- 7) İZİN VERMEYEN: g5 hiç dokunmadı → görünmez ama katkıları istatistikte
  perform set_config('test.sub', 'g5', false);
  select count(*) into n from public.league_region_ranking_v3(2026,'alasehir','',0) where is_me;
  assert n = 0, 'izin vermeyen görünmez';
  select * into r from public.league_region_ranking_v3(2026,'alasehir','',0) limit 1;
  assert r.producer_count = once_u or r.producer_count <= once_u, 'bölge sayısı';

  -- KURAL: toplam katkı ve üretici sayısı hiç değişmedi
  select count(*) into k from private.market_contributions; assert k = once_k, 'katkı sayısı değişmemeli';
  select count(distinct producer_hash) into k from private.market_contributions; assert k = once_u, 'üretici sayısı değişmemeli';

  -- 8) Oturum yoksa katılım değiştirilemez
  perform set_config('test.sub', '', false);
  begin
    perform public.league_set_participation(true);
    raise exception 'OTURUMSUZ_GECTI';
  exception when others then
    assert sqlerrm <> 'OTURUMSUZ_GECTI', 'oturumsuz katılım reddedilmeli';
  end;
end $$;

-- 9) İzinler: eski süzgeçsiz sıralamalar kimseye açık değil, yeniler açık
do $$
begin
  assert not has_function_privilege('anon', 'public.league_region_ranking(integer,text)', 'execute'), 'v1 anon kapalı';
  assert not has_function_privilege('authenticated', 'public.league_region_ranking(integer,text)', 'execute'), 'v1 auth kapalı';
  assert not has_function_privilege('authenticated', 'public.league_region_ranking_v2(integer,text,text,integer)', 'execute'), 'v2 auth kapalı';
  assert not exists (select 1 from pg_proc p, aclexplode(p.proacl) a
                     where p.oid = 'public.league_region_ranking_v2(integer,text,text,integer)'::regprocedure
                       and a.grantee = 0 and a.privilege_type = 'EXECUTE'), 'v2 PUBLIC kapalı';
  assert has_function_privilege('authenticated', 'public.league_region_ranking_v3(integer,text,text,integer)', 'execute'), 'v3 açık';
  assert has_function_privilege('authenticated', 'public.league_set_participation(boolean)', 'execute'), 'katılım açık';
  assert not has_function_privilege('anon', 'private.league_join_on_name()', 'execute'), 'tetikleyici fonksiyonu kapalı';
end $$;
select 'PASS: V4 katılım SQL kontrolleri' as sonuc;
