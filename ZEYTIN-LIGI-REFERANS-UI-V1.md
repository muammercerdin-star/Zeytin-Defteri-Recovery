# Zeytin Ligi Referans UI V1 — teslim notları

Kaynak: Zeytin-Defteri-FINAL-Zeytin-Ligi-Dinamik-UI-V1.zip. 9 Eylül 2026.

## Değiştirilen / eklenen dosyaların tamamı
- app/src/main/java/com/zeytindefteri/app/ZeytinLeagueView.java — yalnızca sunum: kompakt hero, filtre/sezon satırı, yatay görselli kartlar, gerçek kart sayısına bağlı seçili nokta, altı sütunlu tablo, satır açma/kapatma, yatay kişisel özet, küçük yenileme ikonu.
- app/src/main/res/drawable/ic_league_refresh.xml — yeni vector yenileme ikonu.
- ZEYTIN-LIGI-REFERANS-UI-V1.md — bu teslim ve doğrulama notları.

## Görünüm
Hero normal yazı boyutunda 124 dp; kısa ekran ve erişilebilirlik yazı ölçeğine göre sınırlanır. Mevcut zeytinlik görseli ImageView üzerinden yatay çevrilerek zeytin dalı sağda gösterilir; kaynak bitmap değiştirilmez. Koyu katman metin okunurluğunu destekler.

Filtreler solda kaydırılabilir pill satırı, sezon sağdadır. Tüm Türkiye ve Harita mevcut desteklenmeyen durumunu korur. Puan sıralaması salt sunumdur, alternatif sıralama üretmez.

Bölge kartları en az 132 dp genişliktedir. Geniş ekranda yaklaşık dört kart, dar telefonda yaklaşık iki–üç kart görünür; kalanlara yatay kaydırılır. Büyük erişilebilirlik yazılarında kartlar genişler. Dört kartı 360 dp telefona zorla sığdırıp metni okunamaz hale getirmek tercih edilmedi. Görsel dekoratif ortak zeytinlik görselidir, bölgeye ait doğrulanmış fotoğraf iddiası taşımaz. Bölge puanı mevcut public sözleşmede bulunmadığı için uydurulmaz; mevcut veri durumu gösterilir.

Noktalar gerçekten yüklenmiş bölge sayısından üretilir, seçili bölgeye bağlıdır ve tıklanabilir. Ek bölgeler mevcut sayfalama düğmesiyle gelir.

Top 20 altı ağırlıklı sütunda backend sırasıyla gösterilir. İlk yedi satır görünür; daha çok satır varsa Tüm Listeyi Gör mevcut yüklenmiş satırları açar. Sunucu sırası yeniden hesaplanmaz. İlk üç için altın/gümüş/bronz sunum uygulanır. Public modelde fotoğraf olmadığı için kişi fotoğrafı eklenmez. Boş tabloda sahte satır yoktur.

Kişisel sıra ve üç ana metrik aynı yatay banttadır. Yerel veride Ön Puan etiketi, yerel önizleme açıklaması ve ikincil detaylar korunur. Kesin sıra yoksa sıralama numarası uydurulmaz. Yenileme aynı begin(localData) çağrısını küçük vector düğmeden yapar.

## Korunanlar
Kaynak ZIP ile byte karşılaştırmasında yukarıdaki View dışında hiçbir mevcut dosya değişmemiştir. Repository, Score, Functions, attestations/operatör kodu, rules, database, SessionManager, ProducerFeedActivity, Asistan ve navigasyon aynıdır. View içindeki attach/detach, kullanıcı kontrolü, timeout, snapshot dinleyicisi, expiry ve yenileme metotları değişmemiştir. Bölge sayfalama callback'i ve kişisel sonuç seçme koşulları korunmuştur. Demo rakamlar veya üreticiler çalışan UI'ya eklenmemiştir.

## Çalıştırılan kontroller
- Node 22 ile demo-zeytin-league Auth + Firestore emülatöründe mevcut test/*.test.js: 34 PASS, 0 FAIL, 0 SKIPPED. Üretim Firebase kullanılmadı.
- Bunlar gerçek Android parser sözleşmesi ve #37, 25 üretici/Top 20/#21, çok bahçe/tek üretici, sezon/bölge ayrımı, aykırı fiyat, gerçek Java puan eşitliği (40 vaka), evidence onay/iptal, private rank/rules ve snapshot retry senaryolarını kapsar. Mevcut test kaynakları değiştirilmedi.
- npm run check: PASS, backend JavaScript sözdizimi.
- 77 Java dosyası: parser ile 0 sözdizimi hatası. Bu tam Android derlemesi değildir.
- 105 Android XML dosyası parse edildi; yeni vector ve View drawable referansları mevcut. Manifest değiştirilmedi.
- assembleDebug DENENDİ, BAŞARISIZ/ORTAM ENGELİ: Gradle 8.7 indirilirken java.net.UnknownHostException: services.gradle.org. Android compile/resource linking aşamasına ulaşılmadı. APK üretilmedi.

## Doğrulama sınırı
Gerçek cihaz/emülatörde Android UI render, dokunma testi ve pixel karşılaştırması yapılamadı. Tasarım referansa göre kodlandı; birebir görsel eşitlik veya production-ready APK doğrulandığı iddia edilmez. Kullanıcının telefondaki gerçek veritabanına erişilmedi. 522 kg / 31.276 TL hesaplamasını yapan veri katmanı değişmeden kaldı; sayısal eşleşme ve sözleşme testleri geçti. Telefonda bu değerlerin görünümü ayrıca kontrol edilmelidir.

AndroidIDE'de tam projeyi açıp assembleDebug çalıştırdıktan sonra dar/geniş ekran ve büyük yazı boyutunda hero, filtre/sezon, bölge seçimi, boş tablo, gerçek Top 20 ve kişisel kartı kontrol edin. Alt navigasyon/Asistan için cihaz dokunma regresyonu da gereklidir.

Canlı Firebase'e deployment yapılmadı. Kullanıcı kaydı silinmedi, veri tabanı açılıp değiştirilmedi. ZIP tam Android projesi ile önceki backend/test/rules/deployment belgelerini içerir. node_modules, emülatör günlükleri ve geçici çalışma scriptleri pakete dahil edilmez.
