-- Ortak döngü SQL'i: yerel PostgreSQL'de davranış denetimi. Her denetim "PASS ..." yazar ya da hata verip durur.
\set ON_ERROR_STOP on
\set QUIET on

create or replace function pg_temp.ok(kosul boolean, ad text) returns void language plpgsql as $$
begin
    if not coalesce(kosul, false) then
        raise exception 'FAIL %', ad;
    end if;
    raise notice 'PASS %', ad;
end
$$;

-- Yardımcı: n farklı takma adla bir söz-anlamı ekle (yazar rolüyle).
create or replace function pg_temp.ekle(p_kok text, p_soz text, p_eylem text, p_tur text, p_not text, p_il text, n int, p_on text)
returns void language plpgsql as $$
begin
    for i in 1..n loop
        perform ortak.katki_ekle(p_kok, p_soz, p_eylem, p_tur, p_not, p_il, '1.7.0',
            substr(md5(p_on || i), 1, 32), encode(sha256(convert_to(p_on || p_kok || p_tur || i, 'UTF8')), 'hex'),
            substr(md5('baglanti' || p_on || (i % 50)), 1, 32));
    end loop;
end
$$;
grant execute on all functions in schema pg_temp to zd_katki_yazar, zd_paket_yonetici;

-- 1) Yazar rolü: yalnız ekler; okuyamaz, doğrudan yazamaz.
set role zd_katki_yazar;
select pg_temp.ok(ortak.katki_ekle('bakirl', 'bakırladık', 'ISLEM', 'İlaçlama', '', 'Manisa', '1.7.0',
    repeat('a', 32), encode(sha256(convert_to('belge-1-xxxxxxxxxxxx', 'UTF8')), 'hex'), repeat('b', 32)),
    'yazar katkı ekler');
select pg_temp.ok(not ortak.katki_ekle('bakirl', 'bakırladık', 'ISLEM', 'İlaçlama', '', 'Manisa', '1.7.0',
    repeat('a', 32), encode(sha256(convert_to('belge-2-xxxxxxxxxxxx', 'UTF8')), 'hex'), repeat('b', 32)),
    'aynı çiftçinin aynı söz-anlamı ikinci kez sayılmaz');
do $$
begin
    perform count(*) from ortak.katki;
    raise exception 'FAIL yazar katkıları okuyabildi';
exception when insufficient_privilege then
    raise notice 'PASS yazar katkı tablosunu okuyamaz';
end
$$;
do $$
begin
    insert into ortak.katki (kok, soz, eylem, tur, takma_ad, silme_ozeti) values ('x', 'xxxx', 'ISLEM', '', repeat('c', 32), repeat('d', 64));
    raise exception 'FAIL yazar hız sınırını atlayıp doğrudan yazabildi';
exception when insufficient_privilege then
    raise notice 'PASS yazar tabloya doğrudan yazamaz (yalnız katki_ekle)';
end
$$;
do $$
begin
    perform * from ortak.adaylar;
    raise exception 'FAIL yazar aday listesini okuyabildi';
exception when insufficient_privilege then
    raise notice 'PASS yazar aday listesini göremez';
end
$$;
do $$
begin
    perform ortak.paket_uret(1);
    raise exception 'FAIL yazar paket üretebildi';
exception when insufficient_privilege then
    raise notice 'PASS yazar paket üretemez';
end
$$;
-- Geçersiz katkılar: ad gibi büyük harfli söz, genel olmayan eylem, bozuk takma ad.
do $$
begin
    perform ortak.katki_ekle('bakirl', 'Muammer', 'ISLEM', 'İlaçlama', '', '', '', repeat('e', 32), repeat('f', 64), repeat('b', 32));
    raise exception 'FAIL büyük harfli söz kabul edildi';
exception when invalid_parameter_value then
    raise notice 'PASS temizleyiciden geçmemiş söz reddedilir';
end
$$;
do $$
begin
    perform ortak.katki_ekle('bakirl', 'bakırladık', 'SATIS', '', '', '', '', repeat('e', 32), repeat('f', 64), repeat('b', 32));
    raise exception 'FAIL satış eylemi kabul edildi';
exception when invalid_parameter_value then
    raise notice 'PASS yalnız işlem/hasat anlamı kabul edilir';
end
$$;
do $$
begin
    perform ortak.katki_ekle('bakirl', 'bakırladık', 'ISLEM', 'İlaçlama', '', '', '', 'ali', repeat('f', 64), repeat('b', 32));
    raise exception 'FAIL takma ad yerine ad kabul edildi';
exception when invalid_parameter_value then
    raise notice 'PASS takma ad yalnız 32 onaltılık hane';
