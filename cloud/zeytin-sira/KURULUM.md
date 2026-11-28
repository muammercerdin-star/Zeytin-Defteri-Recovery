# Yağhane sırası — kurulum ve dağıtım (İP9-A)

Bu rehberdeki adımları **sen** uygularsın. Geliştirme oturumu canlı veritabanında SQL çalıştırmadı, Cloudflare'e
dağıtım yapmadı, DNS'e dokunmadı ve Cloudflare API anahtarı istemedi. Cloudflare menü adları zamanla değişebilir;
gördüğün ad farklıysa en yakın karşılığını seç.

**Adres:** `https://sira.zeytindefteri.tr` (çiftçi linki `sira.zeytindefteri.tr/<token>`, panel
`sira.zeytindefteri.tr/yaghane`). Web Tezgâh'ın `web.zeytindefteri.tr` adresine ve Pages projesine dokunulmaz.

**Yayın sırası (AGENTS.md):** önce 1.6.0 Play'e çıkar ve site 2.5 yayınlanır; pilottan **önce** yasal metin 2.6
(`work/ip9-hukuk`) sitede yayınlanır. Pilot fabrikada kullanım 2.6 sitede görünmeden başlamaz.

## 0. Ön kontrol (2 dakika)

1. Cloudflare panelinde `zeytindefteri.tr` alanı **Active** olmalı (Web Tezgâh'ın `relay.` ve `web.` adresleri
   zaten burada).
2. **DNS → Records** listesinde `sira` adında bir kayıt **olmamalı**. Varsa ne olduğunu bilmeden silme; Custom
   Domain, aynı adda CNAME kaydı varken kurulamaz.

## 1. Veritabanı (D1)

1. **Storage & Databases → D1 SQL Database → Create**.
2. Ad: `zeytin-sira`. Konum ipucu (Location) sorarsa **Eastern Europe** seç (Türkiye'ye en yakın; veri yine de yurt
   dışındadır, KVKK metninde yazıyor). **Create**.
3. Açılan sayfada **Database ID** değerini kopyala (gizli değildir).
4. **Console** sekmesine geç. `cloud/zeytin-sira/schema.sql` dosyasının **tamamını** yapıştır, **Execute**.
   Beklenen: hata yok; **Tables** altında `yaghane, kullanici, sira, kod_yedegi, isabet, gun_ozet, oturum,
   giris_deneme, islem_kaydi, sayac`.

## 2. Wrangler ayar dosyası

GitHub'da (telefon tarayıcısı ya da GitHub uygulaması) `work/ip9-sira-web` dalında:

1. `cloud/zeytin-sira/wrangler.toml.ornek` dosyasını aç, içeriğini kopyala.
2. Aynı klasörde **yeni dosya**: `wrangler.toml`. İçeriği yapıştır, yalnız şu satırı değiştir:
   `database_id = "BURAYA_D1_KIMLIGI"` → 1. adımda kopyaladığın kimlik.
3. Commit. (Bu dosyada gizli bilgi yok; şifre ya da tuz buraya **yazılmaz**.)

## 3. Worker'ı GitHub'dan dağıt

1. **Workers & Pages → Create → Import a repository** (ya da *Connect to Git*). **Worker** seçili olmalı, Pages değil.
2. Depo: `muammercerdin-star/Zeytin-Defteri-Recovery`.
3. Ayarlar:

