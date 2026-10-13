# Zeytin Ligi Bölge Raporu V10.1 - Runtime Fix

Bu sürüm V10 Trend Cilası üzerine uygulanmıştır.

## Kök neden
V10 rapor RPC'sinde PL/pgSQL `RETURNS TABLE` çıktı değişkenleriyle aynı ada sahip iki kolon, CTE içinde niteliksiz (unqualified) kullanılıyordu:

- `sale_count`
- `producer_count`

Fonksiyon SQL Editor'de başarıyla oluşturulabiliyor; fakat ilk çağrıda sorgu planlanırken isim çakışması oluşup Android güvenli fallback'e düşebiliyordu. Bu durumda ekranda bölge/elek fiyatları görünürken `Toplam satış`, min/medyan/max ve trend alanları boş kalıyordu.

## Düzeltme
- `grade_producer gp` aliası ile `gp.sale_count`, `gp.kg_sum`, `gp.amount_sum` açıkça nitelendirildi.
- `safe_weekly sw` aliası ile grafik JSON alanları `sw.producer_count`, `sw.week_start`, `sw.price` açıkça nitelendirildi.
- Android loguna rapor RPC fallback nedeni eklendi.
- Kullanıcı metni `Veri yetersiz` yerine daha doğru olan `Trend oluşmadı` yapıldı.
- V10'un üretici-dengeli trend, elek bazlı trend, anonimlik eşiği ve kıp-kıp/cache düzeltmeleri korunmuştur.

## Beklenen sonuç
TEST-2 SQL yeniden çalıştırıldıktan sonra 2+ üreticili bölgede:

- Toplam satış tekrar görünür.
- Detaylı Elek Listesi gerçek elek satış adetlerini gösterir.
- Baskın eleğin son 30 gün min/medyan/max değerleri veri varsa görünür.
- İki 7 günlük pencerede yeterli veri oluşursa trend yüzdesi ve grafik aktif olur.
