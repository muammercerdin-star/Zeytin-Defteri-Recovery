# Adım 4 — yazma envanteri ve komut sözleşmesi

İnceleme tabanı: `02c7529422fd6e33269fe6970bc3eb3bf074a1d3`, `work/web-baglanti`. Tarih: 3 Kasım 2026. Satırlar bu başlangıç sürümüne aittir. Canlı Worker adresi, wrangler.toml, config.js ve _headers korunacaktır.

## 1. Mevcut yazma işlemleri envanteri

Dosyalar aksi belirtilmedikçe `app/src/main/java/com/zeytindefteri/app/` altındadır.

| İşlem | Mevcut sınıf/metot ve satır | Girdiler ve mevcut doğrulama | Geri alınabilirlik |
|---|---|---|---|
| Alım ekleme | TezgahActivity.savePurchase:1644 → TezgahPurchases.add:73 | Tarih, üretici adı/telefon, TezgahModel (6 elek, fiyatlar, dara, tartım satırları), başlangıç ödemesi. Net >0; tarih boş olamaz; ödeme 0–toplam arasına kırpılır. TezgahModel.addError:51 tartım >0, ≤20.000 kg, kasa 0–200, kantar > toplam dara; setTare:139 0–10 kg. UI eksik fiyatı sorar. | Açık alım silinebilir; Z'li alım silinemez. |
| Alım düzenleme | Kayıtlı alım için **mevcut metot yok**. TezgahActivity.editGrade:1820 yalnız henüz kaydedilmemiş tartımı düzenler. | Yeni ortak update metodu gerekli. Z kilidi, sonraki ödemeler ve toplam ödenen korunmalı; yeni tutar ödenenin altına düşmemeli. | Eski değerler yeniden girilebilir; geçmiş kayıt düzenleme telefon onaylı olacak. |
| Alım silme | TezgahDayActivity.actions:1122 → TezgahPurchases.delete:187 | Owner + id; yalnız z_id=0. Aynı transaction'da bağlı ödemeler ve eski alıma bağlı kasa hareketleri de silinir. | Geri al düğmesi yok; yedek gerekir. Telefon onayı zorunlu. |
| Üretici seçme/ekleme | TezgahActivity.renderWizardSeller:1392 / askSeller:1085 | İsim/isteğe bağlı telefon; liste alım isimlerinden çıkar. Boş ad münferit satıcıya dönebilir. **Bağımsız üretici tablosu/kart kaydetme metodu yok.** | Yeni ortak üretici kataloğu eklenmeli; mevcut isimlerle eşleşme korunmalı. |
| Tekil ödeme | TezgahPaySheet → TezgahPayments.pay:37 | Owner, alım id, tutar, gün, zaman. Kalan borçla sınırlı; >0,009 TL. Z'li alıma da ödeme yapılabilir. Ödeme geçmişi + amount_paid beraber yazılır. | Ödeme geri alma metodu yok. Açık alımı silmek bağlı geçmişi de siler. |
| Toplu ödeme | TezgahStatementActivity.askBulkPay:184; TezgahStatement.allocate:71; TezgahPayments.pay:37 | Üretici ve tutar; kalan +0,5 TL UI toleransı. En eski borçtan dağıtılır; dış transaction içinde tümü/hiçbiri. | Tekil geri alma yok; telefon onayı zorunlu. Orkestrasyon ortak payBulk metoduna çıkarılacak. |
| Fiziksel kasa giriş/çıkış | TezgahCratesActivity → TezgahCrates.add:84 / delete:101 | Aracı adı, GAVE/RETURNED, adet 1–5.000, tarih, not. Münferit/boş kişi reddedilir. | Hareket silinebilir; web silme telefon onaylı. |
| Para giriş/çıkış, cari tahsilat/ödeme | TezgahLedgerActivity:569 → TezgahLedger.add:74 | Kişi, RECEIVED/PAID, tutar 0,01–100.000.000 TL, tarih/not. NaN/Infinity reddi. Ayrı bir nakit kasa tablosu **yok**; para hareketleri cari tahsilat/ödeme yoludur. | Cari satır silinebilir; web silme telefon onaylı. |
| Cari satış | TezgahLedgerActivity:472–475 → TezgahLedger.add:74 | Numara kg×fiyat, toplu kg×fiyat veya kg×komisyon; yöntem, kişi, not, şimdi tahsil edilen. Hesap TezgahLedger.gradeAmount/times:189/202. Mevcut UI iki ayrı add çağırıyor. | Satış/tahsilat satırları ayrı silinebilir. İkili yazma ortak atomik recordSale metoduna çıkarılacak. |
| Akşam hesabı düzenleme | TezgahDayActivity.saveClosing:494 → TezgahClosing.save:188 | 6 teslim kg, kg başı komisyon, sabah avansı; açık dönem OPEN_KEY. Alınan kg/tutar telefon alımlarından, hesap TezgahClosing.fromPurchases:34 / traderOwes:92. | Açık dönemde yeniden kaydedilebilir. |
| Z / dönem kapatma | TezgahDayActivity.confirmDayEnd:440 → TezgahZ.close:60 | Açık alımlar ve akşam hesabı; boş dönem kapanmaz. Z özeti yazılır, alımlar Z'ye taşınır, açık closing silinir. **Z ve dönem kapanışı aynı mevcut işlemdir.** | Z geri açma metodu yok; telefon onayı zorunlu. |
| Z'yi cari hesaba işleme | TezgahDayActivity.traderSheet:778–781 → TezgahLedger.add | Z, tüccar, telefonun hesapladığı traderOwes, teslim kg, anlık ödeme/tahsilat. İade işareti hesaplanır; hasZ ile tekrar kontrolü UI'dadır. | Cari satırları silinebilir; Z değişmez. Ortak atomik recordDay metodu gerekli. |
| Yerel yedek / bulut yedeği | TezgahBackup.export/read; TezgahCloudBackup.backup:173 | Owner kapsamlı tablo listesi; mevcut telefon tercihleri, sıkıştırma ve Firestore kullanıcı yetkisi. Bulut callback gerçek başarıyı bildirir. | İş verisini değiştirmez. Web yalnız telefondaki yedeklemeyi başlatabilir; yedek içeriği bilgisayara gönderilmez. |
| Yedekten geri yükleme | TezgahBackup.restore:262; TezgahCloudBackup.restore:347 | Geçerli yedek/owner; transaction ve sonrasında bağlantı reset sinyali. | Mevcut veri değişir; yalnız telefonun mevcut akışı. **Web komutu her yetkide reddedilir.** |
| Taslak/tercihler | TezgahActivity model/prices/tare ve TezgahDayActivity komisyon tercihleri | Telefon SharedPreferences; henüz kaydedilmemiş form taslağı ve varsayılanlar. | Telefon ekranından değişir; web formu telefonun taslağını ezmez. |

