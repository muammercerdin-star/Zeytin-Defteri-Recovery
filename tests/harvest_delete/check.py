#!/usr/bin/env python3
"""HarvestRepository: bu sezonun hasat listesi ve hasadı "Hasat" masraf hareketiyle birlikte silme SQL'leri
(kaynaktaki gerçek sorgular) bellek içi SQLite'ta çalıştırılır. Android yok."""
from pathlib import Path
import re, sqlite3
root = Path(__file__).resolve().parents[2]
src = (root / 'app/src/main/java/com/zeytindefteri/app/HarvestRepository.java').read_text(encoding='utf-8')
def body(sig):
    a = src.index(sig); return src[a:src.index('\n    }\n', a)]
def sql_after(text, marker):
    a = text.index(marker); seg = text[a:]
    seg = seg[:seg.index('new String[]')]
    return ''.join(eval(x) for x in re.findall(r'"(?:[^"\\]|\\.)*"', seg))
season_sql = sql_after(body('public Cursor getSeasonHarvests('), 'rawQuery(')
d = body('public int deleteHarvestWithOperation(')
find_h = sql_after(d, 'db.rawQuery("SELECT h.garden_id')
find_op = sql_after(d, 'db.rawQuery("SELECT id FROM operations')
db = sqlite3.connect(':memory:')
db.executescript('''
CREATE TABLE gardens(id INTEGER, owner_uid TEXT, name TEXT);
CREATE TABLE harvests(id INTEGER, garden_id INTEGER, season_year INTEGER, harvest_date TEXT, total_kg REAL,
  labor_cost REAL, transport_cost REAL, other_cost REAL, note TEXT, created_at INTEGER);
CREATE TABLE operations(id INTEGER, garden_id INTEGER, type TEXT, operation_date TEXT, quantity REAL, created_at INTEGER);
INSERT INTO gardens VALUES (1,'u1','Girelli'),(2,'u2','Başkası');
INSERT INTO harvests VALUES (10,1,2026,'20.10.2026',190,500,100,0,'ilk',1000),(11,1,0,'2025-11-01',50,0,0,0,'',10),
  (12,1,2026,'27.10.2026',265,0,0,0,'',3000),(20,2,2026,'20.10.2026',190,0,0,0,'',1000);
INSERT INTO operations VALUES (100,1,'Hasat','20.10.2026',190,1001),(101,1,'Sulama','20.10.2026',190,1001),
  (102,1,'Hasat','20.10.2026',190,999999),(103,1,'Hasat','27.10.2026',265,3001),(200,2,'Hasat','20.10.2026',190,1001);
''')
n = 0
def ok(c, m):
    global n
    assert c, m; n += 1
rows = db.execute(season_sql, ['1', 'u1', '2026', '2026', '2026']).fetchall()
ok([r[0] for r in rows] == [12, 10], 'yalnız bu sezon, en yeni başta, eski sezon yok: %s' % rows)
ok(db.execute(season_sql, ['2', 'u1', '2026', '2026', '2026']).fetchall() == [], 'başka hesabın bahçesi listelenmez')
h = db.execute(find_h, ['10', 'u1']).fetchone()
ok(h == (1, '20.10.2026', 190.0, 1000), 'hasat bulundu %s' % (h,))
ok(db.execute(find_h, ['20', 'u1']).fetchone() is None, 'başka hesabın hasadı silinemez')
op = db.execute(find_op, [str(h[0]), h[1].strip(), str(h[2]), str(h[3])]).fetchone()
ok(op == (100,), 'aynı gün/kilo Hasat hareketlerinden kayıt anına en yakını (Sulama değil): %s' % (op,))
op = db.execute(find_op, ['1', '27.10.2026', '265.0', '3000']).fetchone()
ok(op == (103,), 'ikinci hasadın hareketi')
ok(db.execute(find_op, ['1', '27.10.2026', '300.0', '3000']).fetchone() is None, 'kilo tutmazsa hareket silinmez')
print('PASS: %d hasat silme kontrolü' % n)
