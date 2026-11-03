# Adım 5 — uygulama ve doğrulama durumu

3 Kasım 2026 (Türkiye saati).

## Uygulanan davranış

- QR 68 ASCII karakter: `ZD1.` + base64url(room16 || S32). Oda kimliği B'nin alan ayrımlı 128 bit özetidir. Telefon capability'si HKDF ile türetilir; bootstrap alanları HMAC ile, B ayrıca oda özetiyle doğrulanır. Süre 30 saniyenin altındaysa eşleşme reddedilir.
- H düzeltme, dört modül sessiz bölge, en az 320 piksel, yeşil yuvarlak üzerinde beyaz Z. Üretici qrcode-generator, bağımsız okuyucu ZXing 3.5.3; dokuz kısa/uzun raster vektör çözülür.
- Telefonda ad/yetki kameradan önce seçilir. Kamera sürekli odak, dokunma odağı ve destekleniyorsa fener sunar.
- Web'de varsayılan işaretli kalıcı oturum seçeneği vardır. İlk telefon onayından sonra şifreli cihaz kayıt mesajı gönderilir; telefon Keystore kimliği ve owner'a bağlı metadata kaydı oluşturur, relay'e yalnız hash'leri kaydeder. Tarayıcı CryptoKeyPair ve cihaz kimliğini IndexedDB'ye koyar. İş verisi kalıcı depoya yazılmaz.
- Kayıtlı sayfa açılışında QR oluşturulmadan imzalı relay kimlik doğrulaması ve yeni ECDH el sıkışması yapılır. Telefon hazır değilse uygulamayı açma mesajı gösterilir. Her bağlantıda nonce/epoch/anahtarlar yenidir.
- `TezgahWebReadyService`: connectedDevice, START_NOT_STICKY, görünür uygulama açılışından başlama, bildirimden kapatma, hesap değişimi/çıkış/geri yükleme sinyalinde kapanma. Android arka plan başlangıcını veya OEM pil kısıtlarını aşma girişimi yoktur.
- Telefonda kayıtlı cihaz için Yalnız görüntüleme/Tam kullanım/Çıkış yap seçenekleri vardır. İzin değişimi yeni el sıkışma gerektirir; eski komut yürütücüsü anında geçersizleşir. Silme/Z/toplu ödeme/diğer kritik onaylar aynı komut motorundan geçer; kilitli telefonda onay verilmez, süre 60 saniyedir.
- Kayıtlı oturum 30 dakikada veriyi gizler ve tıklanınca yeniden ister. Geçici oturum beş dakikada anahtar/veriyi temizleyip QR'a döner. Otuz gün kullanılmayan cihaz düşer.
- Kayıtlı cihazların komut günlükleri birbirini silmez. Son 2048 sonucu tutan sınırlı günlük, tekrar eden id'yi yeniden yazmaz; yalnız bitmiş en eski sonuçlar çıkarılabilir.

## Doğrulama sınırları

Host testleri gerçek Java telefon protokolü, gerçek SQLite ve üretim JS sayfasını birlikte çalıştırır. Beş QR'sız açılış, her açılışta yeni epoch, 30 dakika gizleme, cihaz iptali, geçici oturum döngüleri, yanlış imza/anahtar/QR ve 30 gün sınırı sınanır. IndexedDB testi API taklidinde gerçek CryptoKey structuredClone kullanır; gerçek tarayıcı IndexedDB testi değildir. Relay testleri Node DO/WebSocket taklididir; canlı Cloudflare çalıştırması değildir.

Yeni servis/kamera/bağlantı sınıfları Android 15 `android-all` API JAR'ı ve gerçek OkHttp 4.12.1, Okio 3.6.0, Kotlin stdlib 1.8.21 ile Java 8 hedefinde derlenir. Bu kontrolde DatabaseHelper, SessionManager, Home ve bulut yedek facade'ları stub'dır. Bu araçlar uygulama bağımlılığına eklenmedi.

Tam APK derlemesi yapılamadı: Gradle indirme denemesi `UnknownHostException: services.gradle.org` ile durdu; Android SDK kurulu değil. Gerçek telefon/emülatör, kamera odak/fener, arka plan/Doze/pil ve canlı Pages–Worker–APK entegrasyonu doğrulanmadı. Kullanıcı Termux'ta assembleRelease ile derlemeli.

## Free plan etkisi — tahmin

Resmî kaynaklar:

- https://developers.cloudflare.com/durable-objects/platform/pricing/
- https://developers.cloudflare.com/durable-objects/api/state/#setwebsocketautoresponse

DO Free günlük 100.000 istek ve 13.000 GB-s; SQLite günlük 5 milyon okuma, 100.000 yazma, toplam 5 GB sunar. Gelen WebSocket mesajı 20:1 hesaplanır; auto-response ek çalışma süresi oluşturmaz.

Örnek tek cihaz: günde 10.000 iş/protokol mesajı yaklaşık 500 DO istek birimi. Telefon 24 saat ve web 8 saat, 30 saniyelik heartbeat ile 3840 mesaj daha: muhafazakâr 192 ek istek birimi. Bağlantı/HTTP/alarm çağrıları ayrıca eklenir. Olay başı 10 ms aktif süre varsayımıyla 10.000 olay 12,8 GB-s eder. Bunlar ölçüm değildir; diğer Worker kullanımı ve cihaz sayısı dahil edilmeden ücretsiz kota garantisi verilmez. Gerçek pil tüketimi ölçülmedi.

## Yayın kontrolü

SDK/AGP, Android bağımlılıkları, canlı URL'ler, wrangler vars, config.js ve _headers değişmedi. Yeni QR ve cihaz kayıt akışı için Worker, web ve APK güncel olmalıdır. GitHub push, Cloudflare dağıtımının başarılı olduğunu kanıtlamaz. Gerçek deneme adımları DEMO.md dosyasındadır.

## Son host test kaydı

Tam tarama: **112 giriş noktası; 110 geçti, 0 başarısız, 2 çalıştırılamadı**. Çalıştırılamayanlar: tests/league_names/run.sh ve tests/current_prices/run.sh; yerel PostgreSQL/ZD_PG yok.

- `tests/web_devices/run.sh`: 34 kripto/depolama kontrolü + 24 gerçek Java telefon–JS sayfa kontrolü; beş QR'sız açılış, yeni epoch, kilit ve iptal.
- `tests/web_relay/run.sh`: 104 Node mock kontrolü; cihaz iptali, eski kimliğin reddi ve otuz gün sınırı dahil.
- `tests/web_pairing/run.sh`: 60 eşleşme/döngü kontrolü ve 9 bağımsız ZXing logolu QR çözümlemesi.
- `tests/web_commands/run.sh`: 123 gerçek SQLite komut kontrolü + 58 ortak yerel kayıt yolu kontrolü + 50 şifreli telefon–sayfa komut/resume kontrolü.
- `tests/web_page/run.sh`: 89 sayfa/QR/DOM, 88 form/yetki/XSS, 24 gerçek Java telefon–sayfa kontrolü.
- `tests/web_phone/run.sh`: 184 SQLite/debounce, 20 protokol, toplam 39 statik wiring kontrolü. Statik kontroller Android servis çalışma testi değildir.
- `tests/web_crypto/run.sh`: Java 174 ve JS 174 kontrol.

Host komutlarında JSON_JAR JSON-java'ya; logolu QR testinde ZXING_JAR bağımsız ZXing core 3.5.3 JAR'ına ayarlandı. Testte kullanılan geçici Android API JAR'ı/host araçları Android Gradle bağımlılıklarına eklenmedi.
