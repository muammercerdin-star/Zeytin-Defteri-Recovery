# Hava kartı geri dönüş / flicker düzeltmesi

Kaynak: Zeytin-Defteri-FINAL-Meydan-Konum-Fix.zip.

## Gerçek neden

MainActivity, ProducerFeedActivity'den türediği için ana sayfanın lifecycle kodu ProducerFeedActivity içindedir. onResume doğrudan refreshCurrentSection çağırıyor; sosyal/özel veri senkronizasyonu dönüşleri de ana sayfayı yenileyebiliyor. HOME için refreshCurrentSection → showSection → contentHost.removeAllViews → buildHomeSection → buildHomeHero zinciri her defasında yeni bir hava paneli kuruyordu. Panel loading mesajıyla başlıyor ve hem loadForecastForGarden hem loadForGarden yeniden çağrılıyordu.

WeatherRepository'nin mevcut önbelleği tahmin verisi değil, adresin çözülmüş koordinatıdır. Bu yüzden koordinat cache'i olsa da hava kartı ağ dönüşünü bekleyerek tekrar oluşuyordu. WeatherForecastScrollView ölçüm kodu bu geri dönüş sorununun asıl kaynağı değildir; değiştirilmedi.

## Değişen dosya

Yalnızca app/src/main/java/com/zeytindefteri/app/ProducerFeedActivity.java.

- Activity ömrüyle sınırlı HomeWeatherState eklendi. Son başarılı tahmin/güncel hava, mevcut hero View ve refresh durumu tutulur. Static Activity/Context yoktur.
- Aynı kullanıcı, bahçe ve konumda aynı hava hero'su eski ebeveyninden ayrılıp yeni ana sayfa gövdesine bağlanır. Hava panelinin çocukları temizlenmez; loading bloğuna girilmez. Mevcut 24 Saat / 5 Günlük seçimi korunur.
- Aynı hava durumuyla ana sayfa yenilenirken dış dikey ScrollView konumu geri yüklenir. Diğer ana sayfa verilerinin mevcut yenilenme davranışı engellenmez.
- Gerçek bahçe/adres veya çözülmüş koordinat değişimi yeni hava state'i oluşturur. Başka hesabın hava görünümü yeniden kullanılmaz.
- Aktif seçim geçici olarak 0 ise daha önce seçili bahçe ancak mevcut sahiplik kontrolünden geçip hâlâ bulunabiliyorsa korunur. Gerçekten silinmiş bahçe veya kullanıcı değişimi için bu fallback uygulanmaz.
- Her iki mevcut hava isteği için ayrı in-flight bayrağı vardır. Aynı state içinde süren isteğe ikinci bir istek eklenmez. Son denemeden itibaren 10 dakika içinde yeniden ağ isteği başlatılmaz; süre dolduğunda mevcut veri görünürken sessiz refresh yapılır. Otomatik timer eklenmedi.
- Hata callback'i son başarılı veriyi veya paneli silmez. Cache yokken ilk yükleme/hata görünümü devam eder.
- Aynı tahmin içeriği yeniden gelirse panel yeniden çizilmez. Değişen tahmin mevcut seçili sekmede çizilir.
- Bugün Yapılacaklar yeni state'i önce son başarılı hava verisiyle doldurur; bahçe işlem kayıtlarının mevcut yeniden okunması korunur. Öneri hesaplama algoritması değiştirilmedi. Aynı tahmin için gereksiz callback kaynaklı öneri çizimi atlanır.
- Eski bahçe/Activity'ye ait callback'ler güncel state'i değiştiremez. onDestroy retained state referansını bırakır. Mevcut repository threadleri ve 8 saniyelik ağ timeout'ları değiştirilmedi.

## Cache sınırı

Bu düzeltme aynı Activity içindeki bahçeye girip geri dönme ve bölüm yenileme senaryosunu hedefler. Cache Activity belleğindedir; uygulama süreci tamamen öldürülürse veya Activity yeniden oluşturulursa normal cold-start yüklemesi yapılır. Yeni kalıcı hava dosyası/SQLite cache tablosu eklenmedi. İnternet kapalıyken bu Activity'de son başarılı veri varsa korunur; hiç yüklenmemiş veri uydurulmaz.

## Korunan dosyalar

Kaynak ZIP ile byte karşılaştırmasında ProducerFeedActivity dışındaki tüm mevcut dosyalar aynıdır: WeatherRepository, WeatherForecastScrollView, MainActivity, Open-Meteo endpoint/model yapısı, GardenRepository, DatabaseHelper/SQLite, Firebase/Firestore, Meydan/threaded replies, FCM, satış/hasat/salamura, asistan, XML/resources ve Manifest. Navigasyon hedefleri değiştirilmedi. Yeni dependency veya migration yoktur.

## Kontroller

- 73 Java dosyasında Java 8 sözdizimi kontrolü hatasız geçti. Bu Android type-check/full build değildir.
- XML dosyaları parse edildi; resources/Manifest kaynakla aynıdır.
- Kaynak kontrolleri: cached hero erken dönüşü, hero referansının saklanması, iki pending koruması, başarısız refresh'te veri koruması, tek dosya değişikliği doğrulandı. Bunlar canlı Android lifecycle testi değildir.
- Gradle assembleDebug denendi; services.gradle.org için UnknownHostException nedeniyle Gradle 8.7 indirilemedi. Android derlemesine ulaşılamadı; başarılı build veya APK iddiası yoktur.
- Fiziksel cihaz/emülatör, gerçek Open-Meteo ağı ve görsel flicker testi bu ortamda yapılamadı. 3–4 kez bahçeye gir/geri dön, yavaş/offline bağlantı ve gerçek bahçe değişimi senaryoları cihazda doğrulanmalıdır.

## Cihaz kabul adımları

1. Cold start sonrası hava yüklenmesini bekle, 24 Saat veya 5 Günlük sekmesini seç.
2. Bahçeyi Aç → geri işlemini 4 kez tekrarla. Hava çocukları, sekme ve kaydırma konumu korunmalı; loading tekrar görünmemeli.
3. İnterneti kapatıp aynı işlemi tekrarla. Son başarılı hava kalmalı.
4. 10 dakika sonrasında dön. Mevcut veri dururken refresh yapılmalı; ağ hatasında veri silinmemeli.
5. Gerçekten başka bahçeyi veya bahçe konumunu seç. Önceki bahçenin verisi taşınmamalı; yeni yükleme yapılmalı.
6. Bahçede işlem ekleyip dön. Bugün Yapılacaklar işlem geçmişi güncellenirken mevcut hava verisini kullanmalı.
