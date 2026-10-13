# İş Emri 03 testleri

Proje kökünden:

```sh
python3 tests/reminders/check_reminders.py
python3 tests/reminders/check_release_gate.py
```

Python 3 sqlite3 ve jdk.compiler içeren Java 17 gerekir. `java com.sun.tools.javac.Main --release 8` kullanılır. Android SDK/ağ gerektirmez.

İlk test tam üretim ReminderRepository, LocalReminderScheduler, ReminderTime, ReminderActions, receiver ve SessionManager sınıflarını derler; mevcut DatabaseSchema DDL'siyle gerçek SQLite fixture kurar. Android API'leri test doubles üzerinden yürür; reminder SQL'i Python'da yeniden yazılmış sahte hesap değildir. Tombstone üretim metodu da çalıştırılır. 39 runtime kontrol vardır. Bu test gerçek Activity UI veya OS teslim zamanını doğrulamaz.

İkinci test kaynakta korunmuş GardenAnalyticsSnapshot'ın Hasat/Elek fark ve tolerans metotlarını 7 SQLite fixture ile çalıştırır.

validation/ başlangıç/final test loglarını, kaynak karşılaştırmasını, statik kontrolleri ve başarısız gerçek Gradle denemesini içerir. Gerçek cihaz planı proje kökündeki IS_EMRI_03_REPORT.md içindedir.
