# Zeytin Ligi Bölge Raporu V10 - Trend Cilası

Bu sürüm V9.1 Kıp Kıp Fix üzerine uygulanmıştır. Mevcut Supabase/Firebase bağlantıları, bölge kartları, otomatik anonim katkı ve popup mimarisi korunmuştur.

## Uygulananlar

- Trend farklı elek fiyatlarını birbirine karıştırmaz; baskın elek ve her elek kendi içinde hesaplanır.
- Trend karşılaştırması: son 7 gün medyanı / önceki 7 gün medyanı.
- Bir üreticinin çok kayıt girerek fiyat/trendi domine etmesi engellenir: önce üretici-gün medyanı, sonra üretici-dönem medyanı, sonra bölge medyanı kullanılır.
- TEST sürümünde trend için her iki 7 günlük pencerede en az 2 farklı üretici ve en az 2 farklı gün gerekir. PROD sürümünde üretici eşiği 5'tir.
- Yön sınıfları: >= +%5 Yükseliş; +%1,5..+%5 Hafif yükseliş; -%1,5..+%1,5 Yatay; -%5..-%1,5 Hafif düşüş; <= -%5 Düşüş.
- Elek kartlarında ayrı trend yüzdesi gösterilir.
- Detaylı Elek Listesi her elek için son 7 gün, önceki 7 gün ve yüzde değişimi gösterir.
- Güven artık trend verisinin üretici/gün yayılımına göre hesaplanır.
- Min/medyan/max farklı elekleri karıştırmaz; baskın eleğin son 30 günlük üretici-dengeli özeti kullanılır.
- Grafik yalnız baskın eleğe aittir ve haftalık üretici-dengeli medyan kullanır. Büyük tarih boşluklarında alan dolgusu kesilir; veri olmayan günler varmış gibi gösterilmez.
- Bölge raporunda "Ort. fiyat" etiketi "Genel ort." olarak netleştirildi; grafik başlığında hangi elek trendinin gösterildiği yazılır.

## Değişen dosyalar

- app/src/main/java/com/zeytindefteri/app/MarketCloudRepository.java
- app/src/main/java/com/zeytindefteri/app/ZeytinLeagueRegionReportDialog.java
- app/src/main/java/com/zeytindefteri/app/LeagueRegionReportChartView.java
- supabase/ZEYTIN-LIGI-BOLGE-RAPORU-TEST-2.sql
- supabase/ZEYTIN-LIGI-BOLGE-RAPORU-PROD-5.sql

## Doğrulama

- Değişen Java dosyalarında string/comment farkındalıklı parantez/yapı kontrolü: PASS.
- SQL statik kontrolü ve placeholder kontrolü: PASS.
- ZIP bütünlük kontrolü ayrıca paketleme sonrası uygulanır.
- Tam Android Gradle build bu çalışma ortamında Gradle 8.7 dağıtımı ağdan indirilemediği için çalıştırılamadı; AndroidIDE'de build doğrulaması gerekir.
