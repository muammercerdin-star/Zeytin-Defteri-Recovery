# Piyasa Gerçek Veri V1.1 Fix

Bu paket, Piyasa Gerçek Veri V1 üzerine noktasal düzeltmedir.

## Düzeltilenler
- `Double` UI adı artık `sale_grades.grade = DOUBLE` ile eşleşir.
- `Yağlık` UI adı artık `sale_grades.grade = YAGLIK` ile eşleşir.
- `1/2/3/4 No` dönüşümleri korunur.
- Kullanıcı bölgesi ayarı boşsa Piyasa filtresi aktif bahçenin ilçe/il bilgisine düşer.
- Veri yokken büyük, boş fiyat grafiği yerine kompakt açıklama gösterilir.
- Tek gerçek satış varsa grafiğin neden çizilmediği açıklanır.
- Bölgesel kayıt yokken Bölgeler kartı boş kalmaz; açıklayıcı mesaj gösterir.
- `Veri Yok` rozeti artık onay işaretiyle gösterilmez.
- Bölge seçiminde ve hesaplama bilgisinde yalnızca gerçek satış + piyasa paylaşım izninin Piyasa havuzuna girdiği açıkça belirtilir.
- Elek ekranındaki miktar/fiyat kayıtları Piyasa'ya otomatik satış olarak katılmaz.

## Korunanlar
- SQLite şeması ve DB_VERSION değiştirilmedi.
- Firebase/Firestore projesine ve google-services.json'a dokunulmadı.
- Mevcut Zeytin Ligi, alt navigasyon ve kullanıcı verileri korunur.
