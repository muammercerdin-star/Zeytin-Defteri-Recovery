# Piyasa Direct RPC V7

Bu Android paketi, Supabase Dashboard'da daha önce kurulan direct RPC sözleşmesini kullanır:

- `market_submit`
- `market_withdraw`
- `market_snapshot`
- `market_regions`
- `market_chart`

Firebase Third-Party Auth entegrasyonu `zeytin-defteri` projesi için açık olmalıdır.
Firebase ID tokenında `role` claim'i yoksa Supabase Data API sorguyu `anon` Postgres rolüyle yürütür; bu kurulumdaki RPC izinleri hem `anon` hem `authenticated` için tasarlanmıştır.

Android yalnız Project URL ve publishable key taşır. Secret/service-role anahtarları uygulamaya eklenmez.

Eski `supabase/functions/market-api` klasörü V6 geliştirme geçmişi için projede korunmuştur; V7 Android Piyasa akışı onu çağırmaz.

## Production gizlilik eşiği: 5

Android: `PUBLIC_MARKET_MIN_PRODUCERS = 5`. Fiyat yayınlamak için aynı
bölge/elek/dönemde en az 5 **farklı** üretici gerekir; satış adedi sayılmaz.
Eski Edge ve Firebase policy kaynakları da 5 eşiğindedir.

Aktif V7 `market_snapshot`, `market_regions`, `market_chart` tanımları bu ZIP'te
bulunmuyor; Dashboard'da kurulmuşlardır. Eski test patch'i uygulanmışsa
`RESTORE-MIN-PRODUCERS-5.sql`, kayıtlı 5-eşikli tanımları yalnız mevcut gövde
beklenen test sürümüyle hâlâ eşleşiyorsa geri yükler. Sonradan değişmiş bir
fonksiyonu eski yedekle ezmez. 08.1 ile League RPC’leri de yalnız aynı batch yedeği ve tam gövde eşleşmesi varsa restore edilir.

Önce `DRY-RUN-RESTORE-MIN-PRODUCERS-5.sql` çalıştırın; ayrıntılı sıra ve güvenlik koşulları için `RESTORE-SAFETY-08-1.md` dosyasını okuyun. Ardından `VERIFY-PRODUCTION-MIN-PRODUCERS-5.sql` ile mevcut tanımları salt okunur
inceleyin. SQL Editor'da restore dosyasını açık yetkiyle uyguladıktan sonra
aynı sorguyu ve izole test hesaplarıyla 1–5 distinct producer testini tekrarlayın.
Yedek/gerçek RPC tanımları yoksa production hazır denemez; bu dosya bunları
uydurmaz. `market-schema.sql` eski V6 şemasıdır, V7'nin yerine kurulmaz.

Bu teslimde canlı Supabase değişikliği yapılmamıştır.
