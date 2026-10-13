# Güvenli FCM — son paket raporu

## Durum
Android client ve güvenli Cloud Function kaynakları eklendi. FUNCTION VE RULES DEPLOY EDİLMEDİ. Gerçek cihazdan cihaza push henüz doğrulanmadı. Bu dosya paket içindeki önceki çalışmaların raporlarından daha günceldir.

## Android
- Mevcut sabit sürümlü bağımlılık düzenine firebase-messaging:24.1.2 eklendi; BOM/diğer bağımlılıklar değiştirilmedi.
- ZeytinMessagingService: onNewToken/onMessageReceived, sabit meydan_replies kanalı, mevcut ic_bell_line ikonu, API 24+ Notification.Builder, immutable/update-current PendingIntent.
- Android 13+ izin: giriş yapmış kullanıcı ana ekrana geldiğinde bir kez POST_NOTIFICATIONS istenir. Red uygulamayı engellemez; tekrar açmak Android uygulama bildirim ayarlarından mümkündür.
- users/{uid}/devices/{SHA256(token)}: token, session_id, platform=android, updated_at (server timestamp). Çoklu cihazlar ayrı belgelerde durur. Auth UID ve mevcut SessionManager hesabı kontrol edilir. Token yenilenince eski cihaz belgesi temizlenmeye çalışılır; uygulama açılışında kayıt tazelenir.
- Logout/misafir geçişinde yerel oturum kimliği anında geçersizleştirilir, görünen bildirimler temizlenir, eski cihaz belgesi silinmeye çalışılır. Offline/izin nedeniyle uzaktan silme başarısız olabilir; data-only payload recipient_uid ve session_id kontrolleri eski hesabın metninin gösterilmesini önler. Cihaz kayıtları 90 gün tazelenmezse gönderime dahil edilmez.
- Push'a özgü tercihler cihaz yedeğine/transferine dahil edilmez; diğer verilerin mevcut yedek davranışı korunur.
- Bildirim tıklaması PushOpenActivity -> cloud post kimliğinin tek belge sorgusuyla SQLite id'ye çevrilmesi -> PostRepliesActivity. Ana Meydan geri dönüşü kurulur. Aynı yanıt ekranı görünürken ayrıca bildirim gösterilmez. Eski hesap/oturum bildirimi açılmaz.

## Backend ve güvenlik
functions/index.js: social_posts/{postId}/comments/{commentId} onDocumentCreated v2 trigger, Node 22, europe-west1. Parent gönderiden alıcı UID, Firebase Auth'tan yazar adı okunur. Kendi kendine yanıt ve silinmiş yanıt/gönderi atlanır. Yorum kaydı bildirim işleminden bağımsızdır.

Tokenlar 100'lük sayfalarda alınır; en çok 10 paralel gönderim yapılır. Geçersiz token yalnızca halen aynı token/session ise silinir. Geçici hata yeniden denenir. push_deliveries altında sunucuya özel teslim makbuzu/lease ile aynı comment+cihaz+oturum tekrarları takip edilir. İstemciler bu koleksiyona erişemez.

FCM gönderimi ve makbuz kaydı atomik değildir: gönderimden sonra sunucu çökerse tekrar FCM gönderimi olabilir. Android UID/post/comment hash ile 7 günlük yerel dedup ve sabit notification tag kullanır. Tam exactly-once garantisi verilmez; çökme/işletim sistemi kısıtları bildirim kaybına yol açabilir. Data-only mesaj TTL 24 saattir. Force-stop ve işletim sistemi kısıtlarında teslim garanti değildir.

Rules: devices sadece sahibine açıktır; token/platform/session/timestamp/izinli alanlar doğrulanır. Mevcut /users/{uid} diğer alt koleksiyonlarının sahip izolasyonu korunur; geniş wildcard devices yolunu dışlar, böylece dar kural atlanamaz. Comment kuralları aynı kalır. APK'ya server key, private key veya FCM HTTP göndericisi eklenmedi.

## Manuel kurulum / deploy
1. Mevcut Firebase projesi: zeytin-defteri. google-services.json değiştirilmedi. Yeni proje oluşturmayın.
2. Yetkili bilgisayarda Node 22 ve Firebase CLI kurulu olmalı. Projenin Cloud Functions deploy için Blaze planında olması gerekir; bu çalışma ücretli planı açmadı veya değiştirmedi.
3. Proje kökünden:

