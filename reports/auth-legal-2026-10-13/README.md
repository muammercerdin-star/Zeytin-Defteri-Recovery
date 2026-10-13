# Premium Kayıt/Giriş + Hukuki Güvenlik — Çalışma Raporu

- Tarih: 13.10.2026
- Depo / branch: `muammercerdin-star/Zeytin-Defteri-Recovery` / `work/auth-review-2026-10-13`
- Başlangıç HEAD: `4f37b20f962e29a236cf903a7d8c9676016bded1` (doğrulandı, çalışma ağacı temizdi)
- Final HEAD: bu raporu içeren commit (SHA, push sonrasında iletildi)
- `main` branch'ine ve diğer depolara dokunulmadı. Firebase/Supabase yapılandırması, sır veya imzalama dosyası eklenmedi.

> Bu rapordaki hiçbir test sonucu hukuki uygunluk anlamına gelmez. Hukuki metinler **taslaktır**.

## 1. Değiştirilen / eklenen dosyalar

| Dosya | Değişiklik |
|---|---|
| `app/src/main/res/layout/activity_auth_premium.xml` | Ölçüler kompaktlaştırıldı, onay alanı yeniden tasarlandı, içerik dikeyde ortalandı |
| `app/src/main/res/values/auth_premium.xml` | Stil ölçüleri, onay/bağlantı metinleri, eski "onaylı metin yok" diyalog metinleri kaldırıldı |
| `app/src/main/java/com/zeytindefteri/app/LoginActivity.java` | Belge bağlantıları, onay satırı, kayıt engeli mesajı (auth akışı değişmedi) |
| `app/src/main/java/com/zeytindefteri/app/AuthRegistrationPolicy.java` | `documentsReady()` artık `LegalDocuments.readyForRegistration()` |
| `app/src/main/java/com/zeytindefteri/app/LegalDocuments.java` | **Yeni.** Belge sürümü, iletişim, veri sorumlusu, kayıt ön koşulları |
| `app/src/main/java/com/zeytindefteri/app/LegalDocumentActivity.java` | **Yeni.** Üç belge için okuma ekranı, silme talebi ve iletişim e-postası |
| `app/src/main/res/raw/legal_kvkk.txt` | **Yeni.** KVKK Aydınlatma Metni (taslak) |
| `app/src/main/res/raw/legal_privacy.txt` | **Yeni.** Gizlilik Politikası (taslak) |
| `app/src/main/res/raw/legal_terms.txt` | **Yeni.** Kullanım Koşulları (taslak) |
| `app/src/main/java/com/zeytindefteri/app/PrivacyActivity.java`, `TermsActivity.java` | Yeni belge ekranına yönlendirir (Ayarlar ve karşılama ekranı giriş noktaları korunur) |
| `app/src/main/java/com/zeytindefteri/app/SettingsView.java` | "DİĞER" bölümüne KVKK Aydınlatma Metni ve Hesap ve Veri Silme Talebi satırları eklendi |
| `app/src/main/AndroidManifest.xml` | `LegalDocumentActivity` kaydı (`Theme.Legal`, `exported=false`) |
| `tests/auth/LegalDocumentsTest.java`, `tests/auth/check_layout_budget.py`, `tests/auth/run.py` | Yeni host testleri |

Dokunulmayanlar: Firebase e-posta kayıt/giriş, Google Credential Manager girişi, şifre sıfırlama, oturum yönetimi, yedi ana menü (`ProducerFeedActivity`), Gradle dosyaları. Eski `activity_privacy.xml` / `activity_terms.xml` silinmedi; artık gösterilmiyorlar ve onaydan sonra kaldırılabilirler.

## 2. UI düzenleme ayrıntıları

