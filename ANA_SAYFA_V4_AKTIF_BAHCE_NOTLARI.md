# Ana Sayfa V4 — Aktif Bahçe

Bu sürüm V3 hava durumu paketinin devamıdır.

## Yapılanlar

- Ana sayfaya kullanıcı bazlı **aktif bahçe** tercihi eklendi.
- İlk kullanımda mevcut kullanıcıya ait ilk bahçe aktif seçilir ve tercih kaydedilir.
- Sağ üstteki hava alanında artık aktif bahçe adı `Bahçe Adı ▾` biçiminde görünür.
- Hava alanına dokunulduğunda kayıtlı bahçelerden aktif bahçe seçilebilir.
- Seçim `app_settings` içinde kullanıcı anahtarına göre saklanır; başka kullanıcıya tercih taşınmaz.
- Aktif bahçe değişince ana sayfa anında yenilenir.
- Hava durumu yeni aktif bahçenin kayıtlı il/ilçe/mahalle bilgisinden hesaplanan konuma göre yeniden yüklenir.
- **Bugün Yapılacaklar** kartı aktif bahçenin fotoğrafı, son sulama kaydı ve durumuyla yenilenir.
- Sulama / Gübreleme / İlaçlama / Hasat hızlı işlemleri aktif bahçenin `garden_id` değeriyle açılır.
- “Tümünü Gör” işlem geçmişine aktif bahçenin `garden_id` değeri de gönderilir (mevcut ekran desteklediği ölçüde kullanılabilir).
- Firebase projesi, google-services.json, SQLite tabloları ve mevcut kullanıcı kayıtları değiştirilmedi.
- Yeni database migration gerekmedi.

## Seçim mantığı

1. Kullanıcının daha önce seçtiği aktif bahçe hâlâ mevcutsa onu kullan.
2. Seçim yoksa kullanıcıya ait ilk bahçeyi kullan ve tercihi kaydet.
3. Bahçe silinmiş/geçersizse tekrar ilk geçerli bahçeye düş.
4. Kullanıcı hava alanına dokunarak aktif bahçeyi istediği zaman değiştirebilir.

## Doğrulama

- `app/src/main/res` altındaki XML dosyaları parse edildi: hata yok.
- Java kaynak dosyası `javac` ile ayrıştırma aşamasına girdi; çalışma ortamında Android SDK sınıfları classpath'te olmadığı için gerçek Android derlemesi burada tamamlanamadı.
