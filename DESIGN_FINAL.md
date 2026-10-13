# Zeytin Defteri — Meydan Final Tasarım

Bu paket, kullanıcının seçtiği açık/krem “Meydan” referansını ana ürün yönü olarak kullanır.

## Değişmeyen kritik altyapı
- `app/google-services.json`
- Firestore bağlantıları ve `SocialCloudRepository`
- Firebase Authentication
- Özel kullanıcı bulut senkronizasyonu (`PrivateCloudSyncManager`)
- SQLite/Repository veri katmanı
- Firestore ve Storage rules dosyaları

## Yeni ana deneyim
- Uygulamanın ana giriş noktası doğrudan Meydan deneyimidir.
- #Genel, #Piyasa, #Hastalık, #Hasat, #Sulama, #Alım-Satım kanalları.
- Firestore’daki sosyal gönderiler aynı veri modelinden okunur.
- Piyasa bandı mevcut MarketRepository verisini kullanır.
- Mesaj/fotoğraf oluşturma mevcut CreatePostActivity’ye gider.
- Bahçeler, Piyasa, Raporlar ve diğer mevcut modüller korunur.

## Mimari karar
`MainActivity`, `ProducerFeedActivity` tabanını kullanır. Böylece eski ağır dashboard yerine seçilen Meydan tasarımı doğrudan ana ekran olurken Firebase ve repository katmanları korunur.
