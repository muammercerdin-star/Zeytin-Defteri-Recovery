# ZEYTİN DEFTERİ — İŞ EMRİ 01 RAPORU

Tarih: 9 Eylül 2026

## Sonuç ve doğrulama sınırı

Bahçe Detay ekranındaki Not Ekle, Bahçe Düzenle ve Tüm İşlem Geçmişi placeholder akışları
çalışan yerel veri akışlarıyla değiştirildi. Üç eski placeholder mesajı kaldırıldı.
4 mevcut ürün dosyası değiştirildi; 2 yeni Activity eklendi. Sıfırdan proje kurulmadı.

Yeni testte **52 gerçek SQLite kontrolü PASS**. Baseline'ın 13 test komutu son turda
başarıyla tamamlandı. Functions: **30 PASS, 0 FAIL, 4 SKIP**; SKIP sonuçları PASS değildir.

**Gerçek Android APK build doğrulanamadı. Gerçek cihaz UI/offline/lifecycle ve canlı cloud
senkronizasyon testleri yapılmadı.** Bu nedenle tüm cihaz kabul kriterleri doğrulanmış veya
uygulama yayın için tamamen onaylanmış sayılmaz. Kaynak teslimi ve host testleri tamamlandı;
Android build/cihaz doğrulama kapısı açık kalmıştır.

## 1. Başlangıç ana kaynağı

Yalnız `Zeytin-Defteri-RELEASE-BASELINE-00-FINAL.zip` kullanıldı.

SHA-256:
`4fba2e26ce637c9a8bbdbd27a5fcdcac188cded3ad1b400fbe26fb287352efdd`

ZIP CRC/bütünlük kontrolü geçti. Bu hash önceki baseline mühürüyle birebir eşleşti.
Mesaja ekli `Zeytin-Defteri-FINAL-Zeytin-Ligi-Pixel-Close-V2.zip` çalışma kaynağı olarak
kullanılmadı; bulunan doğru baseline üzerinde çalışıldı.

## 2. Mevcut mimari ve veri akışı

- Detay ekranı: `com.zeytindefteri.app.GardenDetailActivity`.
- Layout: `activity_garden_detail.xml`; layout dosyası değiştirilmedi.
- Bahçe kimliği: Intent içindeki `garden_id`, long olarak okunuyor.
- Bahçe okuması: `DatabaseHelper.getGarden(long)` mevcut active-owner filtresine sahip.
- `GardenRepository` içinde mevcut `updateGarden(...)` metodu vardı; yalnız ID filtresiyle
  güncelliyordu. Aynı public int dönüşlü API korundu, güvenli helper güncellemesine devredildi.
- Not modeli zaten `garden_notes` tablosu ve `addGardenNote/getGardenNotes` metotlarıydı.
  Aynı tablo/metotlar kullanıldı; ikinci not sistemi kurulmadı.
- İşlemler `operations` tablosunda; eski `getGardenOperations(long)` korunuyor. Detay/geçmiş
  için eklenen limitli overload aynı helper içindeki owner/sezon kurallarını kullanıyor.
- Mevcut genel `OperationHistoryActivity` bahçe değiştirilebilir filtre ve genel navigasyon
  içeriyor. Onu değiştirmek yerine sadece seçilen bahçeye ait salt-okunur küçük bir Activity
  eklendi; genel işlem geçmişine ve alt navigasyona dokunulmadı.
- SessionManager aktif UID ve aktif sezon kaynağıdır. Firebase bağlantısı yerel işlemlere
  önkoşul yapılmadı. Ekranlar açıldıkları owner oturumunu saklar; owner değişirse kapanır.

### gardens gerçek alanları

`id`, `name`, `tree_count`, `age`, `variety`, `area_decare`, `province`, `district`,
`neighborhood`, `irrigation_type`, `notes`, `photo_uri`, `created_at`, `owner_uid`,
`cloud_id`, `updated_at`.

Bahçelerde season_year yoktur: bahçe kullanıcıya ait kalıcı kayıttır. Konum alanları
il/ilçe/mahalle metinleridir; şemada bulunmayan koordinat/konum-ID alanları eklenmedi.

### garden_notes gerçek alanları

`id`, `garden_id`, `note_date`, `note`, `photo_uri`, `created_at`, `updated_at`,
`owner_uid`, `cloud_id`. Not tablosunda season_year yoktur; notlar mevcut modele uygun
olarak kullanıcı+bahçeye bağlıdır, sezon değiştirince gizlenmez.

### Bağlı kayıtlar

