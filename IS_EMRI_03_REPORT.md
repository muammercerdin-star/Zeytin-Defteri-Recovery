# İş Emri 03 — Gerçek Yerel Hatırlatmalar

Tarih: 2026-09-10. Uygulama kodu, host testleri ve paketleme tamamlanmıştır. Gerçek Android build ortam nedeniyle ENGELLİ; cihaz bildirim testleri ÇALIŞTIRILMADI. Bu durumlar PASS sayılmamıştır.

## 1. Kaynak ve korunan çalışma

Tek ana kaynak kullanıcının son gönderdiği `zeytin-defteri(1).zip` dosyasıdır. SHA-256: `490df38a7f0e02c163ee5be0b41e102e7d583288ce85dff0b27e5df9597b8ec9`. 384 kaynak dosyası vardır; giriş ZIP CRC testi başarılıdır. Eski ZIP'lerden kod/dosya taşınmamıştır. Kesinti sonrası mevcut İş Emri 03 dosyaları korundu; son aşamada regresyon, denetim, rapor ve paketleme tamamlandı.

Aşağıdaki RELEASE GATE dosyaları giriş ZIP'iyle byte düzeyinde aynıdır:
- GardenDetailActivity: Bahçe Karnesi ile ortak analytics kaynağı.
- GardenAnalyticsSnapshot: Hasat ↔ Elek tutarlılık hesapları.
- GardenAnalyticsActivity: baskın Elek açıklaması, toplam ve fark uyarısı.
- GardenAnalyticsRepository: mevcut hesap/veri akışı.

## 2. Mevcut mimari incelemesi

reminders alanları: `id`, `garden_id`, `title`, `reminder_type`, `due_at`, `is_done`, `repeat_rule`, `note`, `created_at`, `updated_at`, `owner_uid`, `cloud_id`. `due_at` epoch millis'tir. Ayrı `season_year`, `deleted_at` veya reminder tablosu içinde `sync_state` alanı yoktur. Silme mevcut `recordCloudDeletion` / `sync_tombstones` yoluyla yürür. SupportCloudRepository reminders için mevcut bulut senkronizasyonunu içerir.

Mevcut ReminderRepository bazı sorgu ve yazmalarında owner filtresi taşımıyordu; ekleme de owner/cloud kimliğini atamıyordu. Bu gerçek güvenlik eksikleri aynı repository içinde giderildi. Activity'ye ham SQL taşınmadı; ikinci reminder tablosu oluşturulmadı.

Android: minSdk 24, targetSdk/compileSdk 34. POST_NOTIFICATIONS ve sohbet FCM altyapısı önceden vardı. Yeni bağımlılık veya WorkManager eklenmedi. Uygulamanın mevcut FCM sistemi korunmuştur; yeni hatırlatma kodu FCM/Firebase kullanmaz.

## 3. Değiştirilen mevcut dosyalar

| Dosya | Neden |
|---|---|
| app/src/main/java/ReminderRepository.java | Ortak owner/owned-garden sorgu kapsamı, doğrulama, güvenli CRUD, scheduler için açık kayıt sorgusu ve koşullu sonraki tarih güncellemesi. Mevcut API adları korundu. |
| app/src/main/java/ZeytinApplication.java | Kanal oluşturma ve Activity dönüşünde yerel planların yeniden kontrolü; yeni ağ çağrısı yok. |
| app/src/main/java/com/zeytindefteri/app/SessionManager.java | Hesap değişimini bildirim gönderimiyle kilitleme; eski hesap alarm/bildirimlerini temizleme ve yeni oturumu yerel planlama. |
| app/src/main/java/com/zeytindefteri/app/GardensActivity.java | “Hatırlatmalar →” giriş noktası. Alt navigasyon değiştirilmedi. |
| app/src/main/AndroidManifest.xml | Dışa kapalı hatırlatma Activity/receiver kayıtları ve RECEIVE_BOOT_COMPLETED. Mevcut POST_NOTIFICATIONS tekrar eklenmedi. |
| app/src/main/res/xml/push_backup_rules.xml | Yalnız cihazın alarm/teslim metadata tercihlerinin yedekten dışlanması. |
| app/src/main/res/xml/push_data_extraction_rules.xml | Aynı cihaz metadata tercihlerinin bulut yedeği ve cihaz transferinden dışlanması. |

## 4. Yeni dosyalar

