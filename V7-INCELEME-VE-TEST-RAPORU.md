# Zeytin Defteri V7 — Akıllı Bugün

Kaynak: kullanıcının sağladığı Zeytin-Defteri-Ana-Sayfa-V6-2-Hava-UI.zip.
Kapsam: yalnızca ana sayfadaki Bugün Yapılacaklar ve bu alanın veri bağlantıları.

## Ön incelemenin yedi cevabı

1. Ana sayfa MainActivity üzerinden ProducerFeedActivity'yi kullanıyor. Kart Java ile buildHomeSection / buildTodayGardenCard içinde oluşturuluyordu; bu alan için XML yok. V7'de renderTodayRecommendations sonuç kartlarını çiziyor.
2. Aktif ID, loadHomeGardenSnapshot üzerinden home_active_garden_id:<kullanıcı> ayarından geliyor. Seçim değişince mevcut refreshCurrentSection çalışıyor. Seçim algoritması değiştirilmedi.
3. Sezon DatabaseHelper.getActiveSeasonYear ile kullanıcıya özel active_season_year ayarından geliyor. Mevcut sezon seçici ekranı yeniden oluşturuyor.
4. DatabaseHelper.getGardenOperations(garden_id), operations ile gardens tablolarını birleştirerek bahçe sahibi ve aktif sezonu filtreliyor. getGardenHarvests aynı işi hasat için yapıyor. Eski sezon_year değeri boş/sıfır olan kayıtların tarih üzerinden filtrelenmesi aynen korunuyor. Son tarih created_at sırasından değil gerçek operation_date / harvest_date üzerinden bulunuyor. Mevcut “Gübre” kayıt türü, “Gübreleme” olarak tanınıyor.
5. WeatherSnapshot: temperatureC, windKmh, humidity, apparentTemperatureC, precipitationProbability. HourlyForecast: timeIso, sıcaklık, hissedilen, rüzgâr, nem, yağış olasılığı. DailyForecast: dateIso, maxTemperatureC, minTemperatureC, precipitationProbability. Mevcut API günlük yağış miktarını sağlamıyor; miktar üretilmedi. Forecast.current veya mevcut güncel sonuç kullanılıyor. Veri modeli/API değiştirilmedi.
6. gardens içinde name, variety, tree_count, age, province, district, neighborhood, photo_uri mevcut. Ana sayfa snapshot'ı ad, fotoğraf ve konumu zaten okuyor. V1, çeşit/yaş/ağaç sayısından zirai ihtiyaç çıkarmıyor; bu alanları gereksiz yere genişletmedik.
7. Mevcut bahçe ve sezon kayıtları, işlem tarihleri, hasat tarihleri, mevcut/saatlik/günlük hava yeterli. Yeni tablo, kolon, migration veya kalıcı öneri kaydı yok.

## İncelenen dosyalar

Yalnız ilgili metotlar ve bağlantılar incelendi; proje genel denetimi yapılmadı.
- app/src/main/java/com/zeytindefteri/app/ProducerFeedActivity.java
- app/src/main/java/DatabaseHelper.java — kullanıcı/sezon filtreleri ve ilgili okuma metotları
- app/src/main/java/GardenRepository.java — bahçe okuma bağlantıları ve mevcut alanlar
- app/src/main/java/com/zeytindefteri/app/SessionManager.java — kullanıcı UID bağlantısı
- app/src/main/java/com/zeytindefteri/app/WeatherRepository.java — sonuç modelleri ve callback akışı
- app/src/main/java/com/zeytindefteri/app/NewOperationActivity.java — mevcut işlem türleri ve Intent parametreleri
- app/src/main/java/OperationHistoryActivity.java — bahçe parametresini kullanıp kullanmadığı
- app/src/main/java/com/zeytindefteri/app/GardenDetailActivity.java — mevcut garden_id giriş noktası
XML dosyalarına yalnız otomatik parse kontrolü uygulandı; içerikleri yeniden tasarlanmadı.

## Değiştirilen mevcut dosya

