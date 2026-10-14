"""Meydan silme/eşleştirme SQL'leri: gerçek SQLite üzerinde, kaynak koddaki SQL metinleriyle çalıştırılır.
Android SQLite'ı değil masaüstü sqlite3'ü kullanır; cihaz testi yerine geçmez."""
from pathlib import Path
import re, sqlite3, sys
ROOT = Path(__file__).resolve().parents[2]
J = ROOT / 'app/src/main/java'
dbh = (J / 'DatabaseHelper.java').read_text(encoding='utf8')
scr = (J / 'SocialCloudRepository.java').read_text(encoding='utf8')
fails, n = [], 0
def check(ok, msg):
    global n; n += 1
    if not ok: fails.append(msg)
def body(src, sig):
    i = src.index(sig); depth = 0; j = src.index('{', i)
    for k in range(j, len(src)):
        depth += src[k] == '{'; depth -= src[k] == '}'
        if depth == 0: return src[i:k + 1]
def sql_in(segment, opener):
    """opener'dan sonraki ilk string birleşimini (\"a\" + \"b\") döndürür."""
    s = segment[segment.index(opener) + len(opener):]
    m = re.match(r'\s*((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+)', s)
    return ''.join(re.findall(r'"((?:[^"\\]|\\.)*)"', m.group(1)))

q_pending = sql_in(body(dbh, 'public boolean isPostDeletionPending('), 'rawQuery(')
w_deact = sql_in(body(dbh, 'public int deactivatePostsFromCloud('), 'db.update("posts", values,')
q_active = sql_in(body(dbh, 'getActiveCloudPostsForSync()'), 'rawQuery(')
q_deletes = sql_in(body(dbh, 'getPendingPostDeletionIds(String userId)'), 'rawQuery(')
q_retry = sql_in(body(scr, 'public void retryPendingPosts()'), 'rawQuery(')
q_publish = sql_in(body(scr, 'public void publishPostDeletion('), 'rawQuery(')
delete_body = body(dbh, 'public int deletePost(\n')
check('values.put("sync_state", cloudId.isEmpty() ? 2 : 1)' in delete_body, 'deletePost bekleyen silme işaretlemiyor')
check('values.put("is_active", 0)' in delete_body and 'updated_at' in delete_body, 'deletePost is_active/updated_at yazmıyor')

con = sqlite3.connect(':memory:')
con.execute("""CREATE TABLE posts (id INTEGER PRIMARY KEY, cloud_id TEXT, user_id TEXT, created_at INTEGER,
               updated_at INTEGER, is_active INTEGER DEFAULT 1, sync_state INTEGER DEFAULT 2)""")
rows = [  # id, cloud_id, user_id, created_at, is_active, sync_state
    (1, 'c1', 'me', 100, 1, 2), (2, 'c2', 'me', 200, 1, 1), (3, '', 'me', 300, 1, 1), (4, 'c4', 'other', 400, 1, 2),
    (5, None, 'other', 500, 1, 2), (6, '  ', 'me', 600, 1, 2), (7, 'c7', 'me', 700, 1, 2), (8, 'c8', 'other', 800, 1, None)]
con.executemany('INSERT INTO posts (id,cloud_id,user_id,created_at,is_active,sync_state) VALUES (?,?,?,?,?,?)', rows)

def delete_post(pid):  # DatabaseHelper.deletePost ile aynı karar
    cid = (con.execute('SELECT cloud_id FROM posts WHERE id=?', (pid,)).fetchone()[0] or '').strip()
    con.execute('UPDATE posts SET is_active=0, updated_at=1, sync_state=? WHERE id=?', (2 if cid == '' else 1, pid))

active = sorted(r[0] for r in con.execute(q_active))
check(active == ['c1', 'c2', 'c4', 'c7', 'c8'], f'etkin bulut gönderileri yanlış: {active}')
check([r[2] for r in con.execute(q_active) if r[0] == 'c8'] == [2], 'sync_state NULL → 2 sayılmıyor')
delete_post(7); delete_post(3)
check(con.execute(q_pending, ('c7',)).fetchone() is not None, 'silinen bulut gönderisi bekleyen sayılmıyor')
check(con.execute(q_pending, ('c1',)).fetchone() is None, 'etkin gönderi bekleyen sayıldı')
check(con.execute('SELECT sync_state FROM posts WHERE id=3').fetchone()[0] == 2, 'bulutta olmayan silme bekletiliyor')
check([r[0] for r in con.execute(q_deletes, ('me',))] == [7], 'bekleyen silme listesi yanlış')
check([r[0] for r in con.execute(q_deletes, ('other',))] == [], 'başkasının silmesi listede')
check(q_deletes.count('?') == 1 and 'user_id = ?' in q_deletes, 'bekleyen silmeler kullanıcıya bağlı değil')
retry = [r[0] for r in con.execute(q_retry, ('me',))]
check(retry == [2], f'yeniden yayın silinen gönderiyi içeriyor: {retry}')
check(con.execute(q_publish, ('7',)).fetchone() == ('c7', 'me'), 'publishPostDeletion satırı bulamıyor')
check(con.execute(q_publish, ('1',)).fetchone() is None, 'publishPostDeletion etkin gönderiyi siliyor')
# Buluttan kapatma: yalnızca etkin satırlar; bekleyen silme ezilmez (zaten is_active=0).
for cid in ['c4', 'c7', '']:
    con.execute(f'UPDATE posts SET is_active=0, sync_state=2 WHERE {w_deact}', (cid,))
check(con.execute('SELECT is_active, sync_state FROM posts WHERE id=4').fetchone() == (0, 2), 'buluttan kapatma çalışmıyor')
check(con.execute('SELECT sync_state FROM posts WHERE id=7').fetchone()[0] == 1, 'bekleyen silme buluttan kapatmayla kayboldu')
check(con.execute('SELECT COUNT(*) FROM posts WHERE is_active=1').fetchone()[0] == 5, 'boş kimlik başka satırları kapattı')

print(f'check_sql: {n} kontrol, {len(fails)} hata')
for f in fails: print('  HATA:', f)
sys.exit(1 if fails else 0)
