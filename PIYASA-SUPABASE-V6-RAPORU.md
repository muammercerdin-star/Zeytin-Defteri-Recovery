# PİYASA SUPABASE V6 RAPORU

## Amaç
Firebase Cloud Functions için Blaze zorunluluğuna takılmadan çok kullanıcılı,
anonim, bölgesel Piyasa'yı çalıştırmak.

## Değişenler
- `MarketCloudRepository.java` Firestore aggregate okuyucusundan Supabase Edge Function istemcisine dönüştürüldü.
- Mevcut Firebase Auth korunuyor; Firebase ID token server-side doğrulanıyor.
- `MarketBackendConfig.java` eklendi.
- Supabase private schema + RLS eklendi.
- `market-api` Edge Function eklendi.
- Eski Piyasa hesap mantığı Supabase policy katmanına taşındı:
  - minimum 5 üretici,
  - üretici başına tek piyasa oyu,
  - medyan,
  - IQR,
  - 25–75 yüzdelik tipik aralık,
  - günlük grafik için minimum 5 üretici,
  - 250 ton üretici hacim tavanı,
  - önceki dönem karşılaştırması,
  - güven puanı.
- Katkı kapatılınca server-side Piyasa katkısı silinir.
- UI ve yerel Elek fallback korunur.

## Korunanlar
- Firebase projesi ve `google-services.json`
- Google/Firebase giriş
- Firestore/Storage ve diğer çalışan özellikler
- SQLite ve mevcut kullanıcı verileri
- Elek ekranı / Elek kayıtları
- Zeytin Ligi
- Alt navigasyon ve Piyasa UI

## Test
`node --test supabase/tests/market-policy.test.mjs`

Sonuç: 5/5 PASS.

Android tam Gradle build bu çalışma ortamında Gradle dağıtımına ağ erişimi olmadığı
icin burada doğrulanamadı. Kaynak üzerinde entegrasyon ve politika testleri yapıldı.

## Deploy durumu
Kod deploy-ready'dir ancak Supabase proje URL/publishable key ve Supabase hesabında
SQL + Edge Function deploy'u kullanıcı projesinde bir kez yapılmalıdır.
Eski `DEPLOY-PIYASA-V5-1.sh` Firebase Piyasa yolu içindir; Supabase V6 için çalıştırma.
