# Ana Sayfa V1 - 5 Eylül 2026

Bu paket, mevcut Zeytin Defteri çekirdek yapısını koruyarak ana sayfayı üreticinin günlük karar ekranı yönünde günceller.

## Yapılanlar
- Uygulama varsayılan açılışı Ana Sayfa olarak değiştirildi.
- Ana Sayfa için kalıcı marka başlığı gizlenerek referans tasarımdaki tam genişlikte karşılama/hero alanı oluşturuldu.
- Saat dilimine göre Günaydın / İyi günler / İyi akşamlar karşılama metni eklendi.
- Kullanıcının adının ilk kısmı ana ekranda gösteriliyor.
- Kayıtlı kullanıcı ilçesi varsa üst bölümde gösteriliyor. Gerçek hava servisi henüz projede olmadığı için sahte sıcaklık üretilmiyor; sıcaklık alanı güvenli olarak bekleme durumunda kalıyor.
- İlk kayıtlı bahçe için günlük takip kartı eklendi.
- Bahçenin gerçek sulama geçmişi aranıyor ve son sulamanın üzerinden geçen gün sayısı gösteriliyor.
- Otomatik tarımsal tavsiye yerine güvenli biçimde "Sulama kaydını kontrol et" bilgilendirmesi kullanılıyor.
- Sulama, Gübreleme, İlaçlama ve Hasat hızlı girişleri seçili bahçe kimliğiyle açılıyor.
- Piyasa Özeti alanı 2 No için mevcut, market_consent=1 satışlardan en yüksek hacimli iki bölgeyi gösteriyor.
- Piyasa kartlarında sahte fiyat/trend kullanılmıyor; gerçek yerel piyasa verisinden medyan fiyat, satış adedi ve kg hesaplanıyor.
- Sezon özeti Hasat / Masraf / Satış gerçek veritabanı toplamlarından besleniyor.
- Alt navigasyon referans ana sayfaya uygun olarak sadeleştirildi: Ana Sayfa, Bahçelerim, +, Meydan, Raporlar. Piyasa ana sayfadaki özet ve Meydan piyasa bandından erişilebilir; Profil diğer ekranlardaki üst profil butonundan erişilebilir.

## Korunanlar
- Firebase projesi ve google-services.json değişmedi.
- SQLite şeması ve migration sistemi değişmedi.
- Repository ve bulut senkronizasyon mimarisi değiştirilmedi.
- Kullanıcı verileri sıfırlanmadı veya silinmedi.
- Diğer ekranların koyu tema renk kodları korundu.

## Doğrulama
- Android XML dosyaları parse kontrolünden geçti.
- Değiştirilen Java dosyası javac ile sözdizimi aşamasını geçti; yalnız Android/Firebase sınıfları yerel JDK classpath'inde bulunmadığı için beklenen symbol/package hataları oluştu.
- Gerçek Gradle build denendi ancak çalışma ortamı services.gradle.org adresine erişemediği için Gradle 8.7 dağıtımı indirilemedi. Bu nedenle APK bu ortamda doğrulanamadı.
