-- Göç: çiftçi bildirim aboneliği (İP9-A-10).
-- Canlı D1'e SEN uygularsın (Cloudflare → D1 → zeytin-sira → Console → yapıştır → Execute).
-- Yeni tablodur; mevcut verilere dokunmaz. Tekrar çalıştırmak güvenlidir (IF NOT EXISTS).
-- Not: schema.sql'in tamamını yeniden çalıştırırsan bu tablo yine oluşur; bu dosya yalnız küçük olsun diyedir.

CREATE TABLE IF NOT EXISTS bildirim_abone (
  yaghane_id INTEGER NOT NULL,
  token TEXT NOT NULL,
  endpoint TEXT NOT NULL,
  p256dh TEXT NOT NULL,
  auth TEXT NOT NULL,
  esik_durumu INTEGER NOT NULL DEFAULT 0,
  olusturma INTEGER NOT NULL,
  PRIMARY KEY (token, endpoint)
);