| Alan | Değer |
| --- | --- |
| Project name | `zeytin-sira` (wrangler.toml'daki `name` ile aynı olmalı) |
| Production branch | `work/ip9-sira-web` |
| Root directory / Path | `cloud/zeytin-sira` |
| Build command | boş |
| Deploy command | `npx --yes wrangler@4 deploy` |

4. **Deploy**. Beklenen: başarılı; `sira.zeytindefteri.tr` için Custom Domain ve sertifika **kendiliğinden**
   oluşturulur (wrangler.toml'daki `custom_domain = true`).
5. Kontrol: **Settings → Domains & Routes** içinde `sira.zeytindefteri.tr` → **Active**. Görünmüyorsa:
   **Add → Custom Domain** → `sira.zeytindefteri.tr` → **Add Custom Domain** (yol ya da `https://` yazma).
6. **Settings → Bindings** içinde `DB → zeytin-sira` ve `ASSETS` görünmeli; **Triggers / Cron** içinde `17 * * * *`.

## 4. Gizli tuz (GIZLI_TUZ)

Elle girişteki "telefonun son 2 hanesi" ve IP özeti bu tuzla HMAC'lenir. Tuz olmadan giriş ve elle giriş **çalışmaz**
(sunucu "Sunucu ayarı eksik" der).

1. Rastgele bir değer üret (en az 32 karakter). Termux'ta: `openssl rand -base64 36`
   (yoksa bir parola yöneticisinin 48 karakterlik rastgele parolası da olur).
2. **Workers → zeytin-sira → Settings → Variables and Secrets → Add**. Tür: **Secret**. Ad: `GIZLI_TUZ`.
   Değer: ürettiğin metin. **Deploy** / **Save**.
3. Değeri bir yere yazmana gerek yok; değişirse yalnız o günün elle giriş özetleri geçersiz olur.

## 4b. Bildirim anahtarı (VAPID) — "sıram gelince haber ver" için (isteğe bağlı)

Çiftçiye sırası yaklaşınca telefonuna bildirim gitmesini istiyorsan iki secret ekle. **Eklemezsen** bildirim özelliği
uykuda kalır (düğme hiç görünmez), sıra sistemi normal çalışır.

1. Anahtar çiftini üret (Termux ya da bilgisayar; Node yeter, paket kurulumu gerekmez):
   ```sh
   cd cloud/zeytin-sira && node vapid-uret.mjs
   ```
   İki satır basar: `VAPID_ACIK_ANAHTAR` ve `VAPID_GIZLI_ANAHTAR`.
2. **Workers → zeytin-sira → Settings → Variables and Secrets → Add**. İki **Secret** ekle:
   - Ad `VAPID_ACIK_ANAHTAR`, değer birinci satır (açık anahtar).
   - Ad `VAPID_GIZLI_ANAHTAR`, değer ikinci satır (**gizli** — kimseyle paylaşma, bir dosyaya yazma).
   - İstersen Ad `VAPID_KONU`, değer `mailto:destek@zeytindefteri.tr` (boşsa varsayılan kullanılır).
3. **Göç:** `cloud/zeytin-sira/goc-bildirim.sql` içeriğini **D1 → zeytin-sira → Console**'a yapıştır, **Execute**
   (yeni `bildirim_abone` tablosu; mevcut veriye dokunmaz). Bu olmadan abonelik kaydedilemez.
4. Deploy zaten GitHub Actions ile olur. Gizli anahtarı sonradan değiştirirsen o ana kadarki abonelikler
   geçersizleşir; çiftçiler "haber ver"e yeniden basar.

Gizlilik: telefonun "haber adresi" yalnız sıra koduna bağlanır (isim/telefon yok), bildirim metni kişisel veri
taşımaz, 24 saatte silinir. iPhone'da bildirim yalnız sayfa Ana Ekrana eklenirse ve iOS 16.4+ ile çalışır;
desteklemeyen telefonda çiftçiye "sayfayı açık tut" denir. Fabrikalar gece de çalıştığı için bildirim 7/24 gider
(gece kuralı yoktur).

## 5. Pilot fabrika ve iki kullanıcı (tohum)

Şifre ekrana yazılmadan sorulur, hiçbir dosyaya ve kabuk geçmişine yazılmaz; çıktıda yalnız PBKDF2 özetleri vardır.
Örnek değerler yer tutucudur; gerçek adları sen yaz.

```sh
# Termux (Node yoksa: pkg install nodejs)
set -e; cd ~/zeytin-final-build && git fetch origin && git checkout -B work/ip9-sira-web origin/work/ip9-sira-web
cd cloud/zeytin-sira
node tohum.mjs --fabrika "[FABRİKA ADI]" --kisa ornekyaghane --ofis ofis1 --havuzcu havuz1 > ~/tohum.sql
cat ~/tohum.sql
```

- `--kisa`: çiftçinin elle girişte yazacağı kısa ad (küçük harf/rakam, örn. fabrikanın kısa adı). Fişe yazılır.
- Şifreler en az 10 karakter. Ofis ve havuzcu şifreleri farklı olsun.
- `cat` çıktısındaki üç satırı **D1 → zeytin-sira → Console**'a yapıştır, **Execute**. Sonra dosyayı sil:
  `rm ~/tohum.sql`.

## 6. Deneme (pilot öncesi, 10 dakika)

1. `https://sira.zeytindefteri.tr/api/saglik` → `{"ok":true}`.
2. Bilgisayarda `https://sira.zeytindefteri.tr/yaghane` → ofis kullanıcısıyla giriş → **Fabrika anahtarı oluştur**.
   Gösterilen **parolayı kâğıda yaz** ve kasada sakla (kaybolursa o günün isimleri okunamaz; sıra çalışır).
3. Havuzcu telefonu: ofis ekranındaki **QR'ı telefonun kamerasıyla** okut → açılan sayfada havuzcu kullanıcısıyla
   giriş. Sarı "anahtar yok" uyarısı görünmemeli. (QR olmazsa parolayı elle yazar.)
4. Havuzcuda **+ Yeni müşteri** → isim "Deneme", kendi telefonun, kasa 1, kilo 100 → **WhatsApp'la gönder** →
   gelen linki **başka bir telefonda** aç: "Sıranız …", isim **görünmemeli**.
5. Havuzcu telefonunu **uçak moduna** al → bir müşteriye **Başlat** → üstte "Çevrimdışı, 1 işlem bekliyor" →
   uçak modunu kapat → birkaç saniyede "● Bağlı".
6. **Ekrana yansıt (TV)** sekmesi: isimler "Deneme" gibi kısaltılmış, kilo yok.
7. `https://sira.zeytindefteri.tr/bul/?f=ornekyaghane` → kod + telefonunun son 2 hanesi → kendi sıra sayfan.
8. Deneme kayıtlarını bırakabilirsin (24 saatte isimler, 90 günde satırlar kendiliğinden silinir).

## 7. Pilotta ölçülecekler

- **Giriş süresi / işlemci sınırı:** Ücretsiz planda istek başına 10 ms işlemci süresi var; şifre özeti (PBKDF2,
  100.000 tekrar) bu sınıra yakın olabilir. Ölçmek için: **Workers → zeytin-sira → Metrics → CPU time** (giriş
  istekleri) ve ofis panelindeki "Son giriş süresi". Girişte "Sunucu şu an cevap vermedi" görülürse tekrar sayısı
  düşürülerek yeniden tohum üretilir (`node tohum.mjs … --tekrar 50000`); tekrar sayısı her özetin içinde saklı
  olduğu için eski şifreler bozulmaz. Oturum 30 gün sürdüğü için giriş seyrektir.
- **Günlük istek:** ofis panelinde "Bugünkü API isteği: … / 100.000". Statik sayfalar sayılmaz; çiftçi sayfası yalnız
  ekrandayken dakikada bir sorar.

## 8. Komut satırından (isteğe bağlı, bilgisayar ya da Termux)

GitHub bağlantılı dağıtım yerine Wrangler ile:

```sh
cd cloud/zeytin-sira
npx wrangler@4 login
npx wrangler@4 d1 create zeytin-sira            # çıktıdaki database_id'yi wrangler.toml'a yaz
cp wrangler.toml.ornek wrangler.toml            # ve database_id satırını düzelt
npx wrangler@4 d1 execute zeytin-sira --remote --file schema.sql
openssl rand -base64 36 | npx wrangler@4 secret put GIZLI_TUZ
npx wrangler@4 deploy
node tohum.mjs --fabrika "[FABRİKA ADI]" --kisa ornekyaghane --ofis ofis1 --havuzcu havuz1 > /tmp/tohum.sql
npx wrangler@4 d1 execute zeytin-sira --remote --file /tmp/tohum.sql && rm /tmp/tohum.sql
```

## 9. Kapatma / geri alma

- Geçici kapatma: **Workers → zeytin-sira → Settings → Domains & Routes** → `sira.zeytindefteri.tr` → **Remove**.
  Çiftçi linkleri ve panel açılmaz; veri durur.
- Tamamen kaldırma: Worker'ı sil, sonra **D1 → zeytin-sira → Delete**. Bütün sıra verisi silinir.
- Web Tezgâh (`web.` / `relay.`) bu işlemlerden etkilenmez.

## Resmî kaynaklar

- [Workers Static Assets ve `run_worker_first`](https://developers.cloudflare.com/workers/static-assets/binding/)
- [Tek sayfa uygulama yönlendirmesi](https://developers.cloudflare.com/workers/static-assets/routing/single-page-application/)
- [Worker Custom Domains](https://developers.cloudflare.com/workers/configuration/routing/custom-domains/)
- [Workers sınırları (ücretsiz plan)](https://developers.cloudflare.com/workers/platform/limits/)
- [D1 sınırları](https://developers.cloudflare.com/d1/platform/limits/)
