# Meydan gerçek iç içe yanıt – FINAL

Bu paket yüklenen Zeytin-Defteri-FINAL-Mesaj-Yanitla-UI.zip üzerine hazırlanmıştır. Tasarım, navigasyon, Firebase projesi ve mevcut veriler korunmuştur.

## Değişen kaynaklar

| Dosya | Değişiklik |
|---|---|
| app/src/main/java/PostRepliesActivity.java | Gerçek yorum hedefi, composer üzerinde iptal edilebilir yanıt göstergesi, ekran yeniden oluşturulmasında hedef/draft saklama, iç içe görünüm, yüklenen alt yanıt sayısı, gönderilen mesaja kaydırma. |
| app/src/main/java/CommentCloudRepository.java | Mevcut add metoduna geriye uyumlu overload; dört alanın aynı comment dokümanına yüklenmesi/indirilmesi; bekleyen yerel parent buluta ulaşmadan child yayınlamama. Mevcut listener, limit, pagination ve outbox korunur. |
| app/src/main/java/DatabaseHelper.java | DB_VERSION 18 → 19. Dört TEXT DEFAULT '' kolonunun mevcut addColumnIfMissing/ALTER TABLE migration yoluyla eklenmesi. Parent aynı post içinde doğrulanır; root/yanıtlanan kişi bilgisi SQLite parent kaydından alınır. Eski metod imzaları korunur. |
| app/src/main/java/CommentThread.java | Yeni küçük, Android bağımsız O(n) ağaç sıralayıcı. Döngü ve derin ağaçlarda iteratif çalışma, yüklenen aktif alt yanıt sayısı. |
| firestore.rules | Dört optional alan whitelist/type/size kontrollerine eklendi. Yeni child aynı post içindeki mevcut parent'a, doğru root ve kullanıcıya bağlanmalı. Güncellemede owner, created_at ve thread ilişkisi değiştirilemez. |

Test kaynakları: tests/threaded/CommentThreadTest.java, RunThreadTests.java, check_roundtrip.py.
Build günlüğü: BUILD-THREADED-SONUCU.txt.

## Kalıcı ilişki ve geriye uyumluluk

Konum değişmedi: social_posts/{postCloudId}/comments/{commentCloudId}.
Alanlar: parent_comment_id, root_comment_id, reply_to_uid, reply_to_name.
Yeni root: parent boş, root kendi cloud_id değeri; child: parent tıklanan yorumun cloud_id değeri, root üst yorumdan devralınır. FirebaseAuth UID yazan kişinin kimliği olarak kalır. Hedef kişinin bilgisi kullanıcının serbest yazdığı composer metninden alınmaz.

Eski belgelerde alanlar bulunmuyorsa veya yerel okuma null döndürürse parent boş/root kendi cloud_id kabul edilir. Mevcut bulut belgeleri topluca değiştirilmedi, taşınmadı veya yeniden yazılmadı. Mevcut cloud_id değerleri değişmedi. Dört yeni SQLite alanı eski satırlarda boş varsayılanla açılır; tablo/veri silme ve cascade eklenmedi.

× yanıt hedefini temizler, yazılan metni korur. Normal gönderi YANITLA aksiyonu root moduna döner. Yalnızca SQLite'a başarılı kayıt sonrası composer/hedef temizlenir; kalıcı outbox bağlantı geldiğinde aynı cloud_id ile gönderir. Başarısız kayıt hedef ve metni korur.

## Görünüm ve silme

Kökler ve kardeşler created_at ASC sırasındadır; eşit tarihlerde cloud_id belirleyici sıralama sağlar. Görsel girinti seviye başına 24 dp, en fazla 2 seviye/48 dp'dir. Veri derinliği bu sınırdan bağımsızdır. Sayılar yalnızca yüklenen aktif alt yanıtları kapsar.

Mevcut soft-delete korunur. Silinen bir parent'ın yüklenmiş çocukları varsa “Bu yanıt silindi.” yer tutucusu gösterilir; çocuklar silinmez. Pagination nedeniyle parent henüz yüklenmediyse çocuk görünür kalır ve üst yanıtın bu sayfada yüklenmediği belirtilir. Önceki yanıtlar yüklendikçe ilişki cloud_id haritasından yeniden kurulur. Her kart için ek Firestore sorgusu/listener açılmaz. Fiziksel olarak eski bir istemci tarafından silinen veya hiç yüklenmeyen parent'ın içeriği uydurulmaz.

## Korunan sistemler

SocialCloudRepository, ReactionManager, Firebase/Auth/Storage yapılandırması, google-services.json, FCM cihaz/token/servis kodu, functions klasörü, Manifest, diğer ekranlar ve tema dosyaları kaynak ZIP ile byte düzeyinde aynıdır. Bildirim backend'i eklenmedi/değiştirilmedi. Mevcut FCM davranışı gönderi sahibine yöneliktir; reply_to_uid üzerinden yeni bildirim yönlendirmesi bu görevde yapılmadı. Yeni dependency yok.

## Canlı kullanım için gerekli adım

**Yeni firestore.rules mevcut Firebase projesine yayımlanmalıdır. Bu görevde deploy yapılmadı.** Eski rules hasOnly kontrolü yeni alanları reddeder; bu durumda mesajlar yerel outbox'ta bekler. Firebase Console'da mevcut rules dosyası yayımlanabilir veya yetkili ortamda yalnızca `firebase deploy --only firestore:rules --project MEVCUT_PROJE_ID` çalıştırılabilir. Functions deploy veya yeni ücretli backend bu değişikliğin koşulu değildir.

## Doğrulama

- 73 Java kaynak dosyasında Java 8 sözdizimi: hata yok. Bu kontrol Android type-check/full build değildir.
- Gerçek CommentThread.java Java 8 hedefiyle derlendi; 20 assertion geçti: root/child/grandchild, sıralama, sayılar, eski/null parent, eksik parent, silinmiş parent, döngü, tekrar çizim, 10.000 seviye ve girinti sınırı.
- Mevcut 18 SQLite migration/owner/dedup/pending-delete fixture geçti.
- Ek SQLite kontrolleri: dört kolon, tekrar güvenli migration, eski içerik/kimlik korunması, disk cache yeniden açıldığında ilişkiler, sahiplik filtresi, parent silinince çocukların korunması; dört alanın upload/download eşlemesi geçti.
- 73 XML parse ve statik kaynak referansı kontrolü geçti; Manifest değişmedi.
- `./gradlew assembleDebug --no-daemon` denendi; Gradle 8.7 indirme aşamasında exit 1 ile sona erdi. Günlük yalnızca indirme satırını içeriyor; ayrıntılı hata nedeni yok. Android derleme/resource merge aşamasına ulaşılmadı. Build başarılı değildir; APK üretilmedi.
- Canlı iki cihaz/Firebase hesabı, Firestore Rules emulator, fiziksel cihaz klavye/görsel testleri bu ortamda yapılmadı. Alanların iki yöndeki eşlemesi ve kalıcılığı yerel olarak kontrol edildi; gerçek cihaz senkronizasyonu doğrulanmış gibi sunulmaz.

Canlı kabul testi: rules yayımlandıktan sonra A root gönderir; B o yorumda YANITLA ile child gönderir; A child'a yanıt verir; iki cihazda aynı ağaç görülür. Uygulamayı yeniden açma, × iptali, offline/online geçiş ve kendi yorumunu silme ayrıca denenmelidir.
