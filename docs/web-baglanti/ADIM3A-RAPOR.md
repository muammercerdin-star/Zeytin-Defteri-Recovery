# Adım 3a — telefon tarafı ve relay düzeltmeleri

Tarih: 3 Kasım 2026. Ebeveyn: `f913fccd8580efeced153235566559863cacba1d`. Dal: `work/web-baglanti`.

## Yapılanlar

- Relay bağlantı başına token-bucket: **20 frame/sn**, **60 frame burst**, **4 MiB/dk**. `[vars] FRAME_RATE/FRAME_BURST/BYTES_PER_MINUTE` ile ayarlanır. Aşan frame düşer; saniyede en çok bir şifresiz `{"v":1,"type":"hiz_siniri"}` gönderilir. Aşım girişimleri arasındaki boşluk bir saniyeyi geçmiyorsa seri devam eder; seri 10 sn'ye ulaşınca kapanır. Arada kabul edilen frame seriyi gizleyemez.
- Geçerli rol capability'siyle gelen yeni socket önce doğrulanır, sonra eski aynı rol socket'ini kapatıp yerini alır. Yanlış token eski sokete dokunmaz. Eski socket'in geciken close olayı yeniyi koparmamalı; test edildi. `peer_joined/peer_left` yalnız bağlantı ipucudur, E2EE yetkisi değildir.
- `BuildConfig.TEZGAH_RELAY_URL`: mevcut `zdBuildString` / local.properties veya ortam değişkeni deseni. Boş, HTTP, credentials/path/query/fragment içeren geçersiz adreslerde UI gizli ve servis kapalı. QR hiçbir relay hedefi seçemez.
- QR → açık telefon onayı/ad → `connectedDevice` foreground service → auth/hello/finished/consume/onaylandi. Tek aktif bilgisayar; status satırı ve yerel metadata listesi. Yeni bağlantı eskisini sonlandırır. Adım 3b için sayfa oluşturulmadı.
- WSS OkHttp istemcisi: tek executor, 30 sn uygulama ping'i, 60 sn yanıtsızlık kontrolü, 1–30 sn jitter reconnect, 5 dk nonce/root resume, RAM anahtarları, START_NOT_STICKY servis. Logout/hesap değişimi/wipe/restore reset'i socket'i ve veri erişimini durdurur. Bekleyen servis onayı da reset generation damgasıyla geçersiz kılınır.
- Gönderim: **10 frame/sn** gerçek zaman aralığı (scheduler catch-up burst'ü de engellenir), yaklaşık **1,9 MiB/dk**, kuyruk **128 frame / 2 MiB**. Eski epoch'un kuyrukta kalan paketleri reconnect/peer değişiminde temizlenir. Hiz sınırında geçici tempo duraklaması vardır; düşen salt okuma isteğini yeniden isteme 3b'nin timeout sorumluluğudur.
- Repository yalnız `bugunun_alimlari`: `open()` / `forDay()`; owner constructor'da telefondan alınır. Para metin; amount_paid güncel toplamdır, payments tekrar eklenmez. Sayfa default 100/max 200; payload bütçesi dolunca daha az satır dönebilir. Cursor/totals/complete ve snapshot sahibi/filtre/60 sn sınırları uygulanır. SQLite hatası boş liste değildir.
- Mutasyon bildirimleri başarılı yazma/commit sonrasında 400 ms birleştirilir. Nested transaction içindeki ödeme bildirimleri atlanır, toplu ödemenin dış transaction'ı başarıyla bitince bir kez bildirilir. Hesap reset'i senkron callback ile veri erişimini keser; transport temizliği executor'da tamamlanır.
- Sessions yalnız ad/room/başlangıç/son görülme/aktif/iptal metadata'sıdır; owner hash'li `getNoBackupFilesDir()` dosyası. SQLite/TezgahBackup ve Android Auto Backup'a girmez. S, capability, kripto anahtarları, alım veya hesap verisi dosyaya yazılmaz.

## Dosyalar

Yeni üretim sınıfları (`app/src/main/java/com/zeytindefteri/app/`): `TezgahWebClient`, `TezgahWebService`, `TezgahWebSessions`, `TezgahWebRepository`, `TezgahWebDto`, `TezgahWebChanges`, `TezgahWebPhone`.

Son sınıf, gerçek telefon protokol motorunu Android taşımasından ayırır; host testleri JavaScript tarayıcı kriptosuyla bu **aynı** motoru çalıştırır. Sahte bir ikinci telefon implementasyonu test edilmedi.

Yeni `tests/web_phone/`: `PhoneTest.java`, `PhoneBridge.java`, `flow.mjs`, `check_wiring.py`, `run.sh`. Bu rapor da yeni dosyadır. Mevcut dosyalardaki ekleme/silme sayıları aşağıdaki diff tablosundadır.

## Testler

| Küme | Sonuç | Yorum |
|---|---:|---|
| Yeni phone SQLite / debounce / mutasyon hook | **184/184** | Python köprüsüyle gerçek SQLite dosyası; owner, Z/açık dönem/takvim, ödeme, hata/boş ve 210 kayıt sayfalama |
| Yeni Java telefon ↔ mevcut JS WebCrypto | **20/20** | Auth/hello/MAC, onay öncesi veri yok, onaylandi, gerçek SQLite cevap, salt okuma, veri_degisti, resume/old epoch, QR/key reddi |
| Yeni wiring | **20/20** | Statik kaynak/XML denetimi; gerçek Android servisi testi değil |
| Güncellenmiş relay | **75/75** | Node mock; burst/drop/10 sn sürekli aşım/vars/aynı rol değiştirme dahil |
| Önceki Java kripto | **174/174** | --release 8 host kontrolü; JS frame açma dahil |
| Önceki JS kripto | **174/174** | Java frame açma dahil |
| Genel mevcut test giriş noktaları | **103/103 geçti** | İstenen run.sh/run.py/run_*.py/check*.py/tests/*.py desenleri; aşağıdaki iki PG testi hariç |

Phone testleri toplam **224 kontrol**. Java crypto runner ilk 173 kontrolü ikinci çalıştırmada tekrarlar; son 174 sayısı bu tekrarı iki kez saymaz. Genel test sayısı assertion değil giriş noktasıdır; bazı shell runner'ları bağımsız çalıştırılan check.py'yi de çağırır.

### Çalıştırılmayanlar

- `tests/league_names/run.sh`: **Postgres yok**, kullanıcı kapsamı gereği çalıştırılmadı; yerel PostgreSQL + `ZD_PG` gerekir.
- `tests/current_prices/run.sh`: **Postgres yok**, aynı durum.
- Android uygulaması **derlenmedi (SDK yok)**. ANDROID_HOME/ANDROID_SDK_ROOT ve yerel Android platform JAR'ı bulunmadı; assembleRelease/compileReleaseJavaWithJavac çalıştırılmış sayılmaz.
- Gerçek Android, OkHttp/WSS, edge hibernation, özel domain TLS, mobil operatör ve ekran kapalı uzun süre testi **doğrulanmadı**. 3b web sayfası yok; host simülasyon gerçek iki cihazlı demo değildir.

Host ortamı: JDK 17.0.20 ve Node 24.22.1. Yeni bağımsız Java motor/repository --release 8 ile derlendi; Android SQLite stub'ları JDK17'de Python sqlite3 köprüsüne gider. Köprü gerçek SQL sonuçlarını doğrular; stub transaction metotları no-op olduğundan Android'in gerçek nested transaction/rollback davranışının testi değildir. Commit sırası kaynak/wiring ile ayrıca kontrol edildi. `javac` launcher eksikliği compiler modülüne repo dışı wrapper ile, JSON test bağımlılığı repo dışı JSON_JAR ile giderildi; üretim bağımlılıkları bundan etkilenmedi. Bütün genel giriş noktaları ve önceki sonuçların biçimi [Adım 2 dökümündedir](ADIM2-TEST-SONUCLARI.md); bu görevde aynı 103 nokta tekrar geçti.

Komutlar:

```
JSON_JAR=/path/to/json.jar bash tests/web_phone/run.sh
bash tests/web_crypto/run.sh
bash tests/web_relay/run.sh
```

## Bağımlılık ve APK etkisi

**Yalnız doğrudan bağımlılık:** `com.squareup.okhttp3:okhttp:5.5.1`. 4.12.x seçilmedi; güncel kararlı sürüm resmî [overview](https://lysine.dev/okhttp/) ve [changelog](https://lysine.dev/okhttp/changelogs/changelog/) üzerinden doğrulandı (22 Ekim 2026 sürümü). Önceki square.github.io/master arama önbelleğinin 5.3.0 bilgisi güncel değildi.

Resmî gereksinim: Android 21+, Java 8+. MinSdk24 bu tabanı karşılar. Okio, Kotlin stdlib ve Android varyantının AndroidX Startup başlangıcı transitiftir; ayrıca Kotlin plugin, logging-interceptor veya core-library-desugaring dependency eklenmedi. Gradle Android varyantını otomatik seçer; uygulama Startup initializer'ını kaldırmıyor. Projenin source/target Java 8 ayarları korundu; kullanılan java.util.function / Collection.removeIf Android24'te vardır. Tam transitif sürüm çözümü ve D8/manifest merge bu ortamda **doğrulanmadı**.

**APK öncesi/sonrası boyut: ölçülmedi.** SDK olmadığı için dosya boyutu farkı veya “APK başarıyla derlendi” iddiası yok. Termux komutu: `bash gradlew :app:assembleRelease` (mevcut yerel imza ayarları gerekir).

## minSdk 24 API envanteri — Android derlenmediği için

Aşağıdaki liste yeni kodun Android framework çağrılarını açıkça sayar. Aynı çağrının tekrarları gruplandı. API24 üzeri her çağrı korumalıdır. Bu liste cihaz doğrulamasının yerine geçmez.

| Dosya / çağrı | İlk API / kullanım |
|---|---|
| Service: `Activity.startForegroundService(Intent)` | **26**, `SDK_INT>=26`; 24–25'te `startService(Intent)` (API1) |
| `Service.startForeground(int,Notification,int)`; `ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE` | **29**, `SDK_INT>=29`; 24–28'de iki argümanlı `startForeground` (API5) |
| `NotificationChannel(String,CharSequence,int)`, `NotificationManager.createNotificationChannel`, `Notification.Builder(Context,String)` | **26**, `SDK_INT>=26`; 24–25'te `Builder(Context)` (API11) |
| `NotificationManager.IMPORTANCE_LOW` | API24; yalnız kanal/API26 dalında |
| `Notification.Builder(Context)`; `setSmallIcon`, `setContentTitle`, `setContentText`, `setOngoing`, `setContentIntent` | API11; min24'te mevcut |
| `Notification.Builder.addAction(int,CharSequence,PendingIntent)`, `build()` | API16; min24'te mevcut |
| `PendingIntent.getService`, `PendingIntent.getActivity` | API1 |
| `PendingIntent.FLAG_UPDATE_CURRENT`; `FLAG_IMMUTABLE` | API3 / **23**; min24'te mevcut |
| `new Intent(Context,Class)`, `Intent.setAction`, `Intent.getAction` | API1 |
| `Context.stopService(Intent)`, `getSystemService(String)`, `NOTIFICATION_SERVICE` | API1 |
| `Service.stopForeground(boolean)`; `START_NOT_STICKY`, `onStartCommand` | API5 |
| `Service.stopSelf`, `onCreate`, `onDestroy`, `onBind`; `IBinder` | API1 |
| `Build.VERSION.SDK_INT` | API4; 26/29 dallarını korur |
| `new Handler(Looper)`, `Looper.getMainLooper`, `Handler.post`, `postDelayed`, `removeCallbacks` | API1; ana ekran yenileme ve servis kapanışı |
| Sessions: `Context.getNoBackupFilesDir()` | **21**; metadata Auto Backup dışında |
| `new AtomicFile(File)`, `readFully`, `startWrite`, `finishWrite`, `failWrite` | **17** |
| Repository/helpers: `SQLiteDatabase.rawQuery`, `execSQL`, `insert`, `update`, `delete`, `beginTransaction`, `setTransactionSuccessful`, `endTransaction`, `inTransaction`; Cursor erişimleri/close | API1; çoğu mevcut helper üzerinden; `inTransaction` opsiyonel hook'ta reflection ile |
| Service/UI: `DatabaseHelper.getWritableDatabase`, `close` (SQLiteOpenHelper) | API1; mevcut uygulama sınıfı |
| UI: `new AlertDialog.Builder`, `setTitle`, `setMessage`, `setView(View)`, `setPositiveButton`, `setNegativeButton`, `show` | API1 |
| UI: `new EditText`, `setHint`, `setSingleLine`, `setFilters`, `getText`; `new InputFilter.LengthFilter` | API1 |
| UI: `new LinearLayout`, `setPadding`, `addView(View,LayoutParams)`, `new LinearLayout.LayoutParams` | API1 |
| UI: `Toast.makeText`, `show`, `LENGTH_LONG`; Activity `onResume/onPause` | API1 |
| Kaynak simgeleri: `android.R.drawable.stat_notify_sync`, `ic_menu_close_clear_cancel` | Min24'te mevcut framework drawable'ları |
| `JSONObject` / `JSONArray`: constructors, get/getString/getInt/getLong, opt/optString/optInt/optLong/optBoolean/optJSONObject, has/isNull/keys/length/put/toString/NULL | Android org.json API1; JSON çift/kayıt kodlayıcı helper checked JSONException'ı yakalar |

Yeni Java standart kütüphane kullanımı: `Consumer`, `BooleanSupplier`, `Collection.removeIf` API24; `StandardCharsets` API19; `AtomicLong`, executor/scheduled future/thread, UUID, BigDecimal, URI, System.nanoTime ve diğer koleksiyonlar min24'te mevcuttur. Önceki P-256/AES/HMAC API listesi tasarımın kripto bölümündedir. OkHttp/Okio/Kotlin transitif kodu bu kaynak API listesinden ayrı; gerçek Android Gradle derlemesiyle doğrulanacak.

Resmî kaynaklar: [Context](https://developer.android.com/reference/android/content/Context), [AtomicFile](https://developer.android.com/reference/android/util/AtomicFile), [Service](https://developer.android.com/reference/android/app/Service), [Notification.Builder](https://developer.android.com/reference/android/app/Notification.Builder), [PendingIntent](https://developer.android.com/reference/android/app/PendingIntent), [Java8 desteği](https://developer.android.com/studio/write/java8-support), [FGS tipleri](https://developer.android.com/develop/background-work/services/fgs/service-types).

## Sapmalar / açık noktalar

1. Host test edilebilirlik için ek `TezgahWebPhone` motoru var; Client bunun gerçek taşımasıdır. Android davranışının ikinci bir kopyası test edilmedi.
2. Mevcut bağımsız host compile listelerini ve yazma davranışını korumak için eski veri sınıflarındaki opsiyonel event/reset köprüsü reflection kullanır. Minify kapalı. İleride küçültme açılırsa keep kuralı gerekir. Hook hatası mevcut finansal yazmayı başarısız yapmaz.
3. 3a tek aktif bilgisayar destekler; metadata geçmişi en çok 20 kayıt. Tasarımın olası iki bilgisayar genişlemesi bu adımda yoktur.
4. Snapshot 24 KiB payload bütçesi nedeniyle 200 isteğinde daha az satır dönebilir; nextCursor/complete bunu açıkça bildirir. Toplamlar bütün snapshot'a aittir.
5. `hiz_siniri` düşen frame'i onaylı teslim hâline getirmez. 3b salt okuma request-id/timeout/yeniden isteme ve `peer_joined/peer_left`/resume davranışını uygulamalı.
6. connectedDevice tipinin bu kullanım için Play kabulü, gerçek mobil bağlantı/pil/OEM davranışı ve API24/36 cihaz sonucu **doğrulanmadı**.
7. QR fotoğrafı gerçek telefonun SQLite verisini çözmez; 6. adımdaki browser'dan girilen yeni verinin sahte telefon kanalına gönderilmesini aynı iddia korumaz. Önceki tasarımdaki açık konu geçerlidir.
8. Relay canlıya bu görevde yüklenmedi; özel alan adı ve hesap kotaları doğrulanmadı. Gizli anahtar/ücretli servis eklenmedi. `cloud/zeytin-ai` içerikleri değişmedi.

## Mevcut dosyalarda satır farkı

Sayım `git diff --numstat f913fcc` biçiminde eklenen/silinen satırdır; değiştirilen bir satır hem ekleme hem silme sayılır. Yeni dosyalar yukarıda listelenir.

| Dosya | + | − |
|---|---:|---:|
| `app/build.gradle` | 2 | 0 |
| `app/src/main/AndroidManifest.xml` | 5 | 0 |
| `app/src/main/java/com/zeytindefteri/app/LocalAccountWipe.java` | 1 | 0 |
| `app/src/main/java/com/zeytindefteri/app/SessionManager.java` | 8 | 0 |
| `app/src/main/java/com/zeytindefteri/app/TezgahBackup.java` | 3 | 2 |
| `app/src/main/java/com/zeytindefteri/app/TezgahClosing.java` | 2 | 2 |
| `app/src/main/java/com/zeytindefteri/app/TezgahHomeActivity.java` | 44 | 0 |
| `app/src/main/java/com/zeytindefteri/app/TezgahPayments.java` | 3 | 2 |
| `app/src/main/java/com/zeytindefteri/app/TezgahPurchases.java` | 12 | 4 |
| `app/src/main/java/com/zeytindefteri/app/TezgahStatementActivity.java` | 1 | 1 |
| `app/src/main/java/com/zeytindefteri/app/TezgahZ.java` | 3 | 2 |
| `cloud/tezgah-relay/KURULUM.md` | 14 | 5 |
| `cloud/tezgah-relay/worker.js` | 20 | 6 |
| `cloud/tezgah-relay/wrangler.toml` | 3 | 0 |
| `docs/web-baglanti/TASARIM.md` | 23 | 13 |
| `tests/web_relay/test.mjs` | 15 | 7 |
