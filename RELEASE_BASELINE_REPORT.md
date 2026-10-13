# Zeytin Defteri — İş Emri 00 / Release Baseline Raporu

**Denetim tarihi:** 2026-09-09  
**Kaynak ZIP:** `Zeytin-Defteri-FINAL-Zeytin-Ligi-Pixel-Close-V2.zip`  
**Amaç:** Mevcut çalışan projeyi değiştirmeden release baseline olarak denetlemek, testleri güncel mimariye göre doğrulamak ve sonraki iş emirleri için güvenli ana kaynak oluşturmak.

---

## 1. Sonuç özeti

- Proje sıfırdan kurulmadı.
- Geniş refactor yapılmadı.
- Yeni özellik eklenmedi.
- Ürün Java/XML/Firebase/SQLite kodunda zorunlu bir hata düzeltmesi gerekmedi.
- Bir adet **eskimiş host testi** güncel Repository mimarisine göre düzeltildi: `tests/elek_archive/check_archive.py`.
- Yeni baseline belgesi olarak bu dosya eklendi: `RELEASE_BASELINE_REPORT.md`.
- Son test turunda çalıştırılabilen tüm test komutları exit code `0` verdi.
- Firebase Functions testleri: **34 toplam / 30 PASS / 0 FAIL / 4 SKIP**.
- 4 SKIP, gerçek Firebase Auth/Firestore/Rules emulator ortamı bulunmadığı için atlanan emulator testleridir; PASS sayılmamıştır.
- Gerçek Android Gradle/APK build bu ortamda **doğrulanamadı**. “Build geçti” sonucu verilmemiştir.
- Kaynak/statik bağlantı denetiminde 104 resource XML dosyası parse edildi, Manifest'teki 26 Activity/Service hedefi sınıflarla eşleştirildi ve Java'daki 461 temel `R.*` kaynak referansı kontrol edildi; belirgin bağlantı sorunu bulunmadı.
- Orijinal kaynakla karşılaştırmada korunan kritik ürün dosyalarının SHA-256 değerleri değişmedi.

**Baseline kararı:** Kaynak/statik ve mevcut host/backend test kapsamı açısından proje sağlam bir başlangıç noktasıdır. Android APK build ve Firebase emulator doğrulaması yapılmadan “tam release build doğrulandı” denemez.

---

## 2. İncelenen proje kapsamı

### Android kaynakları

- 77 Java kaynak dosyası envanterlendi ve ana veri/navigasyon akışları incelendi.
- 104 `app/src/main/res` XML kaynağı parse edildi.
- `AndroidManifest.xml` ve Activity/Service hedefleri kontrol edildi.
- Root ve app Gradle dosyaları, wrapper ve repository ayarları kontrol edildi.
- Paket/application kimliği: `com.zeytindefteri.app`.
- `compileSdk 34`, `minSdk 24`, `targetSdk 34`.
- Android Gradle Plugin: `8.5.2`.
- Google Services plugin: `4.4.2`.
- Gradle wrapper: `8.7`.
- Java source/target compatibility: Java 8.

### Firebase ve bulut

- `app/google-services.json` var ve JSON olarak parse ediliyor.
- Firebase Auth / Google giriş akışı incelendi.
- Firestore repository/sync sınıfları incelendi.
- `PrivateCloudSyncManager` akışı incelendi.
- `firestore.rules`, `storage.rules`, `firebase.json` incelendi.
- Sohbet/sosyal repository yapısı incelendi.
- Zeytin Ligi istemci repository'si ve `functions/league/*` backend/test yapısı incelendi.
- Satış doğrulama / attestation / evidence tekilliği ve operator approval testleri çalıştırıldı.

### SQLite ve özel kullanıcı verileri

- `DatabaseHelper` şeması ve `onUpgrade()` yolları incelendi.
- DB adı: `zeytin_defteri.db`.
- DB sürümü: `19`.
- Foreign key desteği `onConfigure()` içinde etkinleştiriliyor.
- Bahçe, işlem, hasat, Elek, satış, salamura, stok, tahsilat/alacak ve yardımcı tabloların owner/scope davranışları incelendi.
- `SessionManager`, aktif `owner_uid` ve aktif `season_year` bağlantıları incelendi.

### UI / navigasyon / asistan / hava

- `MainActivity` bilinçli olarak ince kabuk; ana davranış `ProducerFeedActivity` içindedir.
- Son alt navigasyon sırası kaynakta doğrulandı:
  `Ana Sayfa | Bahçelerim | Elek | Asistan | Zeytin Ligi | Sohbet | Ayarlar`
