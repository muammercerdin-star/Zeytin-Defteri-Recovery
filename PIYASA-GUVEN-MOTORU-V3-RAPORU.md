# Zeytin Defteri – Piyasa Güven Motoru V3

Bu sürüm, `PIYASA-ELEK-KAYNAGI-V2-FIX` projesinin devamıdır. Mevcut Elek → Piyasa veri kaynağı korunmuştur; SQLite şeması, DB_VERSION, Firebase ve kullanıcı verileri değiştirilmemiştir.

## Yapılanlar

- Piyasa fiyatlarına korumacı **aykırı değer filtresi** eklendi.
  - 6 kayıttan az veri olduğunda agresif filtre uygulanmaz.
  - 6+ kayıtta IQR (çeyrekler arası aralık) kullanılır.
  - IQR sıfır olduğunda, çok uzak veri giriş hatalarını engellemek için geniş bir medyan güven bandı kullanılır.
  - Filtre veri kümesinin %40'tan fazlasını silmeye kalkarsa güvenlik amacıyla ham veriye geri dönülür.
- Aykırı kayıtlar medyan, min–max, bölge fiyatı ve grafikten çıkarılır.
- Kullanıcının **kendi son satışı silinmez**; kullanıcı kendi kaydını görmeye devam eder ve temizlenmiş piyasa medyanıyla karşılaştırılır.
- Güven seviyeleri güncellendi:
  - 1–2: Çok Az Veri
  - 3–5: Sınırlı Veri
  - 6–14: Orta Güven
  - 15–29: Güvenilir Veri
  - 30+: Yüksek Güven
  - Aykırı kayıt oranı %25'i aşarsa güven seviyesi bir kademe düşürülür.
- Grafik artık her ham kaydı ayrı nokta olarak çizmek yerine **aynı günün medyan fiyatını tek nokta** olarak gösterir.
- “Veriler nasıl hesaplanıyor?” penceresi gerçek seçim için:
  - incelenen kayıt sayısını,
  - aykırı olarak çıkarılan kayıt sayısını,
  - hesaplamaya katılan kayıt sayısını
  açıklayacak şekilde güncellendi.

## Korunanlar

- Zeytin Ligi UI/veri mantığı
- Elek kayıtları ve mevcut SQLite verileri
- owner_uid / aktif sezon izolasyonu
- Firebase / Firestore / Storage
- 7 öğeli alt navigasyon
- Piyasa ekranının mevcut görünümü ve filtre davranışı

## Kontroller

- `MarketUiDataProvider.java` gerçek dosyası, minimal Android/Repository/View stub'ları ile `javac` derlemesinden geçti.
- Sentetik testte 9 kayıttan biri 850 TL aşırı uç olduğunda:
  - 1 kayıt filtrelendi,
  - 8 kayıt hesaplamaya katıldı,
  - min–max aralığına 850 TL sızmadı,
  - aynı gün kayıtları günlük medyanla 3 grafik noktasına dönüştü.
- Tam Android Gradle build'i çalışma ortamında Gradle 8.7 dağıtımı bulunmadığı ve dış ağ kapalı olduğu için koşturulamadı. Son build doğrulaması AndroidIDE üzerinde yapılmalıdır.
