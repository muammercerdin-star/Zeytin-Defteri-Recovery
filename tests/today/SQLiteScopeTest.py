"""Host SQLite fixtures for the existing helper query shapes; no device/user database is opened."""
from pathlib import Path
import ast, re, sqlite3
root = Path(__file__).resolve().parents[2]
source = (root / 'app/src/main/java/DatabaseHelper.java').read_text()

def query(method, owner, season):
    body = source[source.index('public Cursor ' + method + '('):]
    expression = body.split('rawQuery(',1)[1].split('args.toArray',1)[0].rstrip().rstrip(',')
    expression = re.sub(r'activeOwnerCondition\("([^"]+)"\)',
        lambda m: repr(m[1]+' = ?' if owner else '('+m[1]+" IS NULL OR TRIM("+m[1]+") = '')"),expression)
    def season_filter(m):
        alias, column = m[1],m[2]
        return repr(f"(COALESCE({alias}.season_year, 0) = {season} OR (COALESCE({alias}.season_year, 0) = 0 AND (substr(TRIM({alias}.{column}), -4) = '{season}' OR substr(TRIM({alias}.{column}), 1, 4) = '{season}')))" )
    expression = re.sub(r'activeSeasonCondition\("([^"]+)", "([^"]+)"\)',season_filter,expression)
    # Only string literals and concatenation from the known query expression are evaluated.
    def string_expr(node):
        if isinstance(node,ast.Constant) and isinstance(node.value,str): return node.value
        if isinstance(node,ast.BinOp) and isinstance(node.op,ast.Add): return string_expr(node.left)+string_expr(node.right)
        raise AssertionError('Unexpected query expression')
    return string_expr(ast.parse(' '.join(expression.splitlines()).strip(),mode='eval').body)

connection=sqlite3.connect(':memory:')
connection.executescript('''
CREATE TABLE gardens(id INTEGER,owner_uid TEXT);
INSERT INTO gardens VALUES(1,'a'),(2,'a'),(3,'b'),(4,'');
CREATE TABLE operations(id INTEGER,garden_id INTEGER,season_year INTEGER,operation_date TEXT,created_at INTEGER);
CREATE TABLE harvests(id INTEGER,garden_id INTEGER,season_year INTEGER,harvest_date TEXT,created_at INTEGER);
''')
rows=[(1,1,2026,'2026-09-03',1),(2,2,2026,'2026-08-01',2),(3,3,2026,'2026-09-04',3),
      (4,1,2025,'2025-09-03',4),(5,1,0,'04.09.2026',5),(6,4,2026,'2026-09-01',6)]
for table in ['operations','harvests']:
    connection.executemany('INSERT INTO '+table+' VALUES(?,?,?,?,?)',rows)
checks=0
for method in ['getGardenOperations','getGardenHarvests']:
    for owner,season,garden,expected in [('a',2026,1,[5,1]),('a',2026,2,[2]),('a',2026,3,[]),
        ('b',2026,1,[]),('a',2025,1,[4]),('',2026,4,[6]),('',2026,1,[])]:
        args=[str(garden)]+([owner] if owner else [])
        actual=[r[0] for r in connection.execute(query(method,owner,season),args)]
        assert actual==expected,(method,owner,season,garden,actual)
        checks+=1
print(f'PASS {checks} host SQLite scope fixtures (existing query expressions; owner/season helper semantics reproduced)')
