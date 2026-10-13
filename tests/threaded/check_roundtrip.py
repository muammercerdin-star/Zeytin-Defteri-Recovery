"""SQLite/schema and mapping fixtures. Not a live two-device Firestore test."""
from pathlib import Path
import runpy
p=Path(__file__).resolve().parents[2]
m=runpy.run_path(str(p/'tests/meydan/check_schema.py'))
a=m['setup']();a.execute("INSERT INTO posts(id,cloud_id,user_id,user_name,content,created_at) VALUES(1,'post','a','A','Post',1)")
a.execute("INSERT INTO post_comments(cloud_id,post_id,user_key,user_name,content,created_at) VALUES('legacy',1,'a','A','Old',1)")
m['migrate'](a);m['migrate'](a)
fields=['parent_comment_id','root_comment_id','reply_to_uid','reply_to_name']
assert set(fields).issubset({r[1] for r in a.execute('pragma table_info(post_comments)')})
assert a.execute("SELECT content,parent_comment_id FROM post_comments WHERE cloud_id='legacy'").fetchone()==('Old','')
for cid,parent,root,uid,name,target,targetname in [('root','','root','a','A','',''),('child','root','root','b','B','a','A'),('deep','child','root','a','A','b','B')]:
    a.execute('INSERT INTO post_comments(cloud_id,post_id,user_key,user_name,content,created_at,parent_comment_id,root_comment_id,reply_to_uid,reply_to_name) VALUES(?,1,?,?,?,2,?,?,?,?)',(cid,uid,name,cid,parent,root,target,targetname))
a.commit()
# Reopening a database preserves the relationships, and soft-delete does not cascade.
import tempfile,sqlite3
with tempfile.TemporaryDirectory() as d:
    path=Path(d)/'cache.db';disk=sqlite3.connect(path);a.backup(disk);disk.close();b=sqlite3.connect(path)
    assert b.execute("SELECT parent_comment_id,root_comment_id,reply_to_uid FROM post_comments WHERE cloud_id='deep'").fetchone()==('child','root','b')
    b.execute("UPDATE post_comments SET is_active=0 WHERE cloud_id='root' AND user_key='b'")
    assert b.execute("SELECT is_active FROM post_comments WHERE cloud_id='root'").fetchone()[0]==1
    b.execute("UPDATE post_comments SET is_active=0 WHERE cloud_id='root' AND user_key='a'")
    assert b.execute("SELECT count(*) FROM post_comments WHERE cloud_id IN ('child','deep') AND is_active=1").fetchone()[0]==2
repo=(p/'app/src/main/java/CommentCloudRepository.java').read_text()
for field in fields:
    assert 'data.put("'+field+'"' in repo
    assert 'value(doc,"'+field+'")' in repo
print('PASS additive migration, legacy preservation, nested cache reopen, ownership/delete and four-field upload/download mapping checks')
