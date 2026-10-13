# İş Emri 03.2 — Geciken alarm occurrence kaybı hotfix

Tarih: 2026-09-10.

## Kaynak ve kapsam

Tek kaynak: `Zeytin-Defteri-IS-EMRI-03-1-HATIRLATMA-GIRIS-FINAL.zip`.
Kaynak SHA-256: `280f56912757020f292330c7bc3fa48fac58c958c52a7f167663cf0975e232ae`.
Kaynak CRC başarılı; 453 kaynak dosyası korunmuştur. Eski İş Emri 03 ZIP'ine dönülmedi, eski dosyalardan toplu kopyalama yapılmadı.

Değiştirilen tek ürün dosyası: `app/src/main/java/com/zeytindefteri/app/LocalReminderScheduler.java`.
Yeni doğrulama dosyası: `tests/reminders/check_alarm_hotfix.py`. Ayrıca bu rapor ve `IS_EMRI_03_2_VALIDATION/` test kanıtları eklendi.

Diğer 452 kaynak dosyası byte düzeyinde aynıdır. ProducerFeedActivity'deki gerçek Bahçelerim Hatırlatmalar kartı ve hero bağlantısı; + Bahçe Ekle, bahçe kartları, alt navigasyon; RemindersActivity UI/CRUD; ReminderRepository, ReminderActions, ReminderTime, receiver'lar; Manifest/izinler; RELEASE GATE dosyaları; DatabaseHelper/şema; Firebase/FCM dosyaları değiştirilmedi. DB_VERSION **19**; migration yok; yeni dependency/exact alarm/WorkManager yok.

## Kök neden

Eski reschedule önce tüm tracked `alarm_{id}` kayıtlarının Android alarmlarını iptal ediyor, ardından yalnız future/repeating repository sonucunu tekrar kuruyordu. due_at'ı birkaç dakika geçmiş ve Android tarafından henüz teslim edilmemiş one-shot bu sorguya girmediğinden kayboluyordu. Eski schedule geçmiş repeating kaydı doğrudan sonraki tarihe ilerleterek mevcut occurrence'ı da atlayabiliyordu.

## Düzeltilen davranış

1. Kilit/oturum güvenliği kontrol edilir. Oturum yoksa tracked eski alarm ve görünür bildirimler temizlenir.
2. `alarm_` ID'leri kopyalanmış key listesi üzerinden güvenli parse edilir. Boş, negatif, sıfır, taşan, sayısal olmayan veya kanonik olmayan anahtarlar temizlenir; NumberFormatException dışarı taşmaz. Aynı yardımcı notice_ ve beforeSessionChange akışında da kullanılır.
3. Tracked ID'ler önce repository ile doğrulayan schedule akışından geçirilir. Hepsi körlemesine iptal edilip kaybedilmez. Completed/deleted/foreign kayıtların alarmı ve görünür bildirimi kaldırılır.
4. Gelecekteki geçerli kayıt normal due_at ile kurulur. Geçmiş fakat teslim edilmemiş ve **DELIVERY_GRACE = 6 saat** içindeki kayıt, Android'e `now + 1000 ms` trigger verilerek aynı occurrence için yeniden arm edilir. Bu adım SQLite due_at'ını veya updated_at'ını değiştirmez.
5. Receiver'ın mevcut teslim yolu korunur: due_at'ı DB'den doğrular, aynı occurrence'ı tek kez işler; repeating ise teslim akışında sonraki due_at ilerler ve kurulur. Receiver dosyası veya receive metodu değiştirilmedi.
6. Grace dışındaki repeat mevcut ReminderTime ile sonraki geçerli tarihe ilerler; eski one-shot sessiz kalır. Teslim token'ı zaten bulunan occurrence tekrar gösterilmez.
7. Repository getSchedulable(now) içindeki diğer açık future/repeating kayıtlar da kurulur. Tracked olarak zaten işlenen ID ikinci turda atlanır. Deterministic URI/immutable PendingIntent ve cancel-before-arm davranışı ek duplicate koruması sağlar.

Untracked eski one-shot geçmişi ayrıca taranmaz. Böylece uygulama/reboot açılışı eski kayıtları bildirim yağmuruna dönüştürmez. Koruma tracked gecikmiş occurrence'ı kaybetmemeye yöneliktir; yeni geçmiş veri tarama politikası eklenmedi.

## Güvenlik ve yerel çalışma

Owner ve owned-garden kontrolü değişmemiş ReminderRepository üzerinden devam eder. Oturum değiştirme ile notification üretimi arasındaki mevcut LOCK korunur. beforeSessionChange artık bozuk alarm_/notice_ anahtarlarıyla da güvenli çalışır. Preferences iterasyonu key snapshot'ı üzerinden yapıldığından anahtar temizleme/iptali iterasyon yapısını değiştirmez.