- Asistan davranışı doğrulandı:
  - Tek dokunma: Zeytin Asistanını açar / açık oturumda orb etkileşimini yürütür.
  - Uzun basma: mevcut hızlı işlem menüsünü (`showQuickCreateMenu`) açar.
- `WeatherRepository` aktif bahçenin kayıtlı konumuna göre çalışıyor; telefonun anlık konumunu zorunlu tutmuyor.
- Hava tahmini ve asistan hava cevapları için mevcut host testleri çalıştırıldı.

---

## 3. Çalışan / doğrulanan temel özellikler

Aşağıdaki ifadeler gerçek APK cihaz testi değil; kaynak incelemesi + mevcut host/backend testlerinin kapsadığı seviyeyi ifade eder.

### Kullanıcı ve sezon izolasyonu

- `SessionManager` kullanıcı UID'sini özel SharedPreferences içinde tutuyor.
- Firebase kullanıcı yönlendirme akışı gerçek `FirebaseUser.getUid()` ile kullanıcı oturumunu güncelliyor.
- Aktif DatabaseHelper sorgularında `activeOwnerCondition(...)` ve sezonlu tablolarda `activeSeasonCondition(...)` kullanılıyor.
- Host SQLite testleri işlem/hasat owner+season izolasyonunu doğruladı.
- Güncellenen Elek arşiv testi, **gerçek `ElekRepository` SQL'ini** çalıştırarak owner/season/garden izolasyonu ile legacy sezon fallback'ini doğruladı.

### Bahçe / işlem / hasat / Elek / satış / salamura

- Aktif bahçe sorguları owner kapsamına bağlı.
- İşlem ve hasat sorguları owner + sezon kapsamına bağlı.
- Elek arşivi Repository üzerinden owner + sezon + isteğe bağlı bahçe filtresiyle çalışıyor.
- Satış repository/özet sorgularında owner + sezon kapsamı mevcut.
- Salamura batch/event okumalarında batch owner + sezon kapsamı uygulanıyor.

### Stok / tahsilat / alacak

- `inventory_items` okuma/yazma akışında `owner_uid` kullanılıyor.
- `receivables` okuma/yazma akışında `owner_uid` kullanılıyor.
- Açık alacak toplamı aktif owner'a göre ve satışla bağlıysa aktif sezona göre kapsamlanıyor.
- Stok kendisi sezonluk tablo olarak modellenmemiş; kullanıcıya özel global stok verisi olarak owner kapsamında tutuluyor.

### Firebase özel veri senkronizasyonu

`PrivateCloudSyncManager` sırası:

1. Yerel pending-delete kayıtlarını buluta gönderir.
2. Buluttaki silmeleri çeker ve uygular.
3. Bahçeleri restore/merge eder.
4. İşlemleri restore/merge eder.
5. Üretim kayıtlarını restore/merge eder.
6. Destek verilerini restore/merge eder.
7. Yerel yeni verileri buluta yollar.

Silme push adımı başarısızsa normal restore'a geçmemesi, silinmiş verinin tekrar dirilmesi riskini azaltır.

### Firestore / Storage erişim modeli

- `users/{uid}` altında read/write erişimi `request.auth.uid == uid` ile kısıtlanıyor.
- Zeytin Ligi yayın snapshot'ları istemci açısından read-only; client write kapalı.
- Lig member dokümanında kullanıcı yalnız kendi UID'sine `get` yapabiliyor; list/write kapalı.
- Storage sosyal dosya yazmaları authenticated UID klasörüne bağlı.

### Zeytin Ligi ve satış doğrulama

Backend testleri aşağıdakileri kapsıyor:

- Operator yetkilendirmesi.
- Self-approval engeli.
- Bağımsız evidence zorunluluğu.
- Aynı evidence/satışın tekrar kullanılmasını engelleyen claim mantığı.
- Attestation revoke/revision davranışı.
- Ranking sözleşmesi / skor doğrulaması / top20 sınırları.
- Sahte score/rank enjeksiyonunun reddi.
- Snapshot/runtime tutarlılığı.
- Pipeline retry/supersede davranışı.

### Sohbet / sosyal

- Yerel sosyal şema ve migration fixture'ları geçti.
- Nested reply cache/round-trip ve legacy preservation testleri geçti.
- Feed preview aggregate SQL testi; deleted exclusion, owner görünürlüğü, latest nested reply ve timestamp tie davranışlarını geçti.
- Gerçek Firestore rules emulator doğrulaması bu ortamda çalıştırılamadı; ilgili testler SKIP'tir.

