#!/usr/bin/env python3
"""Toplam (sınıfsız) elek kaydı: arşiv/lig/piyasa sorguları (gerçek SQLite) ve ekran bağlantıları.

Arşiv sorgusu ElekRepository.archiveQuery'deki Java dize parçalarından aynı sırayla kurulur
(kaynakta bu parçaların bulunduğu ayrıca kontrol edilir). Android SQLite sürümü farklı olabilir.
"""
import os, re, sqlite3, sys
R = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
J = os.path.join(R, "app/src/main/java")
rd = lambda f: open(os.path.join(J, f), encoding="utf-8").read()
repo, market, league = rd("ElekRepository.java"), rd("MarketRepository.java"), rd("com/zeytindefteri/app/ZeytinLeagueRepository.java")
cloud, act, receipt = rd("com/zeytindefteri/app/MarketCloudRepository.java"), rd("com/zeytindefteri/app/ElekActivity.java"), rd("com/zeytindefteri/app/ElekReceiptActivity.java")
archive, feed, bridge, helper = rd("ElekArchiveActivity.java"), rd("com/zeytindefteri/app/ProducerFeedActivity.java"), rd("com/zeytindefteri/app/ElekSaleBridge.java"), rd("DatabaseHelper.java")
fails = []
def check(name, ok):
    print(("PASS " if ok else "FAIL ") + name)
    if not ok: fails.append(name)

# --- Kaynak: arşiv sorgusu karışık sütunları
check("arşiv: mixed_kg ve mixed_revenue sütunları", "AS mixed_kg" in repo and "AS mixed_revenue" in repo and "ElekMixedEntry.GRADE" in repo)
check("arşiv: toplam kilo/tutar tüm satırlardan (karışık dahil)", "SUM(eg.kg),0) AS total_kg" in repo and "SUM(eg.line_total),0) AS total_revenue" in repo)

# --- Gerçek SQLite: aynı sorgu
grades = ["DOUBLE", "1", "2", "3", "4", "YAGLIK"]
cols = ["double_kg", "grade1_kg", "grade2_kg", "grade3_kg", "grade4_kg", "oil_kg"]
sql = "SELECT COALESCE(SUM(eg.kg),0) AS total_kg, COALESCE(SUM(eg.line_total),0) AS total_revenue, COUNT(DISTINCT er.id) AS record_count"
for g, c in zip(grades, cols):
    sql += ", COALESCE(SUM(CASE WHEN eg.grade='%s' THEN eg.kg ELSE 0 END),0) AS %s" % (g, c)
sql += ", COALESCE(SUM(CASE WHEN eg.grade='KARISIK' THEN eg.kg ELSE 0 END),0) AS mixed_kg"
sql += ", COALESCE(SUM(CASE WHEN eg.grade='KARISIK' THEN eg.line_total ELSE 0 END),0) AS mixed_revenue"
sql += ", er.id AS record_id FROM elek_records er INNER JOIN gardens g ON g.id=er.garden_id LEFT JOIN elek_grades eg ON eg.elek_id=er.id WHERE COALESCE(g.owner_uid,'')=? GROUP BY er.id ORDER BY er.id"
db = sqlite3.connect(":memory:")
db.executescript("""
CREATE TABLE gardens(id INTEGER PRIMARY KEY, owner_uid TEXT, district TEXT, province TEXT);
CREATE TABLE elek_records(id INTEGER PRIMARY KEY, garden_id INTEGER, record_date TEXT, total_kg REAL, estimated_total REAL, owner_uid TEXT);
CREATE TABLE elek_grades(id INTEGER PRIMARY KEY, elek_id INTEGER, grade TEXT, kg REAL, price_per_kg REAL, line_total REAL);
INSERT INTO gardens VALUES (1,'u','Alaşehir','Manisa');
INSERT INTO elek_records VALUES (1,1,'2026-10-15',150,8000,'u');
INSERT INTO elek_grades VALUES (1,1,'KARISIK',150,8000.0/150,150*(8000.0/150));
INSERT INTO elek_records VALUES (2,1,'2026-11-02',300,30000,'u');
INSERT INTO elek_grades VALUES (2,2,'DOUBLE',100,120,12000),(3,2,'1',100,100,10000),(4,2,'2',100,80,8000);
""")
rows = db.execute(sql, ("u",)).fetchall()
names = [d[0] for d in db.execute(sql, ("u",)).description]
row = lambda r: dict(zip(names, r))
mixed, graded = row(rows[0]), row(rows[1])
check("karışık kayıt toplam kiloya girer", abs(mixed["total_kg"] - 150) < 1e-9 and abs(mixed["total_revenue"] - 8000) < 0.005)
check("karışık kayıt sınıf sütunlarına girmez", sum(mixed[c] for c in cols) == 0 and abs(mixed["mixed_kg"] - 150) < 1e-9)
check("sınıflı kayıtta mixed_kg sıfır", graded["mixed_kg"] == 0 and abs(sum(graded[c] for c in cols) - 300) < 1e-9)
# Lig: karışık çıkarılınca sınıf toplamı == kilo (ZeytinLeagueScore geçerlilik koşulu)
lk = sum(r_["total_kg"] - r_["mixed_kg"] for r_ in map(row, rows))
lg = sum(sum(r_[c] for c in cols) for r_ in map(row, rows))
check("lig: karışık çıkarılınca sınıf toplamı kiloya eşit", abs(lk - lg) < 1e-6)
check("lig: yerel önizleme karışık kilo ve tutarı çıkarır", 'value(c, "total_kg") - value(c, "mixed_kg")' in league
      and 'value(c, "total_revenue") - value(c, "mixed_revenue")' in league and "if (kg <= 0.000001) continue;" in league)