NotificationChannel, Android 13 izni, reboot/timezone/package replace bağlantıları ve FCM'den bağımsız yerel kod yolu aynıdır. Firebase/REST/Function/read/listener eklenmedi. Aynı ID birden fazla kaynakta görünse bile tek aktif alarm hedeflenir. delivered token temizlenerek eski olay tekrar bildirilmez.

## Test sonuçları

Son turda **18/18 test/kontrol komutu exit 0**. Ham liste ve loglar `IS_EMRI_03_2_VALIDATION/final/` içindedir.

| Test grubu | PASS | FAIL | SKIP |
|---|---:|---:|---:|
| Yeni hotfix senaryoları | 17 | 0 | 0 |
| Mevcut reminder runtime fixture | 39 | 0 | 0 |
| İş Emri 02 RELEASE GATE fixture | 7 | 0 | 0 |
| İş Emri 01 gerçek SQLite | 52 | 0 | 0 |
| Analytics fixture | 38 | 0 | 0 |
| Backend/Functions | 30 | 0 | 4 |

Yeni test mevcut üretim Java/SQLite harness'ini yeniden kullanır: çıktısındaki **56 kontrol = mevcut 39 + yeni 17**. Bu tekrar çalıştırma sayıları ayrıca yeni test gibi toplanmaz. Üretim scheduler/repository gerçek SQLite ile çalışır; Android AlarmManager/NotificationManager OS test doubles'dır. Cihaz teslim zamanının doğrulanması değildir.

Yeni testler: (A) tracked grace içi one-shot rearm, DB due_at koruma, kısa gelecekte OS trigger, tek teslim; (B) repeating occurrence korunması, sonra next ve duplicate reddi; (C) grace dışı one-shot sessizlik; (D) completed; (E) deleted/nonexistent; (F) tracked+schedulable tek alarm. Ayrıca grace dışı repeat, untracked eski one-shot taramama, corrupt metadata, foreign owner, oturumsuz temizlik ve beforeSessionChange bozuk key senaryoları vardır.

03.1 kart sırası/Intent bağlantısı kaynak akışında tekrar denetlendi; ProducerFeedActivity byte düzeyinde değişmemiştir. Mevcut + Bahçe Ekle, gardenCard ve navigasyon korunur. 03.1'de cihaz UI testi bulunmuyordu; bu teslimde de kaynak bağlantı kontrolü cihaz testi olarak sunulmaz.

Statik: 89 Java dosyası Java 8 sözdizimi PASS, 105 XML parse PASS. Bunlar Android build değildir. Diğer mevcut asistan/UX, Elek arşiv/form/popup, akış, konum, Meydan, threaded, today ve npm kontrolleri başarılı. SKIP'ler PASS sayılmadı.

## Android build

Gerçek Gradle wrapper assembleDebug denendi: **exit 1 / ENGELLİ**.
`java.net.UnknownHostException: services.gradle.org`; Gradle 8.7 dağıtımı indirilemedi. Java 17 java-home yalnız komut satırında belirtildi, proje Gradle ayarları değiştirilmedi. Tam hata `IS_EMRI_03_2_VALIDATION/android-build.log` içinde. **APK üretilmedi; BUILD PASS değildir.**

## Kalan sınırlamalar / cihaz doğrulaması

Inexact AlarmManager Android güç tasarrufu/OEM kısıtları altında gecikebilir. `now+1 saniye` bir exact teslim garantisi değildir. Mevcut 6 saat sınırı değiştirilmedi. OS metadata kaybolmuş untracked eski one-shot'lar bu dar hotfix tarafından kurtarılmaz. Mevcut notification izni ve süreç çökmesi/teslim token'ı sınırlamaları İş Emri 03 raporundaki gibi devam eder.

Gerçek cihaz/Doze testi yapılmadı. Cihazda: gelecekte bir one-shot planlayın, Doze ile teslimini geciktirin; due_at geçtikten ve 6 saat dolmadan uygulamayı açıp yeniden planlamayı tetikleyin. DB due_at aynı kalmalı, yeniden arm edilen olay tek bildirim üretmeli. Aynısını günlük tekrar ile deneyin; önce mevcut bildirim, sonra next due_at görülmeli. Complete/delete/hesap değişiminde eski alarmın tekrar kurulmadığını kontrol edin. Bildirim reddi ve uçak modu davranışını da gerçek cihazda doğrulayın; sonuç görülmeden PASS yazılmamalıdır.

## Paketleme

Tam proje: `Zeytin-Defteri-IS-EMRI-03-2-ALARM-HOTFIX-FINAL.zip`. DEFLATE ZIP CRC ve ayrı dizine tam çıkarma sonrası tüm dosyalarda byte eşleşmesi doğrulanır. Nihai ZIP SHA-256, öz referans oluşturmamak için dış raporun dağıtım ekinde ve `.zip.sha256` dosyasında verilir; ZIP içindeki rapor bu paketleme öncesi metindir.
