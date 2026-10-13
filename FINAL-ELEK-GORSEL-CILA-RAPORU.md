# Elek Kaydı görsel cila

Yalnızca XML/drawable/görsel kaynakları değişti. Tüm Java dosyaları, kayıt/hesaplama, fiyat tercihleri, repository, SQLite, UID/sezon, Firebase, navigasyon ve diğer ekranlar kaynak ZIP ile byte düzeyinde aynıdır.

Değişen mevcut dosyalar: activity_elek.xml ve elek_compact_input.xml.
Yeni kaynaklar: drawable-nodpi/elek_hero_olive.png (sağlanan görselin değiştirilmemiş kopyası), elek_hero_shade.xml, elek_save_premium.xml, altı elek_badge_*.xml ve values/elek_polish_colors.xml.

Hero aynı kompakt bölgede centerCrop ImageView ile doldurulur. FrameLayout mevcut yuvarlak background outline'ına clipToOutline uygular. Soldaki hafif karartma ayrı overlay, yazılar ayrı TextView olarak tutulur; görsele yazı işlenmedi. Hero 88 dp yüksekliğinde tutulur; görselin doğal boyutu ScrollView içinde kartı büyütmez. Çok büyük sistem yazı boyutlarında cihaz üzerinde görsel kontrol gereklidir.

Miktarlar 16 sp bold; fiyatlar 14 sp medium; tutarlar 14 sp bold yeşil; toplamlar 22 sp bold; ortalama 16 sp bold yeşildir. Sütun ağırlıkları, giriş yüksekliği ve sıra değişmedi. Sınıf sütununa 20 dp renkli rozetler eklendi. Focus border 2 dp oldu. Kaydet için yalnızca bu ekrana özel yeşil gradient kullanıldı.

Kontrol: ID kümeleri, View türleri, numeric input/IME/Next özellikleri ve tüm Java dosyalarının aynı kaldığı doğrulandı. XML parse kontrolü ve mevcut Activity hesaplama metotlarını çalıştıran test geçti. Yeni kaynaklar paket içinde kontrol edildi. Gradle denendi; ortamda dağıtım indirme nedeniyle Android build doğrulanamadı (günlük ekte). Gerçek Android render, küçük ekran, klavye ve cihaz kayıt testleri yapılamadı. Java kodu değiştirilmedi; başarılı tam build iddiası yoktur.
