"""getSeasonSaleLines sorgusu gerçek şemayla SQLite'ta çalıştırılır: listenin toplamı ana sayfadaki
getTotalSalesRevenue ile aynı olmalı; eleği silinmiş satış doğru işaretlenmeli; başka sahip/sezon girmemeli."""
import ast, re, sqlite3, sys
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
helper = (ROOT / 'app/src/main/java/DatabaseHelper.java').read_text(encoding='utf8')
schema = (ROOT / 'app/src/main/java/DatabaseSchema.java').read_text(encoding='utf8')
fails = []
def check(ok, m):
    print(('PASS ' if ok else 'FAIL ') + m)
    if not ok: fails.append(m)

def java_concat(expr, consts):
    out = ''
    for tok in re.findall(r'"(?:[^"\\]|\\.)*"|[A-Za-z_][A-Za-z0-9_]*\([^)]*\)|[A-Za-z_][A-Za-z0-9_]*', expr):
        if tok.startswith('"'): out += ast.literal_eval(tok)
        elif tok.startswith('activeOwnerCondition'): out += "g.owner_uid = ?"
        elif tok.startswith('activeSeasonCondition'):
            out += ("(COALESCE(s.season_year, 0) = 2026 OR (COALESCE(s.season_year, 0) = 0 AND ("
                    "substr(TRIM(s.sale_date), -4) = '2026' OR substr(TRIM(s.sale_date), 1, 4) = '2026')))")
        elif tok in consts: out += consts[tok]
    return out

iso = re.search(r'private static final String SALE_ISO_DATE =\s*(.*?);', helper, re.S).group(1)
consts = {'SALE_ISO_DATE': java_concat(iso, {})}
body = helper[helper.index('public Cursor getSeasonSaleLines()'):]
lines_sql = java_concat(re.search(r'rawQuery\(\s*(.*?),\s*activeOwnerArgs\(\)', body, re.S).group(1), consts)
tbody = helper[helper.index('public double getTotalSalesRevenue()'):]
total_sql = java_concat(re.search(r'getDoubleValue\(\s*(.*?),\s*activeOwnerArgs\(\)', tbody, re.S).group(1), consts)

db = sqlite3.connect(':memory:')
for block in re.findall(r'db\.execSQL\(\s*((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+)\)', schema):
    sql = ''.join(ast.literal_eval(x) for x in re.findall(r'"(?:[^"\\]|\\.)*"', block))
    if sql.startswith('CREATE TABLE'):
        try: db.execute(sql)
        except sqlite3.OperationalError: pass
db.execute("INSERT INTO gardens(id,name,owner_uid,created_at) VALUES(1,'Girelli','A',1),(2,'Hacılar','A',1),(3,'B bahçe','B',1)")
db.execute("INSERT INTO buyers(id,name,created_at) VALUES(7,'Tüccar Ali',1)")
db.execute("INSERT INTO elek_records(id,garden_id,record_date,owner_uid,created_at,cloud_id) VALUES(5,1,'2026-10-29','A',1,'E5')")
def sale(i, g, date, gross, elek=0, cloud='', season=2026, kind='SALE', tr=0, de=0, buyer=None, owner='A'):
    db.execute("INSERT INTO sales(id,garden_id,buyer_id,sale_date,sale_kind,total_kg,gross_total,transport_cost,deductions,"
               "source_elek_id,source_elek_cloud_id,created_at,season_year,owner_uid,note) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
               (i, g, buyer, date, kind, 100, gross, tr, de, elek, cloud, 1, season, owner, ''))
sale(1, 1, '2026-10-29', 47115, elek=5, buyer=7)
sale(2, 1, '20.10.2026', 8000, tr=200, de=100)          # elle, nakliye/kesinti düşülür → 7700
sale(3, 2, '2026-10-10', 900, elek=99, cloud='GONE')    # eleği silinmiş
sale(4, 1, '2025-10-10', 5000, season=2025)            # geçen sezon: girmez
sale(5, 3, '2026-10-11', 7000, owner='B')              # başka sahip: girmez
sale(6, 1, '2026-10-12', 1000, kind='PURCHASE')        # satış değil: girmez
sale(7, 1, '2026-10-13', 600, elek=0, cloud='E5', season=0)  # sezon 0, tarihten 2026; bulut id ile bağlı elek var

rows = db.execute(lines_sql, ('A',)).fetchall()
total = db.execute(total_sql, ('A',)).fetchone()[0]
check(abs(sum(r[4] for r in rows) - total) < 0.001, f'liste toplamı ana sayfa toplamına eşit ({sum(r[4] for r in rows)} = {total})')
check(sorted(r[0] for r in rows) == [1, 2, 3, 7], f'yalnız bu sahip, bu sezon, SALE: {sorted(r[0] for r in rows)}')
by = {r[0]: r for r in rows}
check(by[1][7] == 1 and by[1][5] == 1 and by[1][8] == 'Tüccar Ali', 'elekten satış: elek var, alıcı adı')
check(by[3][7] == 0 and by[3][5] == 1, 'eleği silinmiş satış işaretlenir')
check(abs(by[2][4] - 7700) < 0.001 and by[2][1] == '2026-10-20', 'net tutar ve gg.aa.yyyy → ISO tarih')
check(by[7][5] == 1 and by[7][7] == 1, 'yalnız bulut id ile bağlı satış da elekten sayılır')
check(by[2][5] == 0, 'elle satış elekten sayılmaz')
check([r[0] for r in rows] == [1, 2, 7, 3], f'yeniden eskiye sıralı: {[r[0] for r in rows]}')
print('season_sales query:', 'PASS' if not fails else f'{len(fails)} FAIL')
sys.exit(1 if fails else 0)