| Ölçü | Önce (4f37b20) | Sonra |
|---|---:|---:|
| İçerik üst/alt boşluğu | 18dp | 8dp |
| Logo | 36dp | 28dp |
| Marka yazısı | 15sp | 14sp |
| Başlık / üst boşluk | 22sp / 20dp | 21sp / 10dp |
| Açıklama / üst boşluk | 13sp / 10dp | 13sp / 6dp (iki satır) |
| Sekmeler | 48dp, üst 20dp | 44dp, üst 12dp |
| Google butonu | 50dp, üst 18dp | 48dp, üst 10dp |
| "veya e-posta ile" boşlukları | 18 / 18dp | 10 / 8dp |
| Metin alanları | 48dp | 46dp |
| Alan grupları arası | 12dp | 6dp |
| Etiket alt boşluğu | 5dp | 3dp |
| Göz ikonları | 48dp | 46dp (alan yüksekliğiyle hizalı) |
| Kayıt butonu | 52dp | 48dp |
| Güvenlik satırı üst boşluğu / ikon | 18dp / 16dp | 8dp / 14dp |

- **Onay alanı:** İki sütuna dağılmış bağlantılar kaldırıldı. Yeni yapı:
  1. Tek satır: onay kutusu + "Kullanım Koşulları'nı okudum ve kabul ediyorum." Satırın tamamı dokunulabilir.
  2. Altında iki ayrı bağlantı: **Kullanım Koşulları · KVKK Aydınlatma Metni**. Her biri kendi belge ekranını açar.
  3. Engel varsa nedenini söyleyen kısa durum satırı (engel kalkınca gizlenir).
- **KVKK ayrımı:** Onay kutusu yalnızca Kullanım Koşulları'nın kabulünü ifade eder. KVKK aydınlatması için "okudum/onaylıyorum" kutusu **yoktur**; aydınlatma metni yalnızca erişilebilir bağlantı olarak sunulur. Kayıt şartı olarak açık rıza istenmez.
- **Açık rıza:** Uygulamada gerçekten rızaya bağlı tek işleme olan piyasa paylaşımı zaten ayrı ve isteğe bağlı bir ayarla (`zd_market_consents`, `market_consent`) yönetiliyor. Metinler bu mevcut mekanizmaya atıf yapar; yeni rıza kutusu üretilmedi.
- Ad Soyad alanı yalnızca `hint` olarak "Örn. Muammer Çerdin" gösterir; alan önceden doldurulmaz (testle doğrulanır).
- E-posta, şifre, şifre tekrar, göster/gizle, şifre gücü göstergesi, renkler ve premium görünüm korundu.
- Erişilebilirlik: onay satırı ekran okuyucuya "işaretli / işaretli değil" durumunu bildirir; bağlantıların açıklayıcı `contentDescription` değerleri var.

## 3. Ekran yüksekliği değerlendirmesi

**Referans cihaz ölçümü (Honor X9 5G ekran görüntüleri):**
- Görüntü 905×1998 px. 48dp minimum yükseklikteki bir alan 115 px → **2,40 px/dp**, yani ekran ≈ 377dp genişlik.
- Durum çubuğu ≈ 105 px, pencere alt sınırı jest çubuğunun üstünde ≈ 1952 px → **kullanılabilir alan ≈ 770dp**.
- Eski düzenin gerçek içerik yüksekliği ekran görüntüsünden ≈ 856dp ölçüldü (bu nedenle ~86dp taşıyordu).

