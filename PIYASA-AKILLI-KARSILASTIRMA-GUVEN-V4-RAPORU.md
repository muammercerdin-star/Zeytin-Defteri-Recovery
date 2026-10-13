# Zeytin Defteri – Piyasa Akıllı Karşılaştırma + Güven V4

## Amaç
V3'te çalışan Elek → Piyasa veri hattını bozmadan Piyasa ekranını daha anlamlı hale getirmek.

## Yapılanlar

### 1. Önceki dönem karşılaştırması
- Son 7 Gün → önceki 7 gün
- Son 30 Gün → önceki 30 gün
- Son 90 Gün → önceki 90 gün
- Aynı elek + aynı bölge için önceki dönem medyanı hesaplanır.
- Ana kartta TL farkı ve yüzde farkı birlikte gösterilir.
- Örnek: `+10,00 TL • önceki 7 güne göre +%12,50`
- Önceki dönem verisi yoksa açıkça belirtilir.
- `Bu Sezon` seçiminde aktif sezon özeti gösterilir; başka sezon verisi karıştırılmaz.

### 2. Akıllı güven puanı (0–100)
Güven artık yalnız kayıt sayısına göre belirlenmez. Dört bileşen kullanılır:
- Kayıt sayısı: 45 puan
- Güncellik: 20 puan
- Fiyat dağılımı / IQR-medyan yakınlığı: 25 puan
- Aykırı kayıt oranı: 10 puan

Kayıt sayısı güven seviyesinin üst sınırıdır. Örneğin 3 temiz kayıt, puanı yüksek olsa bile `Güvenilir Veri` seviyesine çıkamaz.

Güven etiketleri korunur:
- Çok Az Veri
- Sınırlı Veri
- Orta Güven
- Güvenilir Veri
- Yüksek Güven

### 3. Bilgi paneli sadeleştirildi
İlk panelde yalnız temel sonuçlar gösterilir:
- Kullanılan kayıt
- Aykırı çıkarılan kayıt
- Medyan
- Önceki dönem medyanı
- Güven etiketi + 0–100 puan

Teknik açıklamalar ikinci katmana taşındı: `Teknik Ayrıntı`.

### 4. Terminoloji
Ana karttaki `satış` ifadesi `kayıt` olarak değiştirildi. Elek kaydı veri kaynağı olduğu için kullanıcıya daha doğru anlam verir.

## Korunanlar
- Elek kayıtları veri kaynağı olmaya devam ediyor.
- MarketRepository değiştirilmedi.
- DatabaseHelper / DB_VERSION / migration değiştirilmedi.
- Firebase, google-services.json, Firestore ve Storage kuralları değiştirilmedi.
- ProducerFeedActivity değiştirilmedi.
- Zeytin Ligi mantığı değiştirilmedi.

## Doğrulama
- Provider mantık testi: PASS
  - Önceki dönem medyanı
  - TL farkı
  - yüzde farkı
  - aykırı fiyat filtresi
  - güven puanı
- Güven seviyesi testi: PASS
  - 9 temiz/güncel kayıt → en fazla Orta Güven
  - 30 temiz/güncel kayıt → Yüksek Güven
- Değiştirilen Java dosyalarında parser seviyesinde yeni syntax hatası görülmedi.
- Tam Android Gradle build denendi; çalışma ortamında Gradle 8.7 dağıtımı bulunmadığı ve dış ağ kapalı olduğu için wrapper `services.gradle.org` adresine erişemedi. Son Android build doğrulaması AndroidIDE üzerinde yapılmalıdır.
