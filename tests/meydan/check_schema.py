"""Actual schema SQL + v18 migration fixtures; never touches user data."""
from pathlib import Path
import re,ast,sqlite3,uuid
root=Path(__file__).resolve().parents[2]
schema=re.sub(r'/\*.*?\*/','',(root/'app/src/main/java/DatabaseSchema.java').read_text(),flags=re.S)
helper=(root/'app/src/main/java/DatabaseHelper.java').read_text()

def literal(expr):
    def read(node):
        if isinstance(node,ast.Constant) and isinstance(node.value,str):return node.value
        if isinstance(node,ast.BinOp) and isinstance(node.op,ast.Add):return read(node.left)+read(node.right)
        raise ValueError('dynamic expression')
    return read(ast.parse(' '.join(expr.splitlines()).strip(),mode='eval').body)

def setup():
    db=sqlite3.connect(':memory:')
    for expr in re.findall(r'db\.execSQL\(\s*(.*?)\s*\);',schema,re.S):
        try:sql=literal(re.sub(r'//[^\n]*','',expr))
        except (ValueError,SyntaxError):continue
        db.execute(sql)
    return db
migration=helper[helper.index('private static void ensureCommentSyncSchema'):]
def migrate(db):
    for table,column,definition in re.findall(r'addColumnIfMissing\(db, "([^"]+)", "([^"]+)", "([^"]+)"\)',migration):
        if column not in [r[1] for r in db.execute('PRAGMA table_info('+table+')')]:
            db.execute('ALTER TABLE '+table+' ADD COLUMN '+column+' '+definition)
    for expr in re.findall(r'db\.execSQL\((".*?")\);',migration):db.execute(literal(expr))
    for (id,) in db.execute("SELECT id FROM post_comments WHERE cloud_id IS NULL OR TRIM(cloud_id) = ''").fetchall():
        db.execute('UPDATE post_comments SET cloud_id=? WHERE id=?',(str(uuid.uuid4()),id))

checks=0
def check(v):
    global checks
    assert v
    checks+=1
for existing in (False,True):
    db=setup()
    db.execute("INSERT INTO posts(id,cloud_id,user_id,user_name,content,created_at) VALUES(1,'post-a','a','A','Post',1)")
    if existing:
        db.execute("INSERT INTO post_comments(post_id,user_key,user_name,content,created_at) VALUES(1,'a','A','Eski yorum',1)")
    migrate(db);migrate(db)
    check('sync_state' in [r[1] for r in db.execute('PRAGMA table_info(post_comments)')])
    check('cloud_id' in [r[1] for r in db.execute('PRAGMA table_info(post_comments)')])
    check('sync_state' in [r[1] for r in db.execute('PRAGMA table_info(posts)')])
    if existing:
        check(db.execute('SELECT content FROM post_comments').fetchone()[0]=='Eski yorum')
        check(bool(db.execute('SELECT cloud_id FROM post_comments').fetchone()[0]))
    db.execute("INSERT INTO post_comments(cloud_id,post_id,user_key,user_name,content,created_at,sync_state) VALUES('comment-new',1,'a','A','Hello',2,1)")
    try:
        db.execute("INSERT INTO post_comments(cloud_id,post_id,user_name,content,created_at) VALUES('comment-new',1,'A','Duplicate',2)")
        raise AssertionError('duplicate accepted')
    except sqlite3.IntegrityError:checks+=1
    check(db.execute("SELECT count(*) FROM post_comments WHERE user_key='b' AND sync_state IN (0,1)").fetchone()[0]==0)
    db.execute("UPDATE post_comments SET is_active=0,sync_state=1,updated_at=3 WHERE cloud_id='comment-new' AND user_key='a'")
    check(db.execute("SELECT count(*) FROM post_comments WHERE cloud_id='comment-new'").fetchone()[0]==1)
    check(db.execute("SELECT count(*) FROM post_comments WHERE cloud_id='comment-new' AND is_active=1").fetchone()[0]==0)
    db.execute("UPDATE post_comments SET sync_state=2 WHERE cloud_id='comment-new' AND updated_at=2")
    check(db.execute("SELECT sync_state FROM post_comments WHERE cloud_id='comment-new'").fetchone()[0]==1)
    db.close()
print('PASS',checks,'fresh install / migration preservation / duplicate / owner / pending-delete fixtures; not Firestore emulator tests')