**Statik tahmin (`tests/auth/check_layout_budget.py`, cihaz render'ı değildir):**

| Yazı ölçeği | Önce | Sonra | 770dp'ye göre |
|---|---:|---:|---|
| ×1,0 (normal) | 863,9dp | 752,6dp | Sığar (~17dp pay) |
| ×1,15 | 886,6dp | 777,8dp | Kaydırma gerekir |
| ×1,3 | 943,4dp | 838,6dp | Kaydırma gerekir |
| Giriş modu ×1,0 | — | 523,9dp | Sığar |

Model eski düzen için 863,9dp tahmin etti; ekran görüntüsünden ölçülen ≈ 856dp ile %1 içinde örtüşüyor.
Kayıt engeli kalktığında durum satırı gizleneceği için yaklaşık 14dp daha kazanılır.

**Kaydırma yedeği:** `ScrollView` + `fillViewport` korundu, `adjustResize` değişmedi. Normal ekranda içerik dikeyde ortalanır ve kaydırma gerekmez; küçük ekran, büyük yazı veya açık klavyede içerik kesilmez, kaydırılabilir.

## 4. Eklenen hukuki belgeler (taslak, sürüm 0.1)

Uygulama içinde üç ayrı Türkçe belge (`res/raw/legal_*.txt`) ve ortak okuma ekranı (`LegalDocumentActivity`) eklendi. Her belgenin başında "TASLAK" bandı görünür. Erişim noktaları: kayıt ekranındaki bağlantılar, Ayarlar → DİĞER, karşılama ekranı (eski giriş noktaları yönlendirilir), belge ekranlarındaki "İlgili belgeler".

- **KVKK Aydınlatma Metni (m.10):** veri sorumlusu, veri kategorileri, amaçlar, hukuki sebepler (m.5/2-c, ç, e, f), toplama yöntemi, alıcılar ve aktarım amaçları, yurt dışı aktarım, m.11 hakları, başvuru yöntemi. Metin, sözleşme veya açık rıza olmadığını açıkça belirtir.
- **Gizlilik Politikası:** Firebase Authentication ve Google girişi, Firestore eşitleme, Supabase (anonim piyasa ve moderasyon), Open-Meteo, bölge veri servisleri, Meydan içerikleri, FCM bildirimleri, sesli asistan, analitik/reklam yokluğu, saklama ve silme, üçüncü taraflar.
- **Kullanım Koşulları:** amaç, hesap kullanımı, kullanıcı sorumlulukları, yasaklı kullanım, Meydan kuralları, askıya alma/silme, hizmet değişiklikleri, sorumluluk (emredici hükümleri ve kast/ağır ihmal sorumluluğunu kaldırmadığı açıkça yazılı), uygulanacak hukuk.

Uydurulmayan bilgiler: adres, telefon, ticaret unvanı, saklama süresi, sunucu ülkesi, yurt dışı aktarım güvencesi, silme garantisi. Veri sorumlusunun adı yerine "Bireysel geliştirici (ad soyad yayın öncesinde eklenecektir)" yazılır; `LegalDocuments.CONTROLLER_NAME` doldurulduğunda tüm belgelere otomatik yerleşir.

## 5. Veri akışı bulguları (koddan doğrulanan)

| Veri / hizmet | Kaynak |
|---|---|
| Firebase Auth (e-posta + Google) | `LoginActivity` |
| Firestore `users/{uid}/gardens, operations, items, notes, inventory_items, tombstones, devices` | `*CloudRepository`, `PushRegistration` |
| Firestore `social_posts`, `comments` (yalnızca metin; medya yüklenmiyor) | `SocialCloudRepository` (açık "TEXT ONLY" notu), `CommentCloudRepository` |
| Firestore `reports`, `user_blocks` | `SocialReportManager`, `SocialBlockRepository` |
| FCM token → `users/{uid}/devices` (çıkışta siliniyor) | `PushRegistration.beforeSignOut` |
| Cloud Functions bölgesi `europe-west1` (Firestore veritabanı konumu repoda belirtilmemiş) | `functions/index.js`, `functions/market/functions.js` |
| Supabase: piyasa katkıları `producer_hash` ile (ham UID yok); moderasyon edge function'ına Firebase ID token gönderiliyor | `supabase/market-schema.sql`, `ModerationBackendClient` |
| Open-Meteo hava/geocoding (bahçe koordinatı veya yer adı) | `WeatherRepository` |
| `api.turkiyeapi.dev`, `turkey-geo-data.muammercerdin.workers.dev` (bölge listeleri) | `TurkeyLocationRepository` |
| Android `SpeechRecognizer` (RECORD_AUDIO) | `ZeytinAssistantController` |
| Konum izni yok; Firebase Storage bağımlılığı var ama kullanımı yok; analitik/Crashlytics yok; şifre/token içeren log yok | Manifest, `app/build.gradle`, kod taraması |
| `allowBackup="true"` (yedekleme kuralları dosyalarıyla) | Manifest |

## 6. Kayıt engelinin durumu

- Mesajın kaynağı: `AuthRegistrationPolicy.documentsReady()` sabit `false` dönüyordu; metin `auth_legal_missing` dizesindeydi.
- Şimdi: `documentsReady()` → `LegalDocuments.readyForRegistration()` = **`LEGAL_REVIEW_APPROVED == true` VE `CONTROLLER_NAME` dolu**.
- Mevcut durum: her iki koşul da sağlanmadığı için **yeni kayıt (e-posta ve "Google ile kayıt ol") kapalı kalır**. Engel sessizce kaldırılmadı. Kullanıcı gerekçeyi görür: "Hukuki metinler taslak; onaylanana kadar yeni kayıt kapalı. Mevcut hesabınla giriş yapabilirsin."
- Açmak için: ad soyadı doğrulayıp `CONTROLLER_NAME`'e yazın, hukuki incelemeden sonra `LEGAL_REVIEW_APPROVED = true` yapın. Testler iki koşulu ayrı ayrı doğrular.
- **Bulunan açık:** "Giriş Yap" sekmesindeki Google girişi, daha önce hesabı olmayan bir Google hesabı için Firebase'de **yeni hesap oluşturur** ve koşul kabulü istenmez (`firebaseAuthWithGoogle`, `isNewUser` kontrolü yok). Kayıt engeli bu yolla aşılabilir. Auth akışını değiştirmemek için bu görevde düzeltilmedi; yayın öncesi karar verilmesi gerekir (ör. yeni kullanıcıysa koşul ekranı göstermek).
- Koşul kabulünün sürümü ve zamanı şu an hiçbir yere kaydedilmiyor; kayıt açılmadan önce kabul edilen sürümün saklanması önerilir.

## 7. Hesap ve veri silme durumu

- Uygulamada **Firebase Authentication hesabını silen bir işlem yok** (`FirebaseUser.delete()` çağrısı bulunmuyor).
- Kayıt bazında silme var: bahçe/işlem kayıtları `CloudDeletionRepository` ile tombstone (silme işareti) kullanarak cihazlar arasında silinir; Meydan gönderi/yanıtları uygulamadan kaldırılabilir; çıkışta FCM token silinir.
- Supabase: piyasa paylaşımı kapatıldığında `market-api` edge function'ı katkıları siliyor (`setConsent`), ancak uygulamanın canlıda hangi piyasa yolunu kullandığı cihazda doğrulanmadı; metinlerde garanti verilmedi, e-postayla talep yolu yazıldı.
- Eklenen: Ayarlar → "Hesap ve Veri Silme Talebi" ve KVKK/Gizlilik ekranlarında buton; hazır konu/gövdeyle `muammercerdin@gmail.com` adresine e-posta açar (e-posta uygulaması yoksa adresi kopyalar). Metinler tek adımda silme olmadığını açıkça söyler.
- **Google Play gereksinimi:** Play, hesap oluşturan uygulamalardan uygulama içi silme yolu ve **uygulama dışından erişilebilir bir web silme talep sayfası** ister. E-posta bağlantısı tek başına bu gereksinimin tamamı sayılmamalıdır. Yayın öncesi: (1) web silme talep sayfası URL'si, (2) tercihen uygulama içi tam hesap silme (Auth + Firestore alt koleksiyonları + Meydan içerikleri + Supabase katkıları), (3) Play Console Veri Güvenliği formu.

## 8. Eksik yayıncı bilgileri

- Veri sorumlusunun doğrulanmış ad soyadı (`LegalDocuments.CONTROLLER_NAME`).
- KVKK m.13 başvurusu için adres gerekip gerekmediği (yalnızca e-posta yazıldı).
- Firestore veritabanı konumu ve Supabase proje bölgesi.
- VERBİS yükümlülüğünün değerlendirilmesi.

## 9. Hukuki inceleme gerektiren konular

1. Yurt dışı aktarımın KVKK m.9'daki hangi güvenceye dayandığı (ör. standart sözleşme ve Kurul'a bildirimi). Metin "usullere uygun olarak yapılır" diyor; bu ifade doğrulanmadan yayınlanmamalı.
2. Piyasa paylaşımının hukuki sebebi (açık rıza mı, meşru menfaat mi) ve `producer_hash`'in kişisel veri sayılıp sayılmadığı.
3. Meydan'da paylaşılan bölge/bahçe bilgisinin kamuya açıklığı.
4. Saklama süreleri ve yedeklerden silme süreçleri (metinlerde süre verilmedi).
5. Sorumluluk maddesinin tüketici mevzuatı karşısında geçerliliği.
6. Başvurularda kimlik doğrulama yöntemi ve 30 günlük süre takibi.
7. Google ile giriş üzerinden kabul alınmadan hesap oluşması (Bölüm 6).

