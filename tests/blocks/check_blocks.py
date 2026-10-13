"""Host SQLite fixtures and source contracts; not Android/Firebase runtime tests."""
from pathlib import Path
import sqlite3,re,json,hashlib
root=Path(__file__).resolve().parents[2]
java=root/'app/src/main/java'
feed=(java/'FeedManager.java').read_text();replies=(java/'PostRepliesActivity.java').read_text()
repo=(java/'com/zeytindefteri/app/SocialBlockRepository.java').read_text()
ui=(java/'com/zeytindefteri/app/ProducerFeedActivity.java').read_text()
checks=0
def check(value):
 global checks
 assert value
 checks+=1
post_query='SELECT * FROM posts WHERE is_active = 1 ORDER BY created_at DESC, id DESC'
reply_query='SELECT * FROM post_comments WHERE post_id = ? ORDER BY created_at DESC, cloud_id DESC'
check(post_query in feed);check(reply_query in replies)
db=sqlite3.connect(':memory:');db.row_factory=sqlite3.Row
db.executescript('CREATE TABLE posts(id INTEGER,user_id TEXT,is_active INTEGER,created_at INTEGER); CREATE TABLE post_comments(cloud_id TEXT,post_id INTEGER,user_key TEXT,parent_comment_id TEXT,created_at INTEGER);')
db.executemany('INSERT INTO posts VALUES(?,?,1,?)',[(i,'B',i) for i in range(2,160)]+[(1,'C',1)])
db.executemany('INSERT INTO post_comments VALUES(?,?,?,?,?)',[('b'+str(i),1,'B','',i) for i in range(2,160)]+[('c',1,'C','b2',1),('a',1,'A','',0)])
blocked={'A':{'B'},'C':set()}
def visible(rows,viewer,owner_column,limit):
 return [dict(r) for r in rows if r[owner_column] not in blocked[viewer]][:limit]
check([r['user_id'] for r in visible(db.execute(post_query),'A','user_id',100)]==['C'])
check(len(visible(db.execute(post_query),'C','user_id',100))==100)
rows=visible(db.execute(reply_query,(1,)),'A','user_key',100)
check({r['user_key'] for r in rows}=={'A','C'})
check(next(r for r in rows if r['cloud_id']=='c')['parent_comment_id']=='b2')
# Confirmed per-user cache survives serialization; no shared global set.
cache=json.loads(json.dumps({k:list(v) for k,v in blocked.items()}))
check(cache['A']==['B'] and cache['C']==[])
blocked['A'].remove('B');check(any(r['user_id']=='B' for r in visible(db.execute(post_query),'A','user_id',100)))
check(db.execute('SELECT COUNT(*) FROM posts').fetchone()[0]==159)
check('"confirmed_"+owner' in repo and 'owner.equals(user.getUid())' in repo)
check('snapshot.getMetadata().hasPendingWrites()||snapshot.getMetadata().isFromCache()' in repo)
check('if(task.isSuccessful())' in repo and 'runTransaction' in repo)
check('owner.equals(blockedUid)' in repo)
check('socialBlocks.blocked(postOwnerUid)' in replies and 'socialBlocks.blocked(r.uid)' in replies)
check('socialBlocks.blocked(p.userId)' in ui and 'socialBlocks.blocked(uid)' in ui)
check('if(nodes.size()>=visibleLimit)break;' in replies and replies.index('if(socialBlocks.blocked(r.uid))continue;')<replies.index('if(nodes.size()>=visibleLimit)break;'))
check('SocialReportManager.showReasons' in replies and 'SocialReportManager.showReasons' in ui)
check('SocialBlockUi.confirm' in replies and 'SocialBlockUi.confirm' in ui)
check('Engellenen Kullanıcılar' in ui)
print(f'PASS {checks} SQLite fixture/source-contract checks (not Android or Firestore execution)')