`app/src/main/java/com/zeytindefteri/app/` altında:
- LocalReminderScheduler.java: AlarmManager, kanal, bildirim, iptal, yeniden planlama, teslim tekrar koruması.
- ReminderReceiver.java: tek reminder ID alır; yerel kaydı doğrulayıp scheduler üzerinden işler.
- ReminderRestoreReceiver.java: boot, paket güncellemesi, saat ve timezone değişiminde yerel yeniden planlama.
- ReminderTime.java: günlük/haftalık/aylık takvim hesabı ve zaman doğrulaması.
- ReminderActions.java: başarılı SQLite yazmasını yerel alarm işlemine bağlayan koordinasyon.
- RemindersActivity.java: liste, oluştur/düzenle, tamamla/sil onayı, bahçeye geçiş, izin akışı.

`tests/reminders/` altında iki çalıştırılabilir test, README ve validation kanıtları; proje kökünde bu rapor.

## 5. DB / migration / kimlikler

**DatabaseHelper değiştirilmedi. DatabaseSchema değiştirilmedi. DB_VERSION 19 değişmedi. Migration yok.** Yeni sütun/tablo, DROP TABLE veya veritabanı sıfırlama yoktur. Eski kayıtlar topluca değiştirilmez. Düzenleme aynı ID üzerinde UPDATE yapar; owner_uid, cloud_id, created_at korunur. Yeni signed-in kayıtta mevcut UUID cloud_id standardı kullanılır; guest kimliği boş kalır. updated_at yerel değişimde güncellenir.

Silme yalnız kullanıcının açık onayıyla ilgili reminder'a uygulanır. Tombstone kaydı ile silme aynı SQLite transaction içinde yapılır; diğer child kayıtları taşınmaz/silinmez. Mevcut Firebase/sync yöneticileri ve cloud alanları korunmuştur.

## 6. Scheduler ve duplicate stratejisi

`AlarmManager.setAndAllowWhileIdle(RTC_WAKEUP, due_at, PendingIntent)` kullanılır; inexact alarmdır. Exact alarm veya özel exact-alarm izni YOKTUR. Bu üretici hatırlatmaları için ek zamanlama bağımlılığı eklemez; Android güç tasarrufu nedeniyle gecikme mümkündür. UI bunu açıklar.

Her ID için `zeytin-reminder://alarm/{id}` data URI ve explicit receiver hedefi kullanılır. requestCode 0 olsa da URI tüm long ID'leri ayrı kimliklendirir; ID int'e kırpılmaz. FLAG_IMMUTABLE kullanılır. Planlama önce aynı alarmı iptal eder, sonra tek plan oluşturur. Edit/complete/delete ilgili alarm ve görünür bildirimi iptal eder. Update yeni zamanı tekrar planlar; completed kayıt tekrar kurulmaz.

Preferences `local_reminder_delivery` yalnız OS plan kimliklerini ve son teslim edilen owner+due token'ını saklar. Başlık/not/bahçe verisi veya ikinci kayıt sistemi değildir; SQLite tek karar kaynağıdır. Teslim token'ı notify öncesinde kalıcı yazılarak tekrar gelen yayınlarda aynı occurrence tekrar üretilmez. Silme/düzenlemede ilgili token temizlenir. Yeni cihaza bu OS metadata taşınmaz.

## 7. Receiver ve notification

Receiver ID üzerinden repository'den yeniden okur. Başka owner, yabancı garden, tamamlanmış/silinmiş, geçersiz zaman veya desteklenmeyen tekrar kaydı bildirim oluşturmaz. Erken/eski edit yayını yeni tarihten önce bildirim üretmez. Başlık, yerel bahçe adı, not ve tarih/saat SQLite'tan gelir. PRIVATE kilit ekranı görünürlüğü ve gerçek Notification.Builder kullanılır.

Kanal: `zeytin_reminders`; ad: “Zeytin Defteri Hatırlatmaları”; açıklama: “Bahçe işlemleri ve üretici hatırlatmaları”. Kanal aynı ID ile güvenli biçimde oluşturulur; kullanıcı kanal ayarları sıfırlanmaz.

Bildirim tıklaması dışa kapalı RemindersActivity'yi ID ve owner bağlamında açar. Güncel oturumda kayıt yeniden okunur; eski kullanıcı bildirimi yeni kullanıcıya gösterilmez. Detaydan doğru bahçeye geçilebilir. PendingIntent explicit, immutable ve deterministic URI'lidir.

## 8. İzin davranışı

