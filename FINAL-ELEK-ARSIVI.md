# Elek Arşivi – kayıt kartları

Değişen mevcut dosyalar:
- app/src/main/java/ElekArchiveActivity.java
- app/src/main/res/layout/activity_elek_archive.xml

Eklenen dosyalar:
- app/src/main/res/layout/elek_archive_header.xml
- app/src/main/res/layout/item_elek_archive.xml
- app/src/main/res/drawable/elek_archive_card.xml
- tests/elek_archive/check_archive.py
- Bu rapor ve BUILD-ARCHIVE.txt

Eski HorizontalScrollView ve 1240dp tablo kaldırıldı. Yerine yerel Android ListView, kaydırılan başlık ve tekrar kullanılan kart görünümleri konuldu; yeni bağımlılık yok. Kartta Türkçe tarih/gün, bahçe, 6 gerçek sınıf miktarı ve sağda toplam kg/tutar/ortalama bulunur. Dar telefonlarda sınıflar okunabilirliği korumak için 3×2 düzenindedir. Metinler wrap_content ve sp kullanır; uzun değerler gerektiğinde satıra geçer. Üstte yalnızca üç eşit ağırlıklı özet kartı vardır. Kayıt sayısı, alt tekrar toplamları ve eski bilgi kartı UI'dan kaldırılmıştır. recordCount hesabı boş durum için korunur.

Mevcut kantar_toplam_miktar_36dp, para_toplam_tutar_36dp, trend_ortalama_fiyat_24dp PNG'leri ve elek_badge_* kaynakları yeniden kullanılır. PNG değişikliği yoktur. Başlık ic_olive_logo kullanır. Gerçek bir Elek detay ekranı bulunmadığı için chevron görseldir; yeni navigasyon yoktur.

Orijinal SQL metni ve yıl/bahçe parametreleri kaynak ZIP ile birebir karşılaştırıldı, aynıdır. Ortalama mevcut toplam tutar / toplam kg hesabıdır. Üst değerler filtrelenmiş sorgu sonuçlarından gelir. Sayı biçimi Türkçe #,##0.## olarak korunur. owner_uid veya sezon sorgu semantiği değiştirilmedi; bu çalışma mevcut izolasyon davranışını yeniden uygulamaz veya denetlediği iddiasında bulunmaz.

Doğrulama:
- Tüm XML dosyaları parse edildi; arşiv view ID'leri ve drawable referansları bulundu.
- Gerçek SQL, geçici SQLite test verisiyle yıl+bahçe, sıralama, altı sınıf, toplam, ortalama ve boş sonuç için çalıştırıldı.
- Gerçek Java tarih/gün/sayı metotları JVM'de çalıştırıldı: 07 Eylül 2026 Pazartesi; 05 Eylül 2026 Cumartesi.
- Mevcut Elek hesaplama ve fiyat popup testleri geçti.
- ElekArchiveActivity Java 8 sözdizimi parse kontrolü geçti (Android derlemesi değildir).
- Kaynak ZIP'teki diğer bütün dosyalar birebir aynı; ElekActivity, ana dashboard XML/View, fiyat popup, DatabaseHelper, repository, Firebase ve migration değişmedi.
- assembleDebug: Gradle 8.7 indirmesinde UnknownHostException: services.gradle.org. Tam Android compile/resource linking/APK doğrulanamadı.
- Emülatör/gerçek cihaz testi yapılmadı. Görsel taşma, büyük sistem fontu ve fiziksel filtre tıklamaları cihazda ayrıca kontrol edilmelidir.