# Piyasa (yerel Piyasa ekranı) sınıf tablosuna karışık girmez
m = re.search(r'private String validLineWhere\(\) \{(.*?)\n    \}', market, re.S)
check("piyasa: KARISIK satırı fiyat tablolarından dışlanır", m is not None and "eg.grade <> '\" + ElekMixedEntry.GRADE + \"'" in m.group(1))
where = "AND eg.kg > 0 AND eg.price_per_kg > 0 AND eg.grade <> 'KARISIK' "
cnt = db.execute("SELECT COUNT(*) FROM elek_grades eg INNER JOIN elek_records er ON er.id=eg.elek_id WHERE 1=1 " + where).fetchone()[0]
check("piyasa sorgusu gerçek SQLite'ta yalnız 3 sınıflı satırı görür", cnt == 3)

# Bulut piyasa katkısı: tanınmayan sınıf "" olur ve satır gönderilmez (sunucu da yalnız 6 sınıfı kabul eder)
tag = cloud[cloud.index("private String toApiGrade(String raw)"):]
tag = tag[:tag.index("\n    }\n")]
check("bulut katkısı: toApiGrade bilinmeyeni boş döndürür", tag.rstrip().endswith('return "";') and "KARISIK" not in tag)
check("bulut katkısı: boş sınıf gönderilmez", "&& !row.apiGrade.isEmpty()" in cloud)
schema = open(os.path.join(R, "supabase/market-schema.sql"), encoding="utf-8").read()
check("sunucu şeması KARISIK'i kabul etmez (yanlışlıkla gönderilse bile)", "grade in ('DOUBLE','1','2','3','4','YAGLIK')" in schema)

# Satış: köprü elek satırlarını olduğu gibi taşır (karışık dahil)
check("satış köprüsü sınıf süzmez", 'SELECT grade, kg, price_per_kg FROM elek_grades WHERE elek_id=? AND kg>0 ORDER BY id' in bridge)
check("addElekRecord sınıf adını süzmez", "if (grade.isEmpty()\n                        ||\n                        kg <= 0)" in re.sub(r"\n\s*\n", "\n", helper))

# Ekran bağlantıları
check("elek: toplam modu kurulur ve durum korunur", "setupMixedMode();" in act and "restoreMixedMode(savedInstanceState);" in act
      and 'state.putBoolean("elek_mixed_mode", mixedMode);' in act)
save = act[act.index("private void saveElekRecord()"):act.index("private void syncSaleCloud(")]
check("elek kayıt: toplam modda tek KARISIK satırı", "grades = ElekMixedEntry.grades();" in save and "ElekMixedEntry.error(mixed[0], mixed[1])" in save)
check("elek kayıt: toplam modda sınıf kilosu şartı aranmaz", "if (totalKg <= 0 && !mixedMode)" in save)
check("elek kayıt: satış tutarı toplamdan", save.index("grades = ElekMixedEntry.grades();") < save.index("gross += Math.max(0, kilograms[i]) * Math.max(0, prices[i]);"))
check("ses/QR/defter sınıf doldurunca sınıf moduna döner", act.count("setMixedMode(false);") >= 4)
check("tutar Türkçe okunur (8.000 = sekiz bin)", "ElekMixedEntry.parse(etMixedTotal.getText().toString())" in act)
check("fiş: karışık satır ve sınıfsız not", "ElekMixedEntry.isMixed(grade)" in receipt and 'ElekMixedEntry.LABEL + " (sınıfsız)"' in receipt)
check("geçmiş: karışık kart etiketi", 'cursor.getColumnIndexOrThrow("mixed_kg")' in archive and "ElekMixedEntry.LABEL" in archive)
check("meydan elek sekmesi: karışık etiketi", 'r.mixedKg = getDouble(rc, "mixed_kg");' in feed and "ElekMixedEntry.LABEL" in feed)
sys.exit(1 if fails else 0)
