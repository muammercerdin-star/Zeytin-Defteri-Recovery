# İş Emri 06.3D — Android Yetki Yönetimi

Ana kaynak yalnız `Zeytin-Defteri-IS-EMRI-06-3C-SUPER-ADMIN-YETKI-BACKEND-FINAL.zip` dosyasıdır. 06.3D uygulandı; başka moderasyon özelliğine geçilmedi. Canlı yayınlama, gerçek hesapta rol atama ve cihaz üzerinde doğrulama yapılmadı.

## Android dosyaları ve Ayarlar

Yeni dosyalar:

- `app/src/main/java/com/zeytindefteri/app/ModeratorManagementActivity.java`
- `app/src/main/java/com/zeytindefteri/app/ModeratorManagementState.java`
- `functions/test/moderator-management.android.test.js`

Değişen mevcut dosyalar:

- `ProducerFeedActivity.java`: mevcut Moderasyon Merkezi'nin altına 16 dp üst boşlukla Yetki Yönetimi kartı eklendi. Önceki profil/engellenen kullanıcılar düğmeleri aynı kaldı.
- `ModerationAccess.java`: aynı görünürlük yapısı Super Admin kartına uyarlandı; foreground dönüşü için pencere odak yenilemesi ve listener temizliği eklendi.
- `AndroidManifest.xml`: yeni Activity `android:exported="false"` olarak kaydedildi.

Mevcut kaynak dosyalarından yalnız bu üçü değişti; diğer 611 dosya byte düzeyinde aynıdır. 06.3C Functions/operator kodları, şikâyet, engelleme, ModerasyonActivity/ModerationPage, rules, SQLite, Piyasa, Elek, Lig ve diğer ekranlar değiştirilmedi. Yeni test ve bu rapor klasörü dışında backend değişikliği yoktur.

## Görünürlük ve token yenilemesi

Kart başlangıçta GONE'dur; dolayısıyla normal kullanıcı/moderatör hesabında marj dahil boşluk bırakmaz. `super_admin` yalnız Boolean true ise görünür. Normal kullanıcının ve yalnız moderation_admin taşıyan moderatörün erişimi yoktur.

Ayarlar kartları ekrana bağlandığında ve pencere yeniden odaklandığında `getIdToken(true)` ile yenilenir. Bu, Ayarlar'a yeniden girişte ve uygulama arka plandan öne döndüğünde moderatör rolünü günceller. Kart ayrılınca auth/focus listener'ları kaldırılır; gecikmiş eski cevaplar generation ve UID kontrolüyle atılır. Oturum/token yenilemesi başarısızsa kart gizli kalır. Revoke nedeniyle oturumu iptal olmuş hesapta yeniden giriş gerekebilir; uygulamayı silmek gerekmez.

Yeni Activity her onStart'ta zorunlu token yeniler. Yetki doğrulanmadan yalnız bekleme görünür; yetkisiz hesapta kapanır. Arka plana giderken kullanıcı kartları temizlenir, dialog kapanır ve bekleyen cevaplar geçersizleşir. Hesap değişiminde ekran kapanır. FLAG_SECURE hassas kullanıcı bilgilerinin ekran yakalamalarında görünmesini engeller.

Android claim kontrolü yalnız görünürlük içindir. Asıl yetkilendirme mevcut 06.3C callable backend'indedir; eski token, kaldırılan rol, disabled hesap ve iptal edilen oturum kontrolleri orada korunur.

## Kullanıcı bulma, grant/revoke ve liste

| Ekran işlemi | Mevcut callable | Girdi |
|---|---|---|
| Tam e-posta/UID ile bul | moderationLookupUser | email veya uid |
| Moderatör Yap | moderationGrantModerator | target_uid |
| Yetkiyi Kaldır | moderationRevokeModerator | target_uid |
| Listeyi Yenile / Daha fazla yükle | moderationListModerators | limit=25, gerektiğinde cursor |

Bölge `europe-west1`; istek zaman aşımı 30 saniyedir. Tek arama alanı tam e-postayı veya UID'yi kabul eder. İsim/kısmi kullanıcı dizini taraması yapılmaz. E-posta biçimindeki özel bir UID için isteğe bağlı `uid:` öneki desteklenir; normal kullanımda önek gerekmez.

Arama kartında ad, e-posta, tam UID, hesap durumu ve Türkçe rol görünür. Super Admin hedefinde rol değiştirme düğmesi yoktur. Devre dışı normal hesaba grant düğmesi gösterilmez. Liste Super Admin'i rol etiketiyle gösterebilir; bu satırda revoke düğmesi yoktur. Liste UID'yi kısaltır; onay dialogu doğru hedefi ayırt etmek için tam UID'yi gösterir.

Grant ve revoke öncesi hedef bilgili onay dialogu vardır. Başarılı backend cevabının hedef UID ve yeni rol bilgisi doğrulanır; seçili kart hemen güncellenir. Revoke edilen kişi listeden çıkarılır, ardından liste sunucudan yenilenir. Başarısız istekte başarı sonucu uydurulmaz. İşlem sürerken kontroller kapalıdır. Pagination cursor'ı korunur, aynı UID tekrar eklenmez; boş ama next_cursor içeren sayfadan devam edilebilir.

