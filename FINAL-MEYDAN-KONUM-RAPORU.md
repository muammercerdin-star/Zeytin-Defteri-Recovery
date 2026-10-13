# Meydan hızlı mesaj konum düzeltmesi

Kaynak olarak bu mesaja eklenen Zeytin-Defteri-FINAL-Meydan-Ana-Akis-Thread.zip kullanıldı; son ana akış tasarımı korundu.

## Neden

SocialCloudRepository.createTextPost hızlı mesajda aktif bahçeyi okumuyor, kullanıcıya özel olmayan user_province/user_district/user_neighborhood ayarlarını kullanıyordu. Bu ayarlar boş olduğunda yeni postun konumu boş kalıyordu. CreatePostActivity'nin bahçe seçimi üzerinden konum aktarımı zaten mevcuttu. Firestore upload/import ve FeedManager.PostItem alan eşlemesi de mevcuttu.

## Değişen dosyalar

- app/src/main/java/SocialCloudRepository.java: Yalnızca yeni hızlı post oluşturulurken mevcut home_active_garden_id:{Firebase UID} ayarı okunur. Bahçe id ve owner_uid birlikte doğrulanır. province/district/neighborhood aynı bahçeden alınarak mevcut addPost metoduna verilir. İl/ilçe/mahalle farklı kaynaklardan karıştırılmaz. Aktif seçim yoksa, bozuksa, bahçe silinmişse, başka kullanıcıya aitse veya alanlar null ise konum boş bırakılır. Cursor try-with-resources ile kapatılır. Upload/download metotları değiştirilmedi.
- app/src/main/java/com/zeytindefteri/app/ProducerFeedActivity.java: Mevcut place metodu ilçe/il değerlerinin kenar boşluklarını temizler; dolu district, yoksa province gösterilir. Mevcut tarih • kategori • konum metadata satırı ve tüm tasarım korunur.
- tests/location/check_location.py: Gerçek konum SQL sorgusu için sahiplik/eksik veri testleri ve mevcut upload/import/PostItem/UI alan eşlemesi kontrolleri.

Build günlüğü: BUILD-KONUM-SONUCU.txt.

## Davranış

Aktif bahçenin Manisa / Alaşehir / Afşar değerleri yeni postun province / district / neighborhood alanlarına kaydedilir. Akıştaki konum etiketi Alaşehir olur. İlçe boşsa il gösterilir. Konum yoksa ayraç veya sahte yer adı eklenmez. Bu örnek isimler uygulama koduna eklenmedi; yalnızca test verisidir.

Bahçe seçimi değiştirilirse yalnızca bundan sonra oluşturulan postların konumu etkilenir. Önceki postlara geriye dönük konum eklenmez. Kaydedilen post konumu mevcut SQLite → Firestore → SQLite → PostItem akışında taşınır; görüntüleme anında güncel bahçeden tekrar türetilmez.

## Korunanlar

Kaynak ZIP karşılaştırmasında yukarıdaki iki mevcut Java dosyası dışında tüm mevcut dosyalar byte düzeyinde aynıdır. DatabaseHelper, DatabaseSchema, DB_VERSION, eski veriler, threaded alanlar, PostRepliesActivity, CommentCloudRepository, ReactionManager, FCM/Functions, rules, google-services.json, Firebase projesi, resources ve Manifest değişmedi. Yeni migration, dependency, collection veya Firebase deploy gereksinimi eklenmedi. Eski Firestore belgeleri topluca değiştirilmedi.

## Kontroller ve sınırlar

- 73 Java dosyası Java 8 sözdizimi kontrolünden hatasız geçti; bu Android type-check/full build değildir.
- XML dosyaları parse edildi; resources/Manifest kaynakla aynıdır.
- Gerçek seçili bahçe SQL sorgusu testleri geçti: doğru kullanıcı/bahçe, başka kullanıcı bahçesinin reddi, bulunmayan bahçe, yalnızca il, null alanlar.
- Üç alanın upload/import/PostItem eşlemesi ve district → province UI önceliği kontrol edildi.
- ./gradlew assembleDebug --no-daemon denendi. Gradle 8.7 indirme aşamasında exit 1 ile sonlandı; neden services.gradle.org için UnknownHostException (DNS/ağ erişimi). Android build/APK doğrulanmadı.
- Canlı Firebase ve iki fiziksel cihaz erişimi olmadığı için uygulamayı yeniden açma, buluttan geri indirme ve farklı cihazda görüntüleme testleri gerçekleştirilmedi. Mevcut eşleme kodu doğrulandı; canlı senkronizasyon başarıyla test edilmiş gibi sunulmaz.

Cihaz kabul kontrolü: Ana sayfadan konumu dolu bir bahçeyi aktif seç; Meydan hızlı composer ile yeni mesaj gönder; metadata konumunu kontrol et. Aynı postu diğer cihazda mevcut senkronizasyonla aç. İlçesi boş fakat ili dolu bahçeyi ve konumsuz bahçeyi de dene. Önceden konumsuz oluşturulan postların boş kalması beklenen davranıştır.
