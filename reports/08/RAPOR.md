# İş Emri 08 — Piyasa production gizlilik eşiği 5

Ana kaynak: yalnız Zeytin-Defteri-IS-EMRI-07-1-AYARLAR-SON-CILA-FINAL.zip.

## Android eski/yeni değer

MarketBackendConfig.PUBLIC_MARKET_MIN_PRODUCERS: **2 → 5**.
MarketView tasarımı değiştirilmedi. MarketCloudRepository'nin Piyasa snapshot parser'ı artık `published=true` yanında `producer_count >= 5` koşulunu da arar. Eşik altı yanıttaki fiyatları kabul etmez; böyle bir snapshot için chart verisi de kabul edilmez. Bölge fiyat listesi zaten aynı sabiti kullanıyordu. Bu istemci kontrolü sunucu gizliliğinin yerine geçmez; canlı sunucu eski eşiği kullanıyorsa HTTP yanıtındaki verinin gizliliğini tek başına sağlayamaz.

## Supabase eşiği ve kaynak incelemesinin sınırı

- supabase/functions/market-api/policy.mjs: MIN_PRODUCERS=5, zaten doğruydu; değiştirilmedi.
- functions/market/policy.js: MIN_PRODUCERS=5, zaten doğruydu; değiştirilmedi.
- supabase/market-schema.sql: count(distinct c.producer_hash) >= 5, zaten doğruydu; gerçek yerel PostgreSQL çalıştırmasıyla test edildi; değiştirilmedi.
- Aktif Android V7 akışı eski market-api Edge Function'ı kullanmıyor. Doğrudan **market_snapshot, market_regions, market_chart** RPC'lerini çağırıyor. Bu üç fonksiyonun tam SQL tanımı kaynak ZIP'te yok; mevcut README bunların Dashboard'da kurulmuş olduğunu belirtiyor.

Bu nedenle kaynakta bulunan backend eşikleri **5** olarak doğrulandı; **canlı V7 RPC eşiği bu çalışmada doğrulanmadı veya değiştirilmedi**. Eksik canlı SQL gövdeleri tahmin edilerek yeniden yazılmadı. Sunucunun gerçekten production 5 olduğu sonucuna varmak için aşağıdaki manuel işlem ve gerçek RPC testleri gerekir.

## Hazırlanan SQL ve canlıda yapılacak işlem

Canlı veritabanına bağlantı/deploy/SQL uygulaması yapılmadı.

1. Doğru Supabase projesinin SQL Editor'ında `supabase/VERIFY-PRODUCTION-MIN-PRODUCERS-5.sql` çalıştırılır. Salt okunur sorgu üç aktif read RPC'nin ve private Piyasa yardımcılarının bütün overload tanımlarını döndürür. DISTINCT üretici hesabı, aynı bölge/elek/dönem koşulları, önceki dönem ve chart bucket eşikleri incelenmelidir. Bunların çağırdığı diğer yardımcılar varsa ayrıca incelenmelidir.
2. Geçmişte TEST-ONLY patch uygulanmış ve özgün yedek mevcutsa, açık operasyon yetkisiyle `supabase/RESTORE-MIN-PRODUCERS-5.sql` çalıştırılır. Bu dosya eski 5-eşikli yedeği geri yükler; yeni RPC uydurmaz.
3. VERIFY sorgusu tekrar çalıştırılır; canlı/izole test verisiyle aynı kombinasyonda 1,2,3,4 üreticide fiyat dönmediği, 5 üreticide yayın yapılabildiği ve tek üreticinin 10 satışının gizli kaldığı doğrulanır. Mevcut üretici kayıtları silinmemelidir.

Restore güvenlik davranışı:

