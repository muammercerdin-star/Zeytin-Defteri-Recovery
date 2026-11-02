# Adım 3b uygulama ve doğrulama raporu

## 0. OkHttp düzeltmesi

`com.squareup.okhttp3:okhttp:5.5.1` yerine **`com.squareup.okhttp3:okhttp:4.12.1`** kullanılır. Kullanıcının Termux derlemesindeki SDK 37 AAR metadata engelini kaldırmak için düz JVM JAR seçildi. **compileSdk 36, AGP 8.10.0, minSdk 24 ve Java 8 ayarları değişmedi.** Yeni bağımlılık/SDK uyumluluk kuralı kök `AGENTS.md` dosyasında kalıcıdır. Android davranışına müdahale edilmedi.

2 Kasım 2026 doğrulaması:

- [Resmî 4.x changelog](https://lysine.dev/okhttp/changelogs/changelog_4x/) 4.12.1 sürümünü listeler.
- [Yayımlanmış 4.12.1 POM](https://repo.maven.apache.org/maven2/com/squareup/okhttp3/okhttp/4.12.1/okhttp-4.12.1.pom) doğrudan **Okio 3.6.0** ve **kotlin-stdlib-jdk8 1.8.21** belirtir. POM ve JAR doğrudan Maven Central deposundan indirildi. Stdlib-JDK8, asıl stdlib'i de geçişli getirir. Projedeki diğer bağımlılıkların Gradle çözümlemesinde daha yeni bir Kotlin sürümü seçtirmesi mümkündür; tam dependency graph burada **doğrulanmadı**.
- [Etiketli resmî kaynak](https://github.com/lysine-dev/okhttp/tree/parent-4.12.1) klonlandı. README Android 21+ / Java 8+ desteğini belirtir. Kaynak build.gradle Kotlin 1.8.21 / Okio 3.6.0 / version 4.12.1'i doğrular.
- Yayımlanmış **JAR bytecode'u `javap` ile** incelendi; aşağıdaki imzaların tamamı bulundu. `okio.ByteString` yayımlanmış okio-jvm 3.6.0 JAR'ındadır.
- Gerçek `TezgahWebClient.java`, telefon çekirdeğiyle beraber **`javac --release 8`** ve yayımlanmış OkHttp/Okio/Kotlin JAR'ları karşısında başarıyla derlendi. BuildConfig ve Android SQLite için mevcut host test stub'ları kullanıldı. Bu, tam Android APK/D8 derlemesi değildir.
- İndirilen üç JAR'da AAR metadata dosyası yok. OkHttp JAR'ı 791.976 bayt; bu **APK artışı değildir**. APK önce/sonra etkisi **ölçülmedi**.

| API | Yayımlanmış 4.12.1 Java imzası |
|---|---|
| connectTimeout | `Builder connectTimeout(long, TimeUnit)` |
| readTimeout | `Builder readTimeout(long, TimeUnit)` |
| pingInterval | `Builder pingInterval(long, TimeUnit)` |
| newWebSocket | `WebSocket newWebSocket(Request, WebSocketListener)` |
| onOpen | `void onOpen(WebSocket, Response)` |
| onMessage (text) | `void onMessage(WebSocket, String)` |
| onMessage (binary) | `void onMessage(WebSocket, okio.ByteString)` |
| onClosing / onClosed | `void onClosing(WebSocket, int, String)` / `void onClosed(WebSocket, int, String)` |
| onFailure | `void onFailure(WebSocket, Throwable, Response)`; Response nullable |

İndirilen OkHttp JAR SHA-256: `86706f20171c58167b4acca46a66a4039d6b7927543b5ed944b7311c3cb52de0`.

Gradle denemesi: `bash gradlew :app:compileReleaseJavaWithJavac`. Wrapper indirmesi `java.net.UnknownHostException: services.gradle.org` ile durdu. Ayrıca ortamda Android SDK yok. **Android derlenmedi; Termux'ta release derlemesi yeniden gerekli.** Yeni Android API çağrısı eklenmedi. 3a raporundaki 5.5.1 seçimi tarihsel kayıttır; güncel seçim bu rapordaki 4.12.1'dir.

## 1. Yapılanlar

- Yerel QR üretimi, 90 sn otomatik yenileme/eski odayı kapatma, P-256/HKDF/AES-GCM el sıkışması ve telefonun şifreli onayıyla açılan bilgisayar sayfası.
- Açık dönem ve takvim günü ayrımı; Türkçe para/kg gösterimi; toplamlar, salt okunur tablo ve cursor sayfalama. Snapshot süresi dolarsa yeniden alınır; tekrarlı süre aşımında ilk sayfaya dönülür.
- `veri_degisti` için 400 ms birleştirme ve yeni snapshot; mümkünse görünür sayfa korunur. Yanıt timeout'unda en fazla iki tekrar. Kopmada son veri soluk; socket için jitter'lı geri çekilme; 5 dk pencerede yeni epoch/HMAC resume; yeniden telefon onayı mesajından sonra veri isteği.
- 5 dk kullanıcı hareketsizliğinde tablo/anahtarlar temizlenir ve yeni QR. Ağ trafiği idle süresini uzatmaz; sekme görünür olduğunda süre ayrıca denetlenir. Sayfa yenilemede önceki oturum kalıcı olarak geri yüklenmez.
- Tüm dinamik DOM metinleri textContent; browser kalıcı depolama/çerez/service worker yok. Config varsayılanı boş; relay hedefi yalnız config'ten. CSP'de yalnız kurulumda yazılacak relay HTTPS/WSS origin'leri.
- QR kodlayıcı **qrcode-generator 2.0.4**, MIT, yerel ES module; kaynak commit'i ve lisans vendor klasöründe. CDN yok. Bağımsız Nayuki matris vektörleriyle doğrulandı.
- Telefona uygun Workers Builds / Pages kurulum rehberleri ve 15 maddelik gerçek demo kabul listesi.

## 2. Dosyalar

Mevcut dosyalardaki değişiklikler (`git diff --numstat`, bu rapor yazılırken):

| Dosya | Eklenen / silinen satır | Değişiklik |
|---|---:|---|
| app/build.gradle | +1 / −1 | Yalnız OkHttp sürümü |
| app/src/main/java/com/zeytindefteri/app/TezgahHomeActivity.java | +1 / −1 | Yalnız Javadoc'u askQuestion üzerine taşıma |
| cloud/tezgah-relay/KURULUM.md | +84 / −70 | GitHub build, ücretsiz adresler, özel domain, hata ve doğrulama sınırları |
| docs/web-baglanti/TASARIM.md | +7 / −3 | Güncel bağımlılık kararı ve 3b durumu |

Yeni dosyalar:

- `AGENTS.md`: kalıcı SDK/AGP/bağımlılık ve kod stili kuralları.
- `web/tezgah/index.html`, `app.js`, `style.css`, `config.js`, `_headers`, `KURULUM.md`.
- `web/tezgah/js/vendor/qrcode.js`, `LICENSE`, `README.md`.
- `tests/web_page/run.sh`, `test.mjs`, `flow.mjs`, `qr-vectors.json`.
- `docs/web-baglanti/DEMO.md`, `ADIM3B-RAPOR.md`.

`cloud/zeytin-ai`, relay worker.js/wrangler.toml, manifest ve diğer Android sınıfları değiştirilmedi. Adım 2 kripto/protokol modülleri de değişmedi.

## 3. Test sonuçları

| Yeni test | Sonuç |
|---|---:|
| Sayfa durum makinesi, DOM/XSS, format, idle, QR yenileme, invalidation/sayfalama, CSP, bağımsız QR vektörleri, bağımlılık/Javadoc kontrolleri | **82/82** |
| Gerçek Java telefon ↔ üretimdeki JS sayfa mantığı; gerçek SQLite, onay, veri, invalidation, resume, eski epoch/QR ve yanlış kanıt | **24/24** |
| Toplam yeni assertion | **106/106** |

`bash tests/web_page/run.sh` geçici type=module dizinine üretim dosyalarını değiştirmeden kopyalar; Node 20+ ile ES module davranışı korunur. Java köprüsü mevcut `PhoneBridge` ve SQLite test köprüsüdür. Ağ, relay ve DOM'un testte taklit edilen bölümleri canlı cihaz testi sayılmaz. QR fixture'ı bağımsız başka bir kodlayıcıdan elde edilmiştir; yalnız kendi çıktımızla karşılaştırma yapılmaz.

| Mevcut testler | Sonuç |
|---|---:|
| tests altındaki eski run.sh / run.py / run_*.py / check*.py / tests/*.py giriş noktaları | **103 başarılı** |
| web_crypto Java / JS | **174/174 ve 174/174**; ilk Java vektör üretimi ayrıca 173/173 |
| web_relay Node mock | **75/75** |
| web_phone SQLite / karşılıklı protokol / wiring | **184/184 + 20/20 + 20/20** |

Mevcut testlerde **106 giriş noktası başarılı**: 103 eski + 3 web suite'i. Yeni web_page ile toplam **107 başarılı giriş noktası**; assertion sayılarıyla script sayıları birbirine karıştırılmamalı.

Ayrı çalıştırılamayanlar:

- `tests/league_names/run.sh`: **çalıştırılmadı (yerel PostgreSQL / ZD_PG yok; kullanıcı kapsamı gereği)**.
- `tests/current_prices/run.sh`: **çalıştırılmadı (yerel PostgreSQL / ZD_PG yok; kullanıcı kapsamı gereği)**.
- Android Gradle/APK derlemesi: **derlenmedi**; wrapper DNS hatası ve SDK yok. Buna karşılık gerçek istemcinin yayımlanmış 4.12.1 JAR'ına karşı Java 8 host derlemesi başarılıdır.
- Playwright görsel tarayıcı kontrolü: **çalıştırılamadı**. Chromium binary yok; indirme geçersiz/eksik ZIP hatasıyla bitti. Node durum/DOM testleri bunun yerine gerçek tarayıcıda çalışmış sayılmadı.
- Cloudflare canlı deploy, edge hibernation, kamera QR taraması, ekran kapalı Android, Wi-Fi/mobil veri ve 2 sn güncelleme ölçümü: **yapılmadı / doğrulanmadı**.

## 4. Kurulum yolunun resmî dayanakları

- [Workers Builds yapılandırması](https://developers.cloudflare.com/workers/ci-cd/builds/configuration/): build komutu opsiyonel; deploy varsayılanı `npx wrangler deploy`; root directory ayarlanabilir.
- [Workers Builds Free kotası](https://developers.cloudflare.com/workers/ci-cd/builds/limits-and-pricing/): ayda 3.000 dakika, 1 eşzamanlı build, 20 dk timeout.
- [DO migration](https://developers.cloudflare.com/durable-objects/reference/durable-object-class-migrations-legacy/): `new_sqlite_classes`, binding/export ve `wrangler deploy` ile uygulanır. Yeni exports DO deklarasyonu ile migrations birlikte kullanılmaz.
- [DO fiyatlandırması](https://developers.cloudflare.com/durable-objects/platform/pricing/): SQLite backend Free planda var; KV-backed yeni namespace bu planın çözümü değildir.
- [Pages Git rehberi](https://developers.cloudflare.com/pages/get-started/git-integration/): GitHub, dal, boş build komutu ve root directory.
- [Pages statik HTML](https://developers.cloudflare.com/pages/framework-guides/deploy-anything/), [headers](https://developers.cloudflare.com/pages/configuration/headers/), [custom domains](https://developers.cloudflare.com/pages/configuration/custom-domains/), [Worker custom domains](https://developers.cloudflare.com/workers/configuration/routing/custom-domains/).

Workers Builds + DO migration yolunun uygunluğu bu resmî parçaların birlikte değerlendirilmesidir; bu kullanıcının hesabında canlı başarı iddiası değildir. Panelde Git build akışı çalışmazsa ücretsiz Termux Wrangler deploy alternatifi rehberde açıklanır; telefondaki OAuth dönüşü ayrıca **doğrulanmadı**.

## 5. Sapmalar

1. **Alım saati yok:** İstenen saat/tarih sütununda yalnız gerçek tarih gösterilir. Telefon DTO'sunda saat alanı yok; Android davranışı değişmesin kuralı gereği saat uydurulmadı/DTO değiştirilmedi. İleride ayrıca onayla genişletilebilir.
2. **Canlı demo yapılmadı:** Dağıtım hesabı/iki cihaz testi bu ortamda yok. Testler host simülasyonudur; gerçek kabul için DEMO.md doldurulmalı.
3. **CSP adresi ayrıca düzenlenir:** Statik `_headers`, config.js değişkenini okuyamaz. Kurulumda iki dosya aynı origin'le düzenlenir; varsayılan config boş ve CSP yer tutuculu olarak kapalıdır.
4. **Ağ yokken eski oda hemen kapatılamayabilir:** Tarayıcı close kontrolünü gönderip socket'i kapatır; ulaşılamayan pending oda 90 sn TTL ile silinir. Yerel anahtar/veri temizleme her durumda yapılır.

## 6. Açık sorular

- Gerçek Cloudflare hesap/proje adları, Workers alt alan adı ve domain DNS erişimi henüz doğrulanmadı. Kurulum değerleri kullanıcı tarafından girilmeli.
- Kullanıcının Termux release derlemesinde dependency resolution/D8 ve APK boyutu doğrulanmalı. SDK/AGP yükseltmesi yapılmayacak.
- Tarayıcı/kamera okunabilirliği, iki saniye hedefi, mobil operatör erişimi ve ekran kapalı servis davranışı gerçek demo listesinden doğrulanmalı.
- Sayfalarda alım saatinin gerekip gerekmediği sonraki bir Android DTO değişikliği olarak ayrıca kararlaştırılabilir. Bu sürüm mevcut tarih verisini gösterir.