end
$$;
-- 2) Hız sınırı: bir bağlantıdan saatte en çok 30 çağrı.
do $$
declare
    i int;
begin
    for i in 1..40 loop
        perform ortak.katki_ekle('suvar', 'suvardık', 'ISLEM', 'Sulama', '', '', '', substr(md5('hiz' || i), 1, 32),
            encode(sha256(convert_to('hiz' || i, 'UTF8')), 'hex'), repeat('9', 32));
    end loop;
    raise exception 'FAIL hız sınırı çalışmadı';
exception when program_limit_exceeded then
    raise notice 'PASS bağlantı başına hız sınırı (saatte 30)';
end
$$;
-- 3) Geri çekme: belge ile silinir, belge sunucuda tutulmaz; başkasının belgesi bir şey silmez.
select pg_temp.ok(ortak.katki_geri_cek('belge-1-xxxxxxxxxxxx', repeat('7', 32)) = 1, 'silme belgesiyle katkı geri çekilir');
select pg_temp.ok(ortak.katki_geri_cek('belge-1-xxxxxxxxxxxx', repeat('7', 32)) = 0, 'ikinci geri çekme bir şey silmez');
select pg_temp.ok(ortak.katki_geri_cek('baskasinin-belgesi-yyyy', repeat('7', 32)) = 0, 'tanımadığı belge bir şey silmez');
-- Veri: bakırladık 19 çiftçi (eşiğin altı), göztaşıladık 20 (Manisa 20), diskaradık 25 (illeri karışık),
-- "belledik" iki anlamla (Toprak İşleme 22, Gübre 21: çakışma).
select pg_temp.ekle('bakirl', 'bakırladık', 'ISLEM', 'İlaçlama', '', 'İzmir', 19, 'b');
select pg_temp.ekle('goztasil', 'göztaşıladık', 'ISLEM', 'İlaçlama', '', 'Manisa', 20, 'g');
select pg_temp.ekle('diskar', 'diskaradık', 'ISLEM', 'Toprak İşleme', '', 'Aydın', 10, 'd');
select pg_temp.ekle('diskar', 'diskaradık', 'ISLEM', 'Toprak İşleme', '', 'Muğla', 10, 'e');
select pg_temp.ekle('diskar', 'diskaradık', 'ISLEM', 'Toprak İşleme', '', '', 5, 'f');
select pg_temp.ekle('bell', 'belledik', 'ISLEM', 'Toprak İşleme', '', '', 22, 'h');
select pg_temp.ekle('bell', 'belledik', 'ISLEM', 'Gübre', '', '', 21, 'i');
reset role;
truncate ortak.hiz;
select pg_temp.ok((select count(*) from ortak.katki where silme_ozeti like '%belge%') = 0, 'silme belgesi değil yalnız özeti saklanır');
select pg_temp.ok((select sezon from ortak.katki limit 1) = ortak.sezon(), 'sezon sunucuda yazılır');
select pg_temp.ok(ortak.sezon('2026-07-31 23:00+03') = '2025' and ortak.sezon('2026-08-01 00:30+03') = '2026',
    'sezon 1 Ağustos Türkiye saatinde döner (telefondaki takma adla aynı)');

-- 4) Paket yöneticisi: yalnız toplu aday listesi; eşik tek ayardan.
set role zd_paket_yonetici;
do $$
begin
    perform count(*) from ortak.katki;
    raise exception 'FAIL yönetici tek tek katkıları okuyabildi';
exception when insufficient_privilege then
    raise notice 'PASS yönetici tek tek katkıları (takma adları) göremez';
end
$$;
select pg_temp.ok((select count(*) from ortak.adaylar where kok = 'bakirl') = 0, 'eşiğin altındaki söz (19 çiftçi) aday değil');
select pg_temp.ok((select ciftci from ortak.adaylar where kok = 'goztasil') = 20, 'eşikteki söz (20 çiftçi) aday');
select pg_temp.ok((select bolge from ortak.adaylar where kok = 'goztasil') = 'Manisa', 'tek ilde eşiği geçen söz o ile bağlanır');
select pg_temp.ok((select bolge from ortak.adaylar where kok = 'diskar') = '', 'hiçbir ilde eşiği geçmeyen söz bölgesiz');
select pg_temp.ok((select bool_and(cakisma) from ortak.adaylar where kok = 'bell') and (select count(*) from ortak.adaylar where kok = 'bell') = 2,
    'aynı köke iki anlam: çakışma işaretlenir');
select pg_temp.ok((select count(*) from ortak.adaylar where karar is not null) = 0, 'hiçbir aday kendiliğinden onaylı değil');
select pg_temp.ok(jsonb_array_length(ortak.paket_uret(2, '2027-03-01') -> 'sozler') = 0, 'onaysız aday pakete girmez');
do $$
begin
    perform ortak.aday_karar('bakirl', 'ISLEM', 'İlaçlama', '', 'onaylandi');
    raise exception 'FAIL eşiğin altındaki söz onaylandı';