## 2. Yetki ve değişmeyen sınırlar

Telefon eşleşmesinde `read` (varsayılan) / `full` seçilir. Yetki room/owner oturumuna bağlıdır; QR veya web komutu yetki veremez. Bağlı bilgisayarlar listesi yetkiyi ve son işlem izini gösterir. Telefonda düşürme derhal yeni yazmayı durdurur ve bekleyen onayları iptal eder. Resume aynı yetkiyi korur; yeni QR dışında yükseltme yoktur.

Owner yalnız telefon oturumundan alınır. Hesap çıkışı, silme veya geri yükleme bağlantıyı ve bekleyen işleri kapatır. Mevcut şifreleme/IV/epoch/seq kuralları aynıdır. Relay yalnız ciphertext taşır; komut içeriği veya kullanıcı verisi ek bir sunucuya yazılmaz. Yeni bağımlılık/SDK/AGP değişikliği yoktur.

## 3. Şifreli komut ve cevap

Örnek iç payload (mevcut encrypted zarfı içinde):

```json
{"v":1,"type":"komut","id":"rastgele_16_bayt_base64url","tur":"odeme","alanlar":{"alimId":"12","tutar":"250.00"}}
```

```json
{"v":1,"type":"komut_sonucu","id":"rastgele_16_bayt_base64url","durum":"tamam","hata":"","ozet":{"mesaj":"Ahmet Yılmaz için 250,00 ₺ ödeme kaydedildi.","kayitId":"12"}}
```

Durumlar `tamam`, `reddedildi`, `onay_bekleniyor`, `onaylandi`, `iptal`. `onaylandi` yalnız telefon onayının alındığını bildirir; SQL commit başarısı için `tamam` beklenir. Onay penceresi 60 sn; reddetme/timeout/yetki düşürme/oturum bitişi `iptal` verir. Komut id'si tarayıcıda kriptografik rastgele 16 bayttır; tekrar gönderimde aynı id ve alanlar kullanılır. Aynı id farklı içerikle kullanılırsa reddedilir.

Komut türleri: `uretici_ekle`, `alim_ekle`, `alim_duzenle`, `alim_sil`, `odeme`, `toplu_odeme`, `kasa_giris`, `kasa_cikis` (para/cari), `cari_tahsilat`, `cari_odeme`, `cari_satis`, `cari_sil`, `kasa_adet_giris`, `kasa_adet_cikis`, `kasa_adet_sil`, `aksam_hesabi`, `z_kapat`, `donem_kapat` (aynı Z yolu), `z_cariye_isle`, `yedek_al`. `yedek_geri_yukle` koşulsuz reddedilir.

