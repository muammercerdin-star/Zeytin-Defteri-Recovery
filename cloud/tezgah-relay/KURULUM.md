# Tezgâh Relay — telefondan kurulum

Bu klasör ayrı bir Worker'dır. **`cloud/zeytin-ai` üzerinde değişiklik yapmayın.** Adım 3a'da Android telefon tarafı eklendi; çalışan web sayfası 3b'dedir. Android derlemesi SDK olmadığı için yapılmadı. Dosyalar hazırlanıp host testlerinden geçirilmiştir; canlı Cloudflare dağıtımı **doğrulanmadı**.

## Önce bilinmesi gerekenler

- Cloudflare **Workers Free** kullanılacak. Paid/Pro/ücretli eklenti açmayın. DO namespace **SQLite** olmalı; `new_classes` değil `new_sqlite_classes` migration'ı var. Yeni dokümanlarda görülen `exports` deklarasyonunu bununla birlikte eklemeyin.
- **Üretimde özel alan adı şartı:** Bu projede `*.workers.dev` adresinin Türk mobil operatör bağlantısında zaman aşımına uğradığı bildirildi; bu yüzden AI servisinde Supabase proxy kullanılmıştı. Relay için bu proxy'yi otomatik çözüm saymayın: normal HTTP proxy WebSocket tüneli değildir. Üretim adresini örneğin `https://relay.zeytindefteri.com` / `wss://relay.zeytindefteri.com` olarak kurun. `workers.dev` yalnız geliştirme içindir; telefonda mobil veriyle çalışacağı varsayılmaz.
- Özel alan adının zaten size ait ve DNS bölgesinin Cloudflare'de aktif olduğu varsayılıyor; sahiplik/DNS erişimi **doğrulanmadı**. Yeni domain satın almak ücretsiz değildir. Mevcut alan adı yoksa bu varsayım ayrıca çözülmeli.
- Hiçbir service-role, private key, Firebase belgesi veya API sırrı gerekli değil. GitHub bağlantısı Cloudflare'nin kendi yetkilendirme ekranıyla yapılır; token'ı dosyaya yapıştırmayın.
- Anahtarlar yalnız tarayıcı ve telefonda üretilir. Relay yalnız rastgele oda ve token hash'i/süre/sayaç metadata'sı tutar; içerik ve ciphertext saklamaz. IP başlığı okunmaz. Platformun ağ bağlantı metaverisini görebilmesi engellenmiş sayılmaz.

## 1. Telefonda kaynak ayarlarını kontrol et

GitHub uygulamasından veya tarayıcıdan `muammercerdin-star/Zeytin-Defteri-Recovery` → `work/web-baglanti` → `cloud/tezgah-relay/wrangler.toml` dosyasını aç.

| Ayar | Anlamı |
|---|---|
| `name = "tezgah-relay"` | Cloudflare panelindeki Worker adıyla aynı olmalı. |
| `RELAY_URL` | Sonunda yol olmayan HTTPS özel alan adı; örneğin `https://relay.zeytindefteri.com`. Bu bilgi gizli değildir. |
| `ALLOWED_ORIGINS` | JSON dizi olarak izin verilen **tam HTTPS web origin'leri**. Örnek: `["https://web.zeytindefteri.com"]`; `*`, path, sondaki `/` veya http yok. |
| `workers_dev = false` | Üretim için açık bırakma; yalnız geliştirmede ayrı Worker/config ile true yapılabilir. |
| `ROOMS / TezgahRelay` | DO binding/class adı; değiştirmeyin. |
| `new_sqlite_classes` | İlk dağıtımda SQLite namespace'i kurar. Aynı migration tag'ini değiştirmeyin/silmeyin. |
| `CREATE_PER_MINUTE = "10"` | Worker genelinde dakikada oda oluşturma bütçesi. IP kullanılmaz. |
| `FRAME_RATE / FRAME_BURST / BYTES_PER_MINUTE` | Varsayılan 20 frame/sn, 60 frame patlama, 4 MiB/dk. Sınırda frame düşer ve en çok saniyede bir şifresiz `hiz_siniri` gider; aralıksız aşım serisi 10 sn sürerse kapanır. |
| `CREATE_PER_DAY = "1000"` | Worker genelinde günde oda oluşturma bütçesi; UTC gününde yenilenir. Bu uygulama bütçesi, Cloudflare hesabının kalan kotası değildir. |
| `[observability] enabled = false` | Worker kalıcı uygulama logları kapalı. |

Adreslerin gerçek karşılıkları farklıysa yalnız bu ortam ayarlarını düzeltip commit et. Worker koduna adres yazma. `[vars]` Git tabanlı yayının kaynağıdır; panelde tek başına değiştirilen değerler sonraki Git dağıtımında eski değere dönebilir.

## 2. Cloudflare panelinden GitHub'a bağla ve yayımla

