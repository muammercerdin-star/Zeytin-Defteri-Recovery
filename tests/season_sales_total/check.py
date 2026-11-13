"""Tek satış toplamı: Elek kartı = Tanecik = asistan. Gerçek SQLite + kaynak bağlantıları.
Ekrandaki tutarsızlık: Elek 1.899 kg / 85.415 TL, Tanecik 1.717 kg / 76.070 TL (alıcısız elek kayıtları sayılmıyordu)."""
import re, sqlite3
from pathlib import Path
A = Path('app/src/main/java/com/zeytindefteri/app')
ss = (A / 'SeasonSales.java').read_text(encoding='utf-8')
pf = (A / 'ProducerFeedActivity.java').read_text(encoding='utf-8')
ad = (A / 'ZeytinAssistantData.java').read_text(encoding='utf-8')
er = Path('app/src/main/java/ElekRepository.java').read_text(encoding='utf-8')
checks = {}
checks['elek kısmı Elek kartının sorgusu (getArchiveSummary)'] = 'elek.getArchiveSummary(season, own)' in ss and 'getArchiveSummary(int year, String ownerUid)' in er
checks['elekten gelen satış iki kez sayılmaz'] = "COALESCE(s.source_elek_id,0)=0 AND COALESCE(s.source_elek_cloud_id,'')=''" in ss
checks['Tanecik ortak toplamı kullanır'] = 'SeasonSales.season(new ElekRepository(db), rdb, owner, season)' in pf and 'f.seasonKg += b.kg' not in pf
checks['Tanecik alacağı Alacaklarım ile aynı'] = 'SalePayments.outstanding(rdb' in pf and 'f.receivableTotal = open.total;' in pf
checks['asistan aynı kural (elek + elle girilen)'] = 'SeasonSales.fromElek(id(c,"source_elek_id"),str(c,"source_elek_cloud_id"))' in ad and 't.elekTl+=num(grades,"line_total")' in ad
checks['asistan satış = brüt toplam'] = 'value(t.sellRows(),t.sellTl(),' in ad and 'value(t.sellRows(),t.sellKg(),"kg satış")' in ad

db = sqlite3.connect(':memory:')
db.executescript("""
CREATE TABLE gardens(id INTEGER PRIMARY KEY, name TEXT, owner_uid TEXT);
CREATE TABLE elek_records(id INTEGER PRIMARY KEY, garden_id INTEGER, record_date TEXT, season_year INTEGER, owner_uid TEXT, cloud_id TEXT);
CREATE TABLE elek_grades(id INTEGER PRIMARY KEY, elek_id INTEGER, grade TEXT, kg REAL, line_total REAL);
CREATE TABLE sales(id INTEGER PRIMARY KEY, garden_id INTEGER, buyer_id INTEGER, total_kg REAL, gross_total REAL, season_year INTEGER,
  sale_kind TEXT, owner_uid TEXT, source_elek_id INTEGER DEFAULT 0, source_elek_cloud_id TEXT DEFAULT '');
INSERT INTO gardens VALUES(1,'A','u');
-- 1) alıcıya satılmış elek (satışa dönüşmüş): 1717 kg / 76070
INSERT INTO elek_records VALUES(1,1,'2026-11-01',2026,'u','c1');
INSERT INTO elek_grades VALUES(1,1,'2',1717,76070);
INSERT INTO sales VALUES(1,1,5,1717,76070,2026,'SALE','u',1,'c1');
-- 2) alıcı seçilmemiş elek: 182 kg / 9345 (Tanecik bunu sayMIyordu)
INSERT INTO elek_records VALUES(2,1,'2026-11-05',2026,'u','c2');
INSERT INTO elek_grades VALUES(2,2,'3',182,9345);
""")
elek = db.execute("SELECT SUM(eg.kg), SUM(eg.line_total) FROM elek_records er JOIN gardens g ON g.id=er.garden_id LEFT JOIN elek_grades eg ON eg.elek_id=er.id WHERE g.owner_uid='u' AND er.season_year=2026").fetchone()
sql = re.search(r'rdb\.rawQuery\("(SELECT COALESCE\(SUM\(s\.total_kg\).*?)",\s*new String', ss, re.S).group(1)
sql = re.sub(r'"\s*\+\s*"', '', sql)
manual = db.execute(sql, ('u', '2026')).fetchone()
checks['elek kartı 1.899 kg / 85.415 TL'] = elek == (1899.0, 85415.0)
checks['elle girilen satış yok, çift sayım yok'] = manual == (0.0, 0.0)
db.execute("INSERT INTO sales VALUES(2,1,5,100,4500,2026,'SALE','u',0,'')")   # elekten gelmeyen satış
manual = db.execute(sql, ('u', '2026')).fetchone()
checks['elle girilen satış eklenir'] = manual == (100.0, 4500.0)
checks['ortak toplam = 1.999 kg / 89.915 TL'] = (elek[0] + manual[0], elek[1] + manual[1]) == (1999.0, 89915.0)
for k, v in checks.items(): print(('OK  ' if v else 'FAIL') + ' ' + k)
raise SystemExit(0 if all(checks.values()) else 1)
