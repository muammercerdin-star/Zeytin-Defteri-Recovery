# Adım 5 durum ve devam kaydı

**3 Kasım 2026 — Adım 5 bütünüyle tamamlanmadı.** Kısa QR ve geçici oturum düzeltmeleri uygulanmıştır. Kalıcı cihazın kripto/IndexedDB şeması/relay desteği vardır; telefon servisi ve web sayfası entegrasyonu eksiktir. Kullanıcıya kalıcı oturum kutusu gösterilmez, sayfayı kapatıp açınca hâlâ yeni QR gerekir. Bu belge bir tamamlama/onay raporu değildir.

## Uygulanan parçalar

- `ZD1.` + 48 bayt base64url = 68 karakter. Oda kimliği tarayıcı B anahtarının alan ayrımlı 128 bit özetidir. Böylece S'yi gören biri HMAC hesaplayabilse bile aynı oda kimliğiyle başka B yerleştiremez. Telefon capability'si S'ten HKDF ile türetilir. B/nb/son kullanma zamanı, soketten S-türevli HMAC ile gelir. Süre 30 saniyenin altındaysa telefon reddeder. Eski JSON QR telefon tarafında geçiş için desteklenir.
- QR H düzeltmeli, dört modül sessiz bölgeli, en az 320 piksel; ortasında yeşil yuvarlak/beyaz Z. Logonun çapı kodun %18'i, kapladığı alan yaklaşık %2,5'tir.
- Ad/yetki kamera öncesinde seçilir. Web eşleşmesine özel kamera ekranı mevcut ZXing'i kullanır. Sürekli otomatik odak, dokununca odak ve destekleyen kamerada fener vardır. Kamera izni çalışma zamanında istenir; görüntü saklanmaz. Diğer QR ekranları değiştirilmedi.
- Geçici oturumun görünür sekmeye dönüşünde beş dakika içinde anında resume. Explicit relay kapanış nedeni telefon/tarayıcı diye ayrılır. Client terminal kapanışta gereksiz yeniden bağlanmaz; HTTP/relay kodu içermeyen sırlar, içerik ve anahtarlar tanıya eklenmez.
- Service kapanışı nesil + startId ile korunur. Yeni başlangıç eski stopSelf kuyruğuyla durdurulmaz.
- Liste açık oturum + son üç kapanmış oturumu gösterir; geçmiş temizlenir. Bekleyen onay satırı yalnız geçerli bekleyen komut varsa görünür. Yeni QR okut düğmesi beklerken kullanılabilir.

## Kalıcı cihaz altyapısı — henüz devreye alınmadı

- `device-keys.js`: dışarı aktarılamayan ECDSA P-256 özel anahtarı, yalnız `key` ve `device` alanlarına izin veren IndexedDB kayıt yardımcıları. `device` yalnız cihaz kimliği, rastgele oda kimliği ve sabit telefon açık anahtarıdır. İş verisi/ad/yetki saklama API'si yoktur. **Mevcut app.js bu modülü çağırmaz.**
- Java/JS cihaz el sıkışması: telefonun imzalı yeni nonce'u; tarayıcının imzalı geçici B + nb'si; telefonun imzalı geçici P + np'si; uzunluk önekli transcript; yeni ECDH/HKDF yön anahtarları ve iki yönlü HMAC kanıtı. Yeni bağlantılar arasında eski nonce/epoch tekrar kabul edilmez.
- Relay `POST /v2/devices`: yalnız eşleşmiş etkin telefon capability'siyle kayıt. Kalıcı oda ve rol anahtarlarının yalnız hash'leri saklanır. WebSocket girişinde beş saniyelik yeni challenge ve role bağlı ECDSA imzası gerekir. Anahtar/oda kimliği kayıtları relay'de açık olarak saklanmaz. Kayıtlı oda 30 gün kullanılmazsa silinir; heartbeat süreyi uzatmaz. Yalnız doğrulanmış telefonun `device_active` bildirimi süreyi yeniler. `forget` telefon rolüne ayrılmıştır.
- Kalıcı odada hibernation auto-response desteği eklendi. Gerçek Cloudflare hibernation/billing davranışı bu ortamda ölçülmedi; testler Node çalışma zamanı taklididir.

## Tamamlanması gerekenler

