# İş Emri 02 — Bahçe Karnesi + Sezon Zekâsı

Tarih: 2026-09-09. Kod ve host doğrulaması tamamlandı. Gerçek APK derlemesi ortam engeline takıldı; cihaz kabul testi yapılmadı. Bu teslim Android BUILD PASS veya cihazda doğrulanmış release iddiası taşımaz.

## Ana kaynak ve devamlılık

Tek kaynak: `Zeytin-Defteri-IS-EMRI-01-BAHCE-DETAY-FINAL-2.zip` (26.971.962 bayt, 346 dosya).
SHA-256: `b03c4ae269c9a49cc4bcd60cde356b96c201d9cfe4cc1b44f09b7c8db861bdb6`.
Kesinti sonrasında geçici çalışma dizini kaybolmuştu. Aynı İş Emri 01 kaynağı geri alındı; önceki inceleme ve İş Emri 02 kodu konuşmada korunmuş çalışmadan devam ettirildi. Eski ZIP'lerden dosya/kod taşınmadı. İş Emri 01'in tüm kaynak dosyaları teslimde korunmuştur.

## Ön inceleme ve gerçek veri kaynakları

Mevcut ReportActivity, DatabaseHelper rapor metotları, GardenRepository, ElekRepository ve satış/alacak akışı incelendi. gardens: id, owner_uid, tree_count, area_decare; operations: garden_id, owner_uid, season_year, operation_date, cost/type; harvests: total_kg ve labor_cost/transport_cost/other_cost; sales: sale_kind, gross_total, total_kg, transport_cost, deductions, amount_received; sale_grades satış başlığının kaydedilmiş toplamlarının kaynağıdır. Başlık ve satırlar ayrıca toplanmaz.

Elek için elek_records + elek_grades kullanılır; tahmini Elek satış değeri gerçek satış geliri değildir. Salamura için brine_batches ve brine_events kullanılır. Tahsilat için sales.amount_received ve satışa bağlı receivables.total_received bulunur; ayrı payments olay tablosu yoktur. Alacak satırı satışın toplamını yansıtır, ek satış/ek tahsilat olarak toplanmaz.

## Analiz katmanı ve formüller

GardenAnalyticsRepository yalnız mevcut yerel SQLite verisini okur. Aynı yükleme tüm bahçe snapshot'larını ve sezon toplamını üretir. Ayrı SeasonAnalyticsRepository gerekmemiştir; SeasonAnalyticsSnapshot bu kapsamı temsil eder.

| Metrik | Gerçek hesap |
|---|---|
| Hasat | SUM(harvests.total_kg) |
| Kayıtlı masraf | Hasat dışı operations.cost + hasat işçilik/nakliye/diğer + salamura tuz/ambalaj/diğer + salamura olay masrafları |
| kg/ağaç | hasat / güncel tree_count; payda pozitif değilse — |
| kg/dekar | hasat / area_decare; alan yok/pozitif değilse — |
| TL/kg maliyet | kayıtlı masraf / hasat; hasat sıfırsa — |
| Satış geliri | SALE başlıklarında gross_total − transport_cost − deductions |
| Satılan kg | SALE başlıklarında total_kg toplamı |
| Ağırlıklı fiyat | toplam gross_total / toplam satılan kg; kesintiler öncesi fiyat olduğu UI'da açıklanır |
| Satış tahsilatı | Satışın geçerli alacak aynasındaki total_received; ayna yoksa sales.amount_received |
| Kalan alacak | OPEN alacakta max(amount − total_received, 0); kapalı kayıtta 0; satışın alacak aynası yoksa max(net satış − amount_received, 0) |
| Kayıtlı Net Sonuç | satış geliri − kayıtlı masraflar |
| Elek dağılımı | Gerçek elek_grades.kg toplamları: DOUBLE, 1, 2, 3, 4, YAGLIK |
| Baskın Elek | En yüksek pozitif kg; eşitlikte yukarıdaki sabit sıra; veri yoksa “Elek kaydı yok” |
| Sezon değişimi | (aktif − önceki) / önceki × 100; iki tarafta karşılaştırılabilir kayıt yoksa veya önceki değer 0 ise yüzde yok |

Kayıtlı Net Sonuç muhasebe net kârı değildir. Girilmemiş emek, amortisman, kira, vergi ve fırsat maliyeti dahil varsayılmaz. Satış nakliye/kesintileri satış gelirinde düşüldüğü için ayrıca masrafa eklenmez.

Aynı satışa yanlışlıkla birden fazla alacak aynası bağlanmışsa owned satırlardan updated_at, created_at, id azalan sırasıyla en yenisi kullanılır; bunlar taksit olayları gibi toplanmaz. Bağımsız alacak, created_at UTC yılından yalnız genel sezon kalan alacağına eklenir; herhangi bir bahçeye veya satış gelirine/tahsilatına uydurularak bağlanmaz. Bahçesiz salamura masrafı yalnız sezon toplamına eklenir ve UI'da açıklanır.

