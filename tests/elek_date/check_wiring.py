"""Fiş tarih düzeltme: fiş + bağlı satış birlikte (tek işlem, sezon yılıyla), onay, gelecek gün yok, bulut."""
import sqlite3
from pathlib import Path
A = Path('app/src/main/java/com/zeytindefteri/app')
b = (A / 'ElekSaleBridge.java').read_text(encoding='utf-8')
r = (A / 'ElekReceiptActivity.java').read_text(encoding='utf-8')
checks = {
    'fiş ekranında düğme': 'dateButton.setOnClickListener(v -> editDate());' in r,
    'gelecek gün seçilemez': 'dlg.getDatePicker().setMaxDate(System.currentTimeMillis());' in r and 'ElekDateFix.problem(' in r,
    'onay penceresi': '"Tarih düzeltilsin mi?"' in r and '.setNegativeButton("Vazgeç", null)' in r,
    'fiş + satış tek işlemde': 'e.put("record_date", iso);' in b and 's.put("sale_date", ElekSaleModel.saleDate(iso));' in b
        and b.count('db.setTransactionSuccessful();') >= 1,
    'sezon yılı da değişir': 'e.put("season_year", year);' in b and 's.put("season_year", year);' in b,
    'yalnız etkin hesap': '"id=? AND COALESCE(owner_uid,\'\')=?"' in b,
    'fiş ve satış buluta gider': 'syncCloud();' in r and 'syncSale(saleId);' in r,
}
# Gerçek SQLite: aynı UPDATE'ler; sezon filtresiyle kayıt doğru sezona geçer.
db = sqlite3.connect(':memory:')
db.executescript("""
CREATE TABLE elek_records(id INTEGER PRIMARY KEY, record_date TEXT, season_year INTEGER, updated_at INTEGER, owner_uid TEXT);
CREATE TABLE sales(id INTEGER PRIMARY KEY, sale_date TEXT, season_year INTEGER, updated_at INTEGER, owner_uid TEXT, source_elek_id INTEGER);
INSERT INTO elek_records VALUES(1,'2026-01-02',2026,0,'u');
INSERT INTO sales VALUES(7,'02.01.2026',2026,0,'u',1);
INSERT INTO elek_records VALUES(2,'2026-01-02',2026,0,'baska');
""")
db.execute("UPDATE elek_records SET record_date=?, season_year=?, updated_at=? WHERE id=? AND COALESCE(owner_uid,'')=?", ('2025-12-30', 2025, 5, 1, 'u'))
db.execute("UPDATE sales SET sale_date=?, season_year=?, updated_at=? WHERE id=? AND COALESCE(owner_uid,'')=?", ('30.12.2025', 2025, 5, 7, 'u'))
n = db.execute("UPDATE elek_records SET record_date='2025-12-30' WHERE id=? AND COALESCE(owner_uid,'')=?", (2, 'u')).rowcount
checks['başka hesabın fişi değişmez'] = n == 0
checks['fiş 2025 sezonunda'] = db.execute("SELECT COUNT(*) FROM elek_records WHERE season_year=2025").fetchone()[0] == 1
checks['satış 2025 sezonunda, gg.aa.yyyy'] = db.execute("SELECT sale_date FROM sales WHERE season_year=2025").fetchone()[0] == '30.12.2025'
for k, v in checks.items(): print(('OK  ' if v else 'FAIL') + ' ' + k)
raise SystemExit(0 if all(checks.values()) else 1)
