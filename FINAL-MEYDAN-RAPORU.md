# Zeytin Defteri — FINAL Meydan / Green Theme

## Yapılanlar
- Meydan hero fotoğraf alanı ve büyük nabız/özet kartları ekrandan kaldırıldı. Kategori, mevcut gerçek piyasa özeti ve kompakt mesaj listesi korundu.
- Gönderi/yanıt tıklamaları yeni PostRepliesActivity ekranına gider. Ana mesaj, tarihli yanıtlar, önceki yanıtları yükleme ve sabit mesaj alanı eklendi. Manifest: exported=false, adjustResize.
- Ana Meydan kutusu mevcut addPost veri yoluyla doğrudan seçili kategoriye metin kaydeder. CreatePostActivity ve eski medya desteği korunur.
- CommentCloudRepository: social_posts/{postCloudId}/comments/{commentCloudId}, ilk/sonraki sayfa 100 kayıt, Activity görünürken tek snapshot listener. onStop kaldırır; oturum değişiminde durur.
- Firebase UID doğrulaması, boş/500 karakter üstü yanıt kontrolü, gönder tuşu kilidi, kendi yanıtını silme eklendi.
- SQLite kalıcı outbox: sync_state 0=eski yerel, 1=bekleyen, 2=senkronize. Kendi eski kayıtları sabit cloud_id ile gönderilir; başka hesap ve anonim kayıtlar sahiplenilmez. 50 kayıtlık gönderim dilimleri kullanılır. Firestore SDK çevrimdışı kuyruğu aynı belge kimliğine yazar.
- Silme is_active=0 bulut tombstone olarak yayılır; yerel satır korunur. Eski bir cached snapshot bekleyen silmeyi geri açamaz. Sayfa limitinden düşen belgeler silinmiş sayılmaz. Eski sayfalardaki değişiklikler ilgili sayfa yeniden alındığında görünür.
- Ana postların sabit kimlikli bekleyen gönderimleri sonraki Meydan açılışında yeniden denenir. Başka kullanıcının postunu mevcut UID ile sahiplenerek yükleme engellendi.
- colors.xml esas palettir; mevcut zd_* isimleri korunur. GreenTheme, Java sabit/string renklerini aynı kaynaklardan okur. ZeytinApplication yalnızca uygulama Resources nesnesini tutar. Temel ekranlar, ortak drawable/layout renkleri, dialog/input teması ve orb paleti güncellendi. Yeni bağımlılık yok.

## Migration ve veri koruma
DB_VERSION 17 -> 18. post_comments için cloud_id/sync_state ve posts için sync_state eksikse eklenir. Var olan yorum cloud_id değerleri korunur; yalnızca boş olanlara UUID atanır. Unique index ve bekleme indeksi vardır. Yeni kurulumdaki eksik comment cloud_id hatası giderildi. Migration DROP/DELETE kullanmaz. Mevcut fiziksel kullanıcı veritabanına erişilmedi.

google-services.json, storage.rules, app/build.gradle ve gradle.properties kaynak ZIP ile byte-byte aynı. Firebase projesi, koleksiyon adları, özel veri izolasyonu ve asistan komutları değiştirilmedi.

## Firestore rules — uygulanması gerekli
/users/{uid} izolasyonu korunur. Comment oluşturma/güncelleme için auth UID, alan tipi/uzunluk, belge/parent kimliği ve sahiplik kontrolü eklendi. Yalnızca sahip silebilir. Mevcut post delete kuralındaki request.resource kontrolü düzeltildi (delete isteğinde bu nesne yoktur).

Bu dosya canlı Firebase projesine DEPLOY EDİLMEDİ. Yetkili Firebase hesabında mevcut projeyi doğruladıktan sonra bu ZIP'teki firestore.rules uygulanmalı. Firebase CLI hazırsa proje klasöründe `firebase deploy --only firestore:rules --project MEVCUT_PROJE_ID` kullanılabilir; MEVCUT_PROJE_ID yerine google-services.json içindeki mevcut project_id kullanılmalı. Mevcut kurallar uygulanmadan yorum yazma/okuma permission-denied alabilir; yorumlar telefonda korunur. Kurallar uygulandıktan sonra Meydan'a tekrar girilerek bekleyen kayıtlar yeniden denenir.

## Doğrulama
- 69 Java kaynak dosyası JDK parser ile Java 8 sözdizimi kontrolünden geçti. Bu Android/Firebase tip çözümlemeli derleme değildir.
- 69 XML dosyası ve Manifest parse edildi; merkezi renklerde döngü yok. Statik kaynak/Activity kontrolünde eksik manuel kaynak yok. default_web_client_id Google Services Gradle eklentisinin ürettiği kaynaktır; yapılandırmada web OAuth istemcisi mevcuttur.
- Mevcut host testleri: 55 komut + 25 veri adaptörü + 165 final parser/scope + 36 final veri/dönem + 21 bugün danışmanı + 30 asistan oturum/UI politikası + 36 hava ölçüm/cache simülasyonu + 14 SQLite kullanıcı/sezon fikstürü geçti.
- Yeni tests/meydan/check_schema.py: 18 yeni kurulum/migration/veri koruma/unique/owner/bekleyen silme fikstürü geçti. Gerçek kullanıcı verisi kullanılmadı.
- Gradle assembleDebug denendi. Gradle 8.7 indirmesi services.gradle.org için UnknownHostException ile durdu. Android SDK ve Firebase bağımlılıklarıyla gerçek derleme tamamlanamadı; APK üretilmedi. build başarılı iddiası yok.

