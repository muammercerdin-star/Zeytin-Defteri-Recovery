#!/usr/bin/env python3
"""Runs the real getGardenSummaries SQL (from DatabaseHelper.java) against an in-memory SQLite fixture.
Host-only; no device database is opened."""
from pathlib import Path
import ast, re, sqlite3

root = Path(__file__).resolve().parents[2]
source = (root / 'app/src/main/java/DatabaseHelper.java').read_text(encoding='utf-8')
body = source[source.index('public Cursor getGardenSummaries('):]
expr = body.split('rawQuery(', 1)[1]
expr = expr[:expr.index('"ORDER BY g.name COLLATE NOCASE ASC"') + len('"ORDER BY g.name COLLATE NOCASE ASC"')]
YEAR = 2026

def season(m):
    a, col = m[1], m[2]
    return repr(f"(COALESCE({a}.season_year, 0) = {YEAR} OR (COALESCE({a}.season_year, 0) = 0 AND "
                f"(substr(TRIM({a}.{col}), -4) = '{YEAR}' OR substr(TRIM({a}.{col}), 1, 4) = '{YEAR}')))")

expr = re.sub(r'activeSeasonCondition\("([^"]+)", "([^"]+)"\)', season, expr)
expr = re.sub(r'\(getActiveOwnerUid\(\)\.isEmpty\(\)\s*\?[^:]*:\s*("WHERE g\.owner_uid = \? ")\)', r'\1', expr, flags=re.S)

def string_expr(node):
    if isinstance(node, ast.Constant) and isinstance(node.value, str): return node.value
    if isinstance(node, ast.BinOp) and isinstance(node.op, ast.Add): return string_expr(node.left) + string_expr(node.right)
    raise AssertionError('Unexpected query expression: ' + ast.dump(node)[:120])

sql = string_expr(ast.parse(' '.join(expr.split()), mode='eval').body)
db = sqlite3.connect(':memory:'); db.row_factory = sqlite3.Row
db.executescript('''
CREATE TABLE gardens(id INTEGER, owner_uid TEXT, name TEXT, tree_count INTEGER, age INTEGER, variety TEXT, photo_uri TEXT);
CREATE TABLE operations(id INTEGER, garden_id INTEGER, season_year INTEGER, operation_date TEXT, created_at INTEGER, type TEXT, cost REAL);
CREATE TABLE harvests(id INTEGER, garden_id INTEGER, season_year INTEGER, harvest_date TEXT, created_at INTEGER, total_kg REAL);
INSERT INTO gardens VALUES (1,'a','Girelli',80,12,'Gemlik',''),(2,'a','Edremit',100,10,'Gemlik',''),(3,'b','Başkası',5,1,'','');
INSERT INTO operations VALUES (1,1,2026,'2026-09-03',10,'Sulama',500),(2,1,2026,'2026-08-01',5,'Gübreleme',300),(3,1,2025,'2025-12-01',20,'İlaçlama',999),(4,3,2026,'2026-09-04',1,'Sulama',1);
INSERT INTO harvests VALUES (1,1,2026,'2026-09-03',11,1200),(2,1,2026,'2026-08-20',3,300),(3,1,2025,'2025-11-01',30,9999);
''')
rows = {r['id']: dict(r) for r in db.execute(sql, ['a'])}
checks = 0
def ok(v, label):
    global checks
    assert v, label
    checks += 1
ok(set(rows) == {1, 2}, 'owner scope kept')
g = rows[1]
ok(g['last_operation'] == 'Sulama', 'last operation type unchanged')
ok(g['last_operation_date'] == '2026-09-03', 'last operation date from same season, newest created')
ok(g['last_harvest_date'] == '2026-09-03', 'last harvest date, other season excluded')
ok(g['total_harvest'] == 1500, 'season harvest total unchanged')
ok(g['total_cost'] == 800, 'season cost total unchanged')
e = rows[2]
ok(e['last_operation'] is None and e['last_operation_date'] is None and e['last_harvest_date'] is None, 'empty garden')
ok(e['total_harvest'] == 0 and e['total_cost'] == 0, 'empty totals')
ok([r['name'] for r in db.execute(sql, ['a'])] == ['Edremit', 'Girelli'], 'name order unchanged')
print(f'PASS: {checks} garden summary SQL checks (host SQLite fixture).')
