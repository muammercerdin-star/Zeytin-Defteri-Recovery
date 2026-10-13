# Zeytin Defteri - Hata Temizleme Revizyonu

Bu revizyonda Firebase/Firestore yapılandırmasına dokunulmadan tek-ekran kabuğundaki görünür ve mimari tutarsızlıklar temizlendi.

- Profil ana sekmesinden eski açık renk `ProfileActivity`ye geçiş kaldırıldı; profil bilgileri aynı koyu tek-ekran kabuğunda tutuluyor.
- Piyasa ana sekmesinden eski `MarketActivity`ye giden "Detaylı filtreleri aç" geçişi kaldırıldı.
- Raporlar ana sekmesinden eski `ReportActivity`ye giden geçiş kaldırıldı; bahçe performansı aynı sayfaya eklendi.
- Bahçe fotoğrafı için bozuk/geçersiz URI durumunda Glide placeholder/error fallback eklendi.
- Scroll sayfalarında alt navigasyonun içeriği kapatmaması için alt boşluk artırıldı.
- Meydan reaksiyon/yorum yazılarının kontrastı artırıldı.
- Meydan kategori çipleri biraz küçültülerek daha fazla başlığın ilk görünümde görünmesi sağlandı.
- Alt menü yazıları okunabilirlik için büyütüldü.
- Mevcut Firebase Authentication, Firestore sosyal akış, `SocialCloudRepository`, `PrivateCloudSyncManager`, kurallar ve google-services dosyaları korunmuştur.
