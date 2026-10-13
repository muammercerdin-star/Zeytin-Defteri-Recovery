#!/usr/bin/env python3
"""Rebuilds ElekRepository.archiveQuery exactly as the Java code concatenates it and runs it on SQLite.
Verifies the columns the Elek tab reads (record_date, garden_name, total_kg, total_revenue, grade columns)."""
from pathlib import Path
import re, sqlite3
root = Path(__file__).resolve().parents[2]
src = (root / 'app/src/main/java/ElekRepository.java').read_text(encoding='utf-8')
body = src[src.index('private Cursor archiveQuery('):]
body = body[:body.index('return databaseHelper')]
# Pull the literal SQL pieces in order.
lits = re.findall(r'"((?:[^"\\]|\\.)*)"', body)
grades = ["DOUBLE", "1", "2", "3", "4", "YAGLIK"]
cols = ["double_kg", "grade1_kg", "grade2_kg", "grade3_kg", "grade4_kg", "oil_kg"]
assert lits[:12] == grades + cols, 'grade/column arrays changed'
base = lits[12] + lits[13]
grade_sql = ''.join(lits[14] + g + lits[15] + c for g, c in zip(grades, cols))
records = lits[16] + lits[17]
where = ''.join(lits[18:24])
garden_filter = lits[24]
group = lits[25]
assert group.startswith('GROUP BY') and garden_filter.startswith('AND er.garden_id'), 'query pieces moved'
sql_all = base + grade_sql + records + where + group
sql_one = base + grade_sql + records + where + garden_filter + group
sql_summary = base + grade_sql + where
db = sqlite3.connect(':memory:'); db.row_factory = sqlite3.Row
db.executescript('''
CREATE TABLE gardens(id INTEGER, owner_uid TEXT, name TEXT, district TEXT, province TEXT);
CREATE TABLE elek_records(id INTEGER, garden_id INTEGER, owner_uid TEXT, season_year INTEGER, record_date TEXT);
CREATE TABLE elek_grades(elek_id INTEGER, grade TEXT, kg REAL, line_total REAL);
INSERT INTO gardens VALUES (1,'u','Edremit','Gemlik','Bursa'),(2,'u','Girelli','',''),(3,'x','Başka','','');
INSERT INTO elek_records VALUES (10,1,'u',2026,'2026-10-13'),(11,2,'u',2026,'2026-10-01'),(12,1,'u',2025,'2025-10-01'),(13,3,'x',2026,'2026-10-02');
INSERT INTO elek_grades VALUES (10,'DOUBLE',3,360),(10,'1',15,1200),(10,'2',25,1500),(10,'3',40,1600),(10,'4',10,200),(10,'YAGLIK',5,50),
 (11,'1',20,1600),(12,'1',99,9999),(13,'1',7,7);
''')
checks = 0
def ok(v, l):
    global checks
    assert v, l
    checks += 1
rows = [dict(r) for r in db.execute(sql_all, ['u', 'u', '2026', '2026', '2026'])]
ok([r['record_date'] for r in rows] == ['2026-10-13', '2026-10-01'], 'season+owner scope, newest first')
r = rows[0]
ok(r['garden_name'] == 'Edremit' and r['total_kg'] == 98 and r['total_revenue'] == 4910, 'screenshot record totals')
ok([r[c] for c in cols] == [3, 15, 25, 40, 10, 5], 'per-grade kg columns')
one = [dict(r) for r in db.execute(sql_one, ['u', 'u', '2026', '2026', '2026', '2'])]
ok(len(one) == 1 and one[0]['garden_name'] == 'Girelli', 'garden filter')
s = dict(db.execute(sql_summary, ['u', 'u', '2026', '2026', '2026']).fetchone())
ok(s['total_kg'] == 118 and s['record_count'] == 2, 'summary equals sum of records')
print(f'PASS: {checks} elek archive query checks (host SQLite fixture).')
