\set QUIET on
do $$
declare n int; r record; t date := (now() at time zone 'Europe/Istanbul')::date;
begin
  delete from private.market_contributions;
  -- Alaşehir: 6 üretici (bölge açık). Double: son 3 günde 120, 130, 125 (+ eski 140) → 125, pencere 3
  insert into private.market_contributions(producer_hash, source_record_id, region_key, region_label, grade, kg, price_per_kg, sale_date, season_year) values
   ('p1','s1','alasehir','Alaşehir','Double',10,120,t,2026),
   ('p2','s2','alasehir','Alaşehir','DOUBLE',10,130,t-1,2026),
   ('p3','s3','alasehir','Alaşehir','Double',10,125,t-2,2026),
   ('p4','s4','alasehir','Alaşehir','Double',10,140,t-10,2026),
   -- 1 No yalnız 5 gün önce (tek üretici): 7 güne genişler, şart yok → 90
   ('p5','s5','alasehir','Alaşehir','1',10,90,t-5,2026),
   -- 3 No yalnız 20 gün önce → 30 gün
   ('p6','s6','alasehir','Alaşehir','3 No',10,35,t-20,2026),
   -- aynı satış iki kez (aynı kaynak) bir kez sayılır
   ('p1','s7','alasehir','Alaşehir','2',10,60,t,2026),
   ('p1','s7b','alasehir','Alaşehir','2',0,999,t,2026),
   -- gelecekteki tarih ve geçersiz fiyat dışarıda
   ('p2','s8','alasehir','Alaşehir','4',10,999,t+3,2026),
   -- başka sezon / başka bölge karışmaz
   ('p3','s9','alasehir','Alaşehir','Yağlık',10,500,t,2025),
   ('q1','s10','edremit','Edremit','Yağlık',10,700,t,2026);

  perform set_config('test.sub', '', false);
  begin
    perform * from public.market_region_current_prices(2026,'alasehir');
    raise exception 'girişsiz çağrı reddedilmeliydi';
  exception when insufficient_privilege then null;
  end;

  perform set_config('test.sub', 'u1', false);
  select * into r from public.market_region_current_prices(2026,'alasehir') where grade='Double';
  assert r.price = 125 and r.window_days = 3 and r.sale_count = 3 and r.producer_count = 3, 'Double son 3 gün medyanı 125: ' || row_to_json(r)::text;
  select * into r from public.market_region_current_prices(2026,'alasehir') where grade='1 No';
  assert r.price = 90 and r.window_days = 7 and r.producer_count = 1, '1 No 7 güne genişler, tek üretici şartı yok';
  select * into r from public.market_region_current_prices(2026,'alasehir') where grade='3 No';
  assert r.price = 35 and r.window_days = 30, '3 No 30 güne genişler';
  select * into r from public.market_region_current_prices(2026,'alasehir') where grade='2 No';
  assert r.price = 60 and r.sale_count = 1, '0 kg satır sayılmaz';
  select count(*) into n from public.market_region_current_prices(2026,'alasehir') where grade in ('4 No','Yağlık');
  assert n = 0, 'gelecek tarih ve başka sezon karışmaz';
  -- sıralama Double ilk
  select grade into r from public.market_region_current_prices(2026,'alasehir') limit 1;
  assert r.grade = 'Double', 'sıra Double ile başlar';
  -- 5'ten az üreticili bölge kapalı
  select count(*) into n from public.market_region_current_prices(2026,'edremit');
  assert n = 0, 'Edremit (1 üretici) kapalı';
  -- dönen sütunlarda kimlik yok
  select count(*) into n from information_schema.routines rt
    join information_schema.parameters pa on pa.specific_name = rt.specific_name
   where rt.routine_name='market_region_current_prices' and pa.parameter_mode='OUT'
     and pa.parameter_name in ('producer_hash','ph','kg','source_record_id');
  assert n = 0, 'kimlik ya da kg dönmez';
  raise notice 'current_prices: tum kontroller gecti';
end $$;
