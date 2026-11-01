# İnternet yardımcısı – Cloudflare kurulumu (yaklaşık 10 dakika, ücretsiz)

Bu sunucu, asistanın kurallarla anlayamadığı bahçe işi cümlelerini Gemma 4'e (Cloudflare Workers AI) sorar.
Hiçbir anahtar ya da şifre gerekmez; model, sunucuya Cloudflare panelinden "bağlanır".

## 1. Sunucuyu oluştur
1. dash.cloudflare.com'a gir.
2. Sol menüde **Compute (Workers)** ya da **Workers & Pages** bölümünü aç.
3. **Create** (Oluştur) → **Worker** → "Hello World" şablonunu seç.
4. Adını **zeytin-ai** yap → **Deploy** (Yayınla).

## 2. Kodu yapıştır
1. Açılan sayfada **Edit code** (Kodu düzenle) düğmesine bas.
2. Soldaki dosyanın (worker.js) içindekilerin hepsini sil.
3. Bu klasördeki `worker.js` dosyasının tamamını kopyalayıp yapıştır.
4. Sağ üstteki **Deploy** (Yayınla) düğmesine bas.

## 3. Modeli bağla
1. Worker'ın sayfasına dön → **Settings** (Ayarlar) → **Bindings** (Bağlamalar).
2. **Add** (Ekle) → **Workers AI**.
3. Değişken adı (Variable name): tam olarak **AI** yaz (büyük harf) → Kaydet.

## 4. (İsteğe bağlı) Kullanıcı başına sınır
Panelde **Rate limiting** bağlaması ekleyebiliyorsan: adı **RATE**, limit **6**, süre **60** saniye.
Ekleyemezsen sorun değil; sunucu yine yalnız giriş yapmış Zeytin Defteri kullanıcılarına cevap verir.

## 5. Dene
Tarayıcıda Worker'ın adresini aç (örnek: `https://zeytin-ai.KULLANICIADIN.workers.dev`).
Şunu görmelisin: `{"ok":true,"service":"zeytin-ai"}`

Bu adresi geliştiriciye gönder. Adres gizli değildir; şifre ya da anahtar GÖNDERME.

## 6. Uygulamaya adresi yaz (Termux)
```
cd ~/zeytin-final-build
echo "ZEYTIN_AI_URL=https://zeytin-ai.KULLANICIADIN.workers.dev" >> local.properties
```
Sonra her zamanki gibi derle. Adres yazılmazsa özellik tamamen kapalı kalır.

## Ücret ve sınırlar
- Workers AI günde 10.000 "nöron" ücretsiz; bir cümle yaklaşık 5 nöron (yaklaşık 1.900 cümle/gün).
- Ücretsiz planda kota dolarsa model o gün hata verir; uygulama kurallarla devam eder, ücret çıkmaz.
- Cloudflare, Workers AI'a gelen içerikle model eğitmez; sunucu cümleleri hiçbir yere yazmaz.

## Güncelleme
`worker.js` değişirse 2. adımı tekrarla (kopyala, yapıştır, Deploy).
