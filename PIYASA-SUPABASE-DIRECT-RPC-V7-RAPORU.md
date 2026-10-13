# Zeytin Defteri — Piyasa Supabase Direct RPC V7

## Yapılan değişiklik

- Supabase Project URL uygulamaya bağlandı.
- Yalnız istemciye açık `sb_publishable_...` anahtarı Android tarafına eklendi.
- Secret / service-role anahtarı Android'e eklenmedi.
- Eski V6 `market-api` Edge Function bağımlılığı Android Piyasa akışından çıkarıldı.
- Mevcut Firebase / Google giriş korunarak Firebase ID token doğrudan Supabase Third-Party Auth üzerinden kullanıldı.
- Elek katkıları `market_submit` RPC ile gönderiliyor.
- Silinen/değiştirilen daha önce yüklenmiş Elek satırları `market_withdraw` RPC ile temizleniyor.
- Piyasa üst kartı `market_snapshot`, bölgeler `market_regions`, grafik `market_chart` RPC'lerinden besleniyor.
- Yerel Elek önizlemesi ağ/backend hatasında fallback olarak korunuyor.
- Kullanıcının anonim katkı tercihi yerelde saklanıyor; katkı kapatılırken takip edilen sunucu kayıtları silinmeden tercih kapalı sayılmıyor.
- Ham Firebase UID istemci payload'ına eklenmiyor.
- Bölge anahtarı `il_ilce` biçiminde Türkçe karakterlerden güvenli şekilde normalize ediliyor (örn. `manisa_alasehir`).
- Elek `cloud_id` kaynak kimliği olarak kullanılıyor; aynı kayıt tekrar gönderildiğinde sunucuda UPSERT olur.

## Not

Mevcut Supabase SQL V1 fonksiyonları dönem parametresini en fazla 180 günle sınırlar. Bu nedenle Android'deki "Bu Sezon" seçimi direct-RPC V7'de güvenli olarak son 180 güne eşlenmiştir. 7/30/90 gün seçimleri birebir çalışır.