Android 13+ POST_NOTIFICATIONS izni hatırlatma başarıyla kaydedildikten sonra veya kullanıcı bildirimleri açmayı seçince istenir. Uygulama açılışında bu özellik yeni izin popup'ı açmaz. Reddedilince SQLite kaydı korunur; ekranda kapalı bildirim durumu gösterilir. Tekrarlı otomatik runtime izin isteği yerine kapalı izin için açıklama ve isteğe bağlı sistem ayarları bağlantısı vardır. Kanal/app bildirim kapalı durumları da kontrol edilir. İzin notify sırasında geri alınırsa SecurityException yakalanır; kayıt silinmez.

## 9. Reboot / geçmiş / tekrar / timezone

BOOT_COMPLETED, MY_PACKAGE_REPLACED, TIME_SET ve TIMEZONE_CHANGED bağlantıları vardır. Receiver'lar exported=false; directBootAware değildir. UserManager kilit kontrolüyle credential storage erişimi güvenli ertelenir. Yeni servis başlatılmaz; goAsync + tek yerel executor kullanılır.

Yeniden planlamada tamamlanmamış gelecekteki veya günlük/haftalık/aylık tekrar kaydı sorgulanır. Geçmiş tek-sefer kayıt reboot/app açılışında yeniden kurulmaz; listede bekleyen/geçmiş olarak kalır. Gerçekten teslim edilmiş ama güç tasarrufuyla gecikmiş alarm için üst sınır **6 saat**tir; daha eski occurrence sessiz geçilir. Bu grace, geçmiş kayıtları reboot'ta topluca bildirmez.

Tekrarlar NONE/DAILY/WEEKLY/MONTHLY'dir. Günlük/haftalık Calendar takvim günü ile ilerler; aylık kısa ayda son geçerli güne iner. due_at bir sonraki tarih olur; geçmişten birikmiş tekrarlar tek tek bildirilmez. Tamamlandı işareti tekrarları keser. Kısa ay sonrası sonraki tekrar yeni due_at gününden devam eder (31 Ocak → 28 Şubat → 28 Mart); ayrı “ayın son günü” kuralı icat edilmez.

due_at mutlak epoch zamanıdır; timezone değişikliği mevcut tek-sefer anını değiştirmez, gösterimi yerel timezone'a çevirir. Sonraki tekrar mevcut cihaz timezone'unda hesaplanır. Tarih/saat seçici geçmişi reddeder, ay/yıl sınırları ve sıfır/bozuk zaman güvenle ele alınır.

## 10. Owner / season / garden

Tüm ReminderRepository sorguları ve mutasyonları owner ile kapsamlıdır. Bahçe bağlıysa garden'ın aynı owner'a ait olması da zorunludur. Kaydı başka kullanıcı bahçesine taşımak reddedilir. Null/boş guest owner uyumluluğu vardır; foreign explicit owner kabul edilmez. Açılmış eski repository oturum değişirse veri döndürmez/yazmaz. Receiver ile SessionManager değişimleri aynı kilitte yürür; eski aktif alarmlar ve görünür bildirimler hesap değişiminde kaldırılır.

**reminders tablosunda season_year yoktur.** Mevcut hatırlatma davranışı rapor sezonundan bağımsız due_at tarihidir. Aktif rapor sezonunu değiştirmek hatırlatmayı başka sezona taşımaz veya susturmaz; UI'da bu açıklanır. Yeni sezona göre yapay filtre/sütun eklenmedi. İş Emri 00/01/02'nin season_year izolasyon sorguları aynen korunur ve regresyon testleri çalışır.

## 11. Offline / cloud / performans

Önce SQLite'a kayıt, sonra yerel scheduler çalışır; Firebase cevabı beklenmez. Yeni reminder sınıflarında Firestore/FCM/REST/Function/listener çağrısı veya yeni Gradle bağımlılığı yoktur. Mevcut uygulamanın sohbet FCM başlangıcı aynen kalmıştır; “projede hiç Firebase yok” iddiası yapılmaz.

Host testleri tam üretim repository/scheduler/time/receiver/session sınıflarını gerçek SQLite ve Android OS test doubles ile ağ servisi olmadan çalıştırır. Bu local kod yolunun kanıtıdır; uçak modunda gerçek cihaz teslimi henüz doğrulanmamıştır.

SQL/planlama executor üzerinde yürür. Scheduler yalnız açık future/repeating kayıtları getirir; tamamlanan/geçmiş tek-sefer SQLite geçmişini taramaz. Uygulama Activity dönüşündeki kontrol en fazla dakikada bir istenir; Hatırlatmalar ekranı ayrıca yeniler. Liste açıkça istendiğinde mevcut kayıtları gösterir. Cloud'dan gelen kayıtlar sonraki yerel yeniden kontrol/ekran dönüşü veya reboot ile planlara alınır; yeni cloud listener eklenmedi. Cihaz performans ölçümü yapılmadı.

