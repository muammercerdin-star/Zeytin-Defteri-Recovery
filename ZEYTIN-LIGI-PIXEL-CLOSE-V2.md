# Zeytin Ligi Pixel-Close V2

Kaynak: Zeytin-Defteri-FINAL-Zeytin-Ligi-Referans-UI-V1.zip.

## Tam değişiklik listesi
1. app/src/main/java/com/zeytindefteri/app/ZeytinLeagueView.java
2. app/src/main/java/com/zeytindefteri/app/ProducerFeedActivity.java
3. ZEYTIN-LIGI-PIXEL-CLOSE-V2.md (bu rapor)

Activity değişikliği tek görünürlük koşuludur: mevcut HOME gizleme davranışına REPORTS eklenmiştir. Başka bölüme geçildiğinde mevcut koşul başlığı geri gösterir. Alt navigasyon, Intent ve Asistan kodları değiştirilmemiştir.

## UI
- Hero normal yazı ölçeğinde 150 dp, erişilebilirlik/kısa ekran koşullarında 140–170 dp. Mevcut sağa çevrilmiş ortak zeytinlik görseli, kupa ve yeni istenen açıklama korunarak kullanılır.
- Dış boşluklar 8 dp yatay, 4 dp üst olarak daraltılmıştır.
- Bölge kartları normal yazı ölçeğinde 88–110 dp. Büyük erişilebilirlik yazısında okunurluk için genişler. Gerçek kart sayısı korunur. 360 dp ekranda yaklaşık 3.7, 400 dp ekranda yaklaşık 4 kartlık alan oluşur; 5 kart her telefon genişliğinde garanti edilmez.
- Kart görseli 54 dp; metin/iç boşluklar ve nokta satırı kısaltılmıştır. Ortak görsel dekoratiftir, doğrulanmış bölge fotoğrafı değildir.
- Filtre/sezon mevcut yatay satırdadır. Dar ekran veya büyük yazıda filtre alanı kaydırılabilir; sezon sağda kalır. Tüm Türkiye/Harita desteklenmediğinden pasiftir.
- Başlık en fazla iki görsel satır; tam metin erişilebilirlik açıklamasında korunur. Tablo satırları kompakt, normal ölçekte en az 34 dp. Backend sırası ve mevcut yedi satır / tüm liste kontrolü korunur.
- Kişisel sonuç yatay bantta; yerel Ön Puan ve kesin sıra ayrımı korunur. İkincil tutar/kalite/hacim/fiyat bilgileri Ayrıntılar düğmesiyle açılır. Veri hesabı değişmez.
- Büyük yenileme yazısı yok; aynı yenileme çağrısına bağlı küçük vector düğme korunur. Hata mesajı bağlantı sorununu gizlemez, yerel sonuç global olarak sunulmaz.

## Koruma denetimi
Kaynak ZIP ile byte karşılaştırması: yukarıdaki iki Java dosyası dışında mevcut dosyalar aynıdır. Repository, Score, ElekRepository, DatabaseHelper, SessionManager, Functions, attestations/operatör, rules, snapshot ve veri modelleri değiştirilmemiştir. View attach/detach, owner kontrolü, yükleme/timeout, snapshot listener ve yenileme metotları birebir korunur. Sahte üretici/bölge/istatistik eklenmemiştir.

## Test ve build
- 77 Java dosyasının sözdizimi: 0 hata (parser; tam compile değildir).
- 105 XML parse kontrolü ve kullanılan drawable adları: PASS. Manifest değişmemiştir.
- Android assembleDebug denendi: Gradle 8.7 indirmesinde UnknownHostException: services.gradle.org. Kaynak derleme aşamasına ulaşılmadı, APK üretilemedi.
- Gerçek cihaz/Android emülatöründe UI çizimi ve dokunma testleri yapılamadı. Pixel eşitliği, yazı taşması ve navigation dokunma davranışı cihazda ayrıca doğrulanmalıdır.
- Telefonda bulunan gerçek SQLite dosyası kullanılmadı. 522 kg / 31.276 TL / 59,92 TL/kg veri akışını sağlayan kod değişmedi. Cihazdaki kayıtların görünümü ayrıca kontrol edilmelidir.

Canlı Firebase'e deployment yapılmadı. Mevcut kullanıcı verileri açılıp değiştirilmedi veya silinmedi. Paket tam Android projeyi, mevcut backend/test/rules ve deployment dokümantasyonunu içerir; node_modules, emülatör günlüğü ve geçici script içermez.

Mevcut Node 22 backend testleri demo-zeytin-league Auth + Firestore emülatörlerinde tekrar çalıştırıldı: **34 PASS, 0 FAIL, 0 SKIPPED**. Android parser sözleşmesi/#37, Top20/#21, Java puan eşitliği, owner/sezon/bölge izolasyonu, evidence onay/iptal ve snapshot retry testleri dahil. Üretim Firebase kullanılmadı.
