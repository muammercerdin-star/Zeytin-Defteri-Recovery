# Zeytin Defteri - Ana Sayfa V5 Hava Şeridi

V5, V4 aktif bahçe sistemini korur ve hava kartına etkileşimli tahmin paneli ekler.

## Yeni davranış
- Sağ üstteki aktif bahçe adı (`Girelli Zeytinliği ▾`) aktif bahçe seçicisini açmaya devam eder.
- Hava sıcaklığı/durum alanına dokununca hero alanı yumuşak animasyonla genişler.
- İlk görünümde aktif bahçe için önümüzdeki 24 saat yatay film şeridi halinde gösterilir.
- Saat kartları sağa/sola kaydırılabilir ve sırayla giriş animasyonu yapar.
- Bir saate dokununca: hissedilen sıcaklık, rüzgar, nem ve yağış olasılığı o saate göre değişir.
- `5 Günlük` sekmesi 5 günlük tahmine geçer; günlük maksimum/minimum sıcaklık ve yağış olasılığı gösterilir.
- Hava kartına tekrar dokununca panel kapanır ve hero eski yüksekliğine animasyonla döner.
- Hava kartına uzun basmak da aktif bahçe seçimini açar.

## Veri kaynağı
- Hava tahminleri telefon konumundan değil, V4'teki aktif bahçenin kayıtlı konumundan alınır.
- Mevcut mahalle/ilçe/il -> koordinat cache/geocoding yapısı korunmuştur.
- Open-Meteo current + hourly + daily tahmin alanları kullanılır.

## Değiştirilen dosyalar
- `app/src/main/java/com/zeytindefteri/app/WeatherRepository.java`
- `app/src/main/java/com/zeytindefteri/app/ProducerFeedActivity.java`

## Korunanlar
- Firebase projesi / google-services.json değişmedi.
- SQLite şeması ve mevcut kayıtlar değişmedi.
- Aktif bahçe tercihi değişmedi.
- Bahçeye göre Bugün Yapılacaklar ve hızlı işlem hedefi korunur.
- Ana sayfa hero görseli ve mevcut tasarım dili korunur.

## Kontroller
- 69 XML kaynak dosyası parse edildi: hata yok.
- Java dosyaları javac ile sözdizimi aşamasına ulaştı; bu çalışma ortamında Android SDK classpath bulunmadığı için gerçek Android derlemesi burada doğrulanamadı.
- Gerçek cihaz doğrulaması AndroidIDE Build/Run ile yapılmalıdır.
