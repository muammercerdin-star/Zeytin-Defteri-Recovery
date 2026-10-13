# Zeytin Defteri – Piyasa V5 / Çok Kullanıcılı Anonim Bölgesel Piyasa

Tarih: 11.09.2026

## Amaç

V4'te çalışan yerel Elek → Piyasa motoru korunarak Piyasa ekranına çok kullanıcılı,
izinli ve anonimleştirilmiş bölgesel veri katmanı eklendi. Uygulama bulut sonucu hazır
değilse yerel Elek önizlemesine güvenli biçimde geri döner.

## Kullanıcı akışı

1. Piyasa sekmesi her zaman önce cihazdaki mevcut Elek kayıtlarını gösterir.
2. Firebase oturumu varsa istemci aynı elek/bölge/dönem için yayımlanmış anonim
   bölgesel özeti ister.
3. En az 5 farklı üreticiden oluşan geçerli bölgesel özet varsa ekran onu kullanır.
4. Kullanıcının **kendi son Elek fiyatı** cihazdan gelir; başka üreticilerin tekil fiyat
   satırları telefona hiçbir zaman indirilmez.
5. İlk Piyasa açılışında kullanıcıya anonim katkı açıkça sorulur. Varsayılan kapalıdır.
6. Katkı açılırsa mevcut Elek kayıtları da tekrar buluta dokundurularak sunucu
   anonimleştirme kuyruğuna alınır. Katkı kapatılabilir.

## Gizlilik sınırı

- Ham Elek yedeği: `users/{uid}/gardens/{gardenId}/elek_records/{recordId}`
- İstemci başka kullanıcıların `users/{uid}/...` belgelerini okuyamaz.
- Sunucu içi normalize edilmiş kaynak: `market_private_sources` (istemciye kapalı)
- Açık bölgesel çıktı:
  - `zeytin_markets/{season}/regions/{regionId}`
  - `zeytin_market_stats/{statId}`
- Açık çıktıda UID, üretici adı, bahçe adı veya tekil ham kayıt bulunmaz.
- Bölgesel istatistik için minimum **5 farklı üretici** gerekir.
- Günlük grafik noktasının yayımlanması için de o gün minimum **5 farklı üretici** gerekir.
- Bölgesel tipik fiyat aralığı tam min/max yerine 25.–75. yüzdelik olarak yayımlanır.
- Fiyatlar 0,50 TL hassasiyetinde, toplam kg 10 kg hassasiyetinde toplulaştırılır.

> Not: Bu tasarım küçük grupları gizleyen uygulama-seviyesi k-anonimlik/toplulaştırma
> yaklaşımıdır; matematiksel differential privacy iddiasında bulunmaz.

## Piyasa hesaplama politikası

- Elekler: `DOUBLE`, `1`, `2`, `3`, `4`, `YAGLIK`
- Dönemler: Son 7 / 30 / 90 gün ve sezon.
- Bir kullanıcı yüzlerce kayıt girse bile bölgesel fiyatı tek başına sürükleyemez:
  önce her üreticinin kendi medyanı alınır, sonra üretici medyanlarından bölgesel
  medyan hesaplanır (**bir üretici = bir fiyat oyu**).
- Yeterli üretici olduğunda IQR aykırı değer filtresi üretici medyanlarına uygulanır.
- Hacimde tek hesabın aşırı şişirme etkisini azaltmak için dönem başına üretici katkısı
  250.000 kg ile sınırlandırılır.
- Önceki dönem karşılaştırması yalnız önceki dönem de anonimlik eşiğini sağlıyorsa
  yayımlanır.
- Güven skoru; farklı üretici sayısı, güncellik, fiyat dağılımı, kayıt miktarı ve aykırı
  oranından üretilir.

## Sunucu akışı

- `zeytinMarketElekWritten`: izin açıkken özel Elek kaydını normalize eder.
- `zeytinMarketConsentWritten`: katkı açma/kapatma için backfill/purge kuyruğu oluşturur.
- `zeytinMarketGardenWritten`: bahçe ilçe/il değişirse kayıtları yeniden bölgelendirir.
- `zeytinMarketBackfill`: eski Elek kayıtlarını parçalı olarak işler; katkı kapanmışsa
  özel market kaynaklarını temizler.
