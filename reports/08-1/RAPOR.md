# İş Emri 08.1 — Supabase restore güvenlik hotfix

Ana kaynak yalnız `Zeytin-Defteri-IS-EMRI-08-PIYASA-PRODUCTION-5-FINAL.zip`.
Canlı Supabase'e bağlanılmadı; production SQL/deploy yapılmadı.

## Kaynak incelemesi ve gerçek risk

08 paketindeki TEST-ONLY-MIN-PRODUCERS-2.sql artık yalnız hata veren devre dışı giriş noktasıdır. Tarihsel test scriptinin özgün function seçim sorgusu bu ana kaynakta bulunmuyor. Eski ZIP açılmadı. Dolayısıyla tarihsel sorgunun tam pattern'ini kaynaktan kanıtlamak mümkün değildir; kullanıcı tarafından bildirilen geniş seçim riski canlı batch kayıtlarından doğrulanmalıdır.

08 RESTORE dosyasında doğrulanabilen seçim: public şemasında market_snapshot/market_regions/market_chart; private şemasında `^(market_|zd_market_)`, ardından `!~ '^market_league_'` dışlaması. Bu dışlama League'i kanıt olsa dahi atlıyordu. Ayrıca son batch otomatik seçiliyor ve üç public RPC'nin yedeği zorunlu tutuluyordu.

08 restore'undan birebir korunan tarihsel dönüşümler:
1. `count(distinct ...producer...)` karşılaştırmalarında 5→2.
2. İsminde producer bulunan değişken/sütun karşılaştırmalarında 5→2.
3. min/required/minimum + producer sabit atamalarında 5→2.
Karşılaştırma operatörleri ve regexp bayrakları orijinal 08 dosyasındakiyle aynıdır. Bu dönüşümler yalnız beklenen geçmiş gövdeyi yeniden hesaplar; test eşiği çalıştırılmaz.

League SQL'leri mevcut: market_league_regions, market_league_grade_regions, market_league_region_report. Region report tanımında current_producer_count >= 5 ve previous_producer_count >= 5 gibi dönüşümün etkileyebileceği ifadeler bulunuyor. Dolayısıyla League riski teknik olarak gerçek olabilir; bu canlıda patch uygulandığının kanıtı değildir. Ayrı tarihsel League TEST-2 scriptleri değiştirilmedi ve çalıştırılmamalıdır. Restore bunların bağımsız değişikliklerini körlemesine geri almaz.

## Yeni eligibility ve transaction

İsim whitelist/League blacklist kaldırıldı. Tek veya operatörce açıkça seçilmiş 5→2 batch'in bütün yedek kayıtları incelenir. Çoklu batch otomatik seçilmez. Kimlik: schema + function name + identity arguments; tam CREATE tanımı karşılaştırılır. Yedek tanımdan tarihsel dönüşümle elde edilen test tanımı mevcut pg_get_functiondef ile birebir eşleşirse restore edilir. Orijinal tanıma zaten eşitse idempotent şekilde atlanır.

Eksik/farklı signature, sonradan değişmiş gövde, kopya kayıt, geçersiz metadata ve tanınmayan dönüşüm unsafe sayılır. Tek unsafe kayıt tüm apply'ı reddeder. Yedeksiz fonksiyonlar raporlanır fakat değiştirilmez. Tüm batch ilk değişiklik öncesi doğrulanır, apply sırasında tekrar kontrol edilir. BEGIN/COMMIT kullanılır; hata halinde aborted transaction ROLLBACK ile kapanır. Kısmi restore yoktur.

Güvenilir orijinal backup ve eşzamanlı DDL olmayan bakım aralığı gerekir. Bu araç kötü niyetle değiştirilmiş backup'ı kriptografik olarak doğrulamaz. Yedeksiz, farklı batch'te veya tanımı eksik olan fonksiyonlarda canlı production eşiğinin 5 olduğunu garanti etmez.

## Dry-run ve daha sonra canlı uygulama sırası

