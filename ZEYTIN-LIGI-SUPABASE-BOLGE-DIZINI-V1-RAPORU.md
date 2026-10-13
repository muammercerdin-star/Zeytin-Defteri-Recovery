# Zeytin Defteri — Zeytin Ligi Supabase Bölge Dizini V1

## Hedef

Alaşehir cihazı yalnız kendi yerel kartını değil, anonim katkı ağına katılmış Salihli gibi diğer aktif ilçeleri de **Bölgelere Göre** karuselinde görebilsin.

## Yapılan Android değişiklikleri

- `MarketCloudRepository` içine `market_league_regions` RPC okuyucusu eklendi.
- Zeytin Ligi, mevcut yerel kartı koruyup Supabase bölge dizinini bununla birleştirir.
- Aynı bölge iki kez gösterilmez; `region_key` üzerinden birleştirilir.
- Uzak bir bölgede gizlilik eşiği henüz oluşmadıysa kart yine görünür fakat:
  - kg gösterilmez (`— kg`)
  - fiyat gösterilmez (`— TL/kg`)
  - baskın elek gösterilmez
  - kartta `Bölge aktif • eşik bekleniyor` yazar.
- Test eşiği olan 2 farklı üreticiye ulaşıldığında anonim toplam kg, ağırlıklı ortalama fiyat ve baskın elek kartta yayınlanabilir.
- Kullanıcının kendi bölgesindeki yerel önizleme, eşik oluşmadan da yalnız kendi cihazında görünmeye devam eder.
- Firestore Zeytin Ligi Top 20 sistemi kaldırılmadı. Top 20 için mevcut doğrulanmış lig backend'i beklenir; bu iş emri yalnız çok kullanıcılı **bölge keşfini** Supabase'e bağlar.

## Supabase tarafında gereken tek seferlik kurulum

Gerçek cihaz testi için SQL Editor'de:

`supabase/ZEYTIN-LIGI-BOLGE-DIZINI-TEST-2.sql`

çalıştırılmalıdır.

Bu fonksiyon, 1 üreticili bölgenin yalnızca adını ve katılımcı sayısını dizinde gösterir. Tek üreticinin kg/fiyat/baskın elek değerlerini yayınlamaz. 2+ farklı üreticide toplu metrikleri yayınlar.

Production öncesinde:

`supabase/ZEYTIN-LIGI-BOLGE-DIZINI-PROD-5.sql`

çalıştırılmalıdır. Böylece toplu metrik eşiği tekrar 5 farklı üretici olur.

## Beklenen test

- Telefon A: Firebase kullanıcı A, Alaşehir / Manisa, anonim katkı açık.
- Telefon B: Firebase kullanıcı B, Salihli / Manisa, anonim katkı açık.
- Her iki telefonda Zeytin Ligi > Bölgelere Göre ekranı açıldığında hem Alaşehir hem Salihli kartı görünmelidir.
- Her bölgede yalnız 1 üretici varsa uzak bölgenin özel kg/fiyatı görünmemelidir.
- Aynı bölgede 2 farklı Firebase üreticisi oluştuğunda test modunda toplu metrikler görünür.