## 10. Build / test sonuçları (bu ortam)

| Kontrol | Sonuç |
|---|---|
| 160 XML dosyası (res + Manifest) ayrıştırma | GEÇTİ |
| Değişen 7 Java dosyasının sözdizimi ayrıştırması | GEÇTİ (tip kontrolü değildir) |
| `python3 tests/auth/run.py`: 18 hata mesajı + 17 kayıt politikası + 57 hukuki belge + yerleşim bütçesi | GEÇTİ |
| Layout id / string referansları, `git diff --check` | GEÇTİ |
| Firebase Functions `npm test` | 88 test: 78 geçti, 0 başarısız, 10 atlandı (emülatör yok) — değişiklik öncesiyle aynı |
| Supabase testleri | 12 geçti; `restore-safety` `PGLITE_MODULE` ister — değişiklik öncesiyle aynı |
| Diğer Python testleri | 16 geçti, 4 başarısız — **değişiklik öncesinde de aynı 4'ü başarısızdı** (eskimiş kaynak sözleşmesi testleri / `JSON_JAR`) |
| `gradlew :app:assembleDebug` | **Çalıştırılamadı**: Gradle dağıtımı ve Maven indirmeleri ağ politikasıyla engellendi. APK üretilmedi, Android tip kontrolü yapılmadı. |

## 11. Gerçek cihazda doğrulanması gerekenler

Aşağıdakilerin hiçbiri bu ortamda test edilmedi:
- Derleme (`assembleDebug`) ve uygulamanın açılması.
- Honor X9 5G'de normal yazı boyutunda kayıt ekranının kaydırmasız sığması; küçük ekran ve %115/%130/%200 yazı ölçeğinde kaydırmanın çalışması.
- Klavye açılıp kapanırken alanların ve kayıt butonuna erişim.
- E-posta kayıt (engel açıldıktan sonra), e-posta giriş, Google giriş, şifre sıfırlama.
- Şifre göster/gizle, şifre güç göstergesi, form doğrulama hataları.
- Onay satırına dokunma, iki belge bağlantısı, belge ekranları, "İlgili belgeler" geçişleri, silme talebi e-postasının açılması.
- Ayarlar → KVKK / Gizlilik / Kullanım Koşulları / Hesap ve Veri Silme Talebi satırları ve karşılama ekranındaki belge bağlantıları.
- Yedi ana menü regresyonu: Ana Sayfa | Bahçelerim | Elek | Asistan | Zeytin Ligi | Sohbet | Ayarlar.
