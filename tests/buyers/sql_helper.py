"""Test yardımcısı: Java stub'ı her SQL'i bu betikle gerçek bir SQLite dosyasında çalıştırır.
Kullanım: sql_helper.py <db> <mode: query|exec|insert|change> ; SQL ve argümanlar stdin'den (\x1d ayraçlı)."""
import sqlite3, sys
db, mode = sys.argv[1], sys.argv[2]
raw = sys.stdin.buffer.read().decode('utf-8').split('\x1d')
sql, args = raw[0], [None if a == '\x1e' else a for a in raw[1:]]
def conv(a):
    if a is None: return None
    t, v = a[0], a[1:]
    return int(v) if t == 'I' else float(v) if t == 'D' else v
con = sqlite3.connect(db)
con.execute('PRAGMA foreign_keys=OFF')
cur = con.execute(sql, [conv(a) for a in args])
out = []
if mode == 'query':
    for row in cur.fetchall():
        out.append('\x1f'.join('\x1e' if v is None else str(v) for v in row))
    sys.stdout.buffer.write('\n'.join(out).encode('utf-8'))
elif mode == 'insert':
    sys.stdout.write(str(cur.lastrowid))
elif mode == 'change':
    sys.stdout.write(str(cur.rowcount))
con.commit()
