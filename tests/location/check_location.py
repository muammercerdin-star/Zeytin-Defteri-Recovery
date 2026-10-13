"""Run the actual selected-garden SQL and verify existing field mappings; no live Firebase test."""
from pathlib import Path
import sqlite3,re
root=Path(__file__).resolve().parents[2]
s=(root/'app/src/main/java/SocialCloudRepository.java').read_text()
method=s[s.index('private String[] activeGardenPostLocation'):s.index('public void retryPendingPosts')]
sql=re.search(r'"(SELECT province, district, neighborhood FROM gardens[^"\n]+)"',method).group(1)
db=sqlite3.connect(':memory:')
db.execute('CREATE TABLE gardens(id INTEGER,owner_uid TEXT,province TEXT,district TEXT,neighborhood TEXT)')
db.executemany('INSERT INTO gardens VALUES(?,?,?,?,?)',[(1,'A','Manisa','Alaşehir','Afşar'),(2,'B','İzmir','Ödemiş',''),(3,'A','Manisa',None,''),(4,'A',None,None,None)])
assert db.execute(sql,['1','A']).fetchone()==('Manisa','Alaşehir','Afşar')
assert db.execute(sql,['2','A']).fetchone() is None
assert db.execute(sql,['99','A']).fetchone() is None
assert db.execute(sql,['3','A']).fetchone()==('Manisa',None,'')
assert db.execute(sql,['4','A']).fetchone()==(None,None,None)
assert 'home_active_garden_id:' in method
for field in ['province','district','neighborhood']:
 assert re.search(r'data.put\(\s*"'+field+r'",\s*str\(\s*post,\s*"'+field+r'"',s)
 assert re.search(r'value\(\s*document,\s*"'+field+r'",\s*""',s)
feed=(root/'app/src/main/java/FeedManager.java').read_text()
for field in ['province','district','neighborhood']:assert 'item.'+field+' =' in feed
ui=(root/'app/src/main/java/com/zeytindefteri/app/ProducerFeedActivity.java').read_text()
assert 'return district.isEmpty() ? safe(p.province, "") : district;' in ui
assert 'if(post&&!place(p).isEmpty())meta+=" • "+place(p);' in ui
print('PASS actual owner-filtered garden SQL: active location, other owner denied, missing garden, province-only, NULL fields; upload/import/PostItem/UI mappings present')
