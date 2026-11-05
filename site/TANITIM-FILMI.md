# Zeytin Defteri — Tanıtım Filmi Üretim Planı

Durum: **Sinematik afiş/anasayfa bölümü hazır; ana video henüz üretilmedi veya yayınlanmadı.**  
Çalışma dalı: `work/site`. Yalnızca `site/` kapsamı; `work/web-baglanti` ve Android uygulaması değiştirilmeyecek.

## Tasarım ilkesi

Apple ürün sayfalarındaki geniş tipografi ve sade ürün anlatımından öğreniyoruz; marka ve görseller Zeytin Defteri'ne ait olacak. Gece siyahı, zeytin yeşili, sıcak krem, altın ışık. Kadrajda parlak siyah Gemlik/Trilye sofralık zeytin. Yeşil zeytin, abartılı veri iddiası ve doğrulanmamış gerçek zamanlı fiyat gösterimi yok.

## Üç ayrı içerik

1. **Sessiz giriş — 8 sn:** Ana sayfada yalnızca kullanıcının hareket tercihine, bağlantı hızına ve tarayıcı izinlerine uygun şekilde isteğe bağlı dekoratif oynatma. Önizleme için `tanitim-kapak.svg` her zaman çalışır. Otomatik ses **asla yok**.
2. **Ana ürün tanıtımı — 55 sn:** Ayrı başlatılan, kontrolleri ve Türkçe altyazısı olan ana video. Önce gerçek uygulama özellikleri doğrulanacak.
3. **Uzun hikâye — 2:58:** İsteğe bağlı ayrı sayfa/oynatıcı. Ana sayfayı zorlayarak açmayacak.

**Mevcut video notu:** Önceden hazırlanmış `Zeytin-Defteri-60-Saniye.mp4` (60 sn, 720 × 1280, H.264/AAC) görsel ağırlıklı özellik sunumudur; tezgâh ve mali hesap anlatır, planlanan sinematik filmin yerini doğrudan tutmaz. Mevcut film kamusal bir URL'ye yüklenmiş değildir; bu yüzden anasayfaya bozuk video bağlantısı eklenmedi.

## 55 saniyelik sahne listesi

| Süre | Görsel | Önerilen seslendirme |
|---|---|---|
| 00–05 | Sisli Ege zeytinliği, sıcak ışık | “Bir sezonluk emek…” |
| 05–11 | Yağmurun altında eski kâğıt defter | “Her masraf, her hasat, her satış…” |
| 11–17 | Çiftçinin elindeki telefon, gerçek uygulama görüntüsüne geçiş | “Artık hepsi tek bir yerde.” |
| 17–25 | Bahçe ve hasat ekranlarının doğrulanmış kayıt akışı | “Bahçeni, yaptığın işi ve hasadını takip et.” |
| 25–33 | Metal elek, sesle veri girişi ve kaydın doğrulanması | “Elek hesabını söyle, kaydını kolaylaştır.” |
| 33–41 | Satış, masraf ve tahsilat ekranları | “Ne harcadığını, ne kazandığını bil.” |
| 41–48 | Piyasa ve Zeytin Ligi özelliklerinin uygun mevcut durum etiketli gösterimi | “Bölgene bak, emeğini daha iyi değerlendir.” |
| 48–55 | Siyah zeytin + telefon + Zeytin Defteri logosu | “Zeytin Defteri. Emeğinin kaydı.” |

Seslendirme sürelere göre yeniden prova edilecek. Gerçekte olmayan ekran, veri veya otomatik işlem gösterilmeyecek. Hikâyedeki ekranların hepsi dağıtılacak sürümle karşılaştırılacak.

## Video üretim / web yayın kontrol listesi

- [x] Durağan özgün tanıtım kapağı: `site/tanitim-kapak.svg` (harici kaynağa bağlı değil).
- [x] Ana sayfadaki sinematik bölüm ve 55 sn sahne planı.
- [x] Mobil görünüm ve `prefers-reduced-motion` CSS desteği.
- [ ] Gerçek uygulama ekran kayıtlarını doğrula ve izinleri kontrol et.
- [ ] Filmin 16:9 masaüstü, 9:16 telefon kesimlerini hazırla.
- [ ] Sinematik ana filmi en fazla 55 saniye olacak şekilde kurgula.
- [ ] H.264 MP4, uygun boyut ve Türkçe `.vtt` altyazı dosyasını oluştur.
- [ ] Video yükleme alanı seç (Pages dosya sınırına uyan küçültülmüş MP4 veya ayrı video deposu).
- [ ] Dosya doğrulanınca çalışan **Filmi İzle** oynatıcısı, durdurma/altyazı/tam ekran kontrolleri ve `poster` önizlemesini ekle.
- [ ] Gerçek video bağlandıktan sonra “film hazırlanıyor” etiketini kaldır.
- [ ] 360 px ve masaüstünde bağlantı, performans, klavye ile gezinme ve düşük veri kullanımını test et.
- [ ] Cloudflare Pages canlı yayınına ayrıca onaylı dağıtım yap.

## Teknik kurallar

- Otomatik ses yok; ana film yalnızca bilinçli tıklamayla yüklenir.
- Kapak görselinin boşluğu doldurması için `object-fit: cover`; mobilde yeniden kadraj.
- Bağlantısız veya hata veren video URL'sine oynatıcı düğmesi gösterilmez.
- Kullanıcının hareket azaltma ayarına saygı.
- Diğer zorunlu sayfalar (gizlilik, KVKK, koşullar, hesap silme, iletişim) korunur.
- Tanıtım sitesi, telefon-bilgisayar tezgâh bağlantısına bağımlı değildir.