[Cloudflare panelini aç](https://dash.cloudflare.com/) → hesabını seç → **Workers & Pages**.

1. **Create application / Create Worker** yolundan **GitHub repository import/connect** seçeneğini aç. Arayüzde isimler farklı görünürse Worker içinde **Settings → Builds → Connect** yolu kullanılabilir. Git tabanlı Worker oluşturmayı seç; Pages statik site projesi seçme.
2. GitHub hesabını Cloudflare'nin resmî ekranında yetkilendir. Yalnız gerekli Recovery deposuna erişim ver; token veya parola dosyaya yazma.
3. Depo: `muammercerdin-star/Zeytin-Defteri-Recovery`; production branch: **`work/web-baglanti`**. `main` veya AI projesinin ayarlarını değiştirme.
4. Worker adı: **`tezgah-relay`**.
5. Gelişmiş build ayarlarında **Root directory**: `cloud/tezgah-relay`.
6. **Build command** boş; **Deploy command**: `npx --yes wrangler@4 deploy`. Bu, Cloudflare'nin build ortamında Wrangler çalıştırır; telefona Node/Docker kurman gerekmez. Migration/binding'in Wrangler tarafından uygulanması gerekir; sadece Edit Code ekranına worker.js yapıştırmak ilk DO kurulumunu tamamlamaz.
7. Non-production/preview branch otomatik dağıtımlarını kapat; bu Worker için yalnız belirtilen dalı yayımla. Build watch paths destekleniyorsa `cloud/tezgah-relay/**` ile sınırla. Böylece Android veya AI değişikliği gereksiz build tetiklemez.
8. **Deploy** seç. Logda `new_sqlite_classes` migration'ının ve `TezgahRelay` export'unun kabul edildiğini kontrol et. Namespace'in SQLite olması önemli; ücretli plana geçerek hata çözmeye çalışma.
9. Worker → **Settings / Bindings** bölümünde `ROOMS` → `TezgahRelay` görünmeli. Workers & Pages → Durable Objects bölümünde namespace ve storage türünü kontrol et.
10. Değişkenler bölümünde `RELAY_URL`, `ALLOWED_ORIGINS` ve bütçeleri; Observability bölümünde kalıcı logların kapalı olduğunu doğrula. Log tail açıp gerçek QR/token/mesaj dökümü toplama.

**Sınır:** Bu panel akışı resmî Git integration ve DO migration belgelerine dayanıyor. Bu hesaptaki panel, Free build kotası ve dağıtım sonucu **doğrulanmadı**. Ücretsiz build kotası biterse yayını beklet; ücretli plana yükseltme yapma. Hata ekranı çıkarsa gizli bilgi içermeyen hata kodu ile teşhis yapılmalı.

## 3. Özel alan adı bağla

Worker → **Settings → Domains & Routes → Add → Custom Domain** yolunu aç.

1. Alan adını yaz: örneğin **`relay.zeytindefteri.com`**. URL şeması veya `/v1/socket` yolu yazma.
2. Domain'in DNS bölgesinin aynı Cloudflare hesabında aktif olduğunu doğrula. Custom Domain kurulumunun oluşturduğu DNS/kayıt/TLS adımlarını panelden tamamla; aynı hostname için çakışan eski A/CNAME kayıtlarını kontrol et. Diğer uygulama/AI adreslerini silme.
3. Sertifika etkinleşene kadar bekle. HTTPS çalışmadan `wss://` kullanılamaz. Sertifika kontrolünü istemcide kapatma.
4. `wrangler.toml` içindeki `RELAY_URL` ile paneldeki alan adının birebir aynı olduğuna bak. Kaynak dal üzerinden yeni bir dağıtım tetikle.
5. Web istemcisinin adresi relay adresinden farklıdır: `web...` HTML/JS'yi sunacak, `relay...` yalnız WSS/HTTP aktarıcıdır. Web sayfası adım 3b'de hazırlanacak; bu Worker'ı açınca QR ekranı görmemen normal.

**Tarayıcıdan `/v1/rooms` adresini açmak GET gönderir ve 404 verir; kurulum bozuk demek değildir.** Endpoint yalnız izinli origin'den JSON POST kabul eder. `/v1/socket?room=<oda>` WebSocket upgrade ister. Canlı doğrulama adım 3'ün iki cihazlı demosunda yapılacak.

## 4. Ortam adresleri ve ileride Android

Worker yalnız `[vars].RELAY_URL` ve `[vars].ALLOWED_ORIGINS` kullanır; URL token taşımaz. Oda kimliği URL'de olabilir, **capability ilk socket frame'inde** gider. URL'ye S, hesap UID'si veya Firebase token'ı koyma.

Android için proje kökündeki git dışı `local.properties` dosyasında **`TEZGAH_RELAY_URL=https://relay...`** ayarı okunacak. Mevcut `ZEYTIN_AI_URL` / `zdBuildString` deseniyle BuildConfig alanı üretilecek. BuildConfig alanı Gradle'a eklendi; boş/geçersiz HTTPS adresinde özellik gizlidir. Yalnız OkHttp 5.5.1 doğrudan bağımlılığı eklendi. QR'dan gelen serbest bir relay URL'sine bağlanılmayacak; optik QR yalnız odanın/kriptonun girdisi, gerçek hedef dağıtım ayarıdır.

## 5. Endpoint / süre özeti

| İşlem | Kural |
|---|---|
| `POST /v1/rooms` | İzinli Origin, JSON `{}`; rastgele room/cb/cp ve expiresAt döner. S/B/nb relaya gönderilmez. |
| `/v1/socket?room=...` | WSS; ilk frame `{v:1,type:"auth",room,role,capability}`; 5 sn içinde auth yoksa kapanır. |
| Pending oda | 90 sn; 1 phone + 1 browser; en fazla 4 auth-bekleyen/toplam açık socket; geçerli aynı rol capability'si yeni sokete eski soketi kapattırarak yer açar; oda upgrade sınırı 20/dk. |
| Eşleşme | Phone `phone_hello`, browser `web_finished`; açık telefon onayından sonra **phone** `consume{session}` yollar. Tek tüketim ve iki rol token'ı rotasyonu. |
| Consume / onay | Relay phone rolüne güvenir; kullanıcı onayının kendisini göremez. Adım 3, gerçek UI onayını zorunlu kılmalı. `consumed` E2EE onayı değildir. |
| Veri | Active durumda `encrypted` zarfı; yalnız karşı uca; alıcı yoksa atılır. |
| Boyut / hız | Frame ≤64 KiB; bağlantı başına 20 frame/sn, 60 frame burst, 4 MiB/dk; binary mesaj kabul edilmez. |
| Kalp atışı | `{v:1,type:"ping"}` → pong; client 30 sn'de gönderir. 60 sn etkinlik yoksa socket kapanır. |
| Kopma | İlk kopmada 5 dk sabit pencere; active rol capability'siyle yeniden socket auth, ardından iki uçta E2EE resume doğrulaması. Phone `resume_complete` ile pencereyi kapatır. |
| Süre / çıkış | Active oda en çok 8 saat; `close` iki rol için de odayı bitirir. Alarm metadata'yı temizler. Browser/phone RAM anahtarlarını da silmeli. |

Alarm 5 sn ve 90 sn gibi eşikleri uygular; gerçek platform alarmı gecikirse gelen her auth/mesajda saat kontrolü de yapılır. Hibernation sırasında sayaç/rol/deadline attachment'ları korunur; mesajlar korunmaz. DO'nun kalıcı metadata'sında yalnız capability hash'i, oda/session, süre/sayaç durur.

## 6. Yerel testler ve doğrulama sınırı

Repo kökünde `bash tests/web_crypto/run.sh` ve `bash tests/web_relay/run.sh` çalışır. Node WebCrypto, Java 8 uyumlu host derlemesi gerekir. Python yalnız ortak JSON fixture'ını Java Properties girdisine çevirir; üretim bağımlılığı değildir.

Relay testleri Cloudflare çalışma ortamını **Node mock** ile taklit eder. Gerçek `workerd`, canlı WebSocket, edge hibernation, mobil veri/TLS ve 10 saatlik kota/pil ölçümü **yapılmadı**. Bu adımda test sonucu olan “PASS” bu canlı davranışların onayı değildir.

Resmî belgeler: [Workers Git bağlantısı](https://developers.cloudflare.com/workers/ci-cd/builds/), [DO SQLite başlangıcı](https://developers.cloudflare.com/durable-objects/get-started/), [DO legacy migration](https://developers.cloudflare.com/durable-objects/reference/durable-object-class-migrations-legacy/), [Custom Domains](https://developers.cloudflare.com/workers/configuration/routing/custom-domains/), [Hibernation](https://developers.cloudflare.com/durable-objects/best-practices/websockets/), [Alarms](https://developers.cloudflare.com/durable-objects/api/alarms/), [ücretsiz DO kotaları](https://developers.cloudflare.com/durable-objects/platform/pricing/).

## 7. Adım 3a telefon istemcisi

`TEZGAH_RELAY_URL` yerel ayarıyla derlenince Tezgâh ana ekranında “Bilgisayara bağlan” görünür. QR okuma → açık telefon onayı/ad → foreground service. Service yalnız açık dönem ve takvim günü alımlarını okur; hiçbir web yazma komutu yoktur. Tek aktif bilgisayar desteklenir; isim/tarih/durum metadata'sı noBackupFilesDir içindedir.

Telefon gönderim temposu 10 frame/sn ve yaklaşık 1,9 MiB/dk; kuyruk en fazla 128 frame/2 MiB. Relay vars değerlerini bu temponun altına indirirsen cevaplar düşebilir; browser 3b salt okuma isteklerine timeout/tekrar eklemeli. `peer_joined` ve `peer_left` şifresiz bağlantı ipuçlarıdır, kimlik/telefon onayı yerine geçmez; browser yeniden katılınca yeni epoch resume gerekir.

APK boyutu **ölçülmedi**; gerçek Android/OkHttp derlemesi **derlenmedi (SDK yok)**. Termux'ta mevcut imza ayarlarıyla `bash gradlew :app:assembleRelease` kullanılacak. API listesi ve doğrulama sınırları [Adım 3a raporunda](../../docs/web-baglanti/ADIM3A-RAPOR.md).
