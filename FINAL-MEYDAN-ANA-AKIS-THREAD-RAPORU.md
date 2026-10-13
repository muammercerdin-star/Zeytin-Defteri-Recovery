# Meydan ana akış thread görünümü – FINAL

Kaynak: Zeytin-Defteri-FINAL-Threaded-Replies.zip. Bu çalışma yalnızca ana akış çizimi, yerel yorum önizlemesi ve mevcut yanıt ekranına hedef aktarımıdır.

## Değişen dosyalar

| Dosya | Değişiklik |
|---|---|
| app/src/main/java/com/zeytindefteri/app/ProducerFeedActivity.java | Büyük dış kart kaldırıldı. Avatar/ince çizgi, ad, tarih/kategori/konum, mesaj balonu, mevcut reaction, YANITLA ve saat satırı eklendi. Gönderiler 20 dp boşlukla ayrılır. Bir toplu SQLite okumasıyla görünür gönderilerin son aktif yanıtı ve yerel sayısı alınır. Önizleme 24 dp girintili gösterilir. Tüm yanıtlar bağlantısı aynı PostRepliesActivity'yi açar. |
| app/src/main/java/PostRepliesActivity.java | Yalnızca ilk açılış için focus_composer ve reply_parent_cloud_id Intent bilgileri ele alınır. Önizlemedeki YANITLA, mevcut selectReply metodunu kullanarak aynı cloud_id'li yorumu seçer. Yorum artık yoksa başka yorumu hedeflemek yerine bilgi verilir. Ekran yeniden oluşturulurken mevcut draft/hedef saklama davranışı korunur. |
| tests/feed_thread/check_preview.py | Gerçek Java içindeki SQLite sorgusunu çıkarıp fixture verileriyle çalıştırır. |

Ayrıca bu rapor ve BUILD-MEYDAN-THREAD-SONUCU.txt eklendi.

## Önizleme ve veri sınırı

Ana akışta gösterilen en fazla 50 gönderinin yerel post_id değerleri tek sorguya verilir. Mevcut post_comments tablosundan is_active=1 yorumlar gruplanır; created_at en yeni olan seçilir. Eşit tarihlerde cloud_id belirleyici sıralamadır. Gönderi başına tek önizleme gösterilir. Seçilen yorum bir alt yanıtsa reply_to_name ile kime yanıt verdiği belirtilir. Tıklama hedefi gerçek comment cloud_id değeridir. Eski/null parent alanları güvenli boş değer olarak okunur.

Ek Firestore sorgusu veya listener YOKTUR. Yeni backend, koleksiyon, migration, index veya rules değişikliği YOKTUR. Yerel anahtarlar mevcut post tablosunun kimlikleri olduğu için ayrı bir post_cloud_id eşleme sorgusu gerekmemektedir. Reaction verisi aynı ReactionManager üzerinden gelir; yorumlar için sahte reaction oluşturulmadı.

**Önemli sınır:** Kaynak projede ana akış postları senkronize eder; comment senkronizasyonu ilgili PostRepliesActivity açıkken çalışır. Bu nedenle bir yanıtın metni o cihazın SQLite önbelleğine henüz indirilmemişse ana akışta gösterilemez. Mevcut yanıt ekranı açılıp senkronizasyon tamamlandıktan sonra Meydan'a dönüldüğünde önizleme yenilenir. Ana akışta yenileme tek başına uzaktaki bütün yorumları indirmez. Sürekli anlık ana akış yorum senkronizasyonu bu UI çalışmasında eklenmedi ve çalışıyor diye raporlanmaz. Önbellek önizlemesi o sırada uzaktaki en son yorumdan eski olabilir.

## Görünüm ve davranış

- Dış kart yok; sadece içerik balonunda mevcut reply_surface/reply_border renkleri kullanılır. Üst bölüm, Piyasa şeridi, kategori filtreleri, alt composer, navigasyon ve asistan korunur.
- Gönderi içeriği en fazla 5, önizleme 3 satır gösterilir; tamamı mevcut yanıt ekranında açılır.
- Ana gönderide YANITLA root composer'ı açar. Önizlemede YANITLA o gerçek yorumu hedefler. Balona ve tüm yanıtlar bağlantısına dokunmak konuşmayı açar.
- Avatarlarda mevcut baş harfleri ve giriş yapmış kişinin mevcut profil tercihlerindeki fotoğraf kullanılır; başkalarının mevcut yorum modelinde bulunmayan fotoğraf adresleri uydurulmaz, profil başına bulut sorgusu açılmaz.
- Kaynak ProducerFeedActivity'de üç nokta öğesinin ayrı bir düzenleme/silme işleyicisi yoktu. Bu yetkiler bu UI görevinde eklenmedi; mevcut mesaja dokunarak konuşmayı açma davranışı korunur. PostRepliesActivity içindeki kendi yorumunu silme menüsü değişmedi.

## Korunanlar

Kaynak ZIP ile byte karşılaştırmasında yalnızca yukarıdaki iki mevcut Java dosyası değişmiştir. DatabaseHelper, DatabaseSchema, CommentCloudRepository, SocialCloudRepository, CommentThread, ReactionManager, parent/root/reply alanları, Firebase Auth/Firestore/Storage, google-services.json, firestore.rules, storage.rules, FCM/token/Functions, Manifest ve resources aynıdır. Bu UI paketi ek Firebase deploy gerektirmez; önceki threaded sistemin kurulu güvenlik kurallarını kullanır.

## Kontroller

- 73 Java dosyasında Java 8 sözdizimi: hata yok. Android type-check/full build yerine geçmez.
- 73 XML dosyası parse edildi. Manifest ve resource dosyaları değişmedi; kullanılan reply renkleri mevcut kaynaklardır.
- Gerçek önizleme SQL testi geçti: son nested reply, silinmiş kayıtların dışlanması, eski/null parent, sayılar, görünür postlarla sınırlama, aynı tarihli yorumların kararlı seçimi, boş cache.
- Mevcut CommentThread.java Java 8 hedefiyle derlenerek 20 assertion geçti; threaded veri/sıralama sınıfı kaynakla aynıdır.
- Gradle assembleDebug denendi. Günlük Gradle 8.7 dağıtımı indirme satırında kaldı; Android derleme/resource merge aşamasının tamamlandığı doğrulanamadı. Başarılı build veya APK yok; günlük kesin kök neden ayrıntısı vermiyor.
- Gerçek Android cihaz/emülatör üzerinde görsel, klavye, navigasyon ve iki hesaplı canlı Firestore testleri yapılamadı. Bu davranışlar cihazda doğrulanmış gibi sunulmaz.

Cihazda kabul kontrolü: Meydan'da eski/yeni postları aç; reaction ve kategori değiştir; cache'i bulunan bir yanıtın önizlemesini gör; önizlemedeki YANITLA'nın doğru kişiyi seçtiğini kontrol et; × ile iptal et; diğer cihazda yazılan yorumu ilgili mevcut yanıt ekranında senkronize et ve Meydan'a dönerek önizlemenin güncellendiğini doğrula.
