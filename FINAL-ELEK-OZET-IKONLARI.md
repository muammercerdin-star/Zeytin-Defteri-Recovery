# Elek özet ikonları

Değişen mevcut dosya: app/src/main/res/layout/activity_elek.xml.
Eklenen dosyalar (app/src/main/res/drawable-nodpi/): kantar_toplam_miktar_36dp.png, para_toplam_tutar_36dp.png, trend_ortalama_fiyat_24dp.png.

PNG dosyaları değiştirilmeden kopyalandı. Kantar Toplam Miktar'ın, para Toplam Tutar'ın solunda 36×36 dp; trend Ortalama Fiyat etiketinin solunda 24×24 dp. Her birinde fitCenter ve 8 dp metin aralığı var. Ortalamanın sağdaki değer alanının tüm özellikleri korundu. Üst iki alan eşit ağırlıklı, metin grupları kalan genişliği kullanır; metinler gerekirse satır kaydırır, sabit yükseklik veya ellipsis eklenmedi. Hero, renkler, yazı boyutları ve alt butonlar değiştirilmedi.

Tüm orijinal Java dosyaları ve XML dışındaki mevcut dosyalar kaynak ZIP ile byte düzeyinde aynı. SQLite, migration, Firebase, kullanıcı/sezon, tercihler, hesaplama ve onClick kodları değiştirilmedi.

Kontroller: XML parse, mevcut ID/tür eşleşmeleri, üç resource dosyası ve boyutları, PNG birebir karşılaştırması, mevcut ElekActivity hesaplama testleri geçti. Gradle assembleDebug denendi; dağıtım indirme ortam hatası nedeniyle APK doğrulanamadı (build günlüğü ekte). Android cihaz/emülatör çalıştırma, küçük ekran ve büyük yazı boyutu görsel testleri yapılamadı.