## İzolasyon, karşılaştırma ve boş veri

Bahçe verisi owned gardens bağlantısı + garden_id + açık sezon yılı üzerinden gelir. Açıkça başka owner_uid taşıyan child kayıtlar dışlanır. Mevcut legacy davranışla boş/null child owner owned bahçeye bağlıysa kabul edilir. Guest bahçesinde null/boş owner davranışı korunur. Kullanıcı parametreleri SQL'e bağlanır. Bağımsız alacak/salamura kendi owner kapsamından gelir.

season_year null/0 ise mevcut ilk/son dört karakter tarih-yılı uyumluluğu korunur. Aktif sezon ve tam bir önceki yıl ayrı sorgulanır; aktif oturumun sezonu değiştirilmez. Tüm bahçeler toplamı aynı snapshot'lardan ve açıkça bahçesiz kayıtlardan oluşur; çift sayım yoktur.

0/null paydalarda — gösterilir; nonfinite bozuk veri yüklemede reddedilir. Eksik önceki sezon veya boş mevcut sezon için sahte yüzde üretilmez. Toplu alan/ağaç metadatası eksikse toplu verim uydurulmaz. Tarihsel ağaç sayısı/alan tutulmadığından önceki sezon verimi de güncel bahçe metadatasını kullanır; bu sınırlama ekranda belirtilir. Karşılaştırma tam sezon kayıtlarıdır, eşit tarih aralıklı YTD değildir.

## ReportActivity tutarlılığı

ReportActivity'nin mevcut tasarım/kart oluşturucuları korunarak veri bağlama noktası aynı GardenAnalyticsRepository snapshot'ına bağlandı. Ortak hasat, masraf, gelir, tahsilat/alacak ve kayıtlı net sonuç için iki ayrı hesap kaynağı bırakılmadı.

İncelemede gerçek eski rapor kusurları saptandı: NULL maliyet bileşeni bütün satırı yok edebiliyordu; bazı sorgular child owner'ı ayrıca denetlemiyordu; mükerrer alacak aynaları çift sayılabiliyor, alacak aynası olmayan eski satışın bakiyesi görünmeyebiliyordu. Yeni kaynakta bileşenler ayrı COALESCE edilir, child sahipliği denetlenir ve yukarıdaki deterministik alacak politikası kullanılır. Temiz fixture eski gerçek helper formülleriyle aynı ortak toplamları verir; kusurlu fixture için düzeltilmiş toplam hem analizde hem ReportActivity gösteriminde aynıdır.

DatabaseHelper'ın diğer ekranların kullandığı eski metotları geniş refactor edilmedi. Bahçe Detay'ın mevcut daha dar özetleri bu görevde değiştirilmedi; yalnız karnenin giriş noktası eklendi.

## UI, performans ve offline

Bahçe Detay → Bahçe Karnesi; Bahçeler → Sezon Özeti girişleri gerçektir. Koyu zemin/yeşil vurgu, mevcut geri navigasyon ve doğal Android kart yaklaşımı korunur. Hasat, masraf, gelir, net sonuç, verim, satış, Elek ve sezon karşılaştırması kartları bulunur. Seçici aynı önbelleğe alınmış snapshot ile tüm bahçeler/bahçe arasında geçer.

Tek sezon 8, iki sezon 15 sorgu; bahçe sayısına göre N+1 yoktur. Tutarlı okuma işlemi içinde snapshot alınır, arka plan executor ile UI iş parçacığı dışında hesaplanır. Oturum/sezon değişimi ve eski sonuç için kapsam/generation denetimleri vardır. 400 ek işlemde sorgu sayısı değişmedi. Cihazda süre ölçümü yapılmadı.

Yeni analiz kodu Firestore read/listener, Function, REST veya Firebase bağımlılığı eklemez. İşlevin veri yolu tamamen yereldir ve fixture sırasında ağ gerektirmez. Gerçek telefonda uçak modu testi yapılmadı; yerel kod yolu ve SQLite testleri doğrulandı. Mevcut cloud sync, kimlikler, tombstone ve sync yöneticisi değiştirilmedi.

## Değiştirilen dosyalar

- app/src/main/AndroidManifest.xml: dışa kapalı analiz Activity kaydı.
- app/src/main/java/ReportActivity.java: ortak snapshot, arka plan yükleme, doğru etiket/açıklamalar.
- app/src/main/java/com/zeytindefteri/app/GardenDetailActivity.java: karne girişinin bağlanması.
- app/src/main/java/com/zeytindefteri/app/GardensActivity.java: genel sezon özeti girişi.
- app/src/main/res/layout/activity_garden_detail.xml: mevcut tasarımla uyumlu karne düğmesi.

## Yeni dosyalar