- Üç aktif read RPC'nin de 5→2 patch yedeğinde bulunmasını ister. Yedek yoksa veya eksikse durur; production hazır mesajı vermez.
- İlgili Piyasa read RPC'lerini ve yedekteki private Piyasa yardımcılarını ele alır. `market_league_*` fonksiyonlarını değiştirmez.
- Mevcut gövde, yedekten hesaplanan eski test gövdesiyle tam eşleşmiyorsa durur; sonradan güncellenmiş SQL'i eski yedekle ezmez.
- Zaten eski production gövdesine dönmüş fonksiyonda yeniden işlem yapmaz; idempotenttir.
- Tek transaction içinde çalışır. Daha sonraki fonksiyonda uyuşmazlık varsa daha önceki değişiklikler de geri alınır.
- Yedek/eski gövde yoksa gerçek V7 SQL tanımları dışa aktarılıp incelenmeden güvenli otomatik değişiklik yapılmaz. Bu durumda teslimdeki restore dosyasını zorlayarak veya kontrollerini kaldırarak çalıştırmayın.
- `market-schema.sql` eski V6 şemasıdır; V7 eksik tanımlarının yerine canlıya uygulanmamalıdır.

## Değiştirilen dosyalar

1. app/src/main/java/com/zeytindefteri/app/MarketBackendConfig.java — sabit 5, production açıklaması.
2. app/src/main/java/com/zeytindefteri/app/MarketCloudRepository.java — yalnız Piyasa snapshot/chart eşik savunması.
3. supabase/TEST-ONLY-MIN-PRODUCERS-2.sql — eski düşürme girişi kapatıldı; artık hiçbir fonksiyonu değiştirmeden hata verir.
4. supabase/RESTORE-MIN-PRODUCERS-5.sql — kontrollü, transaction'lı, yeni gövdeyi ezmeyen geri yükleme.
5. supabase/DIRECT-RPC-V7-README.md — production eşik ve gerçek V7 kaynağı eksikliği açıklaması.
6. supabase/TEST-MODE-2-URETICI-README.md — geçici modun kapatıldığı bilgisi.

Eklenenler:
- supabase/VERIFY-PRODUCTION-MIN-PRODUCERS-5.sql
- supabase/tests/production-five.test.mjs
- supabase/tests/production-five-sql.mjs
- tests/market-production-five-java.py
- reports/08/ rapor ve test kayıtları

Kaynak karşılaştırması: 6 mevcut dosya değişti, **679 mevcut dosya birebir korundu**. Ayarlar 07.1, moderasyon-api, Auth, rules/index, DatabaseHelper/SQLite, Zeytin Ligi, Elek, Sohbet, onboarding, Manifest, Gradle ve navigasyon değişmedi. Ortak MarketCloudRepository içindeki Zeytin Ligi RPC/metotları da birebir korundu.

## Test sonuçları

| Senaryo | Android parser | Eski Edge/Firebase policy | Kaynak SQL: zd_market_public_regions |
| --- | --- | --- | --- |
| 1 farklı üretici | HIDDEN | HIDDEN | HIDDEN |
| 2 farklı üretici | HIDDEN | HIDDEN | HIDDEN |
| 3 farklı üretici | HIDDEN | HIDDEN | HIDDEN |
| 4 farklı üretici | HIDDEN | HIDDEN | HIDDEN |
| 5 farklı üretici | PUBLIC | PUBLIC | PUBLIC |
| 1 üretici, 10 ayrı satış | HIDDEN | HIDDEN | HIDDEN |

Android testi, gerçek parser metot gövdelerini ve gerçek MarketBackendConfig'i org.json ile derleyip çalıştırır. Satış adedi 10 olduğu hâlde üretici sayısının 1 kalması test edilmiştir. Sunucu published=false yanıtı ve eksik producer_count da reddedilir.

