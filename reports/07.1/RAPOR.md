# İş Emri 07.1 — Ayarlar Son Cila / Kararlı İlk Render

Ana kaynak yalnız yüklenen Zeytin-Defteri-IS-EMRI-07-AYARLAR-PREMIUM-FINAL.zip'tir. İncelenen hata kaydı: 1000173180.mp4, yaklaşık 13,57 saniye. Tasarım bu kaynakta korunan son Ayarlar tasarımıdır.

## Videodaki kıpmanın sebebi

Kayıtta, özellikle yaklaşık 9–10,5 saniye aralığındaki Sohbet → Ayarlar girişinde, Yönetim yokken başlayan görünümün sonradan yönetim satırlarıyla uzadığı görülüyor. Önce tek satır, sonra ikinci satır görülebiliyor. Bu gözlem kaynak koduyla örtüşüyor:

1. Eski ModerationAccess her yönetim satırına ayrı AuthStateListener ve pencere odağı listener'ı kuruyordu. Her listener kendi getIdToken(true) isteğinden önce satırını GONE yapıyor, cevaptan sonra tekrar açıyordu. İki bağımsız yanıt farklı zamanlarda geldiğinde Yönetim kartı kademeli büyüyor ve DİĞER aşağı itiliyordu.
2. ProducerFeedActivity.refreshCurrentSection, Ayarlar açıkken de showSection çağırıyordu. showSection bütün contentHost'u temizleyip SettingsView'ı yeniden oluşturuyordu. onResume ve sosyal/bulut senkronizasyonu tamamlanma çağrıları bu yolu tekrar tetikleyebiliyordu. Böylece yükleme/render döngüsü ve scroll sıfırlanması tekrarlanıyordu.

## Düzeltme ve ilk render

- SettingsContentHost, tek getIdToken(true) yanıtından iki rolü birlikte çözüyor. Yanıt gelmeden SettingsView oluşturulmuyor; yalnız küçük, görünür bir “Ayarlar hazırlanıyor…” durumu gösteriliyor. Rastgele bekleme veya postDelayed yok.
- ModerationAccess.Access, mevcut boolean claim kurallarının değişmez UI sonucudur. Yeni yetki kuralı veya sunucu erişim modeli değildir. Mevcut backend kontrolü korunur.
- Tam sonuç hazır olduğunda SettingsView bütün yetkili satırlarıyla, ekrana bağlanmadan önce oluşturulur ve ana UI işinde tek içerik değişimiyle yerleştirilir. Normal kullanıcı için Yönetim View'ı hiç oluşturulmaz; boş başlık/alan/placeholder kalmaz.
- ModerationAccess.settingsSection artık ağ isteği/listener içermez ve satırları sonradan açıp kapatmaz.
- Ayarlar zaten contentHost içindeyse ProducerFeedActivity aynı ekranı kaldırıp yeniden kurmaz. İçerik parmak izi değişmediyse arka plan senkronizasyonu görünümü ve scroll'u aynen bırakır. Bu parmak izi yalnız profil/sezon/fotoğraf verisi içindir; yetki cache'i değildir.
- Ayarlar'a başka sekmeden her yeni girişte güncel rol doğrulaması yapılır. Aynı yükleme sırasında gelen attach/Auth/focus çağrıları birleştirilir. Normal odak/foreground yenilemesinde aynı kullanıcı için mevcut tam görünüm korunur; rol ve içerik sonucu aynıysa yeniden render yapılmaz. Gerçek rol veya profil/sezon değişimi varsa bütün içerik birlikte değiştirilir.
- Gerekli bütünsel yeniden render'da scroll konumu layout tamamlanınca, ilk çizimden önce geri uygulanır. Satır gizleme/açma animasyonu, gecikmiş scrollTo veya yapay zamanlama yoktur. İçerik kısalırsa Android geçerli scroll aralığına sınırlar.
- Token hatasında kullanıcı normal rol varsayılarak eksik bir Ayarlar ekranı açılmaz; kontrollü hata/tekrar dene görünümü kullanılır.

## Lifecycle / kullanıcı izolasyonu

SettingsRenderState yalnız ilgili View'ın istek nesli ve sahipliğini izler. Static veya kullanıcılar arasında paylaşılan rol cache'i yoktur. Firebase UID ve yerel Session UID/türü birlikte sahiplik kontrolüne katılır. Hesap değişince önceki hesabın içeriği hemen kaldırılır. Pause/detach isteği geçersizleştirir; geç gelen cevap generation, sahiplik, Activity ve attachment kontrollerinden geçmeden UI'ı değiştiremez. View ayrılınca AuthStateListener kaldırılır ve rol/içerik referansları temizlenir. Foreground'a dönüşte doğrulama yeniden yapılır.

## Son görsel cilalar

- Çıkış Yap ikonu ve başlığı: düşük doygunluklu kiremit #C17C70. Açıklama gri kalır. ProfileActivity ve mevcut logout uygulaması değiştirilmedi.
- Hesap/Uygulama/Diğer ikonları kontrollü mint; Üretim/Yönetim ikonları açık zeytin yeşili. Çıkış danger istisnasıdır. Bütün ikonlar tek renge çevrilmedi.
- Yalnız Yönetim mevcutken, onu izleyen DİĞER başlığının üst boşluğu 10 dp'den 7 dp'ye indirildi: 3 dp fark. Normal kullanıcı düzeninde gereksiz boşluk veya farklı azaltma yoktur.
- Kartlar, border/radius, profil, chevron, gerçek veriler, mevcut işlevler, alt navigasyon ve Asistan konumu korunmuştur.

