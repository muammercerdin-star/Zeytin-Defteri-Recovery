# Zeytin Defteri V8 — Gerçek Zeytin Asistanı V1

Kaynak: kullanıcının sağladığı Zeytin-Defteri-V7-Akilli-Bugun.zip.
Kapsam: alt orta asistan düğmesi, ses/yazı soruları, gerçek veriden cevaplar, mevcut ekranlara yönlendirme ve hava şeridi kırpılma düzeltmesi.

## İncelenen kaynaklar

Ana kaynaklar ve ilgili metotları: ProducerFeedActivity.java (ana sayfa, aktif bahçe anahtarı, alt orta düğme, sezon, Intent'ler); AndroidManifest.xml; app/build.gradle (minSdk 24, compileSdk/targetSdk 34 ve Java 8 uyumluluğu).

Veri katmanı: DatabaseHelper.java; GardenRepository.java; SessionManager.java kullanıcı UID bağlantısı; OperationRepository.java; HarvestRepository.java; SaleRepository.java; ElekRepository.java; BrineRepository.java; MarketRepository.java; WeatherRepository.java.

Navigasyon hedefleri/parametreleri: NewOperationActivity, HarvestActivity, SaleActivity, ElekActivity, BrineActivity ve ProducerFeedActivity içindeki HOME/GARDENS/MARKET/MEYDAN/REPORTS yönlendirmeleri. Bu ekranların kendileri değiştirilmedi.

Mevcut mimari: MainActivity ana ekran davranışını ProducerFeedActivity'den alıyor. Ana sayfa ve alt düğme Java ile oluşturuluyor, yeni XML ekranı gerekmiyor. Aktif bahçe kullanıcıya özel home_active_garden_id anahtarından, sezon DatabaseHelper.getActiveSeasonYear(), kullanıcı getActiveOwnerUid()/SessionManager üzerinden geliyor.

## Değiştirilen mevcut dosyalar

1. app/src/main/java/com/zeytindefteri/app/ProducerFeedActivity.java
   - Ortadaki 66dp düğmeye mikrofon simgesi, kısa basmada asistan, uzun basmada aynı eski showQuickCreateMenu eklendi.
   - Asistan Host bağlantısı mevcut ekranları ve WeatherRepository'yi çağırır; mevcut akışlar yeniden yazılmaz.
   - Runtime izin sonucu asistan kontrolcüsüne iletilir. onStop/onDestroy sırasında dinleme ve TTS bırakılır.
   - Yalnız hava film şeridinde ilk ölçümden itibaren boyutlandırma ve sağdan giriş hareketinin kaldırılması.
2. app/src/main/AndroidManifest.xml
   - RECORD_AUDIO izni.
   - Android 11+ paket görünürlüğü için RecognitionService ve TTS_SERVICE queries bildirimleri.
   - Yeni Activity, servis veya arka plan dinleme eklenmedi.

## Yeni sınıflar

- ZeytinAssistantCommand.java: tek merkezli, açıklanabilir Türkçe komut ayrıştırma ve bahçe adı çözümleme. Harici LLM yok.
- ZeytinAssistantData.java: mevcut filtreli okuma API'leri üzerinden gerçek kayıtları okuyan bağımsız adapter. Activity içine SQL eklenmedi; yeni SQL sorgusu, tablo veya kolon yok.
- ZeytinAssistantWeather.java: mevcut hava modellerinden Türkçe cevap. Eksik tahmin alanını uydurmaz.
- ZeytinAssistantController.java: koyu panel, yazılı alternatif, tek seferlik SpeechRecognizer, runtime izin, TextToSpeech, arka planda veri okuma ve eski cevapları engelleme.
- WeatherForecastScrollView.java: kartları çocuklar ölçülmeden önce parent genişliğine sığdırır.

Testler tests/assistant/ altında: AssistantCommandTest.java, AssistantDataTest.java, run_host_tests.py. Test doubles yalnız bu test betiğinin geçici dizininde oluşturulur; uygulama kaynaklarına veya APK'ya dahil edilmez. Testlerdeki örnek sayılar üretim kodunda kullanılmaz.

## Desteklenen ekran komutları

Elek aç / Eleği aç; Hasat aç; Satış aç; Sulama ekle; Gübreleme aç; İlaçlama aç; Salamura aç; Bahçelerimi aç; Piyasayı aç; Meydanı aç; Raporları aç; Ana sayfayı aç.

Sulama/gübre/ilaçlama ve hasat mevcut yardımcı metotları kullanır. Gübreleme kaydının gerçek tür adı “Gübre” olarak gönderilir. Satış mevcut garden_id parametresini alır. Elek ve salamura mevcut genel ekranlarına açılır; bu ekranların bahçe seçme davranışı değiştirilmedi. Sesli “ekle” komutları form açar; kendi başına kayıt oluşturmaz veya kaydetmez.

## Desteklenen veri soruları ve kaynakları

| Soru | Kaynak / anlam |
|---|---|
| Hangi bahçe seçili? | Mevcut aktif bahçe ID'sinin owner filtreli gardens listesindeki adı |
| Kaç ağaç / kaç yaş / çeşidi ne? | tree_count, age, variety; boş değer varsa kayıt yok |
| Son sulama/gübreleme/ilaçlama ne zaman? | getGardenOperations; gerçek operation_date içinden en yeni geçerli geçmiş tarih |
| Kaç kilo hasat? | getGardenHarvests, total_kg |
| Masrafım kaç? | İşlem cost + hasat labor/transport/other + salamura salt/packaging/other/event_cost; mevcut rapor formülü |
| Satış gelirim kaç? | Yalnız SALE; gross_total - transport_cost - deductions; tahsilatla aynı olduğu iddia edilmez |
| Kaç kilo sattım? | Yalnız SALE total_kg; teklif kayıtları hariç |
| 2 numara/yağlık/elek dağılımı | getGardenElekRecords ve getElekGrades; gerçek kg |
| Salamurada kaç kilo? | Mevcut raporun olive_kg giriş toplamı. Cevap bunun kalan stok olmadığını açıkça belirtir |
| Hazır ürün kaç kilo? | Mevcut ready_kg kayıt toplamı |
| Piyasa nasıl / Akhisar 2 numara fiyatı? | Kendi seçili sezon satışlarından market_consent=1, SALE, pozitif kg/fiyat; gerçek satır fiyatlarının medyanı |
| Hava nasıl / rüzgâr / nem? | Değiştirilmemiş WeatherRepository.loadForecastForGarden ve current alanları |
| Yarın yağmur? | Türkiye yerel tarihine göre yarın; günlük yağış olasılığı |
| Yarın ilaç atayım mı? | Yarın için mevcut saatlik rüzgârların en yükseği; yoksa veri yok. Kesin uygulama kararı verilmez |
| Sezon özeti | Gerçek hasat, masraf, satış; kayıt varsa gelir eksi masraf. Eksik kalemler “kayıt bulunamadı” olarak ayrılır |

Piyasa sınırlaması bilinçlidir: asistan başka kullanıcıların kayıtlarını okumaz. Bu nedenle mevcut genel piyasa ekranının tamamına eşdeğer bir sonuç vaat etmez. “Piyasa nasıl?” için 2 numara varsayımı cevapta açıkça yazılır. Bölge belirtilip veri bulunmazsa başka bölgenin fiyatına dönülmez. Sonuç seçili sezondaki kişinin kendi paylaşım izinli satışlarının özetidir; canlı piyasa fiyatı/garantisi değildir.

Salamura giriş kg'sinden sevkiyatlar çıkarılarak kalan stok uydurulmadı. Mevcut rapor alanının anlamı cevapta açıkça korunur.

## Kapsam ve izolasyon

- Normal veri sorusu: adı söylenen bahçe; ad yoksa aktif bahçe; seçili sezon.
- “Toplam” veya genel sezon özeti: mevcut rapor mantığı gibi owner + seçili sezon, tüm bahçeler. Bahçe adı açıkça verilirse o bahçe kapsamı önceliklidir.
- Cevaplar bahçe/tüm bahçeler ve sezonu açıkça yazar.
- Bahçe listesi db.getGardens() üzerinden owner filtreli gelir. Başka kullanıcının bahçe ID'si çözümleme adayına alınmaz.
- İşlemler/hasat/satış/elek DatabaseHelper'ın mevcut owner + season + garden filtreli cursor'larıyla okunur. Brine mevcut owner + season cursor'ından seçili garden_id ile daraltılır; tüm bahçeler sorusunda mevcut rapor gibi bahçeye bağlı olmayan kişisel partiler de dahil olabilir.
- SaleRepository.getSaleGrades yalnız yetkili satış ID'leri için çağrılır; bu metodun kendi activeSaleCondition filtresi de korunur.
- Her istekte kullanıcı, sezon, aktif bahçe ve istek numarası yakalanır. Cevap gösterilmeden tekrar karşılaştırılır. Değişmişse eski sonuç gösterilmez. Panel kapanınca sonuçlar geçersizleşir.
- Aynı bahçe kök adına sahip birden çok adayda açıklama istenir. Tam ad daha güçlü eşleşmedir. Türkçe harfler/harf büyüklüğü/ekler ve zeytinliği-bahçe farkları normalize edilir. Bilinmeyen açık ad aktif bahçeye sessizce çevrilmez. V1 serbest konuşma modeli değildir; desteklenmeyen ifadeler anlaşılmayabilir.
- Aktif bahçe/sezon ayarını asistan değiştirmez; veri sorusu yalnız okunur. Hiçbir veri ekleme, silme, migration veya Firebase yazması yoktur.

## Ses ve izin akışı

Panel açıldığında izin varsa tr-TR ile Android SpeechRecognizer başlatılır. İzin yoksa ilk dinleme isteğinde runtime izin istenir. Red/boş sonuç durumunda yazılı giriş çalışır; otomatik tekrar izin döngüsü yoktur. Cihazda RecognitionService yoksa ya da başlatma hata verirse mesaj gösterilir.

Ses yalnız kullanıcı paneli açınca veya Dinle'ye basınca dinlenir. Sürekli/background dinleme, kayıt dosyası veya üçüncü taraf speech API eklenmedi. Sistem ses tanıma hizmeti cihazına göre internet/uzak sunucu kullanabilir; bunun çevrimdışı çalışacağı garanti edilmez. Yerel veri soruları yazıyla internetsiz çalışabilir; hava sorgusu internet gerektirir.

Türkçe TTS destekleniyorsa cevap otomatik okunur; Oku düğmesi tekrar okur. Türkçe dil/veri/servis yoksa veya speak hata verirse yazılı cevap korunur. Panel kapanışında, Activity duruşunda ve yok oluşunda recognizer iptal/destroy; TTS stop/shutdown uygulanır. TTS konuşmadan önce dinleme durdurulur.

Resmî Android başvuruları:
- https://developer.android.com/reference/android/speech/SpeechRecognizer
- https://developer.android.com/about/versions/11/behavior-changes-11
- https://developer.android.com/reference/android/speech/tts/TextToSpeech.Engine

## Hava kırpılma düzeltmesi

Önceki ölçü güncellemesi layout sonrasında yapılıyordu ve giriş animasyonu kartları sağdan kaydırıyordu. Yeni scroller kart genişliğini onMeasure içinde, ilk çocuk ölçümünden önce ayarlar. Günlük ve saatlik şerit aynı mekanizmayı kullanır. Yatay kaydırma, seçili kart, sekmeler, renkler, metinler, hava repository'si ve API korunur. Girişte yalnız alpha animasyonu sürer, kartlar sağa taşınmaz.

Formül: floor((kullanılabilir genişlik - 4 × boşluk) / 5). Kenar payları ve ekran yoğunluğu dahil edilir.

| Ekran (dp, yoğunluk 1) | Kullanılabilir alan | Kart genişliği | 5 kart + boşluklar |
|---|---:|---:|---:|
| 360 | 336 | 64 | 336 |
| 393 | 369 | 70 | 366 |
| 412 | 388 | 74 | 386 |

Bu üç genişlik 1/2/2.75/3 yoğunluklarda hesapla kontrol edildi: toplam alanı aşmıyor. Bu kontrol gerçek Android ekran görüntüsü testi değildir. Büyük yazı ölçeklerinde ve cihaz fontlarında görünüm cihazda ayrıca doğrulanmalıdır.

## Test sonuçları

- 55 gerçek komut parser/bahçe çözümleme kontrolü: geçti.
- 25 adapter/hava cevap kontrolü: geçti. Gerçek üretim adapter kodu Java 8 hedefiyle geçici Android Context/Cursor/DatabaseHelper test doubles üzerinde derlenip çalıştırıldı. Bu, gerçek SQLite/Android entegrasyon testi değildir.
- Testler gerçek fixture kg/TL/değer değişimlerini, tekliflerin dışlanmasını, masraf kalemlerini, piyasa iznini, bölge yokluğunu, bahçe/kullanıcı/sezon değişimini, eksik hava ve yağış alanlarını doğruladı.
- V7'den gelen 14 host SQLite owner/sezon/garden fixture kontrolü de geçti. Gerçek sorgu ifadelerini kullanır; yardımcı filtrelerin semantiği testte yeniden oluşturulur.
- 70 XML dosyası parse: başarılı.
- Değiştirilen Java ve beş yeni sınıfın Java sözdizimi parse kontrolü: başarılı.
- Mikrofon izni reddi, SpeechRecognition bulunmaması, TTS bulunmaması: ilgili korumalı kod yolları incelendi. Gerçek cihazdaki izin ekranı, mikrofon/ses hizmetleri ve TTS ile yürütülmüş test YOKTUR.
- Navigasyon intentleri kod düzeyinde doğrulandı; cihazda ekran açılışları denenmedi.

İstenen 19 senaryonun ilk 12'si parser ve ilgili veri fixture'larıyla; 16–18 scope/adapter testleriyle; 19 genişlik hesabıyla kontrol edildi. 13–15 yalnız kod yolu incelemesidir, cihaz testinden geçmiş sayılmaz.

Testleri JDK 11+ ve Python bulunan ortamda proje kökünden yeniden çalıştırmak için:
```
python3 tests/assistant/run_host_tests.py
python3 tests/today/SQLiteScopeTest.py
```

## Build sonucu

Gerçek deneme: bash gradlew :app:assembleDebug --offline.
Başarısız/tamamlanamadı: Gradle 8.7 dağıtımı ortamda yok; services.gradle.org için UnknownHostException. Android SDK jar'ı/Gradle dağıtımı bu ortamda bulunmadığından tam Android derlemesi doğrulanamadı. APK üretilmedi. Host Java testlerinin geçmesi tam Android build'in geçtiği anlamına gelmez.

## Kapsam dışında fark edilenler

- Bazı Operation/Harvest/Brine repository sorguları kendi içinde owner/sezon filtresi taşımıyor. Bunlar değiştirilmedi; asistan mevcut filtreli DatabaseHelper yollarını kullanıyor.
- Elek ve Salamura ekranları gönderilen garden_id parametresini kullanmıyor. Ekranlar yeniden yazılmadı; sesli komut mevcut genel ekranı açar, bahçe seçimi o ekranda yapılır. Asistanın sayısal cevapları ise seçilen kapsamdan gelir.
- Mevcut salamura raporunun giriş/ready toplamları kalan fiziksel stok garantisi değildir. Yeni bir stok hesabı veya tablo oluşturulmadı.

## Dosya/veri koruması

Kaynak ZIP'in mevcut dosyalarından yalnız ProducerFeedActivity.java ve AndroidManifest.xml değiştirildi. WeatherRepository, GardenTodayAdvisor, Firebase/google-services, DatabaseHelper, bütün migrationlar, repository'ler ve diğer ekranlar byte bazında korunur. Kaynak dosya silinmedi. Canlı kullanıcı verileri açılıp değiştirilmedi. Final ZIP bütünlüğü ve değişiklik sınırı ayrıca doğrulanır. SHA-256 teslim mesajında verilir.