| Kayıt | Mevcut ilişki | Bu görevde durum |
|---|---|---|
| operations | garden_id → gardens.id | Satırlar/kimlikler korundu |
| harvests | garden_id → gardens.id | Korundu |
| harvest_grades | harvest_id → harvests.id | Korundu |
| elek_records | garden_id → gardens.id | Korundu |
| elek_grades | elek_id → elek_records.id | Korundu |
| sales | garden_id → gardens.id | Korundu |
| sale_grades | sale_id → sales.id | Korundu |
| brine_batches | garden_id → gardens.id | Korundu |
| brine_events | batch_id → brine_batches.id | Korundu |
| garden_notes | garden_id → gardens.id | Mevcut notlar korundu |
| reminders | garden_id → gardens.id | Korundu |
| receivables | related_sale_id → sales.id | Korundu |

## 3. Not Ekle

Mevcut detay butonu koyu temalı bir not giriş penceresi açar. Kaydet, önce SQLite'a yazar;
başarıdan sonra pencere kapanır ve notlar yeniden okunur. Boş/yalnız boşluk içeren not
reddedilir. Geçersiz veya başka kullanıcıya ait bahçeye not yazılamaz.

Bahçe varlığı/owner kontrolü ve insert transaction içindedir. garden_id long tutulur.
Tarih `dd.MM.yyyy HH:mm`; created_at/updated_at mevcut milisaniye standardındadır.
Mevcut owner/cloud_id üretim standardı korunur. Not okuma hem bahçe hem not owner'ını
kontrol eder. Guest için mevcut null/boş/trim-empty owner kuralı kullanılır.

Yeni not, gerçek SQLite dosyası yeniden açılarak testte okundu. Genel bahçe notu
`gardens.notes` ile tarihli `garden_notes` birbirinden ayrı mevcut alanlardır; birbiri
üzerine yazılmaz. Not giriş taslağı Activity state'e alınır.

## 4. Bahçe Düzenle

Yeni `GardenEditActivity`, mevcut kaydı helper üzerinden owner filtresiyle yükler.
Ad, ağaç sayısı, yaş, çeşit, dekar, il/ilçe/mahalle, sulama tipi, genel not ve fotoğraf
mevcut alanlardan düzenlenir. Fotoğraf için sistem belge seçicisi ve kalıcı okuma izni
kullanılır; mevcut URI değiştirilmediği sürece korunur. Fotoğraf kaldırma dosyayı silmez.

Kaydet, mevcut `GardenRepository.updateGarden` üzerinden helper'a gider.
**Sadece UPDATE uygulanır. Bahçe INSERT/DELETE/REPLACE yapılmaz.**

Primary key, owner_uid, cloud_id ve created_at update alanlarına alınmaz.
updated_at, mevcut timestamp-tabanlı sync mantığı için ilerletilir; eski timestamp
cihaz saatinden ilerideyse önceki değerden en az 1 ms büyük tutulur.
Bahçe doğrulaması ve update transaction içindedir; update WHERE koşulu da owner kapsamlıdır.
Boş ad, negatif/taşan tamsayı ve negatif/NaN/sonsuz dekar reddedilir.

Formun taslak alanları ve fotoğraf seçimi savedInstanceState ile korunur.
Kaydetmeden geri dönmek veritabanını değiştirmez. Detay onResume'da, mevcut Bahçelerim
listesi de kendi mevcut onResume akışında güncel bilgiyi okur; listede yeni kod gerekmedi.

## 5. Tüm İşlem Geçmişi

`GardenOperationHistoryActivity` yalnız seçilen bahçenin aktif sezon kayıtlarını okur.
Yeni helper overload'ı:

- `o.garden_id = seçilen ID`;
- parent `gardens.owner_uid` için mevcut active-owner koşulu;
- explicit operation owner aktif kullanıcı olmalı; başka UID varsa kayıt dışlanır;
- boş/null legacy child owner, projede mevcut ElekRepository yaklaşımı gibi owned parent
  üzerinden kabul edilir; yeni owner backfill/toplu değişiklik yapılmaz;
- `activeSeasonCondition` aynen kullanılır. season_year null/0 ise mevcut tarih başı/sonu
  yıl fallback'i korunur; yeni sezon yorumlama kuralı yazılmaz.

Sıralama mevcut created_at DESC standardıdır; eşit timestamp için id DESC eklenmiştir.
Detayın son 5 işlemi ve tüm geçmiş aynı kapsamlı overload'ı kullanır. İşlem türü,
tarih, mevcut miktar/birim/maliyet ve not gösterilir. NULL miktar/maliyet uydurulmaz.

Boş durum: **Bu sezon bu bahçe için henüz işlem kaydı yok.**

Detay içindeki sezon özeti de mevcut scoped helper okumalarına bağlandı. Önceki yalnız
 garden_id kullanan tüm-sezon toplamının aktif-sezon geçmişiyle çelişmesi önlendi.
