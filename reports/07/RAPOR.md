# İş Emri 07 — Ayarlar Premium Kompakt Tasarım

## Değiştirilen dosyalar

- `app/src/main/java/com/zeytindefteri/app/ProducerFeedActivity.java`: Ayarlar dashboard kartları kaldırıldı; gerçek kullanıcı/telefon/e-posta/bölge ve sezon verisi yeni SettingsView'a aktarılıyor. Yalnız Ayarlar sekmesinde eski geniş üst barın yerine yeni görünümün kompakt marka alanı gösteriliyor. Diğer sekmelerin üst barı ve sabit alt navigasyon değişmedi.
- `app/src/main/java/com/zeytindefteri/app/ModerationAccess.java`: Eski ayrı büyük giriş kartları kompakt Yönetim bölümüne bağlandı. Mevcut claim kararı, zorunlu token yenileme, kullanıcı eşleşmesi ve lifecycle kontrolleri korundu. Başlık, separator ve bölüm boşluğu satırlarla birlikte gizleniyor.
- `app/src/main/java/com/zeytindefteri/app/ProfileActivity.java`: Ayarlar'dan mevcut çıkış onayını açan intent parametresi eklendi. confirmLogout, performLogout, signOutFirebase ve goToLogin uygulamaları kaynakla birebir aynı.
- `functions/test/moderator-management.android.test.js`: Eski büyük kart/spacing beklentisi yeni SettingsView ve grup görünürlüğü bağlantısına uyarlandı; backend testi değiştirilmedi.

Eklenen dosyalar:

- `app/src/main/java/com/zeytindefteri/app/SettingsView.java`: 1000172391.jpg referansına göre kompakt profil, soluk zeytinli üst alan, 48/56 dp minimum satırlar, sıkı bölüm aralıkları, telefon/e-posta/konum ikonları, yeşil avatar çerçevesi, kalem diski, gri alt açıklamalar ve gerçek uygulama sürümüyle footer. HESAP / ÜRETİM / UYGULAMA / YÖNETİM / DİĞER kartları korundu. Mevcut home_hero_olive görseli değiştirilmeden yalnız bu görünümde de kullanılıyor.
- `app/src/main/res/drawable/ic_settings_edit.xml`, `ic_settings_block.xml`, `ic_settings_shield.xml`, `ic_settings_users.xml`, `ic_settings_logout.xml`, `ic_settings_person.xml`, `ic_settings_phone.xml`, `ic_settings_mail.xml`, `ic_settings_location.xml`, `ic_settings_document.xml`: On Ayarlar ikonu.
- `tests/settings07_check.py`: Gerçek ModerationAccess kodunu sahte Android/Auth test ortamında çalıştıran rol/lifecycle testi ve bağlantı/resource kontrolleri.
- `reports/07/`: Bu rapor ve doğrulama kayıtları.

## Korunan mevcut bağlantılar

| Satır / işlem | Mevcut hedef |
| --- | --- |
| Profil kartı kalemi ve Profili Yönet | ProfileActivity |
| Engellenen Kullanıcılar | BlockedUsersActivity |
| Aktif Sezon | Mevcut showSeasonPicker; DatabaseHelper aktif sezonu |
| Bildirimler ve Hatırlatmalar | RemindersActivity; mevcut yerel alarm sistemi |
| Sesli Asistan | Mevcut openZeytinAssistant; yeni tercih veya switch değil |
| Moderasyon Merkezi | ModerationActivity; mevcut Supabase istemcisi |
| Yetki Yönetimi | ModeratorManagementActivity; mevcut Supabase istemcisi |
| Gizlilik Politikası | PrivacyActivity |
| Kullanım Koşulları | TermsActivity |
| Çıkış Yap | ProfileActivity açılır ve mevcut çıkış onayı gösterilir; onayda aynı Firebase/Session temizliği ve Login yönlendirmesi çalışır. Vazgeçilirse mevcut Profil ekranında kalınır. |

Profil fotoğrafı/baş harfi mevcut ProfilePhotoManager'dan; isim Firebase/Session kaynağından, telefon yalnız Firebase hesabında varsa, e-posta Firebase/Session'dan, bölge mevcut user_province/user_district kayıtlarından gelir. Misafir kartına eski hesap e-postası/telefonu/bölgesi yazılmaz. Örnek kişi, telefon, bahçe veya sezon değeri hardcode edilmedi.

Yedi öğeli alt navigasyon sırası ve aktif Ayarlar vurgusu korunmuştur. İçerik mevcut ağırlıklı contentHost içinde kayar; alt navigasyon ScrollView dışında sabit kalır. Dikey scrollbar kapalıdır, alt içerikte 24 dp pay vardır. Satırlar sabit yüksekliğe sıkıştırılmaz; dar ekranda metinler sarılabilir, dokunma alanı en az 48 dp'dir.

