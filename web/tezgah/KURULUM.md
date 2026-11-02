# Bilgisayar sayfası — telefondan Pages kurulumu

Bu klasör bağımlılıksız statik sitedir. NPM yüklemesi, derleme, CDN veya dış font yoktur. Kurulumu yalnız telefonunuzdaki Cloudflare/GitHub paneliyle yapabilirsiniz. Kullanım demosunda ise sayfayı gösterecek ayrı bir bilgisayar/ikinci ekran gerekir; aynı telefon kendi ekranındaki QR'ı kamerayla okuyamaz.

## 1. Pages projesini açın; ücretsiz adresi alın

1. Telefonda [Cloudflare paneli](https://dash.cloudflare.com/) → **Workers & Pages → Create application → Pages → Connect to Git**. Yeni arayüzde önce “Get started / Import existing repository” görünebilir; **Pages** ürününü seçin.
2. GitHub'daki `muammercerdin-star/Zeytin-Defteri-Recovery` deposuna izin verin. API anahtarı/parola koda yazılmaz.
3. Proje adı seçin; örneğin `zeytin-tezgah`. Production branch **`work/web-baglanti`** olsun.
4. Framework preset: **None**. **Root directory: `web/tezgah`**. **Build command: boş**. **Build output directory: `.`**. `app` Android klasörünü veya repo kökünü output seçmeyin; yalnız bu statik klasör yayımlanmalı. Panel boş build komutunu kabul etmezse resmî Static HTML rehberindeki `exit 0` kullanılabilir; siteye bir derleme eklemez.
5. Deploy'u başlatın. **Beklenen:** Success; `https://PROJE.pages.dev` adresi oluşur. Açınca koyu yeşil Zeytin Defteri sayfası ve **“Kurulum gerekiyor”** görünür. Henüz QR çıkmaz; varsayılan relay ayarı bilerek boştur.
6. Preview branch otomatik dağıtımlarını kapatın veya yalnız bu dalı kullanın. Kaynak izleme yolu varsa `web/tezgah/**` ile sınırlandırın. Production sabit `PROJE.pages.dev` adresini kopyalayın; hash'li preview adresini kullanmayın.

[Pages Git rehberi](https://developers.cloudflare.com/pages/get-started/git-integration/) boş build komutuna izin verir. [Static HTML rehberi](https://developers.cloudflare.com/pages/framework-guides/deploy-anything/) alternatif `exit 0` örneğini verir. [Build yapılandırması](https://developers.cloudflare.com/pages/configuration/build-configuration/) root ve output dizinlerini açıklar. Free Pages [limitleri](https://developers.cloudflare.com/pages/platform/limits/) ayrıca geçerlidir; ücretli plan seçmeyin.

## 2. Ayrı Worker'ı kurun

[Aktarıcı rehberini](../../cloud/tezgah-relay/KURULUM.md) uygulayın. Worker Git bağlantısı, kök `cloud/tezgah-relay`, migration ve `wrangler deploy` ile kurulur. Pages içine Worker kodu yapıştırılmaz.

İlk denemede Worker `workers_dev = true`; `RELAY_URL=https://tezgah-relay.HESABINIZ.workers.dev`; `ALLOWED_ORIGINS='["https://PROJE.pages.dev"]'`. Worker deploy'u başarılı ve DO binding'i SQLite olmalı.

## 3. Web adresini ve güvenlik başlığını birlikte ayarlayın

GitHub mobil uygulamasında/tarayıcıda **aynı dalda** iki dosyayı düzenleyin:

`web/tezgah/config.js`:

```js
export const RELAY_URL = 'https://tezgah-relay.HESABINIZ.workers.dev';
```

Yalnız bu ortam adresi dışarı aktarılır; URL'ye yol, sonda slash, token veya hesap bilgisi eklemeyin. Sayfa sunucunun döndürdüğü farklı bir relay adresini hedef olarak kabul etmez.

`web/tezgah/_headers` içindeki **yalnız `connect-src` bölümünü** değiştirin:

```text
connect-src https://tezgah-relay.HESABINIZ.workers.dev wss://tezgah-relay.HESABINIZ.workers.dev;
```

Diğer CSP bölümleri, `frame-ancestors 'none'`, `Referrer-Policy: no-referrer`, `Cache-Control: no-store` ve diğer güvenlik başlıkları kalsın. `relay.example.invalid` bilinçli yer tutucudur; gerçek adresle değişmeden ağ erişimi CSP tarafından engellenir. `*` veya tüm `https:`/`wss:` origin'lerine izin vermeyin. Statik Pages ortam değişkenleri JS dosyasının içine kendiliğinden yazılmaz; **config ve header kaynak dosyalarını düzenlemek gerekir**.

Commit'ten sonra Pages'in yeni Git deploy'unun **Success** olmasını bekleyin ve sayfayı yenileyin. **Beklenen:** “Bağlanıyor” ardından QR ve kalan saniye. QR 90 sn sonunda otomatik değişir. Eski socket'e `close` gönderilir; ağ kesikse eski pending odanın sunucu TTL'si dolması beklenir.

Cloudflare Pages `_headers` dosyasını statik yanıt başlıklarına uygular; buradaki site Pages Functions kullanmaz. [Resmî `_headers` biçimi](https://developers.cloudflare.com/pages/configuration/headers/).

## 4. Telefon uygulamasını bağlayın

Repo kökünde git dışı `local.properties` dosyasına `TEZGAH_RELAY_URL=https://tezgah-relay.HESABINIZ.workers.dev` ekleyin. Termux'ta:

```sh
bash gradlew :app:assembleRelease
```

Yeni APK'yı mevcut imza ayarlarıyla kurun. Tezgâh → **Bilgisayara bağlan** → bilgisayardaki QR'ı okutun → ad verin → telefonda **Evet**.

**Beklenen:** Sayfa “Telefonda onay bekleniyor” ardından “Bağlı” olur; Açık dönem alımları görünür. Relay'in `consumed` cevabı tek başına yetmez; telefonun şifreli `onaylandi` mesajı beklenir. Android release derlemesi bu çalışma ortamında SDK olmadığı için **yapılmadı**; Termux sonucu ayrıca kontrol edilmeli.

Başlıktaki **Açık dönem**, Z ile kapanmamış alımlardır; “bugün” ile aynı değildir. Takvim gününden tarih seçerseniz kapalı dönemdeki o günün alımları da takvim filtresiyle gelebilir. **Açık dönem** düğmesi varsayılan kapsama döner. Özet seçilen kapsamın tüm toplamıdır; tablo sayfalıdır. Sonraki/Önceki düğmeleri telefon cursor'larıyla ilerler. Değişiklikte yeni snapshot alınır ve mümkünse bulunduğunuz sayfaya geri ilerlenir; kayıt sayısı azaldıysa son mevcut sayfa gösterilir.

Mevcut telefon DTO'sunda alım **saati yoktur**; tabloda tarih gösterilir. “Son güncelleme” alım saati değildir; telefon snapshot zamanıdır. Bu adımda Android veri davranışı değiştirilmedi.

## 5. Özel alan adına geçin

Aktarıcı rehberindeki Custom Domain adımlarıyla `relay.zeytindefteri.com` ve Pages Custom domains ile `web.zeytindefteri.com` bağlayın. Domain zaten size ait olmalı; domain satın alma bedeli ücretsiz hizmet kapsamına girmez.

Ardından aynı değişiklik kümesinde:

- Worker `RELAY_URL`: `https://relay.zeytindefteri.com`.
- Worker `ALLOWED_ORIGINS`: `["https://web.zeytindefteri.com"]`; üretimde `workers_dev = false`.
- `config.js`: aynı HTTPS relay origin'i.
- `_headers`: `connect-src https://relay.zeytindefteri.com wss://relay.zeytindefteri.com;`.
- Android local.properties: aynı HTTPS origin; yeniden APK derleme.

TLS/domain durumları Active olunca eski oturumları kapatıp yeni QR okutun. Türk mobil operatöründe `workers.dev` timeout sorunu bu projede gözlendiği için üretimde özel domain varsayılıyor. Wi-Fi ve mobil veri kontrollerini ayrı yapın; özel domain'in çalıştığı bu ortamda **doğrulanmadı**.

## 6. Sorun giderme ve güvenlik

| Ekran / hata | Yapılacak kontrol |
|---|---|
| Kurulum gerekiyor | Config boş veya son deploy henüz gelmedi. |
| ORIGIN_403 / genel ağ hatası | Worker izin listesinde açık sayfanın tam production origin'i var mı? CORS hata ayrıntısını gizleyebilir. |
| YAPILANDIRILMADI_503 | Worker RELAY_URL, vars JSON'u ve erişilen hostname eşit mi; DO migration başarılı mı? |
| CSP hatası / hiç QR gelmiyor | `_headers` HTTPS/WSS ikilisini gerçek relay host'una ayarlayın; config ile aynı olsun. |
| QR geçersiz / süre dolmuş | Otomatik yenilenen yeni QR'ı okutun; telefon/tarayıcı saati doğru olsun. |
| Telefonda onay bekleniyor | Telefon ekranındaki Evet düğmesine basın. QR'ı okutmak veri izni değildir. |
| Telefon bağlantısı koptu | Son veri soluk görünür. Telefonun ağını/bildirimini kontrol edin; 5 dk içinde karşılıklı resume denenir. Başarısızsa yeni QR gerekir. |
| Oturum kapandı | Telefon çıkışı/uzaktan kesme, güvenlik hatası, 5 dk hareketsizlik veya 8 saat sınırı; yeni QR ile yeniden onay gerekir. |
| Boş alım listesi | Bir hata değildir. Açık dönem/takvim filtresini ve telefondaki hesap/dönemi kontrol edin. |

Anahtarlar, üretici bilgileri ve alımlar localStorage, sessionStorage, IndexedDB, çerez veya service worker'a yazılmaz. Sekmeyi yenilemek yeni QR üretir. Beş dakika **kullanıcı işlemi** yoksa son tablo/anahtarlar temizlenir; ağ mesajları/ping süreyi uzatmaz. Arka planda tarayıcı timer'ları gecikebilir; sekme görünür olduğunda süre tekrar kontrol edilir. Uyku sırasında monoton saat durabilen sistemler için duvar saati de denetlenir; saat geriye alınırsa monoton saat koruması sürer. İşletim sistemi belleğindeki kopyaların fiziksel sıfırlanması JavaScript'te garanti edilemez; uygulama referansları kaldırılır ve erişilebilen byte dizileri sıfırlanır.

Ortak bilgisayarda işiniz bitince sağ üstten **Bağlantıyı kes** seçin ve sekmeyi kapatın. QR fotoğrafını paylaşmayın. Sağ üst düğme odayı kapatıp veriyi temizler ve yeni, henüz onaysız QR hazırlar.

## 7. Doğrulama

Repo kökünde `JSON_JAR=... bash tests/web_page/run.sh` yeni Node testlerini ve gerçek Java telefon çekirdeği ↔ bu JS sayfası host simülasyonunu çalıştırır. Java 17 host, Node 20+ WebCrypto ve mevcut test SQLite köprüsü gerekir. JSON_JAR varsayılanı `/usr/share/java/json.jar`; uygulamanın yeni bağımlılığı değildir.

Host simülasyonu canlı Cloudflare veya gerçek kamera/Wi-Fi/mobil veri testi değildir. [15 adımlık gerçek demo listesi](../../docs/web-baglanti/DEMO.md) dağıtım sonrası uygulanmalıdır. Bu çalışma sırasında canlı yayın, gerçek QR kamera taraması, ekran kapalı Android servis testi ve iki saniye gecikme ölçümü **yapılmadı / doğrulanmadı**.