1. Telefonda owner'a bağlı cihaz metadata kaydı, Android Keystore kimliği ve çıkış/yetki yönetimi.
2. İlk eşleşmedeki şifreli cihaz kaydının relay + telefon + IndexedDB'ye atomik/geri alınabilir bağlanması. Browser kayıt başarısızlığı geçici oturuma açıkça dönmeli.
3. `TezgahWebService` içinde kayıtlı cihazlar için hazır bağlantılar, uygulama açılışında hazır servisin başlatılması, bildirimden durdurma. Aynı cihazın yeni bağlantısı eski komut/onay neslini kullanmamalı.
4. Web'de varsayılan işaretli kutu, QR'sız açılış ve telefon bekleme durumu. 30 dakika sonunda yalnız iş verisini temizleyip cihaz kimliğini koruyan kilit. Geçici beş dakika davranışı korunmalı.
5. Kalıcı cihazın komut yetkisi telefondan okunmalı; resume/yeni el sıkışma yetki yükseltmemeli. Mevcut idempotency günlüğünün çoklu cihazla uyumu denetlenmeli; başka aktif cihazın günlüğü silinmemeli.
6. Gerçek sayfa + telefon servisinde QR'sız tekrar açılış, telefondan cihaz silme ve 30 gün sonra düşme testleri. Şu an bunların yalnız kripto/relay/depoma bileşenleri sınanmıştır; tam ürün akışı geçti denemez.

## Derleme ve cihaz sınırları

`bash gradlew :app:compileReleaseJavaWithJavac` denendi. Gradle dağıtımını indirirken `UnknownHostException: services.gradle.org` ile durdu. Android SDK da mevcut değil. **APK derlenmedi.** Phone/Client kaynakları Java 8 hedefiyle gerçek OkHttp 4.12.1 / Okio 3.6.0 / Kotlin stdlib 1.8.21 JAR'larına karşı host'ta derlendi; bu Android uygulama derlemesi değildir.

Yeni kamera ekranındaki Android API'leri minSdk 24 altında mevcuttur: Activity/Intent/SurfaceView/SurfaceHolder/WindowManager (API 1), Camera ve preview callback (API 1), Camera.cancelAutoFocus ve flash parametreleri (API 5), arka kamera seçimi/getCameraInfo/open(int) (API 9), sürekli picture focus (API 14), çalışma zamanı kamera izinleri (API 23). Service.stopSelf(int) API 5'tir. Yeni imza/transcript yardımcıları Java 8 host'ta derlenir. Cihazda odak, fener, ekran dönüşü, servis yarışı, arka plan/pil davranışı **doğrulanmadı**.

SDK/AGP/Gradle bağımlılıkları, canlı adresler, wrangler vars, config.js ve _headers değiştirilmedi. Yeni QR için Android/Worker/web sürümlerinin birlikte güncellenmesi gerekir. Kullanıcının ayrı görsel değişikliği commitlere dahil edilmedi.

## Ücretsiz kota hesabı

Resmî kaynaklar (3 Kasım 2026):

- https://developers.cloudflare.com/durable-objects/platform/pricing/
- https://developers.cloudflare.com/durable-objects/api/state/#setwebsocketautoresponse

DO Free: günlük 100.000 istek, 13.000 GB-s; SQLite günlük 5 milyon okuma, 100.000 yazma, toplam 5 GB. Gelen WebSocket mesajlarında 20:1 hesap uygulanır. Auto-response ek çalışma süresi üretmez. Limit aşılırsa Free işlemler hata verir.

**Ölçüm değil, örnek varsayım:** tek tezgâh, günde 10.000 gelen iş/protokol mesajı yaklaşık 500 DO istek birimi eder; bağlantı/HTTP/alarm çağrıları ayrıca eklenir. Telefon 24 saat + web 8 saat, 60 saniyelik uygulama heartbeat'i varsayılırsa 1.920 mesaj, muhafazakâr olarak 96 ek istek birimidir. 10.000 olayın toplam aktif süresi olay başı 10 ms kabul edilirse 100 saniye × 0,128 GB = 12,8 GB-s olur. Gerçek süre, cihaz sayısı ve hesapta diğer Worker kullanımı ölçülmeden kapasite garantisi verilmez. Hazır servis henüz bağlı olmadığından pil ve gerçek kalıcı oturum trafiği ölçülmedi.
