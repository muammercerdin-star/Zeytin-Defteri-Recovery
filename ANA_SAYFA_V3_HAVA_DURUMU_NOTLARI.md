# Zeytin Defteri - Ana Sayfa V3 / Bahçe Bazlı Hava Durumu

## Yapılan değişiklik

Ana sayfadaki hava durumu artık telefonun anlık konumunu kullanmaz. Ana sayfada gösterilen bahçenin kayıtlı konum bilgileri esas alınır.

Mevcut proje şemasında bahçeler için sayısal latitude/longitude kolonları bulunmadığı için sistem şu sırayla çalışır:

1. Bahçenin mahalle/köy + il bilgisi Open-Meteo Geocoding ile koordinata çevrilir.
2. Eşleşme olmazsa ilçe + il denenir.
3. O da olmazsa il denenir.
4. Bulunan koordinat bahçe ID'sine ve konum imzasına bağlı olarak app_settings içinde cache'lenir.
5. Bu koordinatla Open-Meteo Forecast API'den anlık sıcaklık, hava kodu, gündüz/gece ve rüzgar hızı alınır.

Bahçenin il/ilçe/mahalle bilgisi değişirse eski koordinat cache'i otomatik kullanılmaz; yeni konum tekrar çözümlenir.

## Ana sayfa gösterimi

- Üst sağda bahçenin mahalle/ilçe bilgisi görünür.
- Hava yüklenirken `Bahçe havası yükleniyor` yazılır.
- Başarılı olduğunda gerçek sıcaklık, hava simgesi, Türkçe durum ve rüzgar bilgisi gösterilir.
- Bahçe konumu yoksa sahte sıcaklık gösterilmez.
- İnternet/API hatasında `—°C` ve açıklayıcı hata durumu korunur.

## Yeni dosya

- `app/src/main/java/com/zeytindefteri/app/WeatherRepository.java`

## Değiştirilen dosya

- `app/src/main/java/com/zeytindefteri/app/ProducerFeedActivity.java`

## Korunanlar

- Firebase projesi ve google-services.json değiştirilmedi.
- SQLite şeması ve DB_VERSION değiştirilmedi.
- Mevcut kullanıcı/bahçe/işlem/satış kayıtlarına dokunulmadı.
- Telefon konum izni eklenmedi; gereksiz konum izni istenmiyor.
- Mevcut ana sayfa V2 tasarımı ve `home_hero_olive` görseli korundu.

## Teknik doğrulama

- 69 Android XML dosyası parse edildi: 0 hata.
- Gradle build denendi; çalışma ortamı `services.gradle.org` alan adına erişemediği için Gradle 8.7 indirilemeden durdu. Bu nedenle bu ortamda gerçek APK derlemesi doğrulanamadı.

## Sonraki iyileştirme

Bahçe ekleme/düzenleme ekranına gerçek harita pini veya GPS koordinatı (latitude/longitude) eklendiğinde WeatherRepository doğrudan bu koordinatı kullanacak şekilde genişletilebilir. Bu, mahalle merkezi yerine bahçenin gerçek noktasına daha hassas hava verisi sağlar.
