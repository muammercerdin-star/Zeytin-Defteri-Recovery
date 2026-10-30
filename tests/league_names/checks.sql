\set QUIET on
create or replace function pg_temp.h(sub text) returns text language sql as $$ select md5('zd:'||sub)||md5('x'||sub) $$;
create or replace function pg_temp.add(sub text, region text, grade text, kg numeric, price numeric) returns void language sql as $$
  insert into private.market_contributions(producer_hash, source_record_id, region_key, region_label, grade, kg, price_per_kg, sale_date, season_year)
  values (pg_temp.h(sub), md5(random()::text), region, 'Alaşehir / Manisa', grade, kg, price, date '2026-10-20', 2026) $$;
-- Alaşehir: 6 üretici. u1 iyi elek + iyi fiyat; u2 orta; u3 düşük elek; u4 50 kg (sıralanmaz); u5 aşırı fiyat; u6 normal
select pg_temp.add('u1','alasehir','Double',200,130), pg_temp.add('u1','alasehir','1 No',200,100);
select pg_temp.add('u2','alasehir','2 No',300,70),   pg_temp.add('u2','alasehir','1 No',100,100);
select pg_temp.add('u3','alasehir','4 No',300,30),   pg_temp.add('u3','alasehir','Yağlık',100,20);
select pg_temp.add('u4','alasehir','2 No',50,70);
select pg_temp.add('u5','alasehir','2 No',200,700);
select pg_temp.add('u6','alasehir','2 No',150,72),   pg_temp.add('u6','alasehir','3 No',150,40);
select pg_temp.add('u2','alasehir','3 No',100,40), pg_temp.add('u3','alasehir','3 No',100,41);
-- Edremit: 4 üretici (eşik altı)
select pg_temp.add('e1','edremit','2 No',300,70), pg_temp.add('e2','edremit','2 No',300,70),
       pg_temp.add('e3','edremit','2 No',300,70), pg_temp.add('e4','edremit','2 No',300,70);

do $$
declare n int; r record; ok boolean;
begin
  -- girişsiz çağrı reddedilir
  perform set_config('test.sub', '', false);
  begin perform * from public.league_region_ranking(2026, 'alasehir'); raise exception 'GİRİŞSİZ KABUL EDİLDİ';
  exception when insufficient_privilege then null; end;
  begin perform public.league_set_profile('X', true); raise exception 'GİRİŞSİZ AD KABUL EDİLDİ';
  exception when insufficient_privilege then null; end;

  -- u1 adını açar, u2 açmaz, u6 açıp kapatır
  perform set_config('test.sub', 'u1', false); perform public.league_set_profile('  Muammer   Ç. ', true);
  perform set_config('test.sub', 'u6', false); perform public.league_set_profile('Volkan Afşar', true);
  perform public.league_set_profile(null, false);
  select count(*) into n from private.league_profiles; assert n = 1, 'kapatınca ad silinmeli: ' || n;
  -- geçersiz adlar
  perform set_config('test.sub', 'u2', false);
  begin perform public.league_set_profile('A', true); raise exception 'KISA AD'; exception when sqlstate '22023' then null; end;
  begin perform public.league_set_profile('www.site.com', true); raise exception 'LINK AD'; exception when sqlstate '22023' then null; end;
  begin perform public.league_set_profile(repeat('a',41), true); raise exception 'UZUN AD'; exception when sqlstate '22023' then null; end;

  -- kendi profilin
  perform set_config('test.sub', 'u1', false);
  select * into r from public.league_get_profile();
  assert r.display_name = 'Muammer Ç.' and r.show_name, 'ad sadeleşir: ' || coalesce(r.display_name,'null');

  -- Alaşehir sıralaması (u2 gözünden)
  perform set_config('test.sub', 'u2', false);
  select count(*) into n from public.league_region_ranking(2026, 'alasehir');
  assert n = 4, 'u4 (50 kg) ve u5 (aşırı fiyat) sıralanmaz: ' || n;
  select * into r from public.league_region_ranking(2026, 'alasehir') where rank = 1;
  assert r.display_name = 'Muammer Ç.' and not r.is_me, 'birinci u1, adıyla: ' || coalesce(r.display_name,'null');
  assert r.producer_count = 6 and r.ranked_count = 4, 'sayılar';
  assert r.dominant_grade in ('Double','1 No'), 'en çok çıkan';
  select count(*) into n from public.league_region_ranking(2026, 'alasehir') where is_me;
  assert n = 1, 'kendi satırı işaretli';
  select count(*) into n from public.league_region_ranking(2026, 'alasehir') where display_name is not null;
  assert n = 1, 'ad yalnız izin verende: ' || n;
  select count(*) into n from public.league_region_ranking(2026, 'alasehir') x where x.display_name = 'Volkan Afşar';
  assert n = 0, 'izni kapatanın adı görünmez';
  -- son sırada düşük elekli u3
  select * into r from public.league_region_ranking(2026, 'alasehir') order by rank desc limit 1;
  assert r.score < 50, 'düşük elek alt sırada: ' || r.score;

  -- Edremit 4 üretici: hiç satır yok (gizlilik eşiği)
  select count(*) into n from public.league_region_ranking(2026, 'edremit');
  assert n = 0, 'eşik altı bölge boş: ' || n;
  -- geçersiz bölge anahtarı
  begin perform * from public.league_region_ranking(2026, 'Ala şehir; drop'); raise exception 'BOZUK ANAHTAR';
  exception when sqlstate '22023' then null; end;
  -- başka sezon
  select count(*) into n from public.league_region_ranking(2025, 'alasehir'); assert n = 0, 'başka sezon boş';
end $$;

-- Dönen sütunlarda kg / fiyat / tutar yok.
do $$
declare cols text;
begin
  select string_agg(a, ',') into cols from (
    select unnest(proargnames) a from pg_proc where proname = 'league_region_ranking') x;
  assert cols !~ '(kg|price|amount)', 'kg/fiyat dönmemeli: ' || cols;
end $$;

-- İstemci rolleri tabloya doğrudan erişemez; fonksiyonları çalıştırabilir.
do $$
begin
  assert not has_table_privilege('anon', 'private.league_profiles', 'select'), 'anon tabloyu okuyamaz';
  assert not has_table_privilege('authenticated', 'private.league_profiles', 'select'), 'authenticated tabloyu okuyamaz';
  assert has_function_privilege('authenticated', 'public.league_region_ranking(integer,text)', 'execute'), 'yetki';
  assert has_function_privilege('anon', 'public.league_set_profile(text,boolean)', 'execute'), 'yetki anon';
end $$;
select 'PASS: adlı ön sıralama SQL kontrolleri' as sonuc;