Özet tasarımı ve hesaplama formülleri değiştirilmedi. Hasat sorgusu mevcut
`getGardenHarvests(long)` yöntemidir; hasat modülünün kendisine dokunulmadı.

## 6. DB ve veri güvenliği

- **DB_VERSION: 19 → 19. Migration yok.**
- DatabaseSchema, onCreate/onUpgrade zinciri değiştirilmedi.
- DROP TABLE, veritabanı sıfırlama, kayıt silme, child taşıma, owner/season toplu güncellemesi yok.
- ID=7 fixture bahçesi düzenleme sonrasında ID=7 olarak kaldı.
- Bağlı 12 tablonun dolu fixture satırları önce/sonra birebir aynı kaldı.
- Guest ve null-owner guest düzenleme/not okuma davranışları test edildi.
- Testler yalnız geçici fixture üzerinde çalıştı; kullanıcı veritabanı açılmadı.

## 7. Local-first ve cloud sync etkisi

Yeni ekranlarda Firebase API çağrısı, listener veya ağ önkoşulu yoktur. İşlem önce
SQLite'ta tamamlanır. Offline veri davranışı host SQLite testinde doğrulanmıştır;
Android cihazda uçak modu testi bu ortamda yapılmamıştır.

Mevcut `ProducerFeedActivity.onResume → syncPrivateCloudData → PrivateCloudSyncManager`
yolu aynen korunur. Yerel düzenleme/not sonrasında ana ekrana geri dönüldüğünde mevcut
senkronizasyon devreye girer; bu üç ekran için ek read/write döngüsü başlatılmaz.
Ana ekrana dönülmeden veya mevcut sync tetiklenmeden buluta hemen gitme garantisi yoktur.

- Bahçe güncellemesini GardenCloudRepository, updated_at/shouldUploadCloudRecord ile görür.
- Notları mevcut SupportCloudRepository `users/{uid}/gardens/{gardenCloudId}/notes/{cloudId}`
  yolunda taşır.
- sync_state ayrı tablosundaki upload/remote ilerlemesi mevcut yöneticilerce tutulur;
  yeni kod bu tabloyu veya tombstone'ları elle değiştirmez.
- Eski remote veriye karşı mevcut updated_at karşılaştırması korunur.
- Silme push/pull → restore/merge → local upload sırası değiştirilmedi.

Firebase projesi, google-services.json, Auth, rules, Functions ve tüm cloud repository
kaynakları baseline ile byte olarak aynıdır. Canlı çoklu cihaz senkronizasyonu test edilmedi.

## 8. Değiştirilen ve eklenen dosyalar