exception when invalid_parameter_value then
    raise notice 'PASS eşiğin altındaki söz onaylanamaz';
end
$$;
select ortak.aday_karar('goztasil', 'ISLEM', 'İlaçlama', '', 'onaylandi');
select ortak.aday_karar('diskar', 'ISLEM', 'Toprak İşleme', '', 'onaylandi');
select ortak.aday_karar('bell', 'ISLEM', 'Toprak İşleme', '', 'onaylandi');
select ortak.aday_karar('bell', 'ISLEM', 'Gübre', '', 'reddedildi');
select pg_temp.ok((select string_agg(e ->> 'kok', ',' order by e ->> 'kok') from jsonb_array_elements(ortak.paket_uret(2, '2027-03-01') -> 'sozler') e)
    = 'bell,diskar,goztasil', 'onaylı sözler pakete girer, reddedilen girmez');
select ortak.aday_karar('bell', 'ISLEM', 'Gübre', '', 'onaylandi');
select pg_temp.ok((select count(*) from jsonb_array_elements(ortak.paket_uret(2, '2027-03-01') -> 'sozler') e where e ->> 'kok' = 'bell') = 0,
    'aynı köke iki onaylı anlam: hiçbiri pakete girmez');
select ortak.aday_karar('bell', 'ISLEM', 'Gübre', '', 'reddedildi');
do $$
begin
    perform ortak.katki_geri_cek('x', repeat('1', 32));
    raise exception 'FAIL yönetici katkı silebildi';
exception when insufficient_privilege then
    raise notice 'PASS yönetici katkı ekleyemez / silemez';
end
$$;
reset role;

-- 5) Eşik tek yerden değişir; geri çekmeyle eşiğin altına düşen söz paketten çıkar.
update ortak.ayar set deger = 21 where ad = 'esik';
select pg_temp.ok((select count(*) from ortak.adaylar where kok = 'goztasil') = 0, 'eşik 21 yapılınca 20 çiftçilik söz aday değil');
update ortak.ayar set deger = 20 where ad = 'esik';
-- Yardımcıdaki belgeler: p_on || kok || tur || i → "g" + "goztasil" + "İlaçlama" + "1".
set role zd_katki_yazar;
select pg_temp.ok(ortak.katki_geri_cek('ggoztasilİlaçlama1', repeat('7', 32)) = 1, 'bir çiftçi geri çekti');
reset role;
truncate ortak.hiz;
set role zd_paket_yonetici;
select pg_temp.ok((select count(*) from jsonb_array_elements(ortak.paket_uret(3, '2027-03-02') -> 'sozler') e where e ->> 'kok' = 'goztasil') = 0,
    'geri çekmeyle eşiğin altına düşen onaylı söz bir sonraki pakete girmez');
reset role;

-- 6) Temizlik: iki sezon saklanır; daha eskisi silinir.
update ortak.katki set sezon = (ortak.sezon()::int - 1)::text where kok = 'diskar';
update ortak.katki set sezon = (ortak.sezon()::int - 2)::text where kok = 'bakirl';
select pg_temp.ok(ortak.temizle() = 19, 'temizlik: iki sezondan eski katkılar silinir');
select pg_temp.ok((select count(*) from ortak.katki where kok = 'diskar') = 25, 'temizlik: önceki sezon korunur');
select pg_temp.ok((select count(*) from ortak.katki where kok = 'bakirl') = 0, 'temizlik: iki sezon önceki gitti');

-- 7) Kimlik dışı sütun: tabloda ad, telefon, hesap, konum, IP yok.
select pg_temp.ok((select string_agg(column_name, ',' order by ordinal_position) from information_schema.columns
    where table_schema = 'ortak' and table_name = 'katki') = 'id,kok,soz,eylem,tur,not,il,surum,takma_ad,silme_ozeti,sezon,created',
    'katkı tablosu yalnız temizlenmiş alanlar + takma ad + silme özeti');
select pg_temp.ok((select relrowsecurity from pg_class where oid = 'ortak.katki'::regclass), 'katkı tablosunda satır güvenliği açık');
select pg_temp.ok((select count(*) from pg_policies where schemaname = 'ortak' and tablename = 'katki' and cmd <> 'INSERT') = 0,
    'katkı tablosunda okuma / güncelleme / silme kuralı yok');
select pg_temp.ok(not exists (select 1 from pg_roles where rolname in ('zd_katki_yazar', 'zd_paket_yonetici') and rolcanlogin),
    'roller giriş yapamaz');
