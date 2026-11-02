# Zeytin Defteri — Web bağlantısı tasarımı

Tarih: **3 Kasım 2026**. Durum: **adım 2 onaylandı; 3a telefon tarafı uygulandı/host testli; 3b web sayfası bekliyor. Android derlemesi ve canlı dağıtım doğrulanmadı**.

İlk tasarım `ce48c0c` commitinde onaylandı. Adım 2: ayrı relay Worker, saf Java/ES module kripto-protokol yardımcıları ve host testleri eklendi. 3a'da telefon onayı, ekran/servis/OkHttp, salt alım görünümü ve mutasyon bildirimleri eklendi. Gradle/manifest 3a için güncellendi; `cloud/zeytin-ai` değişmedi. [3a raporu](ADIM3A-RAPOR.md) derleme/API/test sınırlarını içerir. Kurulum: [KURULUM.md](../../cloud/tezgah-relay/KURULUM.md); test kayıtları: [ADIM2-TEST-SONUCLARI.md](ADIM2-TEST-SONUCLARI.md).

## 1. İncelenen kaynak ve kapsam

- Gerçek depo: **muammercerdin-star/Zeytin-Defteri-Recovery**. Kullanıcının kısaca `zeytin-defteri` dediği depolar kontrol edildi: `Zeytin-Defteri` deposunda istenen kaynak dalı bulunmadı; istenen dal ve aşağıdaki Tezgâh dosyaları Recovery deposunda bulundu. Bu nedenle çalışma Recovery üzerinde yürütüldü. Diğer depoya yazılmadı.
- Kaynak dal: `work/home-redesign-2026-10-13`.
- İncelenen sabit commit: `d078ca00cd4da04abd390878901422e9a136016e`.
- Yeni dal: `work/web-baglanti`; başlangıcı yukarıdaki commit.
- Satır numaraları **bu kaynak commitine** aittir. `P/` aşağıdaki tablolarda `app/src/main/java/com/zeytindefteri/app/` kısaltmasıdır.
- Kaynak ağacı: [sabit commit](https://github.com/muammercerdin-star/Zeytin-Defteri-Recovery/tree/d078ca00cd4da04abd390878901422e9a136016e).
- `app/build.gradle`: minSdk 24, compileSdk/targetSdk 36, Java 8, debug/release minify kapalı. Yayın derlemesi yerel upload imzası gerektiriyor. Yeni bir imza veya gizli bilgi üretilmeyecek.

Hedefin ilk ürün aşaması: bilgisayarda yalnız görüntüleme ve A4 yazdırma. Sonraki ürün aşaması: bilgisayardan kayıt; bu belge ona hazırlık sınırlarını da tanımlar. Telefon SQLite verisinin tek yetkili kaynağıdır. Telefon kapalıyken web uygulaması yeni veri gösteremez; Firestore yedeği canlı kaynağa dönüştürülmez.

## 2. Mevcut veri envanteri

### 2.1 Dosyalar, fonksiyonlar ve ekran karşılıkları

| İstenen görünüm / bileşen | Dosya ve kaynak satırı | Mevcut davranış ve web karşılığı |
|---|---|---|
| Alım kaydetme | `P/TezgahActivity.java:1644`, `:1655`; `P/TezgahPurchases.java:73` | `savePurchase()` → `add()`. Üretici, telefon, tarih, net kg, kasa, tutar, ödenen ve kodlanmış tartımlar SQLite'a yazılır. İlk aşamada webden çağrılamaz. |
| Yarım tartım | `P/TezgahActivity.java:125`, `:127`, `:143` | `draftKey/loadDraft/saveDraft`; `zd_tezgah` SharedPreferences. Henüz kaydedilmemiş tartım SQLite alımı değildir; ilk web sürümünün canlı görünümüne alınmaz. |
| Tezgâh ana ekranı | `P/TezgahHomeActivity.java:117–136`, `:185–215` | `render()` açık dönem özetini, bütün alımlardan üretici borcunu ve ayrı cari defterden tüccar bakiyesini toplar. |
| Takvimde bugün | `P/TezgahPurchases.java:96`, `:163` | `forDay(db,owner,date)` / `summary()`: `owner_uid` + `record_date=?`; açık ve Z'li alımlar birlikte, `created_at DESC,id DESC`. |
| Uygulamadaki BUGÜN / açık dönem | `P/TezgahPurchases.java:103`, `:149`; `P/TezgahDayActivity.java:96` | `open()` / `openSummary()`: `COALESCE(z_id,0)=0`, **tarih filtresi yok**. Gece yarısında kapanmaz; Z alınana kadar farklı günleri kapsayabilir. Webde varsayılan bu davranış korunur; takvim bugünü ayrıca seçilir. |
| Alım defteri ve arama | `P/TezgahPurchases.java:110`; `P/TezgahDayActivity.java:243`, `:277`; `P/TezgahSearch.java:31`, `:60`, `:106`, `:149` | `all()` sıralı kayıtları LIMIT ile alır; `filter()` ad/telefon/tarih araması ve borç filtresi, `byProducer()` gruplama, `totalRemaining()` borç toplamı. Mevcut ekran araması 2.000 kayıtla sınırlı; bunu sessizce “tüm geçmiş” saymayacağız. |
| Tek alımın elek/tartım detayı | `P/TezgahModel.java:16–18`, `:151`, `:169`, `:171`, `:191`, `:198`, `:308` | `decode(p.data)`, `gradeNet/gradeAmount`, `totalNet/total/averagePrice`. Numara sırası DOUBLE,1,2,3,4,YAGLIK. Tarayıcı hesapları yeniden icat etmez; telefonun hesapladığı sonuçları gösterir. |
| Üretici ekstresi | `P/TezgahStatementActivity.java:74–77`; `P/TezgahStatement.java:29`, `:35`, `:72` | `hasStatement()` / `of(all,seller)` / `text()`. Bütün dönemleri kapsar; eski→yeni kayıtlar, toplam kg/kasa/tutar/ödenen/kalan, numara kg, ortalama, en güncel telefon. UI 100.000 kayıtla okuyor; büyük arşiv için yeni sayfalama gerekir. |
| Üretici eşleştirme | `P/TezgahStatement.java:35–52`; `P/TezgahSearch.java:60` | `fold(seller)` ile ad eşleşir. Kalıcı üretici kimliği yoktur. Adaşlar birleşebilir; “Münferit satıcı” ve boş ad için ekstre yoktur. Web bu gerçeği gizlemez; istemciden gelen üretici adı sahiplik yetkisi değildir. |
| Sonradan ödeme geçmişi | `P/TezgahPayments.java:77–104` | `history(db,owner,p)`: alım kimliği+sahip filtresi, `created_at,id` sırası. İlk alımda ödenen kısım `p.paid − sonraki ödemeler` ile başa eklenir; ayrıca tablodaki toplamı tekrar ekleyip çift sayma yapılmaz. |
| Tüccar cari listesi | `P/TezgahLedger.java:109`, `:144`, `:170` | `entries()` → `parties()` / `totals()`. Cari borç ile üretici borcu farklıdır. Toplam alacak ve toplam borç ayrı, netleştirilmeden gösterilir. |
| Tüccar cari ekstresi | `P/TezgahLedger.java:111`, `:113–120`, `:179`, `:247` | `entriesOf()` `owner_uid+party_key` ile okur; `balanceOf()` ve `statement()`. Bakiye = satış − tahsilat + ödeme; artı tüccar bana borçlu, eksi ben ona borçluyum. |
| Açık akşam hesabı | `P/TezgahDayActivity.java:485–500`; `P/TezgahClosing.java:34`, `:65–91`, `:162` | `fromPurchases(open)` üzerine `load(owner,TezgahZ.OPEN_KEY,c)`. Anahtar `open`; teslim kg, komisyon, avans yüklenir. Kayıt yoksa UI `zd_tezgah/rate_{owner}` kullanır; web de aynı varsayımı kullanmalı. |
| Akşam hesabının sonuçları | `P/TezgahClosing.java:65–91` | Alınan/teslim/fire kg ve fire değeri; komisyon = teslim kg×oran; tüccar ödeyecek = üretici toplam tutarı + komisyon − avans. Üreticiye kalan borç ayrıca hesaplanır. `rate` yüzde değil TL/kg'dır. |
| Z listesi / tek Z | `P/TezgahZ.java:110`, `:116`, `:142`; `P/TezgahPurchases.java:117` | `list()` / `get()` / `closingOf()` + `forZ()`. Z kimliği sahip filtresiyle doğrulanır, kayıtlı teslim/komisyon/avans yeniden uygulanır. |
| Z kapatma davranışı | `P/TezgahZ.java:60–101`; `P/TezgahDayActivity.java:441–473` | `close()` tek transaction ile açık alımları Z'ye bağlar, hesap girdilerini Z'ye kopyalar, `open` hesabını temizler. İlk web aşamasında yasak işlem; yalnız telefondan. |
| Z fişi | `P/TezgahZSlip.java:39–96` | `draw()` Android Bitmap üretir. Ödenen tutarı güncel alım listesinden yeniden toplar (`:63–68`); `tezgah_z.amount_paid` kapanış anının değeridir. Webde “kapanışta ödenen” ve “şimdi ödenen” karıştırılmaz. Bitmap A4 veri API'si değildir. |
| Kasa ek bilgisi | `P/TezgahCrates.java:58`, `:116`, `:205`; `P/TezgahDayActivity.java:805` | `moves()` + `day()` geçmiş devir/gelen/giden/kalan kasa. Z/Akşam hesabında ihtiyaç duyulan özet telefon tarafından sağlanabilir; bağımsız kasa yazma ekranı ilk kapsam dışında. |
| QR okuyucu | `P/TezgahQrUi.java:32`, `:54–69` | `bitmap()` ZXing; `scan()` Google Code Scanner. Mevcut okuyucu tekrar kullanılabilir; ilk modül indirmesi ve Play Services yokluğu hata akışına dahil. Web eşleştirme QR'ı mevcut alım fişi QR biçiminden ayrı sürüm/önek taşır. |
| Yazdırma | `P/TezgahPrintFormat.java:21`, `:67`; `P/TezgahPrinter.java:69` | Mevcut çözüm termal fiş / ESC-POS. Web A4 için aynı iş verisini alan ayrı HTML yazdırma görünümü gerekir; termal metni A4'e büyütmek yeterli değil. |

### 2.2 SQLite tabloları ve sahiplik

**Önemli düzeltme:** Tezgâh tablolarının CREATE/ALTER kodları `DatabaseHelper.java` içinde değildir; yardımcı sınıfların `ensure()` fonksiyonlarıyla ihtiyaç anında kurulurlar. `app/src/main/java/DatabaseHelper.java:59–69` aktif `SessionManager` sahibini verir. Bu dosyada `tezgah_*` tablo tanımı bulunmadığı kaynak aramasıyla doğrulandı. Bütün yeni okumalar aynı helper üzerinden açılan SQLite'a, telefondaki aktif sahibin parametresiyle gider.

| Tablo | Tanım yeri | İlgili alanlar ve ilişki |
|---|---|---|
| `tezgah_purchases` | `P/TezgahPurchases.java:44–59` | id, owner_uid, record_date, seller_name/phone, total_kg/kasa, gross_total, amount_paid, data, created_at; sonradan `z_id` eklenir. `(owner_uid,record_date)` ve `(owner_uid,z_id)` indeksleri. |
| `tezgah_payments` | `P/TezgahPayments.java:30–34` | id, purchase_id, amount, paid_on, created_at, owner_uid. purchase_id alıma mantıksal bağ; CREATE içinde FOREIGN KEY yok. |
| `tezgah_closings` | `P/TezgahClosing.java:149–158` | id, owner_uid, record_date (`open` dahil), delivered (`v2|...`), rate, advance, updated_at; UNIQUE(owner_uid,record_date). |
| `tezgah_z` | `P/TezgahZ.java:34–52` | id, owner_uid, z_no, closed/first/last_at, purchase_count, total_kasa/kg, gross_total, amount_paid, delivered_kg, rate, advance, closing; UNIQUE(owner_uid,z_no). |
| `tezgah_ledger` | `P/TezgahLedger.java:55–59` | id, party/party_key, kind, amount, kg, method, detail/note, entry_date, z_id, created_at, owner_uid. Z'den cari kaydı ayrıca üretilir; her Z otomatik cari satırı demek değildir (`hasZ():101`). |
| `tezgah_crates` | `P/TezgahCrates.java:58–63` | id, party/party_key, kind, count, moved_on, purchase_id, note, created_at, owner_uid. |

Tezgâh tablolarında **season_year sütunu yoktur**; web API'sine bahçe modülünün sezon filtresi körlemesine eklenmez. Her sorgu owner filtresiyle çalışır; owner, UID, tablo adı veya SQL web istemcisinden kabul edilmez. Alım silme sadece açık Z'siz kayıtta yapılır; sonraki ödeme Z'li alıma da yapılabilir (`TezgahPurchases:186`, `TezgahPayments:37`).

### 2.3 Bulut yedeğinin gerçek rolü

- `P/TezgahCloudBackup.java:166–220`: `users/{uid}/tezgah_backup`; `backup()` → `runBackup()` → `TezgahBackup.export()`; aktif yerel sahip ile Firebase UID eşitliği kontrol edilir. `force=false` için deneme aralığı en az 2 dakika (`:50`, `:173–181`).
- `app/src/main/java/ZeytinApplication.java:37–41`: uygulama arka plana geçince yedek denemesi. Z kapatmada ayrıca zorunlu deneme: `TezgahDayActivity.java:466`. “Günde birkaç” bir kullanım beklentisidir, sabit günlük kota/saatli zamanlayıcı değildir.
- `P/TezgahCloudPack.java:20–25`, `:29`, `:55`, `:75`: gzip, 700 KiB parçalar, toplam 9 MiB üst sınır, `@created` hariç SHA-256 değişim özeti. **Gzip şifreleme değildir.** Mevcut yedeğe bu görevle E2EE eklenmiş sayılmaz.
- `P/TezgahCloudIndex.java:14–22`, `:39`, `:54`, `:102`, `:115`: cihaz başına bölüm, index/meta ve eski part uyumu; en fazla beş telefon yedeği. Bu cihaz dizini “bağlı bilgisayarlar” listesi değildir.
- `P/TezgahBackup.java:38`, `:55`, `:68–114`: sekiz tablo (`tezgah_z/purchases/payments/closings`, `sale_payments`, `oil_pressings`, `tezgah_crates/ledger`) ve ayarlar dışa aktarılır; owner dışa aktarılmaz, sorgular owner ile filtrelidir.
- Yeni bağlantı tüm yedeği yayınlamaz; yalnız yetkilendirilmiş ekran verisini okur. Yedekleme ve geri yükleme davranışları korunur. Geri yükleme olduğunda web snapshotları geçersiz kılınmalıdır.

## 3. Mimari ve aktarıcı seçimi

Akış: **Android SQLite → salt okunur TezgahWebRepository → E2EE mesaj → WSS aktarıcı → tarayıcı belleği → ekran / yerel A4**. İstekler ters yönde gider. İnternet iki cihazda da gerekir; aynı Wi-Fi şart değildir. Port açma/NAT yapılandırması yoktur.

Öneri: **Cloudflare Workers Free + SQLite-backed Durable Objects + Hibernation WebSocket API**, ayrı `cloud/tezgah-relay/` Worker'ı. Mevcut `cloud/zeytin-ai/wrangler.toml:1–17` AI binding ve rate limit içeriyor; bu servisin işlevi ve kotası bağlantı özelliğiyle karıştırılmaz. Aynı hesaptaki Workers Free tüketimi yine ortaktır. DO namespace SQLite-backed olur; bu seçim tezgâh içeriğini SQLite bulutuna yazacağımız anlamına gelmez.

### 3.1 2 Kasım 2026 tarihinde resmî belgelerden doğrulanan sınırlar

| Konu | Cloudflare Free | Supabase Free |
|---|---|---|
| Mesaj / istek | DO 100.000 istek/gün; gelen WS mesajları hesaplamada 20:1; giden WS mesajı ücretlendirilmez [K1] | 2 milyon Realtime mesaj/ay [K4] |
| Açık bağlantı | Kullanım bu tasarımda oturum başına iki WS; Supabase'in 200 sınırıyla aynı tür bir hesap kotası doğrulanmadı | 200 eşzamanlı bağlantı; mevcut uygulamanın tüketimi de hesaba katılır [K4,K5] |
| İşlem süresi | DO 13.000 GB-s/gün; hibernation idle süreyi azaltır [K1,K3] | Broadcast için DO benzeri GB-s kotası kullanılmaz |
| Boyut / hız | Uygulama kendi sınırını 64 KiB şifreli frame olarak koyacak | 100 mesaj/sn, 256 KB broadcast payload [K5] |
| Veri / ağ | DO SQL: 5 milyon satır okuma/gün, 100.000 yazma/gün, toplam 5 GB [K1] | 5 GB egress/ay; veritabanı 500 MB; 2 aktif ücretsiz proje, 1 hafta hareketsizlikte duraklatma [K4] |
| Ücretsiz limit aşımı | İlgili işlem hata verir; günlük reset 00:00 UTC [K1]. Worker ön katmanı ayrıca 100.000 istek/gün, 10 ms CPU/HTTP isteği [K2] | Free kullanımında bildirim/grace/fair-use uygulanabilir [K6]; kesintisiz veya sınırsız hizmet sözü verilemez |
| Yetkilendirme | Oda ve rol başına kısa süreli capability; özel minimal protokol | Private channel + RLS + kullanıcı JWT'si gerekir; anon/publishable anahtar tek başına kullanıcı kimliği değildir [K7] |
| Saklamasız iletim | Mesajları storage/loga yazmayan Worker bize ait; süreli oda metadata'sı ayrı | Client broadcast kullanılabilir; DB `realtime.send()` yolunda mesajlar 3 gün saklanır, bu yol kullanılmamalı [K8] |

**Kaynak uyuşmazlığı:** Cloudflare ürün tanıtım sayfasında SQL okuma kotası 50 milyon/gün görünürken teknik fiyatlandırma sayfası [K1] 5 milyon/gün gösteriyor. Tasarım temkinli olan teknik dokümandaki **5 milyon** değerini esas alır. Canlı panelin kota değeri doğrulanmadı.

Supabase zaten projede var ama Firebase oturumundan özel Realtime üyeliğine geçiş, private oda ve RLS düzeni ek iş çıkarır. Tahmin edilemeyen oda adı tek başına yetki değildir. Cloudflare'da oda/rol ve kısa ömürlü bağlantı kontrolü daha küçük, bağımsız bir katmanda kalır. Hibernation zorunlu tasarım tercihi: standart WS accept ile saatlerce aktif DO gereksiz süre tüketir. Ücretsiz kota dolarsa **özellik kapanır, ücretli plana geçilmez**. Tam sınırsız büyüme ve sürekli ücretsiz kalma garantisi verilmez.

### 3.2 Bir tezgâh için örnek trafik hesabı — ölçüm değil

Varsayımlar: bir telefon+bir bilgisayar, 10 saat bağlantı, 300 alım ve 100 ödeme/hesap değişikliği; 400 invalidation, her biri ardından istek+cevap; 200 ek ekran isteği; 30 saniyede bir iki uçtan tek heartbeat ve tek yanıt. Yükler örnektir, gerçek çiftçi verisi kullanılmadı.

| Kalem | Günlük tahmin |
|---|---:|
| Veri frame sayısı | 400×3 + 200×2 = 1.600 |
| Heartbeat frame sayısı | (36.000/30)×2 uç×2 = 4.800 |
| Eşleşme, reconnect payı | 100 |
| Toplam relaya gelen frame | yaklaşık 6.500; bir frame tek karşı uca gider |
| CF DO istek eşdeğeri | 6.500/20 + yaklaşık 30 upgrade/kontrol = 355/gün; alarm/metadata çağrıları eklenir |
| Ortalama veri frame 3 KiB | 4,69 MiB |
| Heartbeat 200 B + bağlantı payı | 0,92 MiB + yaklaşık 0,5 MiB |
| Tahmini toplam istemciye iletilen veri | yaklaşık 6,1 MiB/gün, yaklaşık 183 MiB/30 gün; TLS/IP ve yeniden aktarım dahil değil |

DO işleyicilerinin frame başına ortalama 5 ms aktif kaldığını **varsayarsak** 6.500×0,005×0,125 ≈ **4,1 GB-s/gün**; aktif kalma/uykuya geçiş overhead'i ölçülmedi. Sürekli uyanık tek DO 10×3.600×0,125 = **4.500 GB-s/gün** tüketebilir; yalnız üç benzer DO bile 13.000 sınırını aşabilir. Bu yüzden hibernation ile pilot ölçümü yayından önce şarttır.

Supabase için muhafazakâr olarak gönderim+tek alıcı teslimini iki sayarsak 13.000/gün, 390.000/ay olur; heartbeat'in faturalamaya nasıl yansıdığıyla bu katsayı bu proje üzerinde **doğrulanmadı**. Yaklaşık beş bu yoğunlukta tezgâh mesaj kotasına yaklaşır; 100 çift cihaz da 200 bağlantıyı tüketir. Diğer özellikler bu bütçeyi azaltır. CF için 355 sayısından doğrudan “şu kadar tezgâh kesin destekler” sonucu çıkarılmaz; süre, oda oluşturma saldırıları ve ortak hesap tüketimi farklı sınırlar getirir.

A4 için HTML tarayıcıda basılır; sunucuda PDF üretimi/büyük bitmap aktarımı yoktur. İlk arşiv okuması sayfalıdır; tüm arşivi her değişiklikte göndermek bu tahminleri geçersiz kılar.

### 3.3 İşletim, ücretsiz kalma ve veri minimizasyonu

- Ayrı Worker; ücretli API/AI yok, ücretli plan yükseltme yok. Kota bitiminde `KOTA_DOLDU`, telefonun yerel işlevleri çalışır.
- Önerilen uygulama sınırları: pending oda 90 sn, tek telefon+tek tarayıcı, telefon başına en fazla iki aktif bilgisayar; oda oluşturma Worker genelinde 10/dk ve 1.000/gün, bağlantı başına 20 frame/sn, 60 frame burst ve 4 MiB/dk; oda upgrade 20/dk ve auth bekleyenler dahil en çok dört socket. Bu bütçeler `[vars]` üzerinden oda oluşturma için ayarlanabilir. Bunlar ürün kararlarıdır, pilotta ayarlanır.
- IP hiç okunmaz/saklanmaz. Aynı DO sınıfının ayrı `creation-budget` örneği Worker genelinde oluşturma bütçesini atomik uygular; yalnız UTC gün/dakika ve sayaçları saklar. Kaynak bütçesi hesapta kalan Cloudflare kotasının ölçümü değildir; saldırgan bu bütçeyi tüketip hizmeti durdurabilir. Free planda kesin kullanılabilirlik garantisi yoktur. Ek rate-limit ürünü/ücretli API kullanılmadı; maliyet pilotta ölçülecek.
- SQL/attachment içinde yalnız rastgele oda kimliği, capability hash'leri, rol, son tarih, tüketim sayacı ve iptal durumu. İsim, UID, telefon, alım, ekstre, mesaj ciphertext'i ve QR sırrı saklanmaz. Aktarım tamponu sınırlı geçici bellektir; alıcı yoksa içerik atılır.
- Hibernation sonrası doğrulama için gerekli metadata attachment/storage'dan geri alınır; oda durumu yalnız JS RAM değişkenine bırakılmaz. Tüketilen QR metadata'sı son kullanma süresine kadar, oturum metadata'sı en fazla oturum süresine kadar tutulur; sonra temizlenir. İptal edilmiş session yeniden açılmaz.
- `console.log`, request body/header dökümleri, analytics payload, error stack'e veri ekleme ve tarayıcı hata raporlarına içerik gönderme yok. URL/query içine token yok; WS'nin ilk frame'inde auth, auth tamamlanmadan iletim yok, 5 sn auth timeout. Hosting/platform erişim metadata'sı tamamen ortadan kalkmaz; IP/zaman/uzunluk gibi metaveriyi servis sağlayıcı görebilir. “Aktarıcı hiçbir şey göremez” yalnız içerik için geçerlidir.

## 4. Android kararları

### 4.1 WebSocket

3a seçimi: **`com.squareup.okhttp3:okhttp:5.5.1`**, resmî güncel sürüm belgelerinden doğrulandı (3 Kasım 2026). Java çağrı arayüzü, WSS, ping/close ve yeniden bağlantı olaylarını yönetmek için; Android platformunda uygulamaların kullanabildiği yerleşik genel bir `java.net.http.WebSocket` çözümü minSdk 24 için temel alınmaz. Supabase SDK'sını sırf bu özellik için eklemeyiz.

Resmî güncel kaynak [OkHttp overview](https://lysine.dev/okhttp/) Android 21+ ve Java 8+, Okio/Kotlin stdlib transitif bağımlılıklarını ve AndroidX Startup başlangıcını belgeliyor. [5.5.1 changelog](https://lysine.dev/okhttp/changelogs/changelog/) 22 Ekim 2026 sürümünü gösteriyor. Java 8 compileOptions korunur; Kotlin plugin veya core-library desugaring bağımlılığı eklenmedi. Gradle Android varyantını seçer; Startup initializer kaldırılmadı. Bu projedeki tam transitif sürüm çözümü/D8 sonucu SDK olmadığı için **doğrulanmadı**.

APK boyut etkisi **ölçülmedi**; önceki 0,7–2 MB tahmini ölçüm değildir. Doğrudan yalnız bir implementation eklendi; APK öncesi/sonrası derleme Termux'ta ölçülecek.

Tek paylaşılan istemci, bounded gönderim kuyruğu, ana thread dışında SQLite, bağlantı değişimi dinleyicisi ve jitter'lı reconnect; her Activity ayrı socket açmaz. Sertifika doğrulaması kapatılmaz; `ws://` ve güven-all TrustManager yasaktır.

### 4.2 Ön plan servisi ve Android 14/15

**Birincil tip önerisi: `connectedDevice`.** Telefon, kullanıcının açıkça bağladığı dış bilgisayarla ağ üzerinden sürekli etkileşiyor; resmî tip tanımı ağ bağlantılı dış cihaz etkileşimini içeriyor [K9]. Bunun bu özel internet-relay kullanımında Google Play tarafından kabulü **doğrulanmadı**; platformda başlayabilmek Play onayı demek değildir.

Önerilen manifest değişiklikleri (şimdi uygulanmadı):

- `android.permission.FOREGROUND_SERVICE`.
- `android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE`.
- Tipin önkoşullarından biri olarak `android.permission.CHANGE_NETWORK_STATE`; yalnız `INTERNET` yeterli değildir. Bluetooth izni bu ağ özelliğinin gerekçesi yapılmaz.
- `TezgahWebService`: `android:exported="false"`, `android:foregroundServiceType="connectedDevice"`.
- Var olan INTERNET ve POST_NOTIFICATIONS korunur. API 33+ bildirim izni kullanıcı akışında ele alınır; bildirim izni reddi FGS'yi kendiliğinden yasaklıyor varsayılmaz. Sürekli durumun görünürlüğü cihazda doğrulanır.

Android 14+ doğru service type ve tipe özel izin ister [K10]. Servis yalnız görünür Activity'de QR okuma ve telefon onayı sonrasında başlatılır; API 26+ `startForegroundService`, hemen `startForeground` ve durum bildirimi; API 24/25 uyumlu başlatma yolu. Bildirim: “Bilgisayar bağlantısı açık · 1 bilgisayar”, “Bağlantıları kes”. Servis `START_NOT_STICKY`; boot, force-stop veya süreç ölümünden sonra gizlice açılmaz. Çıkış/hesap değişimi/silme servisi ve anahtarları sonlandırır (`SessionManager.java:144`, `LocalAccountWipe.java:24` entegrasyon noktaları).

Android 15'in **dataSync ve mediaProcessing** tiplerinde uygulama arka plandayken tip başına ortak 6 saat/24 saat sınırı vardır; ön plana getirme sayacı yeniler; `onTimeout(int,int)` sonrası birkaç saniyede `stopSelf()` gerekir [K11]. `connectedDevice` bu belgede bu altı saatlik sınıra dahil değildir; bu, OEM pil yönetimi veya sistem öldürmesine karşı sınırsız çalışma garantisi vermez.

`remoteMessaging` kullanıcının metin mesajlaşmasına devam etmesi içindir; finansal kayıtları JSON ile göndermek tek başına bu tipe gerekçe olmaz. `shortService` uzun oturuma uymaz. `specialUse` sırf süreyi aşmak için seçilmez.

**Geri dönüş yolu:** connectedDevice kullanım gerekçesi kabul görmezse `dataSync` + `FOREGROUND_SERVICE_DATA_SYNC` ile süreli oturum tasarlanır; API 35+ `onTimeout` anahtarları siler/soketi kapatır/servisi durdurur; web “Telefonu açıp yeniden bağlan” gösterir. dataSync'le bütün gün arka planda çalışma sözü verilmez; BOOT_COMPLETED'dan bu servis başlatılmaz [K11]. Bu alternatif ürün davranışında görünür farktır; yayın öncesi kesinleştirilmelidir.

Sürekli WakeLock veya pil optimizasyonunu kapattırma varsayılan değildir. Android 24/34/35/36, ekran kapalı, Wi-Fi↔mobil ve Honor gerçek cihaz testleri yapılmadan ekran kapalı bağlantı kararlılığı doğrulanmış sayılmaz.

### 4.3 E2EE: ilk eşleşme, SAS'sız v1

Seçim değişmedi: **P-256 ECDH + elle RFC 5869 HKDF-SHA256 + AES-256-GCM**. Android bağımsız Java 8 kodu `TezgahWebCrypto.java` / `TezgahWebProtocol.java`; ES module karşılıkları `web/tezgah/js/crypto.js` / `protocol.js`. Java kodu `java.util.Base64` veya `android.util.Base64` kullanmaz; padding'siz canonical base64url yardımcısı vardır. `EC/secp256r1`, SPKI DER public key, HmacSHA256 ve AES/GCM/NoPadding + 128 bit tag kullanılır [K12,K14]. HKDF extract/expand iki tarafta HMAC ile yazıldı; hazır platform HKDF API'sine bağımlı değildir.

1. Relay rastgele 16 bayt room, iki ayrı 32 bayt rol capability ve 90 sn expiresAt üretir. Browser kendi CSPRNG'siyle 32 bayt **S**, geçici ECDH çifti **B** ve 32 bayt **nb** üretir. S, B private key ve nb **room endpoint'ine gönderilmez**. QR'da S, B'nin yalnız public key'i, nb, room/expiry ve phone capability vardır. Relay URL QR'dan seçilmez.
2. Telefon QR'daki B'yi optik olarak sabitler; geçici P çifti ve 32 bayt np üretir. `Pairing` nesnesi B'yi constructor'da tutar; `phoneKeys()` ağdan yeni B parametresi kabul etmez. `browserKeys()` kullanılan browser çiftinin public key'inin QR B ile eşleştiğini kontrol eder. P-256/curve/nokta/DER/base64 doğrulanır.
3. İki uç `Z = ECDH(Bprivate,Ppublic) = ECDH(Pprivate,Bpublic)` hesaplar. `T=UTF8(JSON array ["zd-web",1,room,expiresAt,nb,np,Bspki,Pspki])`, `H=SHA256(T)`, `PRK=HKDF-Extract(salt=S,IKM=Z)`. Alan sırası sabit, base64url padding'siz; expiresAt saniye cinsinden güvenli tam sayıdır.
4. Expand info: `UTF8("zd-web/v1/"+label)||H`. Etiketler `phone-to-web`, `web-to-phone`, `confirm`, `resume`; her biri ayrı 32 bayt. Session `base64url(H[0..15])`, ilk epoch `base64url(H[16..31])`.
5. Telefon `HMAC(Kconfirm,"phone-hello"||H)`, browser `HMAC(Kconfirm,"web-finished"||H)` doğrular. **SAS / altı rakam karşılaştırması v1'de yoktur.** ECDH private key veya S relaya gitmez; MAC'te S değil türetilmiş confirm key kullanılır.
6. Ardından **açık telefon onayı zorunludur**. UI 3a'da Evet düğmesine ve servise bağlandı; Android cihaz testi doğrulanmadı. Mevcut `Approval` kapısı önce peer kanıtı, sonra yerel `phoneApproved()` ister; relay `auth_ok/consumed` mesajları veri izni değildir. Onaydan sonra phone `consume{session}` gönderir, relay tek tüketim ve rol capability rotasyonu yapar, sonra E2EE `onaylandi` gönderilebilir. Protokol motoru yalnız `Approval.canRead()` açıkken repository okur.

**QR fotoğrafı iddiasının protokol analizi:** Fotoğraf hırsızına S, Bpublic ve nb'yi verir; gerçek Z'yi vermez. Gerçek telefon Pprivate'ı ve browser Bprivate'ı onda değildir. Kendi P çiftiyle browser'a geçerli sahte telefon gibi bağlanabilir ve browser'a **sahte veri** gösterebilir; ancak bu yeni Z, gerçek telefonun Z'sinden farklıdır. Gerçek telefonun Bpublic sabitlemesini aşamaz; onun GCM/MAC doğrulaması başarısız olur. Dolayısıyla **yalnız QR fotoğrafı + ağ trafiği/relay denetimi**, gerçek telefonun verisini okumaya yetmez. Node/Java testleri hem gerçek ciphertext'in açılamadığını hem ayrı sahte kanal kurulabildiğini kontrol eder. Bu iddia QR'ın başka bir QR ile değiştirilmesini, gerçek web origin'inin/XSS'nin ele geçirilmesini veya özel anahtar/telefon ele geçirilmesini kapsamaz. Bunlarda veri güvenliği iddiası genişletilemez. Ayrıca adım 6'da browser'a elle yazılan yeni hassas kayıtlar sahte telefon kanalına gönderilebilir: bu protokol browser'ın karşısındaki telefonun hesap/cihaz kimliğini, QR fotoğrafı hırsızına karşı bağımsız olarak kanıtlamaz. Dolayısıyla “hiçbir kullanıcı verisini hiçbir durumda okuyamaz” şeklindeki genel iddia **doğru değildir**; burada doğrulanan iddia gerçek telefonun mevcut SQLite verisiyle sınırlıdır. Yazma aşamasında browser'ın gerçek telefon kimliğine güveni ayrıca ele alınacak.

Frame kuralı: yön başına ayrı key; IV = 4 sıfır bayt + seq'in 8 bayt unsigned big-endian gösterimi, toplam 96 bit. Seq API'leri Java ile ortak pozitif signed-long aralığını (1..2^63−1) kullanır; wire'da ondalık **string** olur. Bu daraltma 64 bit nonce alanının pozitif alt kümesidir, JS precision sorunu yaratmaz. Aynı epoch/anahtarda seq asla tekrar kullanılmaz. AAD = `UTF8(["zd-web",1,session,epoch,dir,seq])`. Plaintext UTF-8 JSON, `ct` ciphertext+16 bayt tag'in base64url gösterimidir. İç payload ≤32 KiB, zarf ≤64 KiB.

`Channel.open()` session/epoch/yön, artan seq ve GCM tag'i doğrulamadan plaintext döndürmez; başarısız decrypt receive sayacını ilerletmez. `Channel` çağrıları Java'da synchronized, JS'te promise kuyruğuyla seridir; eşzamanlı replay kabul edilmez. Zarf parser yinelenen/bilinmeyen alan ve kaçışlı/uygunsuz biçimi reddeder. Açık `encrypt/decrypt` fonksiyonları vektör/alt seviye yardımcılarıdır; uygulama sayaç güvenliği için **Channel** kullanmalıdır.

### 4.4 Resume — yeni ECDH olmadan

Yalnız aynı süreç/sekme belleğinde, iptal/hard-expiry yokken, ilk kopmadan itibaren **5 dk**. İki uç yeni 32 bayt nb/np ve yeni 16 bayt epoch üretir; eski epoch tekrar kabul edilmez. Resume'da public/private ECDH üretilmez.

- `Tr=UTF8(["zd-web-resume",1,session,oldEpoch,newEpoch,nb,np])`, `Hr=SHA256(Tr)`.
- İki rol kanıtı: `HMAC(Kresume,"resume-phone"||Hr)` ve `HMAC(Kresume,"resume-browser"||Hr)`; ikisi de doğrulanmalıdır.
- `PRKr=HKDF-Extract(salt=Hr,IKM=Kresume)`. İlk bağlantıyla aynı etiket/info biçimi, fakat `Hr` ile yeni yön/confirm/resume anahtarları türetilir.
- Yeni resume root'u da türetilerek **ratchet** uygulanır. `ResumeGuard.accept()` iki kanıtı/süreyi/eski epoch'u doğrulamadan durumu değiştirmez; başarılı commit root/epoch'u bir kez değiştirir. Kopma zamanının tekrar bildirilmesi beş dakikayı uzatmaz.
- 3a motoru eski `Channel`ı kopmada kapatır, yeni key/epoch ile **yeni Channel** seçer; sonra seq 1 olabilir. Eski Channel üzerinden cevap kabul edilmez; eski seq/epoch frame'i yeni kanalda reddedilir. Guard/Channel kapama işlemleri caller'ın oturum lifecycle'ına bağlanmalıdır.
- Yeni ECDH olmadığı için, eski Kresume ele geçirilmişse gözlenen yeni nonce'larla sonraki anahtarlar hesaplanabilir; bu resume tasarımı **yeni ECDH'nin sağladığı ileri gizliliği sunmaz**. Kullanıcının onayladığı sadeleştirmedir. Root/nonce/anahtarlar loga veya kalıcı depoya yazılmaz.

Java↔Node WebCrypto ortak fixture'ı RFC5869 A.1/A.2/A.3 ve bağımsız Python cryptography çıktıları içerir. Fixture'daki özel test sayıları P-256 d=1/d=2 **açık test verisidir**, kullanıcı/servis sırrı değildir. Bu host testleri gerçek Android 24 veya farklı tarayıcı sürümlerinde çalıştırıldı anlamına gelmez; bağımsız kriptografi denetimi ve cihaz uyumu **doğrulanmadı**.

## 5. Mesaj protokolü v1

Aşağıdaki JSON'lar **şema örnekleridir**; `<...>` alanları gerçek anahtar/rakam değildir. “İç payload” örnekleri relaya açık JSON olarak gönderilmeyecek; yalnız `ct` içinde gidecektir. Protokol komut adları ASCII Türkçe seçildi (`aksam_hesabi`); ekrandaki Türkçe yazım korunur.

### 5.1 Eşleşme ve relay erişimi

Endpoint'ler: `POST /v1/rooms` (boş JSON `{}`), `WSS /v1/socket?room=<room>` (ilk frame auth). Relay base URL `[vars].RELAY_URL`, tam izinli HTTPS origin listesi `[vars].ALLOWED_ORIGINS` ile ayarlanır; Worker kodunda üretim adresi sabit değildir. Android tarafında ileride git dışı `local.properties: TEZGAH_RELAY_URL` → mevcut `zdBuildString` deseniyle BuildConfig kullanılacak; 3a'da BuildConfig alanı eklendi. Geliştirme origin'i ayrı ortam yapılandırmasındadır. WebSocket Origin doğrulanır ama kimlik doğrulaması yerine kullanılmaz. Android telefon socket'i Origin göndermez; browser rolü için izinli Origin zorunludur, phone rolünde Origin reddedilir. Origin tek başına yetki değildir; ayrı capability rolü zorunludur.

Oda oluşturma yanıtı browser capability `cb`, telefon capability `cp`, room ve 90 sn sunucu expiresAt verir; sonuncusu wire'da saniyedir. Bunlar E2EE anahtarı değildir; relay bilir. Tarayıcı cb'yi tutar; QR'a cp'yi koyar, **S ve B özel anahtarını endpoint'e göndermez**. Kullanıcı hesabı veya Firebase token'ı browser'a verilmez.

QR, URL yerine bağımsız tanınan metin olabilir:

```json
{"kind":"ZD_WEB_PAIR","v":1,"room":"<128-bit-rastgele>","expiresAt":1793649690,"phoneCapability":"<256-bit-cp>","secret":"<32-bayt-S-base64url>","browserPublicKey":"<SPKI-B>","nb":"<32-bayt>"}
```

```json
{"v":1,"type":"auth","room":"<room>","role":"browser","capability":"<cb>"}
```

```json
{"v":1,"type":"phone_hello","room":"<room>","phonePublicKey":"<SPKI-P>","np":"<32-bayt>","proof":"<HMAC-phone-hello>"}
```

```json
{"v":1,"type":"web_finished","room":"<room>","proof":"<HMAC-web-finished>"}
```

Bunların ardından kullanıcı onayına kadar **sorgu yok**. İç payload:

```json
{"v":1,"type":"onaylandi","session":"<rastgele-session>","permissions":["tezgah.read","tezgah.print"],"maxAgeSeconds":28800,"idleLockSeconds":300}
```

Durumlar: `QR_BEKLIYOR → ANAHTAR_DOGRULANIYOR → TELEFON_ONAYI → BAGLI → YENIDEN_BAGLANIYOR → BAGLI`; ret/90 sn süre dolması `KAPALI`; iptal/8 saat hard expiry `KAPALI`. QR yenileme eski pending odayı kapatır; geçerli rol capability'siyle tekrar auth olan socket eski aynı rol socket'ini kapatıp yerini alır. Süre dolması iki uç ve relay tarafından kontrol edilir; telefon monotonic bekleme süresini de tutar. Telefon/browser saati güvenilmezse sessizce süre uzatılmaz.

İlk sürüm “bu bilgisayarı haftalarca hatırla” yapmaz. Anahtarlar ve capability'ler yalnız süreç/sekme belleğinde; sekme yenileme veya süreç ölümü **yeni QR** ister. Telefonda owner'a bağlı bağlantı metadata listesi (kullanıcının verdiği bilgisayar adı, rastgele session, başlangıç/son görülme, aktif/iptal) tutulur; bu liste bulut yedeğine dahil edilmez. Aynı owner'daki iki telefonun bilgisayarları otomatik paylaşılmaz.

### 5.2 Şifreli zarf ve veri mesajları

Relay sadece şu zarfı iletir; veri türü/üretici adı AAD'ye veya URL'ye taşınmaz:

```json
{"v":1,"type":"encrypted","session":"<session>","epoch":"<yeni-rastgele-epoch>","dir":"w2p","seq":"1","ct":"<base64url-ciphertext-ve-tag>"}
```

İç istek örnekleri:

```json
{"v":1,"type":"istek","id":"r1","op":"bugunun_alimlari","args":{"kapsam":"acik_donem","limit":100,"cursor":null}}
```

```json
{"v":1,"type":"istek","id":"r2","op":"bugunun_alimlari","args":{"kapsam":"takvim","tarih":"2026-11-02","limit":100}}
```

```json
{"v":1,"type":"istek","id":"r3","op":"alim_defteri","args":{"arama":"Mehmet","yalnizBorclu":true,"limit":100,"cursor":null}}
```

```json
{"v":1,"type":"istek","id":"r4","op":"ekstre","args":{"ureticiAnahtari":"<telefonun-dondurdugu-fold-anahtari>","limit":100}}
```

```json
{"v":1,"type":"istek","id":"r5","op":"cari","args":{"tuccarAnahtari":null,"limit":100}}
```

Tek tüccarın cari dökümü:

```json
{"v":1,"type":"istek","id":"r5a","op":"cari","args":{"tuccarAnahtari":"<party-key>","limit":100}}
```

```json
{"v":1,"type":"istek","id":"r6","op":"aksam_hesabi","args":{"zId":null}}
```

```json
{"v":1,"type":"istek","id":"r7","op":"z_listesi","args":{"limit":30,"cursor":null}}
```

`zId:null` açık dönem; pozitif zId kayıtlı Z. Ayrıca `alim_detayi{alimId}` ve `odeme_gecmisi{alimId}` okunabilir. `cari` anahtar null ise cari liste/toplam; dolu ise tek tüccar hareket/ekstre. Telefon istemci anahtarının gerçekten kendi kayıt kümesine ait olduğunu doğrular. İstek owner, season_year, SQL, Firestore yolu taşımaz.

Örnek cevap; para ondalık noktalı **metin**, kimlikler metin, kg birimi açık:

```json
{"v":1,"type":"cevap","id":"r1","ok":true,"op":"bugunun_alimlari","data":{"kapsam":"acik_donem","snapshot":"s42","sourceEpoch":"<telefon-surec-kimligi>","revision":"42","asOf":"2026-11-02T23:20:00+03:00","timezone":"Europe/Istanbul","items":[{"id":"17","uretici":"Ornek Uretici","tarih":"2026-11-02","netKg":"125.0","kasa":5,"tutarTL":"10000.00","odenenTL":"6000.00","kalanTL":"4000.00","zId":null}],"totals":{"count":1,"netKg":"125.0","tutarTL":"10000.00","odenenTL":"6000.00","kalanTL":"4000.00"},"nextCursor":null,"complete":true}}
```

DTO sözleşmeleri: `ekstre` toplamlar+gradeKg+alım sayfaları; `cari` artı alacak/eksi borç ve hareket türü; `aksam_hesabi` bought/delivered/edited[6], rateTLPerKg, advanceTL, fireKg/fireValueTL, commissionTL, traderOwesTL, producerDueTL; Z yanıtı kapanış anı ve güncel ödeme toplamlarını ayrı alanlarda taşır. Tüm mali sonuçlar telefondan gelir; tarayıcı yalnız Türkçe biçimlendirir. Null ve boş liste ayrımı tanımlanır; SQLite hatası “0 kayıt” diye sunulmaz.

```json
{"v":1,"type":"hata","id":"r4","code":"URETICI_BULUNAMADI","message":"Bu ureticinin ekstresi bulunamadi.","retryable":false}
```

Diğer hatalar: `SURUM_DESTEKLENMIYOR`, `QR_SURESI_DOLDU`, `ONAY_GEREKLI`, `YETKI_YOK`, `SALT_OKUNUR`, `TELEFON_CEVAP_VERMEDI`, `OTURUM_IPTAL`, `KOTA_DOLDU`, `HIZ_SINIRI`, `GECERSIZ_ISTEK`, `SNAPSHOT_GECERSIZ`, `VERI_OKUNAMADI`. Pre-auth hataları genel ve verisiz olur; stacktrace/SQL/sır gönderilmez. Desteklenmeyen major v reddedilir; güvenlik alanları için bilinmeyen değerler sessizce varsayılana düşmez.

### 5.3 Canlılık, kopma ve yeniden bağlanma

- Önerilen ilk canlılık hedefi: commit edilmiş değişiklikten sonra 1–2 sn içinde webde yenileme. Her tartım taslağı değil, **kaydedilmiş veri** aktarılır.
- Telefon veri yazımı başarıyla commit olunca `veri_degisti` yayınlar. Revizyon, `sourceEpoch` içinde artar; alım ekleme/silme, ödeme, cari, closing, Z kapatma ve restore değişimlerinin hepsi kapsanır. Sadece `max(id)` veya `created_at` kullanmak ödeme ve silmeyi kaçırır.

```json
{"v":1,"type":"olay","event":"veri_degisti","sourceEpoch":"<telefon-surec-kimligi>","revision":"43","scopes":["alimlar","ekstre","aksam_hesabi"]}
```

- Web etkin görünümü tekrar ister; telefon 300–500 ms değişimleri birleştirir. Veri üretimi tek executor/snapshot sınırından geçer; owner değişirse uçuşta cevap da iptal olur. İlk bağlantı/reconnect tam güncel snapshot ister; relay geçmiş olay saklamaz.
- Ağ kaybında ekran “Bağlantı koptu, son güncelleme …” gösterir; eski veri canlı sayılmaz. 30 sn heartbeat, 60 sn yanıtsızlıkta bağlantıyı düşürme; 1,2,4,8…30 sn jitter'lı backoff, en fazla 5 dk yeniden deneme. API idempotent salt okumalar yeniden istenebilir.
- **Resume:** §4.4'teki root+iki nonce+yeni epoch türetmesi uygulanır; **yeni ECDH yoktur**. Wire sırası browser `resume_offer{session,oldEpoch,epoch,nb}` → phone `resume_answer{np,proof}` → browser `resume_finished{proof}`. Kanıtlar aynı tam Tr'ye bağlıdır. İki uç `ResumeGuard` doğrulamasını/kanal değişimini tamamladıktan sonra phone relay'e `resume_complete` kontrolü yollar. Relay HMAC doğrulamaz; kriptografik kabul kararı uçlardadır. Yeni QR kullanılmaz; eski QR da açılmaz.
- Reconnect capability'leri rol başına geçerlilik ve session ile bağlıdır; yalnız relay capability'si kriptografik resume yerine geçmez. Relay restart/oda kaybı otomatik güven genişletmez; resume doğrulanamıyorsa yeni QR. Yeniden kullanım/yarış testleri 2. adımın kabul ölçütüdür.
- `cikis` web veya telefondan geldiğinde yerel anahtarlar silinir; relay kanalı kapatır. Telefonda iptal önce yerelde kesinleşir; relay erişilemese bile yeniden bağlanma reddedilir. Web telefon heartbeat'i kesilince en geç 60 sn içinde görünümü kilitler; daha önce görüntülenmiş/çıktı alınmış verinin uzaktan geri alınacağı iddia edilmez.

### 5.4 Telefon 3a sınırları ve tempo

`TezgahWebPhone` Android'siz protokol motoru; `TezgahWebClient` OkHttp taşıması; `TezgahWebService` owner/lifecycle/foreground yönetimi. QR serbest relay adresi kabul etmez. `TezgahWebSessions` yalnız yerel metadata'yı owner hash'iyle noBackupFilesDir içinde tutar. 3a tek aktif bilgisayardır; yeni bağlantı eskisini bitirir.

`bugunun_alimlari` repository'si owner'ı constructor'da telefondan alır, request owner'ını reddeder. `open()/forDay()` kullanır; default 100, üst sınır 200, 24 KiB sayfa veri bütçesine göre daha az satır dönebilir. En fazla iki 60 sn/10 MiB RAM snapshotı; totals bütün snapshot'ı kapsar, cursor sahibi/filtreyi geçemez. SQLite hatası boş liste değildir. JSON sayfalama host gerçek SQLite köprüsünde sınandı.

Mutasyon hook'ları başarı/commit sonrasında, 400 ms birleştirilmiş owner olayı üretir. SQLite nested transaction sürüyorsa iç bildirim ertelenmez, **atlanır**; toplu ödemenin dış transaction'ı kapandığında ayrı bir satır bildirir. Mevcut bağımsız host derlemelerini korumak için eski veri sınıflarındaki opsiyonel hook reflection kullanır; minify kapalıdır. Hesap/restore reset callback'i service/client'ı durdurur.

Relay vars: `FRAME_RATE=20`, `FRAME_BURST=60`, `BYTES_PER_MINUTE=4194304`. İlk sınır aşımında frame düşer, gönderene en çok saniyede bir şifresiz `hiz_siniri` gider; bir saniyeden uzun kesilmeyen aşım serisi 10 sn sürerse kapanır. Telefon 10 frame/sn, ~1,9 MiB/dk ve 128 frame/2 MiB bounded queue kullanır. `peer_joined/peer_left` yalnız ipucudur, E2EE yetkisi değildir.

### 5.5 Büyük arşiv, tutarlılık ve A4

Yeni `TezgahWebRepository` mevcut helper hesaplarını kullanır; gereken sayfalama sorguları yeni katmanda owner+bound parametrelerle yazılır. Keyset cursor `(created_at,id)` ve filtre/snapshot bağını içerir; cursor telefon tarafından doğrulanır. Varsayılan sayfa 100, üst sınır 200; UTF-8 iç payload hedefi 32 KiB, şifreli frame üst sınırı 64 KiB; tek büyük detay için parçalı yanıt, toplam boyut ve süre sınırı gerekir. 2.000/100.000 kayıt kesilmesi tamamlanmış ekstre sanılmaz; `complete` ve `nextCursor` zorunludur. Arama için mevcut Java fold davranışının SQLite'a taşınması eşdeğerlik testine bağlıdır; SQLite LOWER Türkçe eşleştirmeye otomatik eşdeğer değildir.

Çok sayfalı ekran/yazdırma için telefonda kısa read transaction içinde filtrelenmiş **sabit DTO snapshotı** oluşturulur; sonra transaction bırakılır. Snapshot yalnız telefon RAM'inde en çok 60 sn ve 10 MiB tutulur; aşarsa kullanıcı tarih/kişi aralığını daraltır. Sayfalar aynı snapshot token'ından okunur; bu sürede yeni ödeme toplamı araya karışmaz. Restore/owner değişimi token'ı iptal eder. Büyük sonuç parçalanırken bütünlük ve toplam bayt sayısı kontrol edilir. Gzip ilk sürümde gerekli değil; eklenirse açılmış boyut sınırı ayrıca konur.

A4 yazdırmada `print_snapshot` salt okuma isteği ekran türü, filtre ve opsiyonel zId/üretici anahtarı alır; telefon tek snapshot'ın toplam ve sayfalarını üretir. Tarayıcı tüm sayfalar gelmeden yazdırmayı açmaz. Yerel HTML `@page size:A4`, yazdırmada başlık tekrarı, sayfa numarası ve taşma kontrolü; baskıda kaynak telefon, kapsam, Z numarası, veri zamanı ve “Bilgi amaçlıdır, mali belge yerine geçmez” açıklaması. Uzun ad/çok satır/Türkçe karakter/boş sonuç/A4 portre-yatay durumları test edilir. Yazıcı/PDF dosyası kullanıcı cihazındadır, relay'e yüklenmez. Oturum kapatılınca DOM ve bellek temizlenir; yazıcının kuyruğu veya kullanıcının kaydettiği PDF silinemez.

## 6. Tehditler ve önlemler

| Tehdit | Önlem | Kalan sınır |
|---|---|---|
| QR fotoğrafının çalınması | 90 sn ömür, tek tüketim; B public key optik sabit; ECDH ve açık telefon onayı; S relaya/loga gitmez; **SAS yok** | §4.3'teki tehdit modeliyle gerçek telefon verisini okuyamaz; kendi P'siyle browser'a sahte veri gösterebilir veya eşleşmeyi bozabilir. Tüm QR'ı saldırgan B'siyle değiştirmek/XSS/özel anahtar ele geçirmek ayrı tehdittir. |
| Eski QR / mesaj tekrarı | Sunucu atomik consume/expiry, pending yenilenince iptal, yön/epoch/seq kontrolü; yeniden bağlantıda yeni anahtar | Sistem saati/sunucu kaybı güvenli kapanmaya yol açabilir; availability garantisi yok. |
| Yetkisiz relay bağlantısı | 256 bit rol capability ve tahmin edilemeyen oda kimliği, token hash kontrolü, auth timeout, hız/boyut/oda sınırı; browser origin kontrolü ek katman | Origin taklit edilebilir; capability ve E2EE'nin yerine geçmez. Dağıtık DoS ücretsiz kotayı tüketebilir. |
| Telefon kaybı | Güçlü cihaz kilidi; bağlantı onayında sistem cihaz kimliği/biometrik doğrulama önerisi; anahtarlar RAM'de, süreç ölümü/çıkışta silinir; oturum en fazla 8 saat | Kilidi açık ele geçirilen yetkili telefon zaten yerel veriyi okuyabilir. Telefon olmadan başka cihazdan anlık iptal ilk kapsamda yok; bunu varmış gibi sunmayız. |
| Ortak bilgisayarda açık oturum | Kalıcı localStorage/IndexedDB oturum/veri yok; 5 dk hareketsizlikte DOM/anahtarlar temizlenir, yeni QR; görünür çıkış, telefon listesinde anlık iptal, en çok 8 saat oturum | Ekran fotoğrafı, indirilen çıktı, kötü amaçlı eklenti geri alınamaz. Arkada açık sekme ile güvenli kilit timer'ı test edilir. |
| Kötü niyetli başka web sitesi | Sabit HTTPS domain, CSP (`default-src self`, connect-src yalnız relay, script nonce/hash), frame-ancestors none, no third-party analytics/script; kullanıcı verisi textContent ile | Gerçek web uygulamasının sunumu veya bağımlılığı ele geçirilirse sayfa plaintext/anahtarlara erişebilir. E2EE kötü niyetli uç noktayı korumaz. |
| Relay'in içerik değiştirmesi / MITM | QR'da sabit B, transcript'e bağlı PSK+ECDH, finished MAC'leri, GCM AAD ve telefon onayı; özel anahtarlar uçlarda | Relay mesaj kesebilir/geciktirebilir ve trafik uzunluk/zamanını görebilir. |
| Hesaplar arası sızıntı / SQL injection | Owner yalnız telefondan, her sorguda owner filtresi, doğrulanmış id/cursor, SQL komutu kabul edilmez, logout/restore iptali | Adaş üreticiler mevcut ad tabanlı modelde birleşebilir; protokol yeni kimlik sistemi varmış gibi davranmaz. |
| XSS ile A4/ekstre sızması | Üretici/notları HTML olarak eklememe, sıkı CSP, dış font/QR CDN yok; yazdırma sayfası aynı güvenli uygulamada | Güvenilmeyen tarayıcı uzantıları kapsam dışı. |
| KVKK / logda veri | İçerik ve ciphertext için sıfır kalıcı saklama, log payload yasağı; minimum ekran alanları, telefon varsayılan gizli; kullanım/iptal açıklaması | Sağlayıcının bağlantı metadata'sı ve ülke dışı işlem koşulları ayrıca incelenmeli; hukuk uygunluğu doğrulanmadı. |

Telefonun “Bağlı bilgisayarlar” listesi bilgisayar adı, izinler, son bağlantı ve “Çıkış yaptır / tümünü kapat” sunar. Ad browser tarafından iddia edilir; güvenli cihaz kimliği veya fiziksel konum doğrulaması gibi gösterilmez. Hassas üretici telefonu listede varsayılan gönderilmez; yalnız ekstre ayrıntısı ve kullanıcı ihtiyacında açılır.

## 7. Uygulama adımları 2–6

Adım 2 temel dosyaları oluşturuldu; 3–6 gelecek kapsamdır. Canlı Worker kurulumu/pilot ölçümü adım 2'nin host testlerinden ayrı kabul kapısıdır. Süreler tek geliştiricinin yaklaşık aktif iş günüdür; güvenlik/Play incelemesi, gerçek cihaz erişimi ve kullanıcının geri bildirim bekleme süreleri dahil değil. Her adım sonunda kapsam durumu ve test çıktısı raporlanır.

| Adım | Kapsam / dokunulacak dosyalar | Yeni testler ve kabul şartı | Tahmini süre |
|---|---|---|---|
| 2 — Protokol ve relay temeli | `cloud/tezgah-relay/{worker.js,wrangler.toml,KURULUM.md}`, saf Java Crypto/Protocol, `web/tezgah/js/{crypto.js,protocol.js}`, ortak test vektörleri | `tests/web_crypto/`, `tests/web_relay/`: RFC5869, Java↔JS, tag/AAD/key/epoch/replay, QR B sabitleme, resume root+nonce, atomik consume, hibernation metadata, süre/rol/hız/boyut/log. **Uygulandı; host sonuçları ayrı raporda.** Gradle/manifest yok. Canlı kurulum ve 10 saat pilot doğrulanmadı. | İlk tahmin 4–6 gün; canlı ölçüm ayrı |
| 3a / 3b — Telefon / ilk web demosu | **3a uygulandı:** telefonda eşleşme/onay + bağlı cihazlar/servis + **tek görünüm: açık dönem ve takvim alımları**. **3b bekliyor:** En basit web HTML sayfası/QR/socket adapter. Yeni TezgahWebService/Client/Sessions/DevicesActivity ve dar TezgahWebRepository; TezgahHomeActivity, manifest, Gradle/BuildConfig (`TEZGAH_RELAY_URL`), logout/wipe/restore kapanışları. | Yeni `tests/web_sessions/`, `tests/web_service/`, `tests/web_repository/`: onaysız veri yok; public key QR'dan sabit; canlı telefon↔özel domain↔PC açık dönem; API24/34/35/36 ve Honor ekran kapalı/mobil veri. Eski Channel resume'da kapanır; credential yenilenir, iptal çalışır. | 4–6 gün |
| 4 — Diğer görünümler | Alım defteri/ekstre, cari, akşam hesabı, Z listesi; repository/DTO ve mutation invalidation kapsamı, büyük arşiv sayfalama; web görünüm modülleri | `tests/web_repository/` genişler: owner, 2.001+ kayıt, adaş/anonim, ödeme çift saymama, Z anı/güncel ödeme, arama/borç eşdeğerliği ve tüm mutasyonlar. Bugünün ilk demosu korunur. | 3–5 gün |
| 5 — A4 ve sertleştirme | Yerel HTML/CSS yazdırma, print_snapshot, CSP/header'lar, idle lock/limitler ve kota/pil pilotu | `tests/web_print/`, `tests/web_e2e/`: aynı snapshot, uzun Türkçe/çok sayfa A4, kopuk veri, XSS, RAM/DOM temizliği, quota dolarken yerel işlemlerin korunması | 3–5 gün |
| 6 — Bilgisayardan kayıt | TezgahWebCommands, owner bağlı idempotency/denetim tablosu; dar alım/ödeme/cari komutları, ayrı telefon onayıyla write; ilk dilimde Z kapatma/silme hariç | `tests/web_write/`: çift ödeme/tekrar requestId/transaction rollback/revision çakışması, eşzamanlı yerel-web ödeme ve Z kilidi | 5–8 gün |

Toplam tahmini aktif efor: **22–35 iş günü**. İlk ürün aşaması adım 5 sonunda; adım 6 ayrı kapsamdır. Mevcut `TezgahPayments.pay()` tutar okumasını transaction başlamadan yapıyor (`:41–56`); yeni eşzamanlı yazma aşamasında kayıp güncelleme yarışı için telefon tek yazım kuyruğu ve transaction sınırı yeniden incelenmelidir. Salt okuma aşaması bu davranışı değiştirmez.

Yeni kod yazılan her adımda **tests/ altındaki bütün run.sh ve check*.py kontrolleri** yeniden çalıştırılır; sadece yeni testleri çalıştırmak yeterli değildir. Mevcut `tests/tezgah/run.sh`, `tests/cloud/run.sh`, auth/security/account/role ve Worker testleri özellikle regresyon kapsamındadır. Check dosyaları argüman/çalışma dizini açısından okunarak çalıştırılır; başka run.sh içinden çağrılması tek başına atlama nedeni olmaz, kapsam kayıt edilir. Sonra kullanıcı Termux'ta `bash gradlew :app:assembleRelease` çalıştırır ve gerçek cihaz testini yapar. İmza/yerel yapılandırma yoksa derleme “doğrulanmadı” olarak raporlanır. Adım 2'de istenen bütün mevcut test giriş noktaları denendi; sonuç ve ortam eksikleri `ADIM2-TEST-SONUCLARI.md` içinde ayrı sayılır. APK derlemesi/gerçek cihaz testi yapılmadı; Gradle ve manifest değişmedi. `cloud/zeytin-ai` ve önceki üretim kaynakları diff kontrolüyle korunur.

## 8. Açık sorular ve doğrulanmayan noktalar

1. Repo adı farkı: çalışma dalı ve Tezgâh kaynaklarının eşleşmesi nedeniyle Recovery seçildi. Eski private `Zeytin-Defteri` kaynak dalı olarak kullanılmadı; kullanıcının bunun dışında farklı depo kastı doğrulanmadı.
2. Gerçek Cloudflare/Supabase hesap planı, mevcut diğer modüllerin kota tüketimi ve panelin SQL okuma kotası doğrulanmadı. Ücretsiz servis kotaları değişebilir; uygulama öncesi yeniden kontrol gerekir.
3. `web.zeytindefteri.com` alan adının sahipliği, DNS erişimi ve mevcut hosting durumu doğrulanmadı. Mevcut kayıtlı alan adında alt alan eklemenin dışında yeni alan adı satın almak gerekirse “tamamen ücretsiz” varsayımı sağlanmaz; bu relay için özel alan adı üretim varsayımıdır. Bildirilen Türk mobil operatör `workers.dev` timeout'u nedeniyle bu adres üretim alternatifi sayılmaz; yalnız geliştirmede kullanılır. Detaylar KURULUM.md'dedir.
4. connectedDevice ağ üzerinden eşli bilgisayar kullanımına uygun öneridir; bu özel relay senaryosunun Play kabulü doğrulanmadı. dataSync geri dönüşünün altı saat sınırı ürün açısından kabul edilecek mi? Karar yayın öncesi gerekli; bu belge sınırsız servis vaat etmez.
5. Sekme yenilemede QR, beş dakika hareketsizlikte çıkış, sekiz saat azami oturum ve iki bilgisayar sınırı ürün varsayımlarıdır. Güvenli ilk varsayılan olarak seçildi; kullanıcı tercihiyle değişebilir.
6. Trafik, GB-s, APK artışı ve iş süreleri **tahmindir**; benchmark, APK veya gerçek kullanıcı ölçümü yapılmadı. Heartbeat maliyeti ve DO hibernation overhead'i ölçülmeden kapasite sözü verilmez.
7. OkHttp 5.5.1 resmî sürüm olarak doğrulandı; bu projede Gradle/Android 24 derlemesi ve transitif APK boyutu doğrulanmadı. Ayrıntı 3a raporundadır.
8. E2EE çekirdeği uygulanıp Java↔Node WebCrypto host testleri yapıldı; bağımsız denetim ve gerçek API24↔Chrome/Edge/Firefox/Safari uyumu doğrulanmadı. Hostta resume/replay yarışları test edildi; gerçek soket lifecycle'ı adım 3'te doğrulanacak. Geçici anahtarları Java/JS yönetilen belleğinden fiziksel olarak tamamen sıfırlama garanti edilemez; referanslar sonlandırılır ve kalıcı depolama engellenir.
9. Mevcut üretici ad eşleştirmesinin adaşları ayırması mümkün değil; kalıcı üretici kimliği ayrı veri modeli çalışması olur. Kapsamda ad temelli mevcut davranış korunur.
10. KVKK açısından yurtdışı aktarıcı ve platform bağlantı metaverisinin hukuki şartları doğrulanmadı. İçerik şifrelemesi hukuki incelemenin yerine geçmez.
11. QR fotoğrafı tehdidinde gerçek telefonun SQLite verisi korunur; adım 6'da browser'dan girilen yeni verinin sahte telefona gitmemesi için ek kimlik/güven kararı gerekir.
12. Telefon kaybında başka bir cihazdan merkezi acil iptal, telefon çevrimdışıyken bilgisayarda çalışma ve haftalarca hatırlanan oturum ilk ürün kapsamına dahil değildir. Yeni kapsam istenirse veri/anahtar saklama tasarımı yeniden değerlendirilir.

## 9. Resmî kaynaklar

Kontrol tarihi: 2 Kasım 2026. Dokümandan doğrulanan kurallar ile bu belgenin tasarım/tahmin kararları yukarıda ayrı ifade edilmiştir.

- **[K1]** [Cloudflare Durable Objects fiyatlandırması](https://developers.cloudflare.com/durable-objects/platform/pricing/) — Free SQLite koşulu, günlük istek/süre/SQL sınırları, 20:1 WS hesabı, limit aşımı.
- **[K2]** [Cloudflare Workers limitleri](https://developers.cloudflare.com/workers/platform/limits/) — ön Worker'ın hesap düzeyi istek ve CPU sınırları.
- **[K3]** [Cloudflare DO WebSockets / Hibernation](https://developers.cloudflare.com/durable-objects/best-practices/websockets/) — idle uyku, bağlantı attachment'ları ve yeniden kurulum.
- **[K4]** [Supabase fiyatlandırması](https://supabase.com/pricing) — Free Realtime/egress/proje/duraklatma sınırları.
- **[K5]** [Supabase Realtime limitleri](https://supabase.com/docs/guides/realtime/limits) — bağlantı, hız ve payload.
- **[K6]** [Supabase Realtime mesaj kullanımı](https://supabase.com/docs/guides/platform/manage-your-usage/realtime-messages) — mesaj kotası ve aşım uygulaması.
- **[K7]** [Supabase Realtime yetkilendirme](https://supabase.com/docs/guides/realtime/authorization) — private channel, RLS/JWT ve yetki hesaplama.
- **[K8]** [Supabase Broadcast](https://supabase.com/docs/guides/realtime/broadcast) — istemci yayınları ile DB yayınındaki üç günlük saklamanın ayrımı.
- **[K9]** [Android foreground service tipleri](https://developer.android.com/develop/background-work/services/fgs/service-types) — connectedDevice önkoşulları, dataSync ve remoteMessaging tanımları.
- **[K10]** [Android 14 zorunlu foreground service tipleri](https://developer.android.com/about/versions/14/changes/fgs-types-required).
- **[K11]** [Android foreground service timeout](https://developer.android.com/develop/background-work/services/fgs/timeout) ve [Android 15 davranış değişiklikleri](https://developer.android.com/about/versions/15/behavior-changes-15) — altı saat ve onTimeout/boot kısıtları.
- **[K12]** Android API: [KeyAgreement](https://developer.android.com/reference/javax/crypto/KeyAgreement), [Cipher](https://developer.android.com/reference/javax/crypto/Cipher).
- **[K13]** [Maven Central OkHttp 4.12.0](https://central.sonatype.com/artifact/com.squareup.okhttp3/okhttp/4.12.0) — artefakt arama sonucu doğrulandı; doğrudan sayfa erişimi timeout verdi. [Square kaynak README](https://github.com/square/okhttp/blob/master/README.md) — Java/Kotlin ve Android desteği için resmî kaynak arama sonucu; seçilen sürümün tam doğrulamasının yerine geçmez.
- **[K14]** [W3C Web Cryptography API](https://www.w3.org/TR/webcrypto-1/) — ECDH/P-256, HKDF, AES-GCM.