- app/src/main/java/com/zeytindefteri/app/GardenAnalyticsRepository.java: yerel kapsamlı veri okuma ve hesap kaynağı.
- app/src/main/java/com/zeytindefteri/app/GardenAnalyticsSnapshot.java: değişmez metrikler ve güvenli türev formüller.
- app/src/main/java/com/zeytindefteri/app/SeasonAnalyticsSnapshot.java: sezon toplamı ve bahçe snapshot haritası.
- app/src/main/java/com/zeytindefteri/app/GardenAnalyticsActivity.java: karne/sezon UI.
- tests/garden_analytics/check_analytics.py: gerçek SQLite fixture testleri.
- tests/garden_analytics/README.md ve validation/*: çalıştırma açıklaması ve test/build kanıtları.
- IS_EMRI_02_REPORT.md: bu teslim raporu.

DB_VERSION **19, değişmedi**. Migration yoktur. DatabaseHelper/DatabaseSchema değiştirilmedi. Bahçe/child ID, owner_uid, season_year ve kullanıcı kayıtlarına yazma/silme yapılmaz. Analiz testleri her repository çağrısında veritabanı dosyasının byte hash'inin aynı kaldığını doğrular. Kaynak ZIP'in 346 dosyasından hiçbiri kaldırılmadı; yalnız yukarıdaki 5 mevcut ürün dosyası değişti. Firebase yapılandırması, rules, Functions ve kapsam dışı modüller aynen korunmuştur.

## Test ve build sonuçları

Önceki çalışma bölümünde başlangıç regresyon komutları başarılıydı; kesintide geçici başlangıç logları kayboldu. Son durumda aynı mevcut testler değiştirilmeden yeniden çalıştırıldı. Ham son loglar validation/ altındadır; başlangıç logları yeniden üretilmiş gibi sunulmaz.

- Son tur: 15/15 test/kontrol komutu exit 0 (komut listesi final.json).
- Yeni analiz: **38 fixture kontrolü PASS**. Tam üretim analiz sınıfları Java 8 hedefiyle derlenir; Android SQLite API köprüsü gerçek SQLite dosyasında sorguları çalıştırır. ReportActivity'nin gerçek gösterim metotları hafif TextView adaptörleriyle doğrulanır; Android UI testi değildir.
- Örnekler: 1850/120 = 15,4166667 kg/ağaç; 18450/1850 = 9,97297297 TL/kg; (100×50+900×70)/1000 = 68 TL/kg.
- Owner/garden/season, guest/legacy, NULL maliyetler, 0 payda, absent season, alacak dedup/fallback, gerçek Elek/tie, bağımsız kayıtlar, sabit sorgu sayısı, salt okuma ve ReportActivity ortak toplamları test edildi.
- İş Emri 01: **52 gerçek SQLite kontrolü PASS**; ID/owner/child ilişkileri, düzenleme, notların tekrar okunması ve geçmiş kapsamı korundu.
- Diğer mevcut Asistan, UX, Elek, akış, konum, Meydan, threaded, today testleri exit 0.
- Functions regresyonu: 34 test; **30 PASS, 4 SKIP, 0 FAIL**. Atlanan testler PASS sayılmadı. npm check exit 0. Functions kodu değiştirilmedi.
- Statik: 83 Java dosyası Java 8 sözdizimi/tekrarlı imza kontrolü; 104 XML, 29 Manifest bileşeni, 601 kaynak referansı PASS. Bunlar APK build değildir.
- Gerçek assembleDebug denemesi: **ENGELLİ / exit 1**. Gradle 8.7 dağıtımı indirilemedi: `java.net.UnknownHostException: services.gradle.org`. Ortamda hazır Gradle/Android SDK android.jar bulunmadı. Java 17 ile komut satırı java-home override kullanıldı; proje Gradle ayarı değiştirilmedi. Tam hata android-build.log dosyasında. **APK üretilmedi; BUILD PASS değil.**

## Kalan riskler ve kabul sınırı

Gerçek Android derleme, cihaz açılışı, gezinme/döndürme/arka plana geçiş, uçak modu ve cihaz performansı doğrulaması kalan kabul kapılarıdır. Host testleri bunların yerine geçmez. Tahsilatlar kümülatif alanlardır; önceki sezon satışına bugünkü tahsilat satışın sezonunda görünür, gerçek tahsilat tarihi nakit akışı çıkarılmaz. Tarihsel bahçe metadatası yoktur. Aynı satışın çoklu alacak satırları için en yeni ayna politikası kullanılır, taksit modeli icat edilmez. Kullanıcının girmediği maliyetler bilinmez. Diğer eski helper tüketicileri bu görevin sınırlı rapor düzeltmesi dışında kalır.

## Teslim ve SHA-256

Tam proje: `Zeytin-Defteri-IS-EMRI-02-BAHCE-KARNESI-FINAL.zip`. Standart DEFLATE ZIP kullanılır; CRC ve ayrı dizine tam çıkarma/byte karşılaştırması teslimden önce yapılır. ZIP'in kendi hash'ini içindeki rapora koymak öz referans oluşturacağından nihai ZIP SHA-256 değeri dışarıdaki aynı raporun dağıtım ekine ve `.zip.sha256` dosyasına yazılır. ZIP içindeki rapor bu paketleme öncesi metindir.
