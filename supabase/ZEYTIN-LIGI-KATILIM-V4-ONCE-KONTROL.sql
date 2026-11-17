-- Zeytin Ligi V4 — ÖN KONTROL (yalnız okur, hiçbir şeyi değiştirmez)
-- Supabase SQL Editor'de çalıştır, çıkan tablonun ekran görüntüsünü gönder.
--
-- Her satır bir fonksiyon ya da tablo:
--   var_mi            : nesne bu imzayla veritabanında var mı
--   public_calistirir : PUBLIC (giriş yapmamış dahil herkes) çalıştırabiliyor mu
--   anon / authenticated : bu rollerin çalıştırma izni
--   security_definer  : fonksiyon sahibinin yetkisiyle mi çalışıyor

with beklenen(tur, ad) as (
    values
        ('fonksiyon', 'public.league_region_ranking(integer, text)'),
        ('fonksiyon', 'public.league_region_ranking_v2(integer, text, text, integer)'),
        ('fonksiyon', 'public.league_region_ranking_v3(integer, text, text, integer)'),
        ('fonksiyon', 'public.league_set_profile(text, boolean)'),
        ('fonksiyon', 'public.league_set_profile_v2(text, boolean, integer)'),
        ('fonksiyon', 'public.league_get_profile()'),
        ('fonksiyon', 'public.league_get_profile_v2()'),
        ('fonksiyon', 'private.market_producer_hash()'),
        ('fonksiyon', 'public.league_set_participation(boolean)'),
        ('fonksiyon', 'public.league_get_participation()'),
        ('tablo', 'private.market_contributions'),
        ('tablo', 'private.league_profiles'),
        ('tablo', 'private.league_participants')
),
fonk as (
    select b.ad, to_regprocedure(b.ad) as oid
    from beklenen b where b.tur = 'fonksiyon'
)
select
    f.ad as nesne,
    (f.oid is not null) as var_mi,
    case when f.oid is null then null
         else coalesce(p.proacl is null, false)
              or exists (select 1 from aclexplode(p.proacl) a where a.grantee = 0 and a.privilege_type = 'EXECUTE')
    end as public_calistirir,
    case when f.oid is null then null else has_function_privilege('anon', f.oid, 'EXECUTE') end as anon,
    case when f.oid is null then null else has_function_privilege('authenticated', f.oid, 'EXECUTE') end as authenticated,
    p.prosecdef as security_definer
from fonk f
left join pg_proc p on p.oid = f.oid
union all
select
    b.ad,
    (to_regclass(b.ad) is not null),
    null, null, null, null
from beklenen b where b.tur = 'tablo'
order by 1;