---

## 4. Bulunan gerçek ürün hataları

**Bu baseline denetiminde ürün kodunda doğrulanmış compile/runtime/veri kaybı hatası bulunmadı.**

Bu nedenle ürün Java/XML/Firebase/SQLite kodunda sırf temizlik veya refactor amacıyla değişiklik yapılmadı.

---

## 5. Eskimiş / uyumsuz test

### `tests/elek_archive/check_archive.py`

**İlk durum:** Test, SQL'in hâlâ `ElekArchiveActivity` içinde `String sql = ...` olarak bulunacağını varsayıyordu ve `IndexError` ile kırmızı oluyordu.

**Gerçek mimari:** `ElekArchiveActivity.loadArchive()` sorguyu doğrudan kurmuyor; `ElekRepository.getArchiveRecords(...)` çağırıyor. Owner/season/garden filtreli SQL artık Repository'de.

**Karar:** Çalışan mimari Activity içine geri taşınmadı. Test güncel mimariye göre düzeltildi.

**Yeni test neyi doğruluyor:**

- Activity → Repository sınırı.
- Gerçek production `ElekRepository.java` içindeki SQL'in host tarafında yakalanması.
- SQLite fixture üzerinde owner izolasyonu.
- `season_year` izolasyonu.
- Legacy `season_year=0` için kayıt tarihinden sezon fallback'i.
- Bahçe filtresi.
- Sıralama.
- 6 Elek sınıfı.
- Kg toplamları ve ortalama.
- İlgili XML/ID/drawable bağlantıları.

Sonuç: **PASS**.

---

## 6. Test sonuçları — final tur

| Test komutu/grubu | Sonuç | Not |
|---|---:|---|
| `tests/assistant/run_host_tests.py` | PASS | Parser, scope, adapter, period ve advisor kontrolleri |
| `tests/assistant_ux/run_ux_tests.py` | PASS | 30 UI/session policy assertion; cihaz testi değil |
| `tests/elek_archive/check_archive.py` | PASS | Güncel Repository sınırı + gerçek SQL fixture testi |
| `tests/elek_popup/check_prices.py` | PASS | Elek fiyat pop-up davranışı |
| `tests/elek_ui/check_form.py` | PASS | Elek form/XML ve hesaplamalar |
| `tests/feed_thread/check_preview.py` | PASS | Thread preview aggregate SQL/cache |
| `tests/final_assistant/run_weather_measure_tests.py` | PASS | 36 viewport/cache simülasyonu; cihaz layout testi değil |
| `tests/location/check_location.py` | PASS | Owner-filtered bahçe konumu |
| `tests/meydan/check_schema.py` | PASS | 18 schema/migration/owner/pending-delete fixture |
| `tests/threaded/check_roundtrip.py` | PASS | Additive migration + nested cache/ownership mapping |
| `tests/today/SQLiteScopeTest.py` | PASS | 14 host SQLite owner/season fixture |
| `npm --prefix functions test` | PASS* | 34 toplam: 30 PASS, 0 FAIL, 4 SKIP |
| `npm --prefix functions run check` | PASS | Node syntax/check görevi |

`*` 4 adet emulator bağımlı test SKIP'tir ve PASS olarak yorumlanmamıştır.

### Asistan host testlerinden öne çıkan sayımlar

- 55 parser/command check PASS.
- 25 adapter/scope check PASS.
- 165 FINAL parser/scope check PASS.
- 36 FINAL data/period check PASS.
- 21 GardenTodayAdvisor assertion PASS.
- 30 Asistan UX/session policy assertion PASS.

---

## 7. Android build sonucu

### Sonuç: **GERÇEK ANDROID BUILD DOĞRULANAMADI**

`assembleDebug`/APK derlemesini doğrulayacak Gradle çalışması bu sandbox ortamında başlatılamadı.

İlk ve kesin engel:

- Wrapper `gradle-8.7-bin.zip` dosyası local cache'te yok.
- Wrapper `services.gradle.org` üzerinden indirmeye çalışıyor.
- Ortamda dış ağ/DNS erişimi yok ve `UnknownHostException: services.gradle.org` ile duruyor.

Ek ortam eksikleri:

- `ANDROID_HOME` tanımlı değil.
- `ANDROID_SDK_ROOT` tanımlı değil.
- `local.properties` yok.
- Projedeki `gradle.properties` içinde `org.gradle.java.home=/home/test/java17` bulunuyor; bu yol bu sandbox'ta mevcut değil. Bu ayar önceki gerçek build cihazına/ortamına ait olabilir; baseline görevi kapsamında değiştirilmedi.