| Dosya | Neden |
|---|---|
| app/src/main/java/DatabaseHelper.java | Owner kontrollü transactional update; mevcut not API'sinin güvenli yazma/okuması; detay için scoped tarihçe overload'ı |
| app/src/main/java/GardenRepository.java | Mevcut int updateGarden API'sini güvenli helper'a devretmek |
| app/src/main/java/com/zeytindefteri/app/GardenDetailActivity.java | Üç placeholder'ı bağlamak; not UI; scoped okumalar; state/session/close davranışı |
| app/src/main/AndroidManifest.xml | İki yeni private Activity'yi kaydetmek |
| app/src/main/java/com/zeytindefteri/app/GardenEditActivity.java (yeni) | Mevcut bahçeyi yerel düzenleme formu |
| app/src/main/java/com/zeytindefteri/app/GardenOperationHistoryActivity.java (yeni) | Salt-okunur, tek bahçe/aktif sezon geçmişi |
| tests/garden_detail/check_sqlite.py (yeni) | Production metotlarını gerçek SQLite üzerinde çalıştıran 52 kontrol |
| tests/garden_detail/README.md (yeni) | Test yöntemi ve gerçek cihaz sınırları |
| tests/garden_detail/validation/* (yeni) | Önce/sonra test kayıtları ve build/statik kontrol notları |
| IS_EMRI_01_REPORT.md (yeni) | Bu rapor |

Bahçe Detay layout'u ve geri kalan tasarım korunmuştur. Yeni ekranlar mevcut Theme.App,
koyu zemin, tipografi, yeşil vurgu ve sistem çubuğu davranışlarını kullanır.
Ana Sayfa, Elek, satış, salamura, lig, sohbet, asistan, hava ve alt navigasyon ürün
kaynaklarında değişiklik yoktur. Kapsam dışı compile düzeltmesine gerek kalmadı.

## 9. Test sonuçları

Başlangıçta tüm 13 baseline komutu denendi. Elek arşiv testi `javac` launcher'ı eksik
olduğu için ilk turda çalışamadı. Ortamda var olan JDK compiler modülü için geçici,
proje dışı launcher kullanıldı; ürün değişikliği başlamadan tekrar PASS aldı.
Baseline test kodları değiştirilmedi. `before-0.log` ilk hatayı, `before-0-retry.log`
çözümü kaydeder. Son tur 14 komut (13 baseline + yeni SQLite testi) exit 0 verdi.

| Test | Önce | Sonra |
|---|---|---|
| assistant/run_host_tests.py | PASS | PASS |
| assistant_ux/run_ux_tests.py | PASS | PASS |
| elek_archive/check_archive.py | Ortam düzeltmesi sonrası PASS | PASS |
| elek_popup/check_prices.py | PASS | PASS |
| elek_ui/check_form.py | PASS | PASS |
| feed_thread/check_preview.py | PASS | PASS |
| final_assistant/run_weather_measure_tests.py | PASS | PASS |
| location/check_location.py | PASS | PASS |
| meydan/check_schema.py | PASS | PASS |
| threaded/check_roundtrip.py | PASS | PASS |
| today/SQLiteScopeTest.py | PASS | PASS |
| npm --prefix functions test | 30 PASS / 4 SKIP / 0 FAIL | 30 PASS / 4 SKIP / 0 FAIL |
| npm --prefix functions run check | PASS | PASS |
| garden_detail/check_sqlite.py | Yeni test | 52 PASS |

Yeni test gerçek DatabaseSchema DDL'ini SQLite'a uygular. Production helper metot
 gövdeleri ve repository update delegasyonu değiştirilmeden JVM'de çalıştırılır;
Android database API'leri gerçek sqlite3 bağlantısına köprülenir. Her çağrı fixture'ı
yeniden açar. Bu, yalnız kaynak metninde kelime arayan bir fonksiyon testi değildir.
Android sınıflarının tamamının veya APK'nin derlendiği anlamına gelmez.

Ek statik kontrol: 79 Java kaynağı Java 8 sözdizimiyle parse edildi, duplicate method
imzaları denetlendi; 104 XML, 28 Manifest Activity/Service hedefi ve 590 R referansı geçti.
Google Services tarafından üretilen default_web_client_id ayrı tutuldu.

## 10. Android build

Denenen gerçek komut:

```sh
java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain \
  -Dorg.gradle.java.home=/usr/lib/jvm/java-17-openjdk-amd64 assembleDebug --no-daemon
```

**Sonuç: doğrulanamadı; exit 1.** Gradle 8.7 cache'te olmadığı için indirmeye çalıştı;
`java.net.UnknownHostException: services.gradle.org` ile durdu. APK üretilmedi.

Projede mevcut `org.gradle.java.home=/home/test/java17` değiştirilmedi. Denemede yalnız
komut satırından bu ortamın Java yolu verildi. Android SDK/android.jar da çalışma
ortamında bulunamadı. Statik Java/XML kontrolü BUILD PASS olarak sunulmadı.

## 11. Kalan riskler / doğrulanmayanlar

1. Gerçek SDK ile assembleDebug ve cihazda üç akışın smoke testi yapılmalı; bu teslimde APK yok.
2. Gerçek UI ölçümleri, fotoğraf sağlayıcı davranışı, Android süreç ölümü/lifecycle ve uçak modu
   cihaz üzerinde doğrulanmadı. Draft/state kodu vardır; host SQLite testi cihaz testi değildir.
3. Firebase emulator bağımlı 4 baseline testi SKIP. Canlı cloud/çoklu cihaz testi yapılmadı;
   mevcut sync kodunun aynen kaldığı ve local sync alanlarının üretildiği doğrulandı.
4. Geçmiş ListView satırları yeniden kullanır ancak aktif sezon verisi belleğe alınır;
   çok büyük kayıt kümelerinde gerçek cihaz performansı ayrıca gözlenmelidir.
5. Görev dışında kalan eski repository API'leri refactor edilmedi; bu ekranlar denetlenmiş
   helper yollarını kullanır. Baseline'ın diğer bilinen riskleri kendi raporunda korunur.

## 12. Paketleme ve checksum

Teslim: `Zeytin-Defteri-IS-EMRI-01-BAHCE-DETAY-FINAL.zip`.
Tam `zeytin-defteri/` proje ağacı içerir; patch/diff değildir. Baseline'daki her dosya
korunarak, yukarıdaki değişiklikler ve test/rapor dosyalarıyla paketlenir.

ZIP içindeki rapor ZIP'in kendi hash'ini içeremez: bu hash'i içeri yazmak ZIP'i tekrar
değiştirir. Final arşiv kapatılıp bütünlük testi yapıldıktan sonra SHA-256 ayrı `.sha256`
dosyasında ve ayrıca teslim edilen raporun dağıtım ekinde bulunur. Arşiv içindeki bu
rapor dağıtım eki öncesindeki sürümdür.