## Cihazda ve canlı Firebase'de doğrulanması gerekenler
İki ayrı hesapla yanıtın karşı cihaza gelmesi, diğer kullanıcının silme/yazma reddi, offline gönderme/silme-sonra-online, uygulama yeniden başlatılınca kuyruk, gerçek Google login, eski kullanıcı verileri, reaction ve tüm navigasyonlar, klavye/input/kontrast görsel kontrolü. Firebase Emulator/rules testleri çalıştırılamadı. Nadir/eski ekranlarda bazı ikincil renk tonları kalabilir. Bu paket tam kaynak projedir; doğrulanmış mağaza release APK'sı değildir.

## Değişen veya eklenen dosyalar
- app/src/main/AndroidManifest.xml
- app/src/main/java/AssistantOrbDrawable.java
- app/src/main/java/CommentCloudRepository.java
- app/src/main/java/CommentManager.java
- app/src/main/java/CreatePostActivity.java
- app/src/main/java/DatabaseHelper.java
- app/src/main/java/DatabaseSchema.java
- app/src/main/java/ElekArchiveActivity.java
- app/src/main/java/GardenHomeManager.java
- app/src/main/java/GreenTheme.java
- app/src/main/java/MarketActivity.java
- app/src/main/java/OperationHistoryActivity.java
- app/src/main/java/PostMediaManager.java
- app/src/main/java/PostRepliesActivity.java
- app/src/main/java/ReactionManager.java
- app/src/main/java/ReportActivity.java
- app/src/main/java/SaleActivity.java
- app/src/main/java/SocialCloudRepository.java
- app/src/main/java/SubscriptionActivity.java
- app/src/main/java/ZeytinApplication.java
- app/src/main/java/com/zeytindefteri/app/AddGardenActivity.java
- app/src/main/java/com/zeytindefteri/app/AssistantVoiceWaveView.java
- app/src/main/java/com/zeytindefteri/app/BrineActivity.java
- app/src/main/java/com/zeytindefteri/app/GardenDetailActivity.java
- app/src/main/java/com/zeytindefteri/app/GardensActivity.java
- app/src/main/java/com/zeytindefteri/app/LocationPickerActivity.java
- app/src/main/java/com/zeytindefteri/app/LoginActivity.java
- app/src/main/java/com/zeytindefteri/app/MarketChartView.java
- app/src/main/java/com/zeytindefteri/app/OnboardingActivity.java
- app/src/main/java/com/zeytindefteri/app/ProducerFeedActivity.java
- app/src/main/java/com/zeytindefteri/app/ProfileActivity.java
- app/src/main/java/com/zeytindefteri/app/WelcomeActivity.java
- app/src/main/java/com/zeytindefteri/app/ZdNav.java
- app/src/main/java/com/zeytindefteri/app/ZeytinAssistantController.java
- app/src/main/java/com/zeytindefteri/app/ZeytinAssistantOrbView.java
- app/src/main/res/drawable/bg_card.xml
- app/src/main/res/drawable/bg_market.xml
- app/src/main/res/drawable/bg_nav_item.xml
- app/src/main/res/drawable/bg_nav_item_active.xml
- app/src/main/res/drawable/bg_onboarding_garden_card.xml
- app/src/main/res/drawable/bg_onboarding_premium_card.xml
- app/src/main/res/drawable/bg_onboarding_saved.xml
- app/src/main/res/drawable/bg_search.xml
- app/src/main/res/drawable/bg_soft_circle.xml
- app/src/main/res/drawable/bg_welcome.xml
- app/src/main/res/drawable/bg_welcome_bottom_panel.xml
- app/src/main/res/drawable/bg_welcome_feature_circle.xml
- app/src/main/res/drawable/bg_welcome_step.xml
- app/src/main/res/drawable/ui_bottom.xml
- app/src/main/res/drawable/ui_card.xml
- app/src/main/res/drawable/ui_chip_inactive.xml
- app/src/main/res/drawable/ui_header_circle.xml
- app/src/main/res/drawable/ui_hero_card.xml
- app/src/main/res/drawable/ui_quick.xml
- app/src/main/res/drawable/ui_search.xml
- app/src/main/res/drawable/ui_soft_green.xml
- app/src/main/res/layout/activity_create_post.xml
- app/src/main/res/layout/activity_elek.xml
- app/src/main/res/layout/activity_elek_archive.xml
- app/src/main/res/layout/activity_garden_detail.xml
- app/src/main/res/layout/activity_location_picker.xml
- app/src/main/res/layout/activity_main.xml
- app/src/main/res/layout/activity_onboarding.xml
- app/src/main/res/layout/activity_privacy.xml
- app/src/main/res/layout/activity_terms.xml
- app/src/main/res/layout/activity_welcome.xml
- app/src/main/res/values/colors.xml
- app/src/main/res/values/themes.xml
- firestore.rules
- tests/meydan/check_schema.py
