# Zeytin Defteri — Piyasa Direct RPC V7 / 2 Üretici Test Modu

- Production gizlilik eşiği 5 olarak tasarlanmıştır.
- Gerçek cihaz testini hızlandırmak için bu pakette Android istemci eşiği geçici olarak 2 yapılmıştır.
- Eşik `MarketBackendConfig.PUBLIC_MARKET_MIN_PRODUCERS` sabitinden yönetilir; UI metinleri ve bölge filtresi aynı sabiti kullanır.
- Canlı Supabase tarafı için `supabase/TEST-ONLY-MIN-PRODUCERS-2.sql` eklendi.
- SQL patch mevcut market fonksiyon tanımlarını önce yedekler; Elek verilerini silmez.
- Test sonrası `supabase/RESTORE-MIN-PRODUCERS-5.sql` ile sunucu fonksiyonları birebir eski tanımlarına döndürülmelidir.
- Production APK öncesi Android eşiği tekrar 5 yapılmalıdır.
