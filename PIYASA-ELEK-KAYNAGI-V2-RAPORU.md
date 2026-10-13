# Zeytin Defteri – Piyasa Elek Kaynağı V2

## Amaç
Piyasa ekranının ayrı `sales/sale_grades` kayıtları yerine uygulamanın fiili satış/fiyat ekranı olan Elek kayıtlarından beslenmesi.

## Yapılan düzeltme
- `MarketRepository` veri kaynağı `elek_records + elek_grades + gardens` olacak şekilde değiştirildi.
- Elek satırındaki `price_per_kg > 0` ve `kg > 0` kayıtları piyasa verisi kabul edilir.
- Bölge, kaydın bağlı olduğu bahçenin `district`; boşsa `province` alanından alınır.
- Tüm MarketRepository sorguları aktif `owner_uid` + aktif sezon ile sınırlandırıldı.
- `Double/1/2/3/4/Yağlık` kodları mevcut kanonik `DOUBLE/1/2/3/4/YAGLIK` değerleriyle çalışmaya devam eder.
- Piyasa ekranı, ana sayfa piyasa özeti ve Meydan piyasa bandı aynı MarketRepository üzerinden Elek verisini görür.
- `ZeytinLeagueContainerView` açıklama ve boş-bölge mesajları Elek veri kaynağına göre güncellendi.
- `MarketUiDataProvider` açıklaması Elek kayıtlarını gerçek kaynak olarak yansıtacak şekilde güncellendi.

## Korunanlar
- SQLite şeması ve DB_VERSION değiştirilmedi.
- Migration eklenmedi.
- Mevcut Elek, Zeytin Ligi ve bulut senkronizasyon kayıtları silinmedi/değiştirilmedi.
- Firebase projesi ve google-services.json değiştirilmedi.
- Piyasa UI tasarımı değiştirilmedi.

## Beklenen test
Aktif sezon içinde Elek ekranından 2 No için örn. 522 kg ve 59,92 TL/kg kaydı varsa Piyasa > 2 No > uygun dönem seçildiğinde bu kayıt medyan/min-max/toplam kg/satış sayısı hesabına girmelidir.
