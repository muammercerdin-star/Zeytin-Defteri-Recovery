# İP9-B · Uygulamaya entegre Yağhane Sıram — rapor

Çiftçi, uygulamada **Sıram** kartı ve detay ekranıyla sırasını takip eder; önünde kişi eşiğe (varsayılan 3)
düşünce **otomatik yerel bildirim** gelir. Fabrikacı hiçbir şey yapmaz, çiftçi QR'ı bir kez okutur, SMS parası yok,
numara hiçbir yere gitmez. Yol A (uygulama yoklar + yerel bildirim) uygulandı; web sistemiyle (İP9-A) tam uyumlu.

## Durum özeti

**Doğruluk-kritik çekirdek — saf Java, host'ta DOĞRULANDI (66/66):**
- `SiraDurumu` — sunucu cevabını (/api/sira/&lt;token&gt;) çözer: adım, önünde, tahmin, haber eşiği, randıman (32).
- `SiraDefteri` — takip edilen token'lar yalnız telefonda; gönderilen haber biti (18).
- `SiraBildirim` — otomatik "ne zaman / hangi mesaj" kararı, web tetiklemesiyle birebir (10).
- `SiraYoklama` — bir yoklama turunun çekirdeği (Getirici/Haberci arayüzle test edildi) (6).
- `SiraBulut` — ağ çağrısı: saf JDK, host'ta **derlendi** (ağ testi yapılamadı — sunucuya bu ortamdan erişim yok).

**Android tesisatı — inceleyip yazıldı, ama bu ortamda DERLENMEDİ (Android SDK yok):**
- `SiraDepo` — SharedPreferences (telefonda).
- `SiraBildirimci` — yerel bildirim (kanal + Notification), tıklanınca detay. ZeytinMessagingService desenine uygun.
- `SiraTarama` — `AlertCenter`'ın arka plan turuna (ve ekran öne gelince) bağlı; sıra yoksa ağ açmaz.
- `SiramActivity` — detay ekranı (büyük sıra no, adımlar, tahmin, randıman); derin link + bildirimden açılır.
- `ProducerFeedActivity` — Ana Sayfa'ya bağlama-duyarlı **Sıram kartı** (tamamı korumalı: hata olsa bile ana ekranı bozmaz).
- `AndroidManifest` — derin link: `sira.zeytindefteri.tr` App Link + `zeytin-sira://` (bildirim).

Hepsi mevcut desenlere (AlertCenter/JobScheduler, ZeytinMessagingService, GreenTheme) uyularak ve imzalar
tek tek doğrulanarak yazıldı; yine de **derleme Termux/Android Studio'da yapılmalı** (aşağıda).

## Gizlilik
- Token yalnız telefonda (`SiraDepo`), sunucuya gitmez; sıra sunucusu çiftçiyi tanımaz (anonimlik korunur).
- Ağa yalnız token gider (kişisel veri değil), yalnızca OKUMA — şifreli röle istisnasına uygun.
- Bildirim metni kişisel veri taşımaz (başlık + kısa metin + kod).

## Gece kuralı (bilinçli istisna)
AGENTS: uygulama genelde gece (22:00–05:00) bildirim göndermez. Ama **Sıram bildirimi operasyoneldir**
(fabrika gece de çalışır, sıra gece gelebilir) — çiftçinin açık onayıyla bu kurala **bilinçli istisna** kondu;
koda yorumla işlendi. Web ile de tutarlı (web'de gece kuralı kaldırılmıştı).

## Sende kalan (canlıya alma + derleme)
1. **Termux'ta derle:** `./gradlew :app:assembleDebug` (yeni sınıflar + manifest + ProducerFeedActivity/AlertCenter
   değişiklikleri). Derleme hatası çıkarsa bana ilet, düzeltir yeni commit atarım — Defter görünümündeki akış.
2. **App Link doğrulama:** Ofis QR'ının uygulamayı sorunsuz (chooser'sız) açması için
   `sira.zeytindefteri.tr/.well-known/assetlinks.json` yayınlanmalı (uygulamanın paket adı + imza SHA-256). Bu
   olmadan link tarayıcıda açılır (web çiftçi sayfası) — yine çalışır, ama uygulamaya düşmez.
3. **Eşik (3 kişi)** `SiraYoklama.ESIK` sabitinde; istersen ayarlanabilir yaparız.

## Henüz YAPILMADI — bir sonraki parça: deftere yazma
Söz verdiğimiz "yağ/randıman otomatik deftere" kısmı **bilerek yazılmadı**, çünkü AGENTS kuralı net:
*"Kayıt, satış, ödeme ve silme her zaman çiftçinin onayını ister."* Yani bu **sessiz otomatik kayıt olamaz**;
doğrusu: sıra bitince uygulama kaydı **hazır doldurur** (fabrika, kg, çıkan yağ, randıman) ve çiftçi **tek dokunuşla
onaylar**. Bu, hangi kayıt türüne (işlem/elek) yazılacağına ve onay ekranına karar vermeyi gerektiriyor — ayrı,
küçük bir iş olarak planlayıp sana sunacağım.

## Zamanlama sınırı (Yol A)
Arka plan yoklaması Android'de seyrektir (AlertCenter ~4 saat; uygulama açıkken ~30 dk). Yani bildirim birkaç
dakika gecikebilir. Pilotta "daha anlık" gerekirse Yol B'ye (sunucu → FCM) geçeriz; altyapı (web push motoru)
zaten var, app FCM'i de var.
