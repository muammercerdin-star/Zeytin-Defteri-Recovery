-- Yağhane sırası (İP9-A) — Cloudflare D1 şeması.
-- En az veri: sunucuda okunabilir kişisel veri yok. Görünen ad yalnız fabrika anahtarıyla şifreli (ad_sifreli)
-- durur ve 24 saat sonra silinir. Telefon numarası sunucuya hiç gelmez; elle giriş için yalnız son 2 hanenin
-- tuzlu HMAC özeti tutulur (o da 24 saat sonra silinir). Satırlar 90 gün sonra silinir, isimsiz gün toplamı kalır.
-- Bu dosya canlı veritabanına KULLANICI tarafından uygulanır (KURULUM.md).

CREATE TABLE IF NOT EXISTS yaghane (
  id INTEGER PRIMARY KEY,
  ad TEXT NOT NULL,
  kisa_ad TEXT NOT NULL UNIQUE CHECK (kisa_ad GLOB '[a-z0-9]*' AND length(kisa_ad) BETWEEN 2 AND 32),
  kapasite_kg_saat INTEGER NOT NULL DEFAULT 1200 CHECK (kapasite_kg_saat BETWEEN 100 AND 20000),
  gun_baslangic TEXT NOT NULL DEFAULT '05:00',
  -- Fabrika anahtarının parmak izi (anahtarın kendisi değil): cihaz doğru anahtarı taşıyor mu diye bakılır.
  anahtar_izi TEXT,
  olusturma INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS kullanici (
  id INTEGER PRIMARY KEY,
  yaghane_id INTEGER NOT NULL REFERENCES yaghane(id),
  kullanici_adi TEXT NOT NULL UNIQUE,
  sifre_ozeti TEXT NOT NULL,
  rol TEXT NOT NULL CHECK (rol IN ('ofis', 'havuzcu'))
);

CREATE TABLE IF NOT EXISTS sira (
  id INTEGER PRIMARY KEY,
  yaghane_id INTEGER NOT NULL REFERENCES yaghane(id),
  gun TEXT NOT NULL,
  -- Çiftçinin fiş numarası: sabit kalır. Sırayı değiştirmek yalnız konumu değiştirir.
  sira_no INTEGER NOT NULL,
  konum REAL NOT NULL,
  kod TEXT NOT NULL CHECK (length(kod) = 4),
  token TEXT NOT NULL UNIQUE CHECK (length(token) = 22),
  ad_sifreli TEXT CHECK (ad_sifreli IS NULL OR length(ad_sifreli) <= 160),
  bul_tuz TEXT,
  bul_ozet TEXT,
  kasa INTEGER NOT NULL CHECK (kasa BETWEEN 0 AND 500),
  kg REAL NOT NULL CHECK (kg > 0 AND kg <= 100000),
  durum TEXT NOT NULL CHECK (durum IN ('sirada', 'isleniyor', 'bitti', 'iptal')),
  gelis INTEGER NOT NULL,
  baslama INTEGER,
  bitis INTEGER,
  yag_kg REAL CHECK (yag_kg IS NULL OR yag_kg >= 0),
  UNIQUE (yaghane_id, gun, kod),
  UNIQUE (yaghane_id, gun, sira_no)
);
CREATE INDEX IF NOT EXISTS sira_gun ON sira (yaghane_id, gun);
CREATE INDEX IF NOT EXISTS sira_durum ON sira (yaghane_id, durum);
CREATE INDEX IF NOT EXISTS sira_gelis ON sira (gelis);

-- Çevrimdışı yeni müşteri için önceden ayrılan kod + token (havuzcu cihazı başına, o gün için).
CREATE TABLE IF NOT EXISTS kod_yedegi (
  yaghane_id INTEGER NOT NULL,
  gun TEXT NOT NULL,
  kod TEXT NOT NULL,
  token TEXT NOT NULL UNIQUE,
  ayrilma INTEGER NOT NULL,
  PRIMARY KEY (yaghane_id, gun, kod)
);

-- Sezon raporu için isimsiz gün toplamları (satırlar 90 gün sonra silinince kalan tek şey).
CREATE TABLE IF NOT EXISTS gun_ozet (
  yaghane_id INTEGER NOT NULL,
  gun TEXT NOT NULL,
  toplam_kg REAL NOT NULL,
  toplam_yag_kg REAL NOT NULL,
  islem_sayisi INTEGER NOT NULL,
  PRIMARY KEY (yaghane_id, gun)
);

-- Oturum: tarayıcıdaki rastgele değerin yalnız SHA-256 özeti.
CREATE TABLE IF NOT EXISTS oturum (
  ozet TEXT PRIMARY KEY,
  kullanici_id INTEGER NOT NULL REFERENCES kullanici(id),
  bitis INTEGER NOT NULL
);

-- Deneme sınırı: anahtar "k:<kullanıcı adı>", "i:<IP özeti>" ya da "b:<IP özeti>"; IP'nin kendisi yok. 1 gün durur.
CREATE TABLE IF NOT EXISTS giris_deneme (
  anahtar TEXT NOT NULL,
  zaman INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS giris_deneme_anahtar ON giris_deneme (anahtar, zaman);

-- Çevrimdışı kuyruktan gelen işlem iki kez uygulanmasın: işlem kimliği ve sonucu 3 gün durur.
CREATE TABLE IF NOT EXISTS islem_kaydi (
  yaghane_id INTEGER NOT NULL,
  op_id TEXT NOT NULL,
  durum INTEGER NOT NULL,
  sonuc TEXT NOT NULL,
  zaman INTEGER NOT NULL,
  PRIMARY KEY (yaghane_id, op_id)
);

-- Bulut kotası için günlük API istek sayısı (UTC günü; kota UTC gece yarısı sıfırlanır).
CREATE TABLE IF NOT EXISTS sayac (
  gun TEXT PRIMARY KEY,
  n INTEGER NOT NULL
);
