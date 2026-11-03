# Kısa ve logolu QR host testi

`JSON_JAR` JSON-java JAR'ını, `ZXING_JAR` ZXing core **3.5.3** JAR'ını gösterir.
ZXing yalnız host'taki bağımsız okuyucudur. QR üretimi repodaki JavaScript qrcode-generator ile yapılır. APK'ya yeni bağımlılık eklenmez; uygulama zaten ZXing core kullanır.

Örnek:

```sh
JSON_JAR=/dosya/json.jar ZXING_JAR=/dosya/core-3.5.3.jar bash tests/web_pairing/run.sh
```

Okuyucu: https://repo.maven.apache.org/maven2/com/google/zxing/core/3.5.3/core-3.5.3.jar

JAR SHA-256: `8d8064c1636fdaef7189dd9055c7d59950a8940a12f2293956446ec3c109fd82`.

Test gerçek `drawCode` çizimini piksellere dönüştürür; yeşil yuvarlak ve beyaz Z bu görüntünün içindedir. ZXing, 1 karakter, 68 karakterlik farklı kısa QR'lar ve 512 karakter dahil 9 görüntüyü çözer. Matris özeti kontrolünün ötesinde bağımsız çözümleme yapılır. Kamera, ekran parlaklığı, baskı ve hareket bulanıklığı cihazda ayrıca denenmelidir.

Aynı Java telefon motoru ve gerçek SQLite sürecinde beş geçici eşleşme döngüsü: tarayıcı kesmesi, telefon kesmesi, sayfa kapanışı, görünür sekmede resume, hareketsizlik kilidi ve QR süresinin dolması. HMAC, sabit B, eski QR ve 30 saniye sınırı da sınanır.