app/src/main/java/com/zeytindefteri/app/ProducerFeedActivity.java
- Sabit kart yerine en çok üç öneriyi gösteren kompakt kartlar.
- Başlık korundu. İlgili üst bağlantı ve kart aksiyonu “Bahçeyi Aç” ile seçili bahçeye gider; bahçe yoksa “Bahçe Ekle”.
- Hava Detayı, mevcut hero alanına kaydırır; yeni ekran veya navigasyon yok.
- Mevcut hava callback'lerinin sonuçları Today alanına da iletilir. Ek API isteği yok.
- Her ekran oluşumunda bağımsız TodayState; kullanıcı/sezon/bahçe ve durum nesnesi kontrolüyle eski callback'ler yeni kartları güncelleyemez.
- getGardenById tek başına sahiplik filtrelemediğinden, yeni alanın veri okumaları öncesi mevcut db.getGardens() sahiplik filtresiyle ek doğrulama yapılır. Repository ve aktif seçim sistemi değiştirilmez.
- Eski sabit kartın artık kullanılmayan sulama özeti hesabı kaldırıldı; diğer ana sayfa alanlarına dokunulmadı.

## Eklenen dosyalar

- app/src/main/java/com/zeytindefteri/app/GardenTodayAdvisor.java: Android/veritabanı/ağ bağımlılığı olmayan saf kural motoru. Girdi scope, tarih, kayıtlar, hava; çıktı öneri listesi.
- tests/today/GardenTodayAdvisorTest.java: motorun davranış testleri.
- tests/today/SQLiteScopeTest.py: mevcut sorgu ifadelerinin örnek SQLite verileriyle izolasyon kontrolü; kullanıcı veritabanını açmaz. Owner/sezon yardımcılarının koşulları testte eşdeğer biçimde oluşturulur.
- V7-INCELEME-VE-TEST-RAPORU.md: bu inceleme, kurallar ve doğrulama raporu.

## Kurallar

Bütün eşikler GardenTodayAdvisor içinde tek yerde tanımlıdır. Bunlar UI dikkat eşikleridir; ilaçlama güvenlik sınırı, don hasarı sınırı veya zirai karar değildir.

| Durum | Eşik | Seviye / davranış |
|---|---|---|
| Yağış | olasılık >= %60 | WARNING, gerçek tarih/saat ve olasılık; sulama planını kontrol et |
| Rüzgâr | >= 25 km/sa | WARNING, ilaçlama öncesi koşulları kontrol et |
| Rüzgâr | >= 60 km/sa | CRITICAL, aynı ihtiyatlı mesaj |
| Yüksek sıcaklık | >= 35°C | WARNING, koşulları kontrol et |
| Yüksek sıcaklık | >= 42°C | CRITICAL, koşulları kontrol et |
| Soğuk | <= 0°C | WARNING, koşulları kontrol et |
| Soğuk | <= -5°C | CRITICAL, koşulları kontrol et |
| Kayıt kontrolü | son kayıttan >= 30 takvim günü | INFO, yalnız kayıt/bahçe kontrolü; uygulama zamanı kararı yok |
| Eksik kayıt | ilgili türde geçerli geçmiş yok | INFO; “kaydı bulunmuyor” veya geçersiz tarih için “tarihini kontrol et” |
| Normal durum | mevcut ölçümler / yakın tarihli kayıtlar | NORMAL |

- Öncelik CRITICAL > WARNING > INFO > NORMAL; ekranda enum isimleri gösterilmez.
- Aynı hava türü birleştirilir; daha yüksek önem varsa o örnek kullanılır. Eşit seviyede ilk karşılaşılan (şimdi, saatlik sıra, günlük sıra) korunur. En fazla üç kart döner.
- Sulama, gübreleme, ilaçlama ve hasat geçmişi bir kartta özetlenir; veri yoksa ayrı ayrı belirtilir.
- Nem yalnız gerçek güncel ölçüm olarak gösterilir; bitkinin su ihtiyacı çıkarılmaz.
- NaN/sonsuz değerlerden ve geçersiz yağış yüzdesinden uyarı üretilmez.
- Tarihler yyyy-MM-dd, dd.MM.yyyy veya dd/MM/yyyy biçiminde tam ve geçerli olmalıdır. Gelecek tarih geçmiş işlem sayılmaz. Takvim günü farkı kullanılır.
- Hava kaynağı başarısız olursa o kaynak kullanılmaz; diğer başarılı hava sonucu varsa kullanılabilir. Her iki kaynak başarısızsa yalnız yerel geçmişle çalışır.
- İlaç/gübre dozu, sulama miktarı, hastalık teşhisi, yapay zekâ API'si yok.

