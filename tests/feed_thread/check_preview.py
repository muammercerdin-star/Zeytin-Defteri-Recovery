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
rows=list(db.execute(sql,['1','2']));by_post={}
for row in rows:by_post.setdefault(row['post_id'],row)
assert len(by_post)==2
assert by_post[1]['cloud_id']=='deep' and by_post[1]['parent_comment_id']=='child'
assert by_post[1]['reply_count']==3
assert by_post[2]['cloud_id']=='legacy' and by_post[2]['reply_count']==1
db.execute("INSERT INTO post_comments VALUES('z-tie',1,1,30,'Same timestamp','root')")
rows=list(db.execute(sql,['1','2']));by_post={}
for row in rows:by_post.setdefault(row['post_id'],row)
assert by_post[1]['cloud_id']=='z-tie' and by_post[1]['reply_count']==4
assert list(db.execute(sql,['8','9']))==[]
assert not any(x in method for x in ['FirebaseFirestore','addSnapshotListener','.collection('])
print('PASS actual aggregate SQL: latest nested reply, deleted exclusion, legacy NULL parent, counts, visible-post isolation, stable timestamp tie, empty cache; no Firestore access in preview reader')
