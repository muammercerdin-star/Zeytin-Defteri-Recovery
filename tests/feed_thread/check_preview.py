"""Execute the actual feed aggregate SQL against local fixtures; no live Firebase claim."""
from pathlib import Path
import sqlite3,re,ast
root=Path(__file__).resolve().parents[2]
source=(root/'app/src/main/java/com/zeytindefteri/app/ProducerFeedActivity.java').read_text()
method=source[source.index('private void loadFeedReplyPreviews'):source.index('private View buildPostCard')]
expr=re.search(r'String sql=(.*?);',method,re.S).group(1)
sql=''.join('?,?' if t=='marks' else ast.literal_eval(t) for t in re.findall(r'"(?:[^"\\]|\\.)*"|\bmarks\b',expr))
db=sqlite3.connect(':memory:');db.row_factory=sqlite3.Row
db.execute('CREATE TABLE post_comments(cloud_id TEXT, post_id INTEGER, is_active INTEGER, created_at INTEGER, content TEXT, parent_comment_id TEXT)')
db.executemany('INSERT INTO post_comments VALUES(?,?,?,?,?,?)',[
 ('root',1,1,10,'Root',''),('child',1,1,20,'Child','root'),('deep',1,1,30,'Nested reply','child'),
 ('deleted',1,0,40,'Deleted','root'),('legacy',2,1,5,'Old comment',None),('other',3,1,50,'Outside visible posts','')])
# Sorgu artık yalnız etkin yanıtları yeniden eskiye getirir; sayım ve engel süzgeci Java'da yapılır.
rows=list(db.execute(sql,['1','2']))
ids=[r['cloud_id'] for r in rows]
assert 'deleted' not in ids and 'other' not in ids, ids
assert ids==['deep','child','root','legacy'], ids
first={}
for row in rows: first.setdefault(row['post_id'],row)
assert first[1]['cloud_id']=='deep' and first[1]['parent_comment_id']=='child'
assert sum(1 for r in rows if r['post_id']==1)==3 and first[2]['cloud_id']=='legacy'
db.execute("INSERT INTO post_comments VALUES('z-tie',1,1,30,'Same timestamp','root')")
rows=list(db.execute(sql,['1','2']))
assert rows[0]['cloud_id']=='z-tie', [r['cloud_id'] for r in rows]
assert list(db.execute(sql,['8','9']))==[]
assert 'previous.cachedCount++' in method and 'socialBlocks.blocked(uid)' in method
assert not any(x in method for x in ['FirebaseFirestore','addSnapshotListener','.collection('])
print('PASS reply preview SQL: active-only, newest first, stable tie, visible-post isolation; counting/blocks in Java; no Firestore access')