## Regresyon sonuçları

Aşağıdaki tekrarlar gerçek SettingsContentHost, SettingsRenderState ve ModerationAccess Java kodları üzerinde; kontrollü Android View/Firebase Auth taklitleriyle çalıştırıldı. Cihazda 10 kez dokunulmuş veya canlı hesaplarla test edilmiş gibi raporlanmamaktadır.

| Test | Sonuç |
| --- | --- |
| Süper Admin: 10 giriş/çıkış | 10/10 geçti. Token öncesi kısmi Settings yok; iki satır ilk tam içerikte birlikte mevcut; giriş başına tek ilk render. |
| Moderatör: 5 giriş/çıkış | 5/5 geçti. Yalnız Moderasyon Merkezi; sonradan Yetki Yönetimi eklenmiyor. |
| Normal kullanıcı: 5 giriş/çıkış | 5/5 geçti. Yönetim başlığı/View/placeholder yok. |
| Aynı rol/veriyle focus ve senkronizasyon | Mevcut SettingsView nesnesi ve scroll korunuyor; ek render yok. |
| Rol veya profil/sezon değişimi | Tek bütünsel render; scroll çizimden önce geri uygulanıyor. |
| HOME / MEYDAN / GARDENS → PROFILE ve PROFILE → MEYDAN → PROFILE | Host attach/detach akış modeli geçti; gerçek yedi sekmenin Activity kaynak bağlantıları ayrıca kontrol edildi. |
| Pause/resume ve sırası bozulmuş eski yanıt | Eski nesil UI'ı değiştiremiyor. |
| Hesap/yerel session değişimi sırasında yanıt | Eski rol/içerik yeni hesaba sızmıyor. |
| Token hatası, tekrar dene, anonymous/oturum yok | Kontrollü görünüm; tahmini rol veya ham exception yok. |
| View ayrılması / listener temizliği | Geç yanıt render etmiyor; listener kaldırılıyor. |

`python3 tests/settings071_check.py` bütün bu senaryoları çalıştırır. Eski `tests/settings07_check.py` komutu aynı güncel regresyon paketine yönlenir. Testler fiziksel ekran piksel koordinatlarını veya gerçek Firebase gecikmesini ölçmez. Düzeltme sonrası cihaz ekran kaydı elde edilmedi; aynı 10/5/5 dokunma testi cihazda da yapılmalıdır.

## Değişen dosyalar

Mevcut dosyalar:
- app/src/main/java/com/zeytindefteri/app/ProducerFeedActivity.java
- app/src/main/java/com/zeytindefteri/app/ModerationAccess.java
- app/src/main/java/com/zeytindefteri/app/SettingsView.java
- tests/settings07_check.py
- functions/test/moderation.android.test.js
- functions/test/moderator-management.android.test.js

Yeni dosyalar:
- app/src/main/java/com/zeytindefteri/app/SettingsContentHost.java
- app/src/main/java/com/zeytindefteri/app/SettingsRenderState.java
- tests/settings071_check.py
- reports/07.1/ rapor ve test kayıtları

## Build / static doğrulama

- 113 gerçek proje Java dosyası, gerçek Android API 34/Firebase/AndroidX/Glide jar'ları ve Java 8 hedefiyle ayrı derlendi: PASS. Yalnız R sınıfı test için üretildi; uygulama sınıfları veya Firebase sınıfları bu derlemede stub değildir. Mevcut deprecated/unchecked uyarıları devam eder.
- Moderasyon/Yetki Yönetimi eski Android model ve bağlantı testleri: 4/4 PASS. Static assertion'lar yeni tek yükleyiciyi kontrol edecek şekilde uyarlandı.
- 153 resource XML parse ve bütün Java resource referansları: PASS. Resource dosyaları ve Manifest değişmedi; yeni resource eklenmedi.
- Kaynak ZIP karşılaştırması: 6 mevcut dosya değişti, 670 mevcut dosya birebir korundu. DatabaseHelper/SQLite, Gradle, Manifest, bütün resource'lar, Supabase, Firestore rules/index, operator ve backend dosyaları değişmedi. ProfileActivity/logout, alt navigasyon ve contentHost kabuk ölçüleri korunmuştur.
- Tam Gradle :app:compileDebugJavaWithJavac: tamamlanamadı. services.gradle.org DNS erişimi olmadığı için Gradle 8.7 indirilemedi. APK üretildiği iddia edilmez.
- Kaynak 07 raporundaki dört Elek PNG/JPEG biçim uyuşmazlığı bu kapsamda değiştirilmedi. Tüm AAPT resource derlemesi bu görevde tekrar çalıştırılmadı; yalnız değişmemiş resource/XML bağlantıları doğrulandı.
- Fiziksel cihazda son görünüm, tam APK ve canlı backend testi yapılmadı. Bu görevde backend/deploy değişikliği yoktur.