## Gerçek karşılığı olmadığı için eklenmeyen referans öğeleri

- Çalışan Hesap ve Güvenlik ekranı bulunmadı; genel profil bilgisi ekranı güvenlik ekranı gibi sunulmadı.
- Genel bir Varsayılan Bahçe ayarı bulunmadı. Ana sayfanın aktif bahçe seçimi bu isimle yeni bir ayara dönüştürülmedi.
- Kalıcı davranışı olan Tema/Dil/Sohbet Bildirimleri/Piyasa Bildirimleri anahtarları bulunmadı; sahte toggle eklenmedi.
- Mevcut bir Bize Ulaşın akışı bulunmadı; e-posta adresi veya boş ekran uydurulmadı.
- Sonradan gönderilen `1000172391.jpg` görseli incelendi ve tasarım düzeltmesi buna göre yapıldı. Görseldeki örnek kullanıcı bilgileri ve çalışmayan seçenekler kopyalanmadı. Referanstaki switch yerine mevcut çalışan Sesli Asistan açılışı korunmuştur. Fiziksel cihaz görüntüsüyle pixel karşılaştırması yapılmadı.

## Rol görünürlük testi

Gerçek ModerationAccess sınıfı, JVM üzerinde test amaçlı Android View/Auth taklitleriyle çalıştırıldı; canlı hesap veya cihaz testi değildir.

| Senaryo | Sonuç |
| --- | --- |
| Normal kullanıcı / oturum yok / anonymous | YÖNETİM başlığı ve tüm satırlar GONE |
| moderation_admin=true | Yalnız Moderasyon Merkezi görünür |
| super_admin=true ve moderation_admin=true | İki yönetim satırı görünür |
| Claim değeri boolean yerine metin | Reddedilir |
| Token yenileme başarısız | Bölüm gizli kalır |
| Yetki eklenmesi/kaldırılması ve pencere odağı yenilenmesi | Zorunlu getIdToken(true) sonrası görünürlük güncellenir |
| Hesap değiştikten veya View ayrıldıktan sonra eski callback | Görünürlüğü açamaz |
| View ayrılması | Auth/focus listener'ları temizlenir |
| İki yönetim satırına tıklama | Doğru Activity intent hedefi |

## Build / static test sonucu

- 111 gerçek proje Java dosyası, Android API 34 sınıfları ve gerçek Firebase/AndroidX/Glide bağımlılıklarıyla Java 8 hedefinde ayrı derlendi: **başarılı**. Yalnız R sınıfı test için üretilmiştir; bu doğrulama Gradle APK build değildir. Mevcut deprecated/unchecked uyarıları vardır.
- Yeni on ikonun AAPT2 8.5.2 resource derlemesi: **başarılı**.
- 153 resource XML parse, Manifest hedefleri ve Java resource referansları: **başarılı**. default_web_client_id, mevcut Google Services yapılandırmasından üretilen kaynaktır.
- `python3 tests/settings07_check.py`: **başarılı**. Rol/lifecycle davranışı ve static bağlantılar doğrulandı.
- `node --test functions/test/moderation.android.test.js functions/test/moderator-management.android.test.js`: **4/4 başarılı**. Moderasyon ve yetki yönetimi sayfa/modelleri korundu.
- Kaynak ZIP karşılaştırması: **4 mevcut dosya değişti, 652 mevcut dosya aynı kaldı**. Firestore rules/index, bütün Supabase dosyaları, moderasyon/backend/operator kodu, Manifest, Gradle, SQLite ve mevcut logout uygulaması değiştirilmedi.
- Tam Gradle `:app:compileDebugJavaWithJavac`: **tamamlanamadı**; services.gradle.org DNS erişimi olmadığı için Gradle 8.7 indirilemedi.
- Tüm mevcut kaynakların AAPT2 derlemesi: **başarısız**. Kaynak ZIP'ten değişmeden gelen dört dosya PNG uzantılı olmasına rağmen JPEG imzası taşıyor: `drawable-nodpi/elek_hero_olive.png`, `kantar_toplam_miktar_36dp.png`, `para_toplam_tutar_36dp.png`, `trend_ortalama_fiyat_24dp.png`. Elek'e dokunulmaması talimatı nedeniyle değiştirilmedi. Bu mevcut sorun tam resource build için açık kalmaktadır.
- Küçük cihazda gerçek ekran görüntüsü, fiziksel cihaz tıklama/çıkış testi ve APK çalıştırması yapılmadı. Esnek ölçüler, ScrollView/contentHost ayrımı, sabit navigasyon ve bağlantılar kaynak/derleme düzeyinde doğrulandı; cihazda görsel onay hâlâ gereklidir.

Test kayıtları bu klasördedir. Bu görevde backend/deploy testi tekrarlanmadı; backend dosyalarının kaynakla birebir korunması doğrulandı.
