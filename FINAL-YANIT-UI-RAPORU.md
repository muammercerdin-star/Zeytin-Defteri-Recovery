# Mesaj Yanıtla — UI yenilemesi

Son çalışma yalnızca UI ve mevcut Meydan filtrelerine yönlendirme kapsamındadır. Bu turda referans görsel eki görünmedi; kullanıcı mesajındaki ayrıntılı tasarım tarifi esas alındı.

## Değişen kaynak dosyaları
- app/src/main/java/PostRepliesActivity.java: büyük dış yorum kartları yerine avatar/ince bağlantı çizgisi/kimlik-metadata/kompakt mesaj balonu/aksiyon satırı. Kendi yorumunda üç nokta -> silme mevcut comments.delete metodunu kullanır. Diğer kullanıcının menüsünde silme yok. Başlangıç verisi ve liste sorgusu aynı; comments.add/listen/loadOlder/stop/close ve FCM görünür gönderi işaretlemesi korunur. Sabit composer, koyu input, kategori seçici, adjustResize ve draft geri yükleme korunur. Kategoriler kaydırılan içerikte yer alır; klavye açılınca sabit başlık/composer dışındaki alan kaydırılabilir. Metinler doğal satır sayısıyla büyür. Mevcut Glide ile kendi kayıtlı/Google profil fotoğrafı kullanılır; fotoğrafı olmayan kullanıcılar baş harfleriyle gösterilir. Yeni profil sorgusu/listener eklenmez.
- app/src/main/java/com/zeytindefteri/app/ProducerFeedActivity.java: yalnızca izin verilen open_category Intent değerini mevcut activeType filtresine aktaran küçük yönlendirme eklendi. Ekran tasarımı, nav/asistan ve veri sorguları değişmedi.
- app/src/main/res/values/reply_colors.xml: sadece yanıt ekranına ait #092532 arka plan, #102B39 mesaj yüzeyi, #294654 border. Global renkler/temalar değişmedi.

## Mevcut modelin sınırları
Yorumlar düz bir gönderi konuşmasıdır; yorum reaction tablosu ve yorumun altına alt yorum modeli yoktur. Bunlar eklenmedi, sahte reaction/alt yanıt sayaçları üretilmedi. Ana gönderide mevcut ReactionManager'ın gerçek reaction verisi gösterilir. Yorumlarda YANITLA mevcut konuşmanın composer alanına odaklanır; ayrı alt yorum oluşturmaz.

Kategori chipleri ve alttaki kategori seçici ilgili Meydan filtresine yönlendirir; mevcut yanıtın/gönderinin kategori alanını değiştirmez. Yazılmakta olan taslak varsa kategori geçişi engellenerek taslak korunur. Piyasa bağlantısı mevcut MarketActivity'ye gider; döviz/kripto/sahte fiyat eklenmez. Yanıt ekranının geri davranışı ve ana uygulamanın mevcut alt nav/asistanı korunur; yanıt ekranına ayrı bir kopya asistan veya yeni alt navigasyon eklenmez.

## Veri koruma doğrulaması
Kaynak ZIP karşılaştırmasında yukarıdaki üç kaynak dışındaki dosyalar aynı: google-services.json, Firebase Auth/FCM servisleri, Firestore/Storage rules, CommentCloudRepository, SocialCloudRepository, ReactionManager, DatabaseHelper/DatabaseSchema, functions, global renkler/temalar. Mevcut UID/cloud_id ve Firestore yorum akışı değiştirilmedi. SQLite migration veya veri değişikliği yok. Gerçek kullanıcı veritabanına erişilmedi.

## Test/build
72 Java dosyası JDK Java 8 sözdizimi parser kontrolünden geçti. XML/resource statik taramasında eksik kaynak yok; Manifest değişmedi ve parse edildi. Mevcut 18 SQLite migration/owner/duplicate fikstürü geçti. Bunlar tam Android derlemesi/cihaz testi değildir.

assembleDebug denendi. services.gradle.org UnknownHostException nedeniyle Gradle 8.7 indirilemedi; Android build/Manifest merge/APK doğrulanamadı. Gerçek cihazda klavye, küçük ekran, uzun mesaj, fotoğraf/reaction menüsü ve görsel görünüm doğrulanamadı. İki cihaz Firestore senkronizasyonu burada canlı test edilmedi; bulut dosyaları birebir korunarak mevcut akışa dokunulmadı. Önceki FCM deploy/gerçek push sınırları bu UI çalışmasıyla değişmedi.