Arama ve liste için ayrı bekleme/hata metinleri vardır. Ham exception metni gösterilmez. Kullanıcı bulunamadığında sade mesaj; yetki reddinde ekran kapanması; diğer hatalarda “İşlem tamamlanamadı. Tekrar deneyin.” kullanılır. Arama veya liste yenileme düğmesiyle tekrar denenebilir.

Android'de setCustomUserClaims, Firebase kullanıcı dizini okuma, Firestore rol/audit yazımı ve hardcode hesap UID'si yoktur.

## Test sonuçları

| Kontrol | Sonuç ve sınırı |
|---|---|
| Tüm Functions suite, Auth + Firestore emülatörleri | 88 test; 86 PASS, 2 FAIL, 0 SKIP |
| Yeni 06.3D testleri | 2 PASS; gerçek Java model çalıştırma ve kaynak bağlantı kontrolü |
| 06.3B mevcut Android model/bağlantı testleri | PASS |
| 06.3C rol güvenliği ve gerçek Auth/Firestore testleri | PASS |
| Functions syntax | PASS |
| Yeni Activity/model, ModerationAccess ve mevcut ModerationActivity/ModerationPage ayrı Java derlemesi | PASS; Android 14 android-all ve gerçek Firebase Auth 24.2.0 / Functions 22.0.0 sınıflarıyla |
| Tam Gradle Android derlemesi/APK | DOĞRULANAMADI; Gradle 8.7 indirilirken services.gradle.org için UnknownHostException |
| Fiziksel Android / Android UI emülatörü | ÇALIŞTIRILMADI; görsel yerleşim ve uçtan uca telefon davranışı cihazda kontrol edilmeli |

Ayrı Java derlemesinde üretilen R ve uygulama palette erişimi GreenTheme için küçük test tanımları kullanılmıştır; Android/Firebase API'leri taklit edilmemiştir. Bu kontrol tam uygulama derlemesi veya ekran render testi değildir. Eksik annotation sınıfı ve Java 8 bootstrap classpath için iki uyarı vardır; derleme exit code 0'dır.

İki başarısız test, önceki 06.3C raporunda da bulunan `blocks.rules.test.js:30` ve `reports.rules.test.js:68` offline transaction beklentileridir. `disableNetwork()` sonrasında transaction reddedilmesi beklenirken başarılı olur. 06.3D bunları değiştirmedi/atlamadı. Bu iki testin başarısız adımdan sonraki assertions'ları çalışmaz; tüm suite veya offline davranış için PASS iddiası yoktur. Önceki 06.3C logları reports/06.3C altında aynen korunmuştur.

Yeni Java model testleri şunları çalıştırır: normal/moderatör/Super Admin ayrımı; tam e-posta ve UID istekleri; grant/revoke sonrasında kart/liste rolü; Super Admin hedef koruması; liste cursor/dedup; boş sayfa; hatalı cevabın state'i kısmen bozmaması. Activity erişim gate'i, onaylar, loading/error metinleri, lifecycle, callable bağlantıları, token refresh ve GONE/spacing kaynak düzeyinde kontrol edilmiştir. Bunlar telefonda yapılan testler olarak sunulmaz.

## Cihaz kabul kontrolü

06.3C işlevleri yayımlanmış test ortamında ve bootstrap/index geçişi tamamlandıktan sonra:

1. Normal kullanıcı ve moderatör hesabında Ayarlar'a girin: Yetki Yönetimi görünmemeli, boşluk bırakmamalı. Moderatörde mevcut Moderasyon Merkezi görünmeli.
2. Uygulama içi instrumentation ile Activity'yi normal/moderatör hesabında açın: kartlar gösterilmeden kapanmalı. Dış uygulamadan açma exported=false nedeniyle engellenmeli.
3. Super Admin ile Ayarlar'a girin: Yetki Yönetimi görünmeli. Açıp tam e-posta ve ayrı olarak tam UID ile aynı hesabı bulun.
4. Normal hesabı Moderatör Yap: önce iptal edip rolün değişmediğini, sonra onaylayıp kartın Moderatör olduğunu doğrulayın.
5. Liste içinden Yetkiyi Kaldır: onay sonrası satır kalkmalı; aynı kişi arama kartındaysa Normal Kullanıcı olmalı.
6. Super Admin hedefini arayın ve listede kontrol edin: hiçbir rol değiştirme düğmesi olmamalı.
7. İkinci telefonda hedef kullanıcı Ayarlar'a yeniden girsin: verilen rol sonrası Moderasyon Merkezi belirmeli. Revoke sonrası yenilemede gizlenmeli; oturum iptali halinde yeniden giriş yapılmalı.
8. İnterneti kapatıp arama/liste/işlem deneyin: teknik hata veya crash olmamalı; bağlantı geri gelince düğmelerden yeniden denenebilmeli.
9. İstek sürerken geri gidin, arka plana geçin veya hesabı değiştirin: eski kullanıcı bilgisi yeniden açılan ekrana taşınmamalı.
10. 25'ten fazla indeks kaydıyla devam sayfası; uzun isim/e-posta, büyük yazı boyutu, dar ekran ve mevcut Moderasyon Merkezi liste/status davranışını kontrol edin.

Token API referansı: [FirebaseUser.getIdToken](https://firebase.google.com/docs/reference/android/com/google/firebase/auth/FirebaseUser).