Ek okuma istekleri üretici arama, alım detayı, cari hareketler, kasa hareketleri, akşam hesabı ve Z listesiyle form seçeneklerini doldurur. `komut_sonucu` okuması bağlantı kopunca son durumu sorgular; bilgisayar sonucunu tahmin ederek yeni kayıt yaratmaz. Güncel izinler şifreli `onaylandi`/`yetki_degisti` mesajıyla gelir.

## 4. Tek kayıt, doğrulama ve eşzamanlılık

- Telefon `tezgah_web_commands` tablosunda owner+oturum+id, içerik hash'i ve sonucu tutar; iş mutasyonu ve `tamam` sonucu **aynı SQLite transaction'ında** commit edilir. Çökme arası çift kayıt boşluğu bırakılmaz. Bu tablo bulut yedeğine girmez; kripto anahtarı içermez.
- Aktif oturumda en fazla 2.048 id; id'ler aktif oturum sırasında düşürülmez. Limit dolduğunda yeni komut reddedilir ve yeni eşleşme gerekir. Böylece evrilmiş bir son-N kuyruğundan atılan eski id'nin ikinci kez yazılma açığı oluşmaz. Yeni oturum eski oturum kayıtlarını temizleyebilir; eski anahtarlar o oturumda kullanılamaz.
- Komut yürütücüsü tek istemci executor'ında sırayla çalışır. Kritik okuma+değiştirme ortak sınıfların SQLite transaction'ına alınır; telefon ekranının farklı bağlantıdaki yazısı da SQLite tarafından sıralanır. Düzenleme/silme için telefonun ürettiği kayıt fingerprint'i istenir; beklerken değişen kayıt reddedilir. Z/toplu ödeme onayı öncesindeki kapsam da yeniden doğrulanır.
- Bütün JSON alanları allowlist ile kontrol edilir. Sayılar wire'da noktalı ondalık metindir; JS Türkçe `1.140,5` girişini `1140.5` yapar, telefon tekrar sınır ve tür doğrular. Owner, gross, bakiye, toplam, z_id gibi hesaplanan alanlar istemciden kabul edilmez.
- İsim ≤100, telefon ≤32, not ≤500 karakter; tartım sayısı ≤100; elek 0–5, kasa 0–200, tartım ≤20.000 kg, toplam net ≤2.000.000 kg, dara 0–10, fiyat ≤100.000 TL/kg, toplam para ≤100.000.000 TL. Tutar/kg hiçbir koşulda NaN/Infinity olamaz. Mevcut model hesabı kullanılır; web'e yeni hesap otoritesi verilmez.
- Telefon hız sınırı: saniyede 3 yeni komut; yinelenen id sonucu yeni kayıt değildir. Onay bekleyen iş sayısı sınırlıdır. Taşkında Türkçe `reddedildi` cevabı döner.
- Başarı sonrası mevcut commit-sonrası değişiklik sinyali kullanılır. Telefonda bilgisayar adı + yapılan işin kısa izi gösterilir. Başarısız/rollback yazısı başarı olayı üretmez.

## 5. Telefon onayı ve web davranışı

Silme (alım/cari/kasa hareketi), Z/dönem kapatma, toplu ödeme, geçmiş tarihli alım düzenleme telefon onaylıdır. Z kilidi onayla da aşılamaz. Bildirim Onayla/Reddet aksiyonları veya Tezgâh ana ekranındaki onay diyaloğu yalnız mevcut owner+room+komut id'si için geçerlidir. Bildirim izni kapalıysa kullanıcı telefonun Tezgâh ana ekranından bekleyen isteği görebilir. Geri yükleme web'de hiç uygulanmaz.

Web formları yalnız full yetkide görünür. Enter submit, anlamlı Tab sırası, üretici arama/ekleme, altı elek ve çoklu tartım; ödeme/cari/para-kasa/fiziksel-kasa, kapanış ve telefon yedeği işlemleri. Pending komutlar RAM'dedir; otomatik tekrar aynı id ile yapılır. Sayfa yenileme yeni oturumdur. innerHTML, kalıcı tarayıcı depolaması, dış kaynak yok; CSP/config/relay ayarları korunur. 5 dk idle kilidi tüm form alanlarını ve bekleyen komut belleğini de temizler.

## 6. Test ve teslim sırası

1. Bu envanter/protokol commit + push.
2. Ortak mutasyonlar, telefon komut motoru, yetki/onay/iz entegrasyonu commit + push.
3. Web formları ve protokol adaptasyonu commit + push.
4. Gerçek SQLite transaction'lı Java testleri (mevcut no-op transaction stub'ı tek başına atomiklik kanıtı sayılmaz), JS form/XSS/yetki ve Java↔sayfa simülasyonu; tüm eski testler. Son rapor ve commit + push.

Henüz uygulanmayan adımlar tamamlanmış sayılmaz. Gerçek Android/Cloudflare/cihaz testi çalıştırılamazsa açıkça raporlanacaktır.