```sh
npm --prefix functions ci
npm --prefix functions test
firebase login
firebase deploy --only firestore:rules,functions:meydanReplyCreated --project zeytin-defteri
```

Mevcut uzak rules dosyasına bu kaynak dışında değişiklik yapıldıysa deploy öncesi birleştirin. Firebase CLI API/IAM önkoşullarını bildirirse mevcut proje üzerinde tamamlayın. Function runtime hesabının Firestore okuma/yazma, Auth kullanıcı okuma ve FCM mesaj gönderme izinlerini doğrulayın; istemciye servis hesabı anahtarı vermeyin. Function bölgesi europe-west1 seçildi; üretim Firestore konumuyla uygunluğunu deploy öncesi kontrol edin.
4. Android projesini build edip iki telefona kurun; farklı hesaplarla giriş ve bildirim izni verin. A'nın gönderisine B yanıt yazsın; A'da açık/arka plan durumunu ve dokunma hedefini deneyin. Kendi yanıtı, çift event, offline, hesap değişimi ve token yenileme de test edilmeli.
5. İsteğe bağlı sunucu temizliği: push_deliveries koleksiyonu için expires_at TTL alanını etkinleştirin. Kodda alan var ancak TTL politikası deploy edilmedi. TTL etkin değilse teslim makbuzları otomatik silinmez.

## Kontroller ve sınırlar
- 72 Java kaynak dosyası JDK Java 8 parser ile sözdizimi kontrolünden geçti. Bu tam Android tip çözümlemeli build değildir.
- XML/resource/Activity/Service statik kontrolleri temiz; Google Services üretilen default_web_client_id ayrı ele alındı.
- functions npm lock dosyası oluşturuldu ve npm ci tamamlandı. Gerçek firebase-admin/firebase-functions modülleri yüklendi ve v2 trigger export'u doğrulandı.
- 7 Node testi geçti: politika + gerçek trigger kodunun mock servislerle replay/self/transient/invalid-token senaryoları. Gerçek Firestore Emulator/rules testleri veya canlı FCM testleri değildir.
- Host Node sürümü 24.19.0, deploy hedefi Node 22. npm bu fark için engine uyarısı verdi; Node 22 üzerinde ayrıca doğrulayın.
- Gradle assembleDebug denendi; services.gradle.org DNS/UnknownHostException yüzünden Gradle 8.7 indirilemedi. Android build/Manifest merge doğrulanamadı, APK yok.
- Firebase CLI ve yetkili deploy oturumu bulunmadı; function/rules deploy edilmedi.
- google-services.json, Storage kuralları, SQLite şeması/veri metotları, CommentCloudRepository, ReactionManager, renkler ve temalar yüklenen ZIP ile aynı. SocialCloudRepository'ye yalnızca tek gönderi okuma metodu eklendi; mevcut sync korunur.

## Resmi başvuru kaynakları
- https://firebase.google.com/docs/cloud-messaging/android/get-started
- https://firebase.google.com/docs/functions/firestore-events
- https://firebase.google.com/docs/functions/get-started
- https://firebase.google.com/docs/functions/manage-functions

## Değişen/eklenen dosyalar
- app/build.gradle
- app/src/main/AndroidManifest.xml
- app/src/main/java/PostRepliesActivity.java
- app/src/main/java/PushOpenActivity.java
- app/src/main/java/PushRegistration.java
- app/src/main/java/SocialCloudRepository.java
- app/src/main/java/ZeytinApplication.java
- app/src/main/java/ZeytinMessagingService.java
- app/src/main/java/com/zeytindefteri/app/ProducerFeedActivity.java
- app/src/main/java/com/zeytindefteri/app/ProfileActivity.java
- app/src/main/java/com/zeytindefteri/app/SessionManager.java
- app/src/main/res/xml/push_backup_rules.xml
- app/src/main/res/xml/push_data_extraction_rules.xml
- firebase.json
- firestore.rules
- functions/index.js
- functions/package-lock.json
- functions/package.json
- functions/policy.js
- functions/test/policy.test.js
- functions/test/trigger.test.js
