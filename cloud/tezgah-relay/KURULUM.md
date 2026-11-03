# Tezgâh aktarıcısı — yalnız telefonla Cloudflare kurulumu

Bu Worker ayrıdır: **`cloud/zeytin-ai` dosyalarına ve yayınına dokunmayın.** Kaynak depo `muammercerdin-star/Zeytin-Defteri-Recovery`, dal **`work/web-baglanti`**, Worker kökü **`cloud/tezgah-relay`**. Bilgisayar/yerel Node gerekmez; telefondaki tarayıcıda Cloudflare ve GitHub kullanılır. Panel dar görünürse tarayıcı menüsünden “Masaüstü sitesi” açılabilir.

Kaynaklar 2 Kasım 2026 tarihinde kontrol edildi. GitHub bağlantılı Workers Builds Free planda var: **3.000 build dakikası/ay, aynı anda 1 build, 20 dakika/build**. SQLite-backed Durable Objects Free planda kullanılabilir. Bu, ücretsiz kotasız hizmet demek değildir. Ücretli plan/eklenti açmayın; kota dolarsa bekleyin. [Build kotası](https://developers.cloudflare.com/workers/ci-cd/builds/limits-and-pricing/), [DO fiyatlandırması](https://developers.cloudflare.com/durable-objects/platform/pricing/).

**Doğrulama sınırı:** Workers Builds'in `wrangler deploy` çalıştırması ve Wrangler'ın `new_sqlite_classes` migration'ını uygulaması resmî belgelerde doğrulandı. Bu nedenle aşağıdaki yol desteklenen parçaların birleşimidir. Bu hesaba giriş yapıp canlı dağıtım ve iki cihaz testi **yapılmadı / doğrulanmadı**. Paneldeki menü adları değişebilir.

## 1. Ücretsiz deneme adreslerini belirleyin

Önce [web kurulumunun](../../web/tezgah/KURULUM.md) 1. adımıyla Pages projesini oluşturup ücretsiz `https://PROJE.pages.dev` adresini alın. Yapılandırma boşken **“Kurulum gerekiyor”** görmelisiniz; henüz QR çıkmaması beklenir.

Ardından Cloudflare → **Workers & Pages** içinde Worker hesabınızın `workers.dev` alt alan adını bulun/oluşturun. Worker adı `tezgah-relay` olunca adres örneği `https://tezgah-relay.HESABINIZ.workers.dev` olur. Bunu tahminle kullanmayın: yayımdan sonra panelin gösterdiği gerçek adresle karşılaştırın.

GitHub mobil uygulamasında veya tarayıcıda ilgili dalda `cloud/tezgah-relay/wrangler.toml` dosyasını düzenleyin:

```toml
workers_dev = true

[vars]
RELAY_URL = "https://tezgah-relay.HESABINIZ.workers.dev"
ALLOWED_ORIGINS = '["https://PROJE.pages.dev"]'
```

Bunlar **örnektir**; kendi gerçek adreslerinizi yazın. Dosyadaki diğer `[vars]` değerlerini, binding ve migration bölümünü koruyun. `[vars]` bölümünü ikinci kez eklemeyin. `ALLOWED_ORIGINS` JSON dizidir; `*`, yol veya sonda `/` yok. Preview dağıtım adresi yerine sabit production `PROJE.pages.dev` adresini kullanın.

**Beklenen:** GitHub commit'i doğru dalda görünür. Her iki origin HTTPS'tir. Bu adresler gizli değildir; parola, özel anahtar, Firebase token'ı veya service-role anahtarı gerekmez.

**Bilinen mobil veri sorunu:** Bu projede Türk mobil operatöründen `*.workers.dev` bağlantısında zaman aşımı yaşandı. Önce Wi-Fi'de ücretsiz adreslerle deneyin. Mobil veride zaman aşımını şifreleme hatası sanmayın; üretimde mevcut Cloudflare alan adındaki `relay.zeytindefteri.com` kullanılmalıdır. AI için kullanılan HTTP Supabase proxy'si WebSocket relay yerine otomatik olarak kullanılamaz.

## 2. Worker'ı GitHub bağlantılı derlemeyle yayımlayın

1. Telefonda [Cloudflare paneli](https://dash.cloudflare.com/) → hesabınız → **Workers & Pages → Create application → Worker → Import repository / Connect GitHub** yolunu seçin. Panel önce Worker oluşturmanızı isterse oluşturduktan sonra **Settings → Builds → Connect** ile depoyu bağlayın. **Pages projesini burada seçmeyin.**
2. Cloudflare'nin resmî GitHub yetkilendirme ekranında yalnız gereken depoya erişim verin. Ekranın yönettiği kimlik doğrulamayı kullanın; token'ı dosyaya yapıştırmayın.
3. (3 Kasım 2026 canlı kurulumda görüldü) Yeni "Continue with GitHub" ekranında dal seçimi yoktur; ilk build `main` dalından alınır ve "root directory not found" ile düşer. Bu beklenir: Worker → **Settings → Builds → Production branch** = `work/web-baglanti` yapıp bu dala yeni bir commit gönderin. API token için mevcut bir token yerine **Create new token** seçin; eski token'da izin eksik uyarısı çıkabilir.
   Depo: `muammercerdin-star/Zeytin-Defteri-Recovery`; production branch: **`work/web-baglanti`**; Worker adı: **`tezgah-relay`**. Worker adı `wrangler.toml` içindeki `name` ile aynı olmalı.
4. **Root directory:** `cloud/tezgah-relay`.
5. **Build command:** boş. **Deploy command:** `npx --yes wrangler@4 deploy`. Sürüm yüklemesi Cloudflare'nin build ortamında yapılır. Telefona Node/Wrangler kurulmaz. İlk migration'da `versions upload` veya gradual deployment kullanmayın.
6. Preview/non-production branch dağıtımlarını kapatın. Build watch paths seçeneği varsa `cloud/tezgah-relay/**` ile sınırlandırın; Android veya web değişikliği gereksiz Worker build'i açmasın.
7. **Save / Deploy** seçin. **Beklenen:** build durumu Success; deploy çıktısında `TezgahRelay` için migration kabul edilir. İlk defa çalıştığı için migration çıktısı görünür; sonraki dağıtımlarda tekrar migration üretmeyin.
8. Worker → **Settings → Bindings**: `ROOMS` → `TezgahRelay` görünmeli. Durable Objects bölümünde namespace'in **SQLite** olduğunu kontrol edin. `new_classes` ile eski KV-backed DO oluşturmayın; ücretli plana geçerek çözmeye çalışmayın.
9. **Settings → Domains & Routes** içinde gerçek `workers.dev` adresini kopyalayın. Git dosyasındaki `RELAY_URL` aynı değilse GitHub'da düzeltip yeniden deploy edin.
10. Değişkenleri ve **Observability / Logs kapalı** durumunu kontrol edin. Kalıcı uygulama logları `[observability] enabled = false`. Gerçek QR/mesaj/token içeren tail log açmayın. Panelde elle yapılan vars değişiklikleri sonraki Git deploy'unda dosyadaki değerlere dönebilir; kalıcı değişikliği Git üzerinden yapın.

**Neden kod yapıştırma yok?** Durable Object export'u, binding ve migration birlikte dağıtılmalıdır. `worker.js` dosyasını Edit Code ekranına yapıştırmak ilk DO namespace kurulumunu tamamlamaz. Yeni `exports` DO deklarasyonu ile bu projenin legacy `[[migrations]]` tanımını birlikte kullanmayın.

Workers Builds bağlantısı hesap/panel nedeniyle kullanılamıyorsa ücretli plana geçmeyin. Alternatif olarak telefonunuzdaki Termux'ta depoyu alıp bu klasörde Node + Wrangler ile `npx wrangler@4 login` ve `npx wrangler@4 deploy` kullanılabilir; resmî CLI yolu aynı migration'ı uygular. Bu alternatif panelden yapılmaz; Termux/Node kurulumu ve telefondaki OAuth dönüşünün çalışması bu ortamda **doğrulanmadı**. Service-role veya private key gerektirmez. Öncelikli yol yukarıdaki GitHub bağlantılı build'dir.

## 3. Web ve Android'i aynı adrese bağlayın

[Web rehberindeki](../../web/tezgah/KURULUM.md) `config.js` ve `_headers` düzenlemelerini tamamlayın. Üç yapılandırmanın anlamı:

| Yer | Ücretsiz deneme örneği |
|---|---|
| Worker `RELAY_URL` | `https://tezgah-relay.HESABINIZ.workers.dev` |
| Worker `ALLOWED_ORIGINS` | `["https://PROJE.pages.dev"]` |
| Web `config.js`: `RELAY_URL` | Aynı HTTPS Worker origin'i |
| Web `_headers`: `connect-src` | Aynı hostname'in **HTTPS ve WSS** origin'leri |
| Android `local.properties`: `TEZGAH_RELAY_URL` | Aynı HTTPS Worker origin'i |

Android proje kökündeki git dışı `local.properties` dosyasına şunu ekleyin:

```properties
TEZGAH_RELAY_URL=https://tezgah-relay.HESABINIZ.workers.dev
```

Termux'ta repo kökünden `bash gradlew :app:assembleRelease` ile yeniden derleyip mevcut imza düzeniyle kurun. **Beklenen:** Tezgâh ana ekranında “Bilgisayara bağlan” satırı görünür. URL boş/geçersizse satır gizlidir. QR içinden relay hedefi alınmaz; hedef BuildConfig'teki adrestir. OkHttp **4.12.1 JVM JAR** kullanılır; compileSdk **36**, AGP **8.10.0**, minSdk **24** korunur. Bu ortamda Android SDK yok; gerçek APK derlemesi ve boyutu **ölçülmedi / doğrulanmadı**.

## 4. Özel alan adına geçin

Mevcut alan adının size ait olduğu ve DNS bölgesinin Cloudflare'de aktif olduğu varsayılır. Yeni alan adı satın almak ücretsiz değildir; bu görev domain satın almaz.

1. Worker → **Settings → Domains & Routes → Add → Custom Domain** → `relay.zeytindefteri.com`. Şema/yol yazmayın. Cloudflare'nin DNS/TLS kurulumunu tamamlayın. **Beklenen:** Domain Active, HTTPS sertifikası etkin.
2. Pages projesi → **Custom domains → Set up a custom domain** → `web.zeytindefteri.com`. Aynı hostname'in mevcut DNS kayıtlarını kontrol edin; AI/diğer servislerin kayıtlarını silmeyin. **Beklenen:** Domain Active.
3. GitHub'dan Worker vars'ı `RELAY_URL = "https://relay.zeytindefteri.com"`, `ALLOWED_ORIGINS = '["https://web.zeytindefteri.com"]'` yapın; `workers_dev = false` üretim varsayılanına dönsün. Binding/migration değişmez.
4. Web `config.js` adresini ve `_headers` içindeki HTTPS/WSS origin'lerini değiştirin. Pages/Worker Git dağıtımlarının başarıyla bittiğini bekleyin. Eski ücretsiz web origin'ini izin listesinden çıkarın; iki origin'in geçici birlikte kabulü gerekiyorsa açıkça ikisini JSON diziye yazın, `*` kullanmayın.
5. Android `TEZGAH_RELAY_URL=https://relay.zeytindefteri.com` yapıp tekrar derleyin. Açık eski oturumları kapatıp yeni QR ile eşleşin.
6. [Gerçek demo listesini](../../docs/web-baglanti/DEMO.md) Wi-Fi ve mobil veride uygulayın. Özel domain de mobil veride ayrıca doğrulanmalıdır; çalışan kabul edilmedi.

## 5. Sık hatalar ve beklenen ekranlar

| Belirti | Kontrol |
|---|---|
| Worker adresini açınca 404 | Normal: kökte HTML yok. `/v1/rooms` yalnız JSON POST kabul eder; adres çubuğu GET gönderir. QR web sayfasındadır. |
| 403 / `ORIGIN_403` / tarayıcıda genel ağ hatası | Web'in **tam** origin'i `ALLOWED_ORIGINS` içinde mi? `pages.dev` ile preview/custom domain farklıdır. CORS 403 yanıtını tarayıcı JS'inden saklayabilir; Network ekranında 403 görünür. |
| 503 `YAPILANDIRILMADI` | Worker vars JSON'u, HTTPS origin biçimi, `RELAY_URL` ile istek yapılan gerçek hostname aynı mı? Binding/migration başarılı mı? |
| Sayfada “Kurulum gerekiyor” | `config.js` boş; düzenleyip Pages deploy'unun bitmesini bekleyin. |
| CSP bağlantıyı engelliyor | `_headers` içindeki `relay.example.invalid` yer tutucularını gerçek HTTPS **ve** WSS origin'leriyle değiştirin. `connect-src` için wildcard açmayın. |
| QR değişti / telefon “süresi dolmuş” diyor | QR 90 saniye yaşar; otomatik yenisi gelir. Yeni QR'ı okutun; telefon/tarayıcı saatlerini kontrol edin. |
| Telefonda satır yok | `local.properties` satırı + **yeniden APK derleme** gerekir. HTTPS dışında adres kullanmayın. |
| Wi-Fi çalışıyor, mobil veri timeout | Bilinen `workers.dev` erişim sorunu; özel domain'e geçip tekrar deneyin. TLS doğrulamasını kapatmayın. |
| Migration veya ad hatası | Root directory ve Worker adı doğru mu? `new_sqlite_classes = ["TezgahRelay"]` ve export/binding aynı mı? İlk migration tag'ini silmeyin. |
| 429 | Uygulama oda bütçesi/dakika veya gün sınırı dolmuş olabilir. Yenilemeyi durdurup bekleyin. Free plan kotası ayrıca paylaşılır. |

## 6. Protokol ve test sınırları

- QR 90 sn, ilk socket auth 5 sn, oda başına 1 telefon + 1 browser. Geçerli aynı rol capability'siyle yeni soket eskisinin yerini alır.
- Aktarıcı token hash'i ve süre/sayaç metadata'sı tutar; içerik, ciphertext veya IP uygulama tarafından saklanmaz/loglanmaz. Cloudflare ağ metaverisini görebilir. Anahtarlar uçlarda RAM'dedir.
- Frame ≤64 KiB; varsayılan 20 frame/sn, 60 burst, 4 MiB/dk. Aşımda frame düşer/`hiz_siniri` bildirimi; aralıksız 10 sn aşımda kapanır. Telefon 10 frame/sn ve yaklaşık 1,9 MiB/dk tempoludur.
- Resume penceresi 5 dk; yeni epoch/noncelar ve iki yön HMAC ile yeni anahtarlar; eski epoch kabul edilmez. Active oda en çok 8 saat. Tarayıcı 5 dk kullanıcı hareketsizliğinde veriyi/anahtarları siler ve yeni QR üretir.
- `close` iki ucu kapatır. Ağ yokken close ulaşamayabilir: pending oda 90 sn sunucu TTL'siyle, active oda kopma süresiyle temizlenir. Tarayıcıda veri/anahtar temizleme ağdan bağımsızdır.
- `bash tests/web_relay/run.sh` Node mock testidir; gerçek edge hibernation veya canlı iki cihaz denemesi değildir. Yeni sayfa ve gerçek Java/SQLite host simülasyonu `bash tests/web_page/run.sh` içindedir.

## Resmî kaynaklar

- [Workers Builds / Git bağlantısı ve deploy komutu](https://developers.cloudflare.com/workers/ci-cd/builds/)
- [Build yapılandırması / root directory](https://developers.cloudflare.com/workers/ci-cd/builds/configuration/)
- [Workers Builds Free limitleri](https://developers.cloudflare.com/workers/ci-cd/builds/limits-and-pricing/)
- [DO migration: `new_sqlite_classes` ve `wrangler deploy`](https://developers.cloudflare.com/durable-objects/reference/durable-object-class-migrations-legacy/)
- [SQLite DO Free fiyatlandırması](https://developers.cloudflare.com/durable-objects/platform/pricing/)
- [Worker Custom Domains](https://developers.cloudflare.com/workers/configuration/routing/custom-domains/)
- [Pages Git bağlantısı](https://developers.cloudflare.com/pages/get-started/git-integration/)
- [Pages custom domains](https://developers.cloudflare.com/pages/configuration/custom-domains/)