## 12. Testler ve sonuçlar

| Grup | PASS | FAIL | SKIP | Açıklama |
|---|---:|---:|---:|---|
| İş Emri 03 runtime fixture | 39 | 0 | 0 | Gerçek SQLite + üretim Java repository/scheduler/receiver/time/session; OS servisleri test doubles |
| RELEASE GATE ek fixture | 7 | 0 | 0 | Hasat/Elek fazla, eksik, eşit, boş, veri eksik, tolerans |
| İş Emri 01 SQLite | 52 | 0 | 0 | Bahçe ID/owner/child, düzenleme, not, geçmiş |
| İş Emri 02 analytics fixture | 38 | 0 | 0 | Ağırlıklı fiyat, verim, maliyet, alacak/tahsilat, izolasyon, Report uyumu |
| Functions/backend | 30 | 0 | 4 | Mevcut atlanan testler PASS sayılmadı; backend kodu değişmedi |

Başlangıçta mevcut 15 test/kontrol komutu exit 0; finalde yeni iki testle 17/17 komut exit 0. Diğer mevcut asistan/UX, Elek form-popup-arşiv, akış, konum, Meydan, threaded, today ve npm syntax kontrolleri başarılı. Bu komut sayıları test assertion sayılarıyla toplanmaz. Ham sonuçlar `tests/reminders/validation/before` ve `final` altındadır.

İş Emri 03 testleri A–N kapsamını içerir: insert→schedule; edit cancel→tek alarm; complete/delete; tekrar schedule duplicate koruması; owner ve garden sınırı; mevcut sezon-bağımsız hatırlatma semantiği; eski tek-sefer; tekrar hesabı; sıfır/bozuk tarih; reboot idempotence/kilit; yerel kod yolu; izin reddi ve notify sırasında izin geri alınması. Gerçek UI dokunuşları/OS alarm teslimi bu testlerin kapsamı değildir.

Statik denetim: 89 Java kaynak dosyası Java 8 sözdizimi PASS; 105 XML parse, 32 Manifest bileşen hedefi, reminder permission/exported bağlantıları PASS. Bunlar APK compile/build değildir. PendingIntent immutable/URI kimliği ve notification tıklama ID'si üretim scheduler runtime testinde de çalıştırıldı.

## 13. Gerçek Android build

Komut: `java -cp gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain -Dorg.gradle.java.home=/usr/lib/jvm/java-17-openjdk-amd64 assembleDebug --no-daemon`.

Sonuç: **ENGELLİ / exit 1**. `java.net.UnknownHostException: services.gradle.org`; Gradle 8.7 dağıtımı indirilemedi. Hazır Android SDK android.jar bulunamadı. Java 17 host compiler mevcut; host testlerinde Java 8 hedefi kullanıldı. Projenin Gradle ayarları değiştirilmedi. Tam hata `validation/android-build.log` içinde. **APK üretilmedi. BUILD PASS değildir.**

## 14. Gerçek cihaz manuel test planı — HENÜZ ÇALIŞTIRILMADI

Ön koşul: SDK/Gradle erişimi olan ortamda assembleDebug başarıyla tamamlanmalı, APK telefona kurulmalı. Ayrı fixture/test hesabı kullanın. Aşağıdaki adımlar için cihaz sonucu kaydedilmeden PASS yazılmamalıdır.

