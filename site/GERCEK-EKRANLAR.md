# Zeytin Defteri — Gerçek uygulama ekranları (Adım 3)

Durum (6 Kasım 2026): **Üç gerçek Android görüntüsü kullanıcı tarafından önceki sohbetlerde sağlanmıştı; web için kırpılıp kişisel alanları gizlendi.** Bu işte GitHub'a yalnızca görsel yayın altyapısı, stil ve testler işlendi. İkili WebP dosyaları GitHub'a yüklenmedi; bu yüzden canlı sitede yeni galeri henüz görünmez. Bilgisayarda/telefonda açılabilen önizleme ve üçlü görsel paketi ayrı hazırlanmıştır.

## Kaynaklar ve şeffaflık

| WebP dosyası | Kaynak görüntü | Ekran |
| --- | --- | --- |
| `ana-sayfa.webp` | `1000179251.jpg` | Ana Sayfa / günlük iş ve hava durumu |
| `bahcelerim.webp` | `1000179252.jpg` | Bahçelerim / zeytinlikler |
| `elek-kaydi.webp` | `1000179253.jpg` | Elek Kaydı / sınıf, kilo ve fiyat |

Her görüntü telefonun sistem çubuklarından kırpıldı; `ana-sayfa` üzerindeki karşılama adı, `bahcelerim` üzerindeki zeytinlik adları bulanıklaştırıldı. Son dosyalar 360 × 731 pikseldir; toplam boyut yaklaşık 84 KB. Bunlar **13 Ekim 2026 tarihli uygulama ekran görüntüleridir, yeni APK sürümü doğrulanmış değildir.** Ekrandaki örnek fiyatlar/özetler güncel piyasa bilgisi anlamına gelmez.

## Nasıl yayına alınır?

1. `work/site` dalında ol; `main`, Android uygulama ve `work/web-baglanti` dallarına dokunma.
2. Üç WebP dosyasını `site/assets/screenshots/` klasörüne yerleştir veya bu çalışmada hazırlanan ZIP dosyasını Termux'tan şu komutla işle:

   ```sh
   sh site/tools/install_screenshots.sh /sdcard/Download/zeytin_gercek_ekranlar_paket.zip
   ```

3. Bu komut dosyaları kopyalar, `python3 site/tools/build_legal.py` ve `sh tests/site/run.sh` çalıştırır. **Commit, push veya Cloudflare yayını yapmaz**.
4. Testlerden sonra görüntüleri telefonda ve bilgisayarda kontrol et. Kişisel veri görünmüyorsa `git add site/index.html site/assets/screenshots` ile hazırlayıp yalnız `work/site` dalına commit/push yap.
5. Cloudflare Pages `work/site` dalına bağlıysa push sonrası otomatik dağıtım olabilir. Canlı adresi ve dağıtım sonucunu ayrıca doğrula.

## Fail-closed yayın davranışı

- Görsellerin üçü de yoksa `build_legal.py` galeriyi HTML'e eklemez.
- Yalnız bir veya iki WebP varsa üretim **hata verir**; eksik görselli sayfa oluşturulmaz.
- Dosyaların WebP başlığı ve bireysel dosya boyutları kontrol edilir.
- Sayfada ek JavaScript, üçüncü taraf font/izleyici veya dış kaynak kullanılmaz.
- `index.html` tek başına elle güncellenmez; kaynak `site/tools/home.template` + `site/tools/build_legal.py` kullanılır.

## Son doğrulama işleri

- [x] Gerçek ekran kaynaklarını kullanıcı görüntüleri içinde tespit et
- [x] Status bar kırpma ve seçili özel alan gizleme
- [x] Üç görseli WebP'ye dönüştür, boyutu küçült
- [x] Galeri için CSS ve mobil yerleşim kodunu yaz
- [x] Kırık link olmaması için üreticiyi ve testleri güncelle
- [ ] Üç WebP dosyasını GitHub `work/site` dalındaki klasöre yükle
- [ ] Gerçek tarayıcıda 360 px, 768 px ve 1440 px görsel kontrol yap
- [ ] Güncel APK ile ekranların uyumunu doğrula
- [ ] Cloudflare dağıtımını doğrula
