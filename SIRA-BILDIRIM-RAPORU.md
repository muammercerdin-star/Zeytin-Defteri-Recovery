# İP9-A-10 · Çiftçiye sıra bildirimi (web) — rapor

Çiftçi, sıra sayfasında **"Sıram gelince haber ver"** diyince telefonuna Web Push bildirimi gider:
önünde 2 kişi kalınca "yola çık", sırası gelince "sıra sende". Amaç: çiftçi gidip beklemesin, haber gelince gelsin.
Yalnız **web** tarafıdır; sitede uygulamadan önce yayınlanabilir (yayın günü kuralı). Uygulama entegrasyonu İP9-B'ye
(pilot sonrası) bırakıldı.

## Ne yapıldı (yedi parça, her biri ayrı commit)

1. **Şema + göç + temizlik** — `bildirim_abone` tablosu (yalnız sıra token'ına bağlı), 24 saatte silinir.
   Canlı için `cloud/zeytin-sira/goc-bildirim.sql`. Temizlik ayrı + `catch`'li: göç uygulanmadan tablo yokken
   bile diğer temizlik bozulmaz.
2. **Abonelik uçları** — `POST /api/sira/<token>/bildirim` (haber ver), `DELETE` (vazgeç). Hız sınırlı,
   VAPID yoksa `503 KAPALI`. Çiftçi durum cevabına açık VAPID anahtarı eklendi.
3. **Gönderim motoru** (`src/bildirim.mjs`) — VAPID JWT (ES256) + RFC 8291/8188 uçtan uca şifreli içerik
   (aes128gcm). Yalnız WebCrypto, dış bağımlılık yok.
4. **Tetikleme** — Başlat/Bitti/İptal/taşı sonrası eşiğe inen çiftçilere haber. `waitUntil` ile sıra işlemini
   bloklamaz; gönderim hatası yutulur (bir çiftçiye ulaşılamaması ötekini ve sırayı etkilemez).
5. **Çiftçi sayfası** — "haber ver" düğmesi, izin akışı, aç/kapat, desteklemeyen tarayıcıya "sayfayı açık tut"
   notu (iPhone yolu dahil). `sw-bildirim.js`: push'u gösterir, tıklanınca sıra sayfasını açar.
6. **Testler** — worker +25 kontrol, web statik kontroller.
7. **Rapor + kurulum** (bu dosya + KURULUM.md 4b + `vapid-uret.mjs`).

## Kararlar
- **Eşik:** önünde 2 kala "yaklaştı" (bit 1) + sıra sende/başladı (bit 2). Her eşik bir kez (`esik_durumu` biti).
- **Tetikleme:** ayrı zamanlayıcı yok; durum değişince taranır. Yağ yazımı sırayı değiştirmediği için tetiklemez.
- **Gece kuralı yok:** fabrika sezonda gece de çalışır, çiftçi gece de sırasını bekler → bildirim 7/24 gider.
- **Gizlilik (AGENTS şifreli röle istisnası):** push adresi yalnız koda/token'a bağlı, isim/telefonla eşleşmez;
  mesaj kişisel veri taşımaz ("Sıran yaklaştı · kod 7532"); içerik uçtan uca şifreli (ileten push servisi okuyamaz);
  24 saatte silinir. `bildirim_abone` tablosunda ad/telefon/kg/kasa sütunu yoktur (testle doğrulandı).

## Testler (bu ortamda çalıştırıldı)
- `tests/sira_worker` — **228/228** (VAPID JWT imzası açık anahtarla doğrulandı; aes128gcm
  round-trip çözüldü; tetikleme: yaklaştı bir kez, ilerleyince sıra sende, gece de gönderir, 410 → sil; abonelik
  uçları 503/201/400/409, yalnız token'a bağlı).
- `tests/sira_web` — **213/213** (çiftçi bildirim arayüzü statik kontrolleri).
- `tests/sira_e2e` — **53/53** (mevcut sıra akışı bozulmadı; bildirim VAPID yokken gizli).

## Sende kalan (canlıya alma)
1. `node cloud/zeytin-sira/vapid-uret.mjs` → iki secret ekle (`VAPID_ACIK_ANAHTAR`, `VAPID_GIZLI_ANAHTAR`).
   Ayrıntı: KURULUM.md **4b**.
2. `cloud/zeytin-sira/goc-bildirim.sql` → D1 Console'da Execute.
3. Push (deploy GitHub Actions ile). **Önce göç + secret, sonra push:** böylece canlı bir an bile tablosuz kalmaz.

Secret'leri ve göçü eklemezsen özellik uykuda kalır, mevcut sistem normal çalışır.

## Gerçekçi sınır
- iPhone'da Web Push yalnız sayfa Ana Ekrana eklenirse ve iOS 16.4+ ile çalışır; bazı telefonlarda bildirim gelmez,
  onlarda eski yöntem (sayfayı açık tut) kalır. Bu çiftçiye açıkça yazılır.
- Gerçek cihaza push bu ortamda gönderilmedi (ağ/secret yok); kripto round-trip ile doğrulandı. Canlıda ilk
  denemeyi kendi telefonunla yapman iyi olur.

## İP9-B (pilot sonrası, bu işin dışında)
Uygulamada "Sıram" kartı + yerel bildirim (mevcut FCM) + sıktırılan yağın deftere otomatik kaydı + aynı QR ile
App Link. Tasarım hazır; inşa pilot geri bildirimine bağlı.