- `zeytinMarketPublish`: kirli bölge/sezon kohortlarını anonim toplu çıktıya dönüştürür.
- `zeytinMarketRefreshWindows`: kayan 7/30/90 günlük pencereleri her gece tazeler.

## Android tarafı

Yeni:
- `MarketCloudRepository.java`

Güncellenen ana sınıflar:
- `MarketUiDataProvider.java`
- `MarketView.java`
- `ZeytinLeagueContainerView.java`
- `ProducerFeedActivity.java`
- `ProductionCloudRepository.java`

Piyasa sekmesi yerel sonucu anında gösterir, ardından varsa anonim bölgesel sonucu
asenkron olarak bind eder. UI'da kaynak metni `Yerel Elek kayıtlarından piyasa önizlemesi`
veya `Anonim bölgesel ağ • X üretici` olarak değişir.

## Firestore güvenliği ve indeksler

- `firestore.rules`: anonim market çıktısı oturum açmış kullanıcılar için salt okunur.
- `market_private_sources`, `market_dirty_regions`, `market_backfills` ve
  `market_region_catalog` istemci kuralları tarafından okunamaz/yazılamaz.
- `firestore.indexes.json`: anonim market sorguları ve Elek collection-group backfill
  için gerekli indeks tanımları eklendi.
- `firebase.json`: index dosyası deploy kapsamına alındı.

## Test / doğrulama

- `npm run check`: geçti.
- `npm test`: **42 test**, 37 geçti, 5 emulator-bağımlı test ortam olmadığı için skip,
  başarısız test yok.
- Yeni Piyasa politika testleri: Türkçe bölge normalizasyonu, minimum 5 farklı üretici,
  aykırı fiyat filtresi, günlük grafik gizlilik eşiği, önceki dönem gizlilik eşiği ve
  tek üreticinin çok kayıtla medyanı sürükleyememesi doğrulandı.
- Android tarafındaki değiştirilmiş Java dosyalarında `javac` parser kontrolünde
  sözdizimi hatası bulunmadı; Android/Firebase sınıfları bu çıplak classpath'te doğal
  olarak çözümlenemiyor.
- Tam Android Gradle build doğrulanamadı: wrapper `gradle-8.7-bin.zip` indirmek istedi,
  çalışma ortamında dış ağ/DNS kapalıydı. Son derleme AndroidIDE'de yapılmalıdır.
- Firestore Security Rules emulator testi dosyası eklendi; emulator çalışmadığı için
  bu ortamda skip edildi.

## Üretime alma

Bu ZIP **kod ve altyapı tanımlarını içerir fakat Firebase production deploy yapılmadı**.
Önce AndroidIDE build alın. Ardından doğru Firebase projesinin seçili olduğunu kontrol
ettikten sonra proje kökünde:

```bash
cd functions
npm install
cd ..
firebase deploy --only firestore:rules,firestore:indexes,functions
```

Deploy sonrası ilk yayınların oluşması için katkı açılmış üreticilerin özel Elek
kayıtlarının buluta senkronize olması ve aynı bölge + sezon + elek + dönem kohortunda
en az 5 farklı üretici bulunması gerekir. Backfill ve publisher 5 dakikalık işlerdir;
yayın anlık olmak zorunda değildir.

## Beklenen smoke test

1. Android build → Piyasa açılır, önce yerel veri görünür.
2. `Anonim Katkı` açılır; mevcut Elek kayıtları yeniden sıraya alınır.
3. Aynı bölge/elek/dönemde 5 üreticiden az varsa yerel önizleme devam eder (doğru).
4. 5+ izinli üretici olduğunda kaynak etiketi `Anonim bölgesel ağ • X üretici` olur.
5. Başka kullanıcının ham Elek kayıtları istemci tarafından okunamaz.
6. Aşırı fiyat ve tek hesabın çok kayıt girmesi bölgesel medyanı tek başına bozamaz.
