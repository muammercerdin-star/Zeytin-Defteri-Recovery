# İlk gerçek demo — 15 adımlık kontrol listesi

**Durum: uygulanmadı / doğrulanmadı.** Bu dosya kullanıcı tarafından gerçek cihazlarda doldurulacak kabul listesidir. Host testlerinin geçmesi bu kontrollerin geçtiği anlamına gelmez.

Kurulumu yalnız telefonla yapabilirsiniz; bu kullanım demosu için QR'ı gösterecek bir bilgisayar veya ayrı ekran gerekir. [Web kurulumu](../../web/tezgah/KURULUM.md) ve [Worker kurulumu](../../cloud/tezgah-relay/KURULUM.md) tamamlanmış olmalı. Gerçek alımları silmeden önce demo hesabı/küçük test kayıtları kullanın. QR/token/üretici verisi içeren ekranları paylaşmayın.

Her satırın yanına tarih, cihaz/tarayıcı ve geçti/kaldı yazın. Gecikmeyi ekrandaki saatten değil, telefondaki işlemin bitişinden bilgisayarda görünene kadar ölçün.

| # | Yapılacak işlem | Beklenen sonuç | Sonuç |
|---|---|---|---|
| 1 | Termux'ta compileSdk 36 ve AGP 8.10.0 korunarak `bash gradlew :app:assembleRelease` çalıştırın; yeni APK'yı kurun. | OkHttp SDK 37 metadata hatası çıkmaz; derleme başarılıdır. | ☐ |
| 2 | Telefon ve bilgisayar Wi-Fi'deyken production Pages adresini açın. | Koyu temalı sayfa, QR ve en çok 90 sn geri sayım görünür; henüz alım verisi yoktur. | ☐ |
| 3 | QR'ı hiç okutmayıp 90 sn bekleyin. | Yeni QR otomatik gelir; eski oda kapatılır veya TTL ile sona erer. Eski QR fotoğrafını telefonda okutmayı deneyin. | ☐ |
| 4 | Telefon Tezgâh → Bilgisayara bağlan ile yeni QR'ı okutun; önce Vazgeç seçin. | Telefon onay penceresi açılır; bilgisayarda hiçbir alım görünmez. | ☐ |
| 5 | Güncel QR'ı okutun; bilgisayara ad verip Evet seçin. | Ön plan bildirimi “Bilgisayar bağlantısı açık”; sayfa “Bağlı”; açık dönem alımları görünür. | ☐ |
| 6 | Bilgisayardaki alım sayısı, kg, tutar, ödenen ve kalanı telefondaki açık dönemle karşılaştırın. | Aynı hesaptan aynı toplamlar; sonraki ödemeler dahil kalan doğru; Türkçe para/kg biçimi. Alım saati gösterilmez çünkü DTO'da yoktur. | ☐ |
| 7 | Takvim günü seçin; Z'ye girmiş bir gün varsa onu da deneyin; sonra Açık dönem düğmesine basın. | Takvim filtresi yalnız seçilen gündür; açık dönem Z ile kapanmamış alımlardır. Boş sonuç hata değildir. | ☐ |
| 8 | 100'den çok kayıt varsa Sonraki/Önceki ile gezin. | Kayıp/tekrar olmadan sayfalama; son sayfada Sonraki kapalı; özet tüm seçili kapsamın toplamı. Yeterli kayıt yoksa “uygulanamadı” yazın. | ☐ |
| 9 | Telefonda demo alımı ekleyin. | İşlem commit olduktan sonra **2 sn içinde** görünür kapsam yeniden alınır; son güncelleme değişir. Gerçek ölçülen süreyi yazın; 2 sn hedefi bu ortamda doğrulanmadı. | ☐ |
| 10 | İlgili demo alımına telefondan ödeme girin; güvenle silinebilen demo kaydını silin. | Bilgisayarda ödeme/kalan ve liste yenilenir; telefondaki yazma davranışı aynıdır. | ☐ |
| 11 | Telefon ekranını kilitleyin; bir dakika bekleyin, sonra açıp bir demo alım daha girin. | Bağlantı bildirimi sürer veya durum açıkça kopuk görünür; yeniden bağlanınca güncel veri gelir. Üreticiye/Android pil yönetimine bağlı davranışı kaydedin. | ☐ |
| 12 | Telefonda Wi-Fi'yi kapatıp mobil veriye geçin; bilgisayar bağlantısını koruyun. | Kısa kopmada tablo soluklaşır; 5 dk içinde “Yeniden bağlandı” ve güncel veri. workers.dev timeout olursa özel domain ile tekrar deneyin; bunu “geçti” saymayın. | ☐ |
| 13 | Tüketilmiş eski QR'ı tekrar okutmayı deneyin; mevcut oturumu kapatıp yeniden de deneyin. | İkinci tüketim reddedilir; eski QR yeni oturum açamaz. Geçerli capability ile teknik socket yenilemesi QR'ı ikinci kez tüketmek değildir. Yeni eşleşme için yeni QR gerekir. | ☐ |
| 14 | Telefonda Bağlı bilgisayarlar → Bağlantıyı kes seçin. Tekrar eşleşip bilgisayarda Bağlantıyı kes düğmesini de deneyin. | Her iki yönden kesmede eski tablo/anahtarlar temizlenir; bilgisayarda yeni onaysız QR gelir; eski oturumdan veri okunmaz. | ☐ |
| 15 | Yeniden eşleştirin; bilgisayara dokunmadan 5 dk bekleyin. Sonra tekrar eşleşip sekmeyi yenileyin. | Ağ ping/değişiklikleri idle süresini uzatmaz; 5 dk'da tablo temizlenir/yeni QR gelir. Sekme yenilemede de yeni QR ve tekrar telefon onayı gerekir. Arka plan sekmesinde süre, görünür olunca ayrıca kontrol edilir. | ☐ |

Bir adım kalırsa: gizli veri içermeyen hata kodu, ağ türü, cihaz/Android/tarayıcı sürümü, beklenen/gerçek davranış ve ölçülen süreyi not edin. Kamera QR'ı okuyamıyorsa tarayıcı yakınlaştırmasını %100 yapın, kodu tam gösterin ve beyaz kenarını kesmeyin. Canlı dağıtım ve bu liste tamamlanmadan “ilk gerçek demo başarılı” denmez.
