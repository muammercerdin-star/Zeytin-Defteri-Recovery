# Piyasa Otomatik Anonim Katkı V8.1 - Compile Fix

## Düzeltilen hata
AndroidIDE derlemesinde `ZeytinLeagueContainerView.java:216` satırında kalan eski
`marketCloudRepository.getContributionStatus(...)` çağrısı `cannot find symbol`
hatası veriyordu.

V8'de kullanıcıya bağlı katkı aç/kapat API'si kaldırıldığı için bu eski çağrı artık
geçersizdi. Çağrı tamamen kaldırıldı.

## Korunan davranış
- Firebase oturumu ve Supabase Third-Party Auth korunur.
- Anonim piyasa katkısı otomatik çalışmaya devam eder.
- Zeytin Ligi bölge/elek fiyatları korunur.
- TEST2 üretici eşikleri ve mevcut SQL/RPC yapısı değiştirilmedi.
- Kullanıcı veritabanı/migration/Firebase yapılandırmasına dokunulmadı.

## Kontrol
- Projede `getContributionStatus` referansı kalmadı.
- ZIP bütünlük kontrolü yapılmıştır.
- Tam Android derlemesi bu çalışma ortamında Android SDK olmadığı için burada çalıştırılamadı;
  gerçek doğrulama AndroidIDE `assembleDebug` / normal Build ile yapılmalıdır.
