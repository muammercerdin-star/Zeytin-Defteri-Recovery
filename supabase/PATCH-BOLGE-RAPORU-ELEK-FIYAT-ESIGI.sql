-- Zeytin Defteri / Zeytin Ligi — Bölge raporu elek fiyatı gizlilik eşiği (14.10.2026)
--
-- Sorun: market_league_region_report, grade_breakdown içindeki elek başına average_price ve
-- median_price değerlerini 1 üretici varken bile döndürüyordu. Bu, tek tek üreticilerin fiyatını
-- ortaya çıkarır ve "5 üretici olmadan bölge rakamı yayınlanmaz" kuralına aykırıdır.
-- Düzeltme: iki satırda ">= 1" yerine ">= v_threshold" (üretimde 5).
--
-- Bu dosya canlı fonksiyonu YENİDEN YAZMAZ: canlıdaki tanımı pg_get_functiondef ile alır, yalnızca
-- bu iki ifadeyi değiştirir ve aynı tanımı geri kurar. Yetkiler (grant) ve diğer davranış korunur.
-- Supabase SQL Editor'da adım adım çalıştırın.

-- 1) KONTROL (salt okunur). İki sütun da true çıkmalı; değilse 2. adımı ÇALIŞTIRMAYIN.
select position('when coalesce(gs.pc, 0) >= 1 then round(gs.balanced_average, 2)' in p.prosrc) > 0 as ortalama_acik,
       position('when coalesce(gs.pc, 0) >= 1 then round(gs.balanced_median, 2)' in p.prosrc) > 0 as medyan_acik,
       md5(p.prosrc) as ozet
from pg_proc p
where p.oid = 'public.market_league_region_report(integer,text,integer)'::regprocedure;
-- Repodaki V10.1 sürümünün düzeltme öncesi özeti: 4b5a562f1d3c9ad89a4354f398382f41

-- 2) DÜZELTME (canlıyı değiştirir).
do $patch$
declare
    def text;
begin
    def := pg_get_functiondef('public.market_league_region_report(integer,text,integer)'::regprocedure);
    if position('when coalesce(gs.pc, 0) >= 1 then round(gs.balanced_average, 2)' in def) = 0
       or position('when coalesce(gs.pc, 0) >= 1 then round(gs.balanced_median, 2)' in def) = 0 then
        raise exception 'Beklenen ifadeler bulunamadı; fonksiyon değiştirilmedi.';
    end if;
    def := replace(def, 'when coalesce(gs.pc, 0) >= 1 then round(gs.balanced_average, 2)',
                        'when coalesce(gs.pc, 0) >= v_threshold then round(gs.balanced_average, 2)');
    def := replace(def, 'when coalesce(gs.pc, 0) >= 1 then round(gs.balanced_median, 2)',
                        'when coalesce(gs.pc, 0) >= v_threshold then round(gs.balanced_median, 2)');
    execute def;
end
$patch$;

-- 3) DOĞRULAMA (salt okunur). İki sütun da false çıkmalı. Canlı tanım repodakiyle aynıysa özet:
--    384bcf9ef399124ad69a6071ee508ab1
select position('when coalesce(gs.pc, 0) >= 1 then round(gs.balanced_' in p.prosrc) > 0 as hala_acik,
       position('>= v_threshold then round(gs.balanced_median, 2)' in p.prosrc) = 0 as esik_eksik,
       md5(p.prosrc) as ozet
from pg_proc p
where p.oid = 'public.market_league_region_report(integer,text,integer)'::regprocedure;