## Test sonuçları ve sınırlar

Java 8 API hedefiyle bağımsız motor ve testi derlendi, 21 assertion geçti:
1. Son sulama iki gün önce.
2. Sulama kaydı yok.
3. Yaklaşan yağış, gerçek örnek olasılıkla.
4. Kuvvetli rüzgâr.
5. API başarısızlığını temsil eden boş hava girdisi.
6. Bahçe değişimi: eski kapsamlı kayıt ve hava reddedilir.
7. Sezon değişimi: eski sezon verisi reddedilir.
8. Çoklu bahçe: doğru bahçenin tarihi seçilir.
9. Başka kullanıcı ve bahçenin kaydı dışlanır.
10. İnternetsiz girdide yerel geçmiş devam eder.
11. Yeni bahçe: geçmiş uydurulmaz.
12. Beş adaydan yalnız en önemli üçü.
Ek kontroller: kayıt oluşturma sırasından bağımsız son tarih, Gübre etiketi, gelecek tarih, bozuk tarih, eksik/geçersiz hava, eşik altı değerler, eski sulamadan kesin karar çıkarılmaması, DB okuma hatası ile boş geçmişin ayrılması, bahçe olmaması.

14 host SQLite fixture kontrolü geçti: her iki sorgu için kullanıcı/bahçe/sezon ayrımı, eski tarih fallback'i ve misafir hesap sınırı.

70 XML dosyası parse edildi: başarılı. Değişen Java ve yeni motorun sözdizimi parse kontrolü başarılı.

V6.2 hero layout'u ve bütün hava film şeridi çizim metotları karşılaştırıldı: aynı. Hero metodundaki tek işlevsel ek, sonuçları Today alanına da bildiren korumalı callback bağlantılarıdır. API çağrı sayısı artırılmadı.

Gerçek Gradle denemesi: bash gradlew :app:assembleDebug --offline.
Sonuç: başarısız / tamamlanamadı. Gradle 8.7 dağıtımı ortamda yok; wrapper services.gradle.org adresini çözemedi (UnknownHostException). Uygulamanın tam Android derlemesi başarılı kabul edilemez; APK üretilmedi.

Gerçek telefon/emülatör testi yapılamadı. Bahçe/sezon değiştirme ve asenkron callback koruması kod düzeyinde incelendi; cihaz üzerindeki akış, ölçümler ve dokunma davranışı doğrulanmış sayılmaz. Host motor testleri ve SQLite fixture'ları gerçek Android entegrasyon testinin yerine geçmez.

Testleri yeniden çalıştırmak için proje kökünde (JDK ve Python olan bir ortamda):
```
javac -encoding UTF-8 -d /tmp/today-tests app/src/main/java/com/zeytindefteri/app/GardenTodayAdvisor.java tests/today/GardenTodayAdvisorTest.java
java -cp /tmp/today-tests GardenTodayAdvisorTest
python3 tests/today/SQLiteScopeTest.py
```

## Koruma doğrulaması

Kaynak ZIP'teki mevcut dosyalardan yalnız ProducerFeedActivity.java değişti. Diğer bütün dosyalar byte bazında aynı: DatabaseHelper, Repository'ler, SessionManager, WeatherRepository, Firebase dosyaları, XML ve Gradle dahil. Silinen kaynak dosya yok. Yeni kod yalnız mevcut okuma metotlarını kullanıyor; kullanıcı verilerine yazma/silme veya migration yok. Canlı kullanıcı veritabanına erişilmedi.

## Kapsam dışında fark edilenler

- GardenRepository.getGardenById/getGarden sorgusu kendi içinde owner filtresi kullanmıyor. Bu yöntem değiştirilmedi; yalnız yeni Today alanında mevcut owner filtreli bahçe listesiyle erişim doğrulandı. Bu bulgu projenin diğer ekranlarının genel güvenlik denetimi anlamına gelmez.
- OperationHistoryActivity mevcut garden_id Intent parametresini okumuyor. Bu ekran değiştirilmedi; yeni Today bağlantıları bahçe ID'sini kullanan mevcut GardenDetailActivity'ye gider.
