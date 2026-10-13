# Zeytin Ligi Bölge Raporu Popup V1

Bu sürüm, V8.2 UI Stable projesinin devamıdır.

## Yapılanlar

- Zeytin Ligi bölge kartlarına dokununca yeni Activity açmadan, mevcut ekranın üzerinde premium bir alt popup açılır.
- Popup başlığı: `<İlçe / İl> Bölge Raporu`.
- Sezon özeti kartları:
  - Toplam satış
  - Toplam üretici
  - Toplam kg
  - Ortalama fiyat
- Özet göstergeleri:
  - Piyasa yönü
  - Baskın elek
  - Güven seviyesi
- Elek dağılımı:
  - Double
  - 1 No
  - 2 No
  - 3 No
  - 4 No
  - Yağlık
  - Güven eşiği üstündeki eleklerde kg ve yüzde dağılımı gösterilir.
  - Tek üreticili eleklerde kg gizli kalır, mevcut fiyat ön izlemesi gösterilebilir.
- Son 30 gün anonim piyasa trend grafiği eklendi.
- En düşük / medyan / en yüksek fiyat alanları eklendi.
- Otomatik kısa bölge özeti eklendi.
- `Detaylı Liste` düğmesi elek bazında kg, satış sayısı ve fiyat özetini açar.
- Popup alt navigasyonun üstünde açılır; Zeytin Ligi ekranı arka planda görünür kalır.

## Backend

Yeni RPC: `market_league_region_report`

TEST2 için:
`supabase/ZEYTIN-LIGI-BOLGE-RAPORU-TEST-2.sql`

Production (5 üretici) için:
`supabase/ZEYTIN-LIGI-BOLGE-RAPORU-PROD-5.sql`

RPC kurulmamışsa Android tarafı mevcut güvenli bölge RPC'lerinden sınırlı bir fallback özet üretir; uygulama çökmez.

## Gizlilik

- Üretici adı, Firebase UID, bahçe adı ve ham satış satırları popup'a dönmez.
- Bölge toplam satış/kg bilgisi TEST modunda en az 2 farklı üreticiyle açılır.
- Elek kg ve elek satış sayısı, ilgili elekte en az 2 farklı üretici olduğunda açılır.
- Tek üreticili elek için yalnız anonim fiyat ön izlemesi devam edebilir.
