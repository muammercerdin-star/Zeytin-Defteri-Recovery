# Zeytin Defteri — Tek Ekran Final

Bu sürümde uygulamanın üst seviye bölümleri tek Activity kabuğunda birleştirildi.

## Aynı ekranda çalışan bölümler
- Ana Sayfa
- Bahçelerim
- Meydan
- Piyasa
- Raporlar
- Profil

Alt navigasyona basıldığında yeni Activity açılmaz; `ProducerFeedActivity` içindeki içerik alanı değiştirilir.

## Ayrı kalan iş formları
Kayıt oluşturma/düzenleme gibi yoğun formlar mevcut çalışan ekranları kullanmaya devam eder:
- Bahçe ekleme/detay
- Yeni işlem
- Hasat
- Satış
- Paylaşım oluşturma
- Detaylı piyasa filtreleri
- Detaylı profil/hesap ayarları

Böylece mevcut veri yazma akışları bozulmadan ana gezinme tek ekrana alınmıştır.

## Firebase koruması
Aşağıdaki dosyalar bu revizyonda değiştirilmedi:
- `app/google-services.json`
- `firestore.rules`
- `storage.rules`
- `firebase.json`
- `SocialCloudRepository.java`
- `PrivateCloudSyncManager.java`

## Görsel revizyon
- Ana başlıklar 30–31sp
- Kart başlıkları 18–22sp
- Gövde metinleri 14–15sp
- Meydan mesajları 15sp
- Kategori çipleri 13sp
- Piyasa bandı 14sp
- Daha geniş dokunma alanları ve daha belirgin kontrast
