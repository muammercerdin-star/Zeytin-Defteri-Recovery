# İş Emri 03.1 — Hatırlatmalar giriş bağlantısı

Tarih: 2026-09-10.

## Kaynak ve değişiklik

Tek kaynak: `Zeytin-Defteri-IS-EMRI-03-YEREL-HATIRLATMALAR-FINAL.zip`.
Kaynak SHA-256: `b115dc3809b859eef3037e7fd274d2b5d22442174995b6bf60c851e34e14383b`.
Kaynak CRC sağlam; 431 dosya korunmuştur. Sıfırdan proje kurulmadı, eski ZIP'ten kod alınmadı.

Yalnız değiştirilen ürün dosyası:
`app/src/main/java/com/zeytindefteri/app/ProducerFeedActivity.java`.

Alt navigasyondaki gerçek Bahçelerim ekranı `ProducerFeedActivity.buildGardensSection()` ile çiziliyor. Önceki GardensActivity girişi bu ekranda görünmüyordu. Gerçek ekrana mevcut rowCard/text/GREEN/MUTED ve rowCard'ın round/BORDER tasarımını kullanan “🔔 Hatırlatmalar” kartı eklendi.

Sıra: mevcut Bahçelerim başlığı/açıklaması ve + Bahçe Ekle bölümü → Hatırlatmalar → mevcut bahçe kartları (veya mevcut boş durum). Kart en az 80 dp yüksekliğinde, tüm satır tıklanabilir ve focusable; contentDescription içerir. Alt metin: “Sulama, gübreleme ve diğer işlerini zamanında hatırla”. Sağda › işareti bulunur.

Tıklama: `startActivity(new Intent(ProducerFeedActivity.this, RemindersActivity.class))`.
Yeni wrapper/Activity/XML eklenmedi. Hero'daki mevcut işlevsiz `bell` görünümünün şekil, boyut ve konumu değiştirilmeden aynı Intent bağlandı; açıklaması “Hatırlatmaları aç” oldu.

Bahçe ekleme listener'ı, gardenCard metodu/bahçe gezinmesi ve alt navigasyon kodu değiştirilmedi. ReminderRepository, scheduler, receiver'lar, ReminderActions, RemindersActivity, SessionManager, DatabaseHelper, DB_VERSION 19, şema, Manifest, Firebase/FCM ve İş Emri 02 RELEASE GATE dosyaları kaynak ile byte düzeyinde aynıdır. Migration yoktur. Kaynaktaki diğer 430 dosya değişmemiştir.

## Doğrulama

- Son regresyon turu: 17/17 test/kontrol komutu exit 0.
- İş Emri 03: 39 PASS / 0 FAIL / 0 SKIP.
- RELEASE GATE fixture: 7 PASS / 0 FAIL / 0 SKIP.
- İş Emri 01 SQLite: 52 PASS; İş Emri 02 analytics: 38 PASS.
- Backend: 30 PASS / 0 FAIL / 4 SKIP. SKIP testler başarılı sayılmadı.
- 89 Java dosyası Java 8 sözdizimi kontrolünden, 105 XML parse kontrolünden geçti. Kartın kaynakta doğru sıraya yerleşmesi, RemindersActivity Intent hedefi ve mevcut Manifest kaydı kontrol edildi. Ürün dosya farkı yalnız ProducerFeedActivity'dir.
- Bunlar gerçek cihazda görünürlük/dokunma testi değildir. Cihaz UI testleri çalıştırılmadı. Kaynak akışında doğru yerleşim ve bağlantı doğrulandı; reminder/analytics davranışı mevcut runtime fixture testlerinde korundu.

## Gerçek Android build

assembleDebug gerçek Gradle wrapper ile denendi; exit 1. Gradle 8.7 indirilemedi: `java.net.UnknownHostException: services.gradle.org`. **BUILD PASS değildir; APK üretilmedi.** Statik Java/XML denetimi Android build yerine sunulmamıştır. Ham loglar `IS_EMRI_03_1_VALIDATION/` altındadır.

Cihaz kontrolü: Alt menü Bahçelerim'i açın; başlık/+ Bahçe Ekle altında ve bahçeler üstünde kartı görün. Kartın farklı noktalarına dokunup Hatırlatmalar'ın açıldığını kontrol edin. Geri dönün; + Bahçe Ekle, mevcut bahçe kartı ve alt menü geçişlerini deneyin. Ana sayfa hero simgesinin aynı hatırlatma ekranını açtığını kontrol edin. Bu adımlar için cihaz sonucu henüz verilmemiştir.

## Teslim

Tam proje: `Zeytin-Defteri-IS-EMRI-03-1-HATIRLATMA-GIRIS-FINAL.zip`.
Standart DEFLATE ZIP; CRC ve ayrı klasöre tam çıkarma sonrası dosya içerikleri karşılaştırılır. ZIP'in kendi hash'ini içindeki rapora yazmak öz referans oluşturacağından nihai SHA-256, dış raporun dağıtım ekinde ve eşlik eden `.zip.sha256` dosyasında sunulur.