Bu nedenle:

- **“Build geçti” denmemiştir.**
- Kaynak/statik denetim sonucu ile gerçek APK build sonucu ayrı tutulmuştur.

### Statik Android kaynak kontrolü

Build yerine geçmemek kaydıyla aşağıdaki kontroller PASS:

- 104 resource XML dosyasının parse edilmesi.
- Manifest'teki 26 Activity/Service hedefinin Java sınıflarıyla eşleşmesi.
- 461 temel Java `R.*` kaynak referansının karşılığının bulunması.
- `LoginActivity` tarafından kullanılan `R.string.default_web_client_id`, dosyalarda elle tanımlı değil; `google-services` plugin tarafından `google-services.json` içindeki web OAuth client bilgisinden üretilen kaynaktır. Gerekli web OAuth client girdisi mevcut.

---

## 8. SQLite migration / veri kaybı riski

### Güvenli görünen noktalar

- `DatabaseHelper` içinde `DROP TABLE` bulunmadı.
- `onUpgrade()` additive migration yaklaşımı kullanıyor.
- owner/cloud identity ve `season_year` alanları eski kayıtları silmeden ekleniyor/backfill ediliyor.
- Comment/thread migration testleri legacy kayıtların korunmasını fixture düzeyinde doğruluyor.
- Production/support cloud restore akışları owner UID ve cloud ID üzerinden merge/upsert yaklaşımı kullanıyor.
- Silme işlemleri tombstone akışıyla korunuyor.

### Bilinçli eski migration davranışı

`oldVersion < 9` reaction migration'ında deprecated `NOT_USEFUL` tepki kayıtlarına yönelik bir `DELETE FROM post_reactions ...` bulunuyor. Bu tüm kullanıcı/üretim verisini silen bir reset değildir; eski reaction modelinin semantik dönüşümünün parçasıdır.

### Dikkat edilmesi gereken migration noktası

`DB_VERSION = 19` olmasına rağmen klasik ayrı `if (oldVersion < 18)` / `if (oldVersion < 19)` blokları görünmüyor. Son comment/thread alanlarının bir bölümü, upgrade sonunda çağrılan idempotent `ensureCommentSyncSchema(...)` ile tamamlanıyor.

Mevcut fixture testleri bu additive migration'ı ve legacy preservation'ı geçiriyor. Bu baseline kapsamında çalışan mekanizma değiştirilmedi. Ancak sonraki DB sürüm artışlarında migration bookkeeping açık ve tek yönlü tutulmalı; `DB_VERSION` artırılırken bu yardımcıya körlemesine güvenip yeni zorunlu migration unutulmamalıdır.

**Baseline veri kaybı değerlendirmesi:** Mevcut kaynak ve test kapsamı içinde yeni bir veri kaybı riski oluşturan değişiklik yapılmadı. Gerçek cihaz üzerinde eski üretim DB'sinden upgrade testi bu ortamda yapılmadığı için sıfır risk garantisi verilmez.

---

## 9. Korunduğu doğrulanan kritik dosyalar / alanlar

Orijinal ZIP ile çalışma ağacı karşılaştırıldı. Aşağıdaki kritik dosyaların SHA-256 değerleri birebir aynı kaldı:

- `app/google-services.json`
- `firestore.rules`
- `storage.rules`
- `app/src/main/java/DatabaseHelper.java`
- `app/src/main/java/com/zeytindefteri/app/SessionManager.java`
- `app/src/main/java/com/zeytindefteri/app/PrivateCloudSyncManager.java`
- `app/src/main/java/com/zeytindefteri/app/ProducerFeedActivity.java`

Buna ek olarak baseline görevi sırasında Firebase proje kimliği, Google login bağlantısı, repository/sync mimarisi, Zeytin Ligi backend'i ve kullanıcı verisi şeması değiştirilmedi.

---

## 10. Sonraki iş emirlerinde korunması gereken kritik dosyalar

Özellikle aşağıdaki alanlarda “temizlik/refactor” amaçlı geniş değişiklik yapılmamalı; değişiklik yapılacaksa ilgili owner/season/migration testleri birlikte güncellenmelidir:

- `app/google-services.json`
- `AndroidManifest.xml`
- Root `build.gradle`, `app/build.gradle`, `settings.gradle`, Gradle wrapper dosyaları
- `DatabaseHelper.java`
- `DatabaseSchema.java`
- `SessionManager.java`
- `GardenRepository.java`, `ElekRepository.java`, `SaleRepository.java`
- `GardenCloudRepository.java`
- `OperationCloudRepository.java`
- `ProductionCloudRepository.java`
- `SupportCloudRepository.java`
- `CloudDeletionRepository.java`
- `PrivateCloudSyncManager.java`
- `SocialRepository.java`
- `SocialCloudRepository.java`
- `CommentCloudRepository.java`
- `firestore.rules`, `storage.rules`, `firebase.json`
- `ZeytinLeagueRepository.java`, `ZeytinLeagueScore.java`, `ZeytinLeagueView.java`
- `functions/league/*`, `functions/operator/*`, ilgili backend testleri
- `ProducerFeedActivity.java` alt navigasyon ve Asistan bağlantıları
- `ZeytinAssistant*` sınıfları
- `WeatherRepository.java`

---

## 11. Sonraki geliştirmeler için teknik riskler

1. **Android build bu baseline oturumunda doğrulanmadı.** Yeni ana kaynak gerçek Android SDK + Gradle 8.7 cache/internet + uygun JDK bulunan ortamda en az `assembleDebug` ile doğrulanmalı.
2. **Firebase emulator testleri çalışmadı.** 4 test SKIP kaldı; production'a geçmeden Auth/Firestore/Rules emulator ile çalıştırılması önerilir.
3. **Host testleri gerçek cihaz değildir.** SpeechRecognizer, TTS, Android lifecycle, görsel ölçüm ve cold-start davranışları gerçek cihazda ayrıca smoke test gerektirir.
4. **`gradle.properties` mutlak JDK yolu içeriyor.** `/home/test/java17` bu sandbox'ta yok. Bu yol yalnız belirli build cihazına bağımlıdır; başka build ortamlarında konfigürasyon engeli oluşturabilir. Baseline kapsamında değiştirilmedi.
5. **Legacy repository metotları mevcut.** Aktif ekranların önemli akışları owner/season kapsamlı DatabaseHelper/Repository metotlarını kullanıyor; ancak geçmişten kalan bazı repository metotları aynı yeni kapsam garantisini taşımayabilir. Yeni ekran yazarken “var olan metot” diye doğrudan bağlamadan önce owner/season filtresi kontrol edilmelidir. Bu baseline'da kullanılmayan kod sırf temizlik için refactor edilmedi.
6. **Legacy `ZdNav` hâlâ üç eski ekranda kullanılıyor:** `OperationHistoryActivity`, `MarketActivity`, `ProfileActivity`. Ana `ProducerFeedActivity` üzerindeki final 7 öğeli navigasyon doğru ve korunmuştur. Eski yardımcı navigasyon bu baseline'da davranış değiştirme kapsamına alınmadı; ileride menü tutarlılığı görevi açılırsa ayrıca ele alınmalıdır.
7. **DB version bookkeeping:** DB 19; comment/thread şema tamamlaması idempotent helper ile yapılıyor. Sonraki schema değişikliklerinde açık migration adımı + eski DB fixture testi zorunlu tutulmalıdır.
8. **Hava ağı:** Open-Meteo/geocoding gerçek ağ erişimine bağlıdır. Bu sandbox'ta dış ağ kapalı olduğundan canlı endpoint testi yapılmadı; mevcut hava host/simülasyon testleri PASS.

---

## 12. Bu iş emrinde değiştirilen dosyalar

1. `tests/elek_archive/check_archive.py`
   - Eskimiş Activity-içi SQL varsayımı kaldırıldı.
   - Güncel Activity → Repository mimarisi ve production SQL'i test edilir hale getirildi.

2. `RELEASE_BASELINE_REPORT.md`
   - Bu baseline denetim raporu eklendi.

**Ürün Java/XML/Firebase/SQLite dosyalarında değişiklik yapılmadı.**

---

## 13. Release baseline mühürü

Bu ZIP bundan sonraki iş emirleri için **release baseline / ana kaynak** olarak kullanılabilir; ancak aşağıdaki ifade korunmalıdır:

> Kaynak/statik denetimler ve mevcut host/backend testleri temizdir. Gerçek Android APK build'i ve Firebase emulator testlerinin tamamı bu ortam kısıtları nedeniyle doğrulanmamıştır.

Yeni iş emirleri bu baseline üzerinde küçük, izole ve testlenebilir değişikliklerle ilerlemeli; Firebase kimliği, SQLite migration zinciri, `owner_uid + season_year` kapsamı, cloud tombstone/sync sırası, Zeytin Ligi doğrulama backend'i ve Asistan uzun basma davranışı korunmalıdır.
