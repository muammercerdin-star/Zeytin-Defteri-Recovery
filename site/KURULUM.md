# Zeytin Defteri — telefondan kurulum

Doğrulama tarihi: 3 Kasım 2026. Adımlar resmî dokümana göre hazırlanmıştır; kullanıcının hesabında canlı yayın ve telefondaki panel görünümü doğrulanmadı. Cloudflare menü adları zamanla değişebilir. Bu iş kapsamında yayınlama, alan adı satın alma ve e-posta yönlendirmesi yapılmadı.

## 1. İlk yayın: yalnız telefon ve Cloudflare paneli

1. Telefon tarayıcında [Cloudflare panelini](https://dash.cloudflare.com/) aç, hesabına giriş yap. Dar ekranda alan görünmezse tarayıcının **Masaüstü sitesi** seçeneğini aç.
2. **Workers & Pages** bölümüne gir. Yeni uygulama oluşturma ekranında **Pages** ve **Connect to Git** / GitHub bağlantısını seç. Workers oluşturma akışı yerine Pages seçili olmalı. Panel sürümüne göre önce **Create application**, ardından Pages sekmesi görünebilir.
3. GitHub yetkilendirmesinde hesabını seç; erişilecek depo olarak **muammercerdin-star/Zeytin-Defteri-Recovery** seç. Cloudflare'a dönünce depo listesinde bu adı görmelisin. Depoya dokun, **Begin setup** ile ilerle.
4. Ayar ekranını aşağıdaki tabloya göre doldur. **Root directory (advanced)** bölümünü açınca **Path** alanı görünür.

| Ekrandaki alan | Değer |
| --- | --- |
| Project name | Örneğin `zeytin-defteri` — uygunluk doğrulanmadı |
| Production branch | `work/site` |
| Framework preset | `None` / çerçeve yok |
| Root directory → Path | `site` |
| Build command | Boş bırak |
| Build output directory | `.` — seçilen `site` kökünün kendisi |
| Environment variables | Ekleme |

5. **Save and Deploy** düğmesine dokun. İşlem günlüğü, ardından başarılı dağıtım ve `https://<proje-adı>.pages.dev` bağlantısı görünür. Verilen bağlantıyı aç; ana sayfa, Gizlilik ve Hesap silme bağlantılarını kontrol et. **Continue to project** ekranında üretim adresi ve commit bilgisi görünür.
6. Pages ayarlarında **Web Analytics** ve benzeri takip özelliklerini açma. Bu site dış font, CDN, JavaScript, analitik ve çerez kullanmaz.

Cloudflare Git rehberi, derleme gerekmiyorsa komutu boş bırakmayı açıkça destekler. Statik HTML rehberinde Functions gibi özellikler için `exit 0` da önerilir; bu projede Functions yok ve istenen ayar **boş komuttur**. Çıktı klasörünü `site` yazma: kök zaten `site` olduğundan `.` seçilir.

## 2. Gerçek yayın adresini kaydetme

`config.json` içindeki `SITE_URL` başlangıçta gelecekteki hedef olan `https://zeytindefteri.com` değeridir. Alan adının satın alındığı, sahipliği veya bu adreste yayın yapıldığı **doğrulanmadı**. İlk `pages.dev` adresi belli olduğunda, arama motoruna bildirmeden önce geliştirici gerçek adresi bu alana yazmalı, aşağıdaki üretimi çalıştırıp oluşan dosyaları commit/push etmelidir. Telefon kullanıcısı panelden bu adresi geliştiriciye iletebilir.

```sh
python3 site/tools/build_legal.py
sh tests/site/run.sh
```

Betik geliştirici tarafından çalıştırılır; Cloudflare derleme komutu değildir. GitHub'a gönderilen hazır HTML'ler yayınlanır. `work/site` dalına sonraki push'lar bağlı Pages projesini otomatik günceller. `robots.txt`, `sitemap.xml` ve Open Graph adresleri tek ayardan üretilir. Üretim kontrolü için `python3 site/tools/build_legal.py --check` kullanılır.

E-posta `config.json` içinden alınır ve uygulamadaki `LegalDocuments.CONTACT_EMAIL` ile aynı olmak zorundadır; farklıysa üretim durur. Yasal içerikler uygulamadaki üç `.txt` dosyasıdır; hazır HTML'leri elle değiştirme. Logolar da uygulamanın vektör path'lerinden üretilir. Python ve Java yalnız geliştirici testleri içindir; ziyaretçinin telefonu hiçbirini çalıştırmaz.

## 3. Alan adı alınınca — daha sonra yapılacak

1. Alan adını bir kayıt firmasından satın al. Alan adı ücretsiz değildir; bu proje satın alma yapmaz.
2. Cloudflare'a **zeytindefteri.com** alan adını ekle ve ücretsiz planı seç. Verilen iki nameserver adresini kayıt firmasının ad sunucuları ekranına yaz. Cloudflare'da alanın etkinleşmesini bekle.
3. **Workers & Pages → Pages projesi → Custom domains → Set up a custom domain** yolunu aç. `zeytindefteri.com` yaz, devam et ve DNS önerisini onayla. Ana alan adı, Pages projesiyle aynı Cloudflare hesabında olmalı. Panelin oluşturduğu CNAME kaydı ve alan durumunu kontrol et.
4. Durum etkin olduktan sonra HTTPS adresini telefonda aç. `SITE_URL` değerini yeni adrese aldır; üretim, test ve commit/push işlemlerini tekrarla.

## 4. destek@zeytindefteri.com yönlendirmesi — daha sonra yapılacak

1. Cloudflare panelinde **Compute → Email Service → Email Routing** bölümünü aç. **Onboard Domain** ile alan adını seç. Gösterilen MX, SPF ve DKIM kayıtlarını incele, **Done** ile tamamla. Mevcut bir e-posta hizmeti varsa MX kayıtlarını değiştirmeden önce birlikte kullanımını değerlendir.
2. **Destination Addresses** bölümüne kişisel alıcı e-postanı ekle. O kutuya gelen doğrulama mesajında **Verify email address** bağlantısına dokun.
3. Alanın **Routing Rules** sekmesinde **Create routing rule** seç. **Email pattern** alanında `destek` ve `zeytindefteri.com`, **Action** alanında **Send to an email**, **Destination** alanında doğruladığın kişisel adresi seç. **Save** ile kaydet.
4. Başka bir e-posta adresinden `destek@zeytindefteri.com` adresine deneme mesajı gönder; kişisel kutuya ulaştığını kontrol et.
5. Adres çalışınca geliştirici uygulamadaki iletişim adresi ile sitenin `config.json` adresini **aynı değişiklikte** güncellemeli, metinleri yeniden üretip test etmelidir. Bu teslimde uygulama adresi değiştirilmedi.

Email Routing ile gelen e-postayı kişisel kutuya yönlendirmek ücretsizdir. Bu kurulum ayrı bir posta kutusu veya o adresten gönderim hizmeti kurmaz. Statik Pages içerikleri ücretsiz planda barındırılabilir; alan adı satın alma/yenileme ücretleri ayrıdır.

## Resmî kaynaklar

- [Pages Git bağlantısı ve boş derleme komutu](https://developers.cloudflare.com/pages/get-started/git-integration/)
- [Kök ve çıktı dizini ayarları](https://developers.cloudflare.com/pages/configuration/build-configuration/)
- [Statik HTML yayını](https://developers.cloudflare.com/pages/framework-guides/deploy-anything/)
- [Pages özel alan adı ve nameserver koşulları](https://developers.cloudflare.com/pages/configuration/custom-domains/)
- [Email Routing kurulumu, alıcı doğrulama ve yönlendirme](https://developers.cloudflare.com/email-service/get-started/route-emails/)
- [Ücretsiz e-posta yönlendirmesi](https://developers.cloudflare.com/dns/manage-dns-records/how-to/email-records/)
- [Pages ücretsiz plan sınırları](https://developers.cloudflare.com/pages/platform/limits/)