1. Bahçelerim → Hatırlatmalar → Yeni hatırlatma. Bahçe, başlık, not, tek-sefer, şimdiden 3–5 dakika sonraki saat seçin. Kaydedin; listede görünmesini ve tekrar açıldığında kalmasını kontrol edin.
2. Android 13+ bildirim iznini kabul edin. İnterneti/uçak moduyla ağı kapatın, uygulamayı son uygulamalardan kapatın, ekranı kilitleyin. Sistem ayarlarından “Zorla durdur” kullanmayın; bu Android'de alarmları etkiler.
3. Bildirimi gözleyin; inexact alarm olduğundan tam dakikada geliş şart değildir. Doze/pil kısıtlarını ve gözlenen gecikmeyi kaydedin. “3 dakika oldu, gelmedi” tek başına exact-alarm testi değildir.
4. Bildirime dokunun: aynı owner'ın doğru reminder ayrıntısı açılmalı; Bahçeyi aç doğru garden_id'ye gitmeli. Not ve tarih doğru olmalı.
5. Tamamlandı işaretleyin ve onaylayın; bildirim/alarm kaldırılmalı. Tekrar/reboot sonrasında bildirim gelmemeli.
6. Edit: gelecekteki tek-sefer reminder'ı önceki saati gelmeden başka saate alın. Eski saatte bildirim olmamalı; yeni occurrence en fazla bir kez görünmeli.
7. Delete: gelecekteki reminder'ı silip onaylayın. Liste, alarm ve görünür bildirim kalkmalı; reboot sonrası dönmemeli.
8. Reboot: gelecekteki tek-sefer, tamamlanmış, silinmiş, eski tek-sefer ve açık tekrar kayıtlarını hazırlayın. Telefonu yeniden başlatıp kilidi açın. Yalnız geçerli açık future/tekrar planları kurulmalı; geçmiş kayıtlar bildirim yağmuru yapmamalı.
9. Owner: A ile reminder oluşturun ve görünür bildirim bırakın. B'ye geçin. A'nın bildirim/alarmları kalkmalı; listesi/notu B'ye görünmemeli. A'ya dönün, hâlâ gelecekteki açık kayıt yeniden planlanmalı. Guest ve çıkış için tekrarlayın.
10. İzin: uygulama bildirimini ve ayrıca kanalı kapatın. Reminder kaydedilmeli, açıklama görünmeli, crash olmamalı. İzni açtıktan sonra yeni gelecekteki reminder ile tekrar deneyin; geçmiş occurrence sonradan topluca gösterilmez.
11. Tekrar: günlük/haftalık/aylık kayıtlarda bir sonraki due_at ileri gitmeli; tamamlanınca tekrar kesilmeli. Ay sonu/yıl sonu/DST/timezone testlerini emülatör veya test cihazında yapın. Cihaz saatini değiştirmek diğer uygulamaları da etkileyebilir; test sonrası geri alın.
12. Eski ekranlar: Bahçe Detay/Karnesi ortak toplamları, Elek fark uyarısı, satış/tahsilat ve mevcut gezinmenin değişmediğini gözleyin. Aktif rapor sezonu değişse de tarihli hatırlatmalar görünür kalmalıdır.

## 15. Sınırlamalar ve kabul durumu

Kod ve host kontrolleri tamamlandı; gerçek APK/build ve cihaz/Doze/OEM teslim testleri kalan kabul kapılarıdır. Android alarm gecikmesi, üretici pil politikası, uygulamanın zorla durdurulması veya kapalı bildirim izni teslimi engelleyebilir. Altı saatten geç teslim edilen occurrence gösterilmez. Teslim token'ının yazılması ile notify arasında süreç ölürse duplicate önleme tercihi nedeniyle o occurrence kaçabilir; geçmiş olay otomatik tekrar gönderilmez. İzin kapalıyken geçen occurrence kaydı durur ama sonradan replay edilmez.

Cloud'daki doğrudan değişiklikler için yeni listener eklenmedi; yerel yeniden kontrol gerektiği yukarıda açıklanmıştır. Aylık clamp kalıcı gün kaymasına yol açabilir ve UI bunu “kısa ayda son geçerli gün” şeklinde belirtir. Geçersiz eski tekrar kuralı güvenli atlanır; düzenleme ekranında desteklenen bir tür seçilebilir. Bu sınırlar gizlenerek bütün cihaz kabul kriterleri PASS ilan edilmemiştir.

## 16. Referanslar ve teslim bütünlüğü

Android resmi dokümanları: [inexact AlarmManager ve Android zamanlama kısıtları](https://developer.android.com/develop/background-work/services/alarms), [Android 13 bildirim izni](https://developer.android.com/develop/ui/compose/notifications/notification-permission).

Teslim tam projedir: `Zeytin-Defteri-IS-EMRI-03-YEREL-HATIRLATMALAR-FINAL.zip`. Standart DEFLATE, CRC kontrolü ve ayrı dizine tam çıkarma sonrası her dosyanın byte karşılaştırması uygulanır. Girişteki 384 dosyadan hiçbiri kaldırılmaz. Nihai ZIP'in kendi içindeki rapora kendi SHA'sını koymak öz referans oluşturacağından final hash dış raporun dağıtım ekine ve `.zip.sha256` dosyasına yazılır; ZIP içindeki rapor bu paketleme öncesi metindir.