1. Yetkili operatör doğru projeyi doğrular, DDL/deploy'u durdurur ve mevcut tanımları/yedek tablosunu yedekler.
2. `supabase/DRY-RUN-RESTORE-MIN-PRODUCERS-5.sql` TAM dosya: READ ONLY transaction; sonuç NOTICE/messages panelinde. RESTORE_ELIGIBLE, SKIPPED_ALREADY_ORIGINAL, SKIPPED_NO_BACKUP, MISMATCH ve UNSAFE durumları.
3. Gerekirse incelenen batch_id iki dosyanın selected_batch alanına yazılır; dry-run tekrarlanır.
4. `supabase/VERIFY-PRODUCTION-MIN-PRODUCERS-5.sql`: mevcut Piyasa/League SQL ve bağımlılıklarını inceleme.
5. Hiçbir unsafe/mismatch kalmadığında ve canlı uygulama yetkisi verildiğinde `supabase/RESTORE-MIN-PRODUCERS-5.sql` TAM dosya. Hata halinde ROLLBACK; dosyanın ortasından devam edilmez.
6. Dry-run + VERIFY tekrar; gerçek RPC'lerde 1–5 distinct ve tek üretici/10 satış doğrulanır.

Tam kullanım: `supabase/RESTORE-SAFETY-08-1.md`.

## Test sonuçları

Yerel PGlite 0.5.8 PostgreSQL motorunda 11 güvenlik senaryosu PASS:
- Kanıtlı Market ve League test patch'i restore edildi.
- Hiç patch edilmemiş League değişmedi.
- Sonradan değiştirilmiş gövde bütün batch'i durdurdu; başka candidate değişmedi.
- Yedeksiz fonksiyon değişmedi.
- Farklı signature hiçbir değişiklik olmadan reddedildi.
- READ ONLY dry-run tanımları birebir korudu, eligible/mismatch/no-backup raporladı.
- Eksik backup tablosu, kopya kimlik, çoklu belirsiz batch, yanlış CREATE hedefi ve tanınmayan patch reddedildi.
- Tekrar restore idempotent; doğrulanmış restore fixture'larında 2 eşiği kalmadı.

Restore edilmiş Market VE League distinct fixture'larında:
| Üretici | Sonuç |
|---|---|
| 1 | HIDDEN |
| 2 | HIDDEN |
| 3 | HIDDEN |
| 4 | HIDDEN |
| 5 | PUBLIC |
| Tek üretici, 10 satış | HIDDEN |

08 gerçek market-schema.sql yerel regresyon testi aynı sınırları geçti. Mevcut Edge/Firebase + 08 policy testleri 19/19 PASS. SQL'ler gerçek yerel PostgreSQL motorunda çalıştırıldı. Patch fixture'ları canlı V7/League tanımlarının yeniden yapımı değildir. Canlı V7 RPC gövdeleri kaynakta hâlâ eksiktir; gerçek endpoint/cihaz/deploy testi yapılmadı.

Android Java/XML/Manifest/Gradle byte-byte korunmuştur; bu SQL-only görevde Android build tekrar çalıştırılmadı. Önceki 08 raporundaki build sınırlaması geçerlidir.

## Değişen dosyalar

Mevcut:
- supabase/RESTORE-MIN-PRODUCERS-5.sql
- supabase/VERIFY-PRODUCTION-MIN-PRODUCERS-5.sql
- supabase/DIRECT-RPC-V7-README.md
- supabase/tests/production-five-sql.mjs (artık geçerli olmayan üç public RPC kapsam beklentisi güncellendi; değişmiş gövde hâlâ reddedilir)

Yeni:
- supabase/DRY-RUN-RESTORE-MIN-PRODUCERS-5.sql
- supabase/RESTORE-SAFETY-08-1.md
- supabase/tests/restore-safety.test.mjs
- reports/08-1/ altındaki rapor, test logları ve integrity.json

08 raporu geçmiş teslim kaydı olarak aynen korunmuştur; restore kapsamı için güncel olan bu 08.1 raporudur. Android, moderation-api, Firebase, Firestore, Auth, SQLite, navigasyon ve League Android UI değiştirilmedi.