- Mevcut ve yeni Piyasa Node policy testleri: **19/19 PASS**. Eski Edge policy için 7d/30d/90d örnekleri; Firebase policy regresyonları dahil.
- Gerçek `market-schema.sql`, yerel PGlite 0.5.8 PostgreSQL ortamında çalıştırıldı: **1–5 distinct ve tek üretici/10 satış PASS**. Bu fonksiyon bölge dizinidir; canlı V7 snapshot/chart işlevlerinin yerine test edilmiş sayılmaz.
- Restore SQL testleri: **PASS** — 2→5, ikinci uygulama, değişmiş gövdeyi reddetme, transaction rollback, eksik/olmayan yedek, League fonksiyonunu koruma, eski düşürme scriptinin reddi.
- Restore testindeki üç V7 isimli fonksiyon yalnız test fixture'ıdır; canlı fonksiyonların kopyası veya yeni production implementasyonu değildir.
- Java sabiti ve değişen parser gövdeleri Java 8 hedefiyle derlendi/çalıştırıldı: **PASS**. Tüm Android projesi/APK derlendiği iddia edilmez.
- XML parse ve korunan dosya karşılaştırması: **PASS**.
- Mevcut market.rules emulator testi: emulator olmadığı için **1 SKIP**, PASS değil. Rules değişmedi.
- Tam Gradle compileDebugJavaWithJavac: services.gradle.org DNS erişimi olmadığı için Gradle 8.7 indirilemedi; **tamamlanamadı**. Kaynak projedeki önceki Elek görsel biçim sorunlarına bu kapsamda dokunulmadı.

Tekrar çalıştırma:

```sh
node --test supabase/tests/market-policy.test.mjs supabase/tests/production-five.test.mjs functions/test/market.policy.test.js
npm install --prefix /tmp/zeytin-market-test --no-audit --no-fund @electric-sql/pglite@0.5.8
PGLITE_MODULE=/tmp/zeytin-market-test/node_modules/@electric-sql/pglite/dist/index.js node supabase/tests/production-five-sql.mjs
JSON_JAR=/absolute/path/to/json-20240303.jar python3 tests/market-production-five-java.py
```

Test araçları proje runtime bağımlılıklarına eklenmedi. SQL testleri yalnız yerel veritabanı oluşturur; canlı URL veya credential kullanmaz.

## Kalan 2 eşikleri / kapsam

Piyasa Android sabiti, eski Edge/Firebase production policy ve market-schema üretici eşiğinde çalışan 2 bulunmadı. `TEST-ONLY-MIN-PRODUCERS-2.sql` artık çalışır bir düşürme işlemi değildir. Restore dosyasındaki 2 değerleri yalnız eski gövdeyi tanıyıp karşılaştırmak içindir; 2'ye dönüştürülmüş gövde hiçbir zaman EXECUTE edilmez.

Aşağıdaki ayrı Zeytin Ligi manuel test scriptleri, “Zeytin Ligi'ne dokunma” kapsamı nedeniyle korunmuştur; bu Piyasa production işleminde çalıştırılmamalıdır:
- ZEYTIN-LIGI-BOLGE-DIZINI-TEST-2.sql
- ZEYTIN-LIGI-ELEK-BOLGE-FIYATLARI-TEST-2.sql
- ZEYTIN-LIGI-BOLGE-RAPORU-TEST-2.sql

League'e ait bazı PROD-5 isimli kaynaklarda da özel fiyat önizleme davranışı vardır; bunlar ana Piyasa V7 RPC tanımları değildir ve bu görevde düzeltilmedi/uygulanmadı. MarketView'daki grafik için iki nokta ve trend için iki gün gibi değerler üretici gizlilik eşiği değildir; değiştirilmedi. Geçmiş rapor/test fixture'larında tarihsel 2 değerleri kalabilir.

**Kalan production doğrulaması:** Canlı V7 read RPC tanımları/bağımlılıkları görülüp 5 eşiği doğrulanmadan veya kontrollü restore uygulanıp gerçek senaryolar test edilmeden sunucu tarafı tamamlandı denemez. Bu teslimde canlı deploy yapılmadı.
