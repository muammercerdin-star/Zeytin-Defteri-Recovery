from pathlib import Path
import re, sqlite3, xml.etree.ElementTree as ET, subprocess, tempfile

p = Path(__file__).resolve().parents[2]
activity = (p / 'app/src/main/java/ElekArchiveActivity.java').read_text()
repository_path = p / 'app/src/main/java/ElekRepository.java'
res = p / 'app/src/main/res'
a = '{http://schemas.android.com/apk/res/android}'

# UI contract: archive screen stays compact and all bound IDs/drawables exist.
layouts = [res / 'layout' / f for f in [
    'activity_elek_archive.xml', 'elek_archive_header.xml', 'item_elek_archive.xml'
]]
ids = set()
for f in res.rglob('*.xml'):
    ET.parse(f)
for f in layouts:
    root = ET.parse(f).getroot()
    for e in root.iter():
        assert e.tag != 'HorizontalScrollView'
        assert '1240dp' not in e.attrib.values()
        if a + 'id' in e.attrib:
            ids.add(e.get(a + 'id').split('/')[-1])
        for value in e.attrib.values():
            if value.startswith('@drawable/'):
                name = value.split('/')[1]
                assert any(res.glob('drawable*/' + name + '.*')), name
assert set(re.findall(r'R.id.(\w+)', activity)) <= ids
assert 'tvArchiveScreenRecordCount' not in activity
assert all('archiveGrade' + str(i) in ids for i in range(6))

# Architecture contract: SQL moved out of Activity. Keep the current Repository boundary.
assert 'new ElekRepository(databaseHelper).getArchiveRecords(' in activity
assert 'String sql =' not in activity

# Compile the actual production ElekRepository with tiny Android/database stubs and
# capture the exact SQL + bind arguments produced by getArchiveRecords().
def capture_query(year, garden_id, owner_uid):
    with tempfile.TemporaryDirectory(prefix='zeytin-elek-archive-') as directory:
        t = Path(directory)
        for rel in ['android/content', 'android/database/sqlite', 'android/database', 'com/zeytindefteri/app']:
            (t / rel).mkdir(parents=True, exist_ok=True)
        (t / 'android/content/ContentValues.java').write_text('''
package android.content; public class ContentValues {
 public void put(String k,String v){} public void put(String k,long v){}
 public void put(String k,int v){} public void put(String k,double v){}
}''')
        (t / 'android/database/Cursor.java').write_text('''
package android.database; public interface Cursor {
 boolean moveToFirst(); double getDouble(int i); int getInt(int i); void close();
}''')
        (t / 'android/database/sqlite/SQLiteDatabase.java').write_text('''
package android.database.sqlite;
import android.content.ContentValues; import android.database.Cursor;
public class SQLiteDatabase {
 public static String sql; public static String[] args;
 public void beginTransaction(){} public long insert(String t,String n,ContentValues v){return 1;}
 public void setTransactionSuccessful(){} public void endTransaction(){}
 public Cursor rawQuery(String s,String[] a){sql=s;args=a;return new C();}
 public int update(String t,ContentValues v,String w,String[] a){return 1;}
 public int delete(String t,String w,String[] a){return 1;}
 static class C implements Cursor {public boolean moveToFirst(){return false;} public double getDouble(int i){return 0;} public int getInt(int i){return 0;} public void close(){}}
}''')
        (t / 'com/zeytindefteri/app/DatabaseHelper.java').write_text('''
package com.zeytindefteri.app; import android.database.sqlite.SQLiteDatabase;
public class DatabaseHelper {
 private final SQLiteDatabase db=new SQLiteDatabase();
 public SQLiteDatabase getWritableDatabase(){return db;} public SQLiteDatabase getReadableDatabase(){return db;}
 public String getActiveOwnerUid(){return "owner-a";} public void recordCloudDeletion(String t,long id){}
}''')
        (t / 'com/zeytindefteri/app/ElekRepository.java').write_text(repository_path.read_text())
        (t / 'com/zeytindefteri/app/Capture.java').write_text('''
package com.zeytindefteri.app; import android.database.sqlite.SQLiteDatabase;
public class Capture {
 public static void main(String[] x){
  int year=Integer.parseInt(x[0]); long garden=Long.parseLong(x[1]); String owner=x[2];
  new ElekRepository(new DatabaseHelper()).getArchiveRecords(year,garden,owner);
  System.out.println(SQLiteDatabase.sql); System.out.println(String.join("\\u001f",SQLiteDatabase.args));
 }
}''')
        sources = [str(f) for f in t.rglob('*.java')]
        subprocess.run(['javac', '--release', '8', '-encoding', 'UTF-8', '-d', str(t / 'classes')] + sources,
                       check=True, stdout=subprocess.DEVNULL)
        out = subprocess.check_output([
            'java', '-cp', str(t / 'classes'), 'com.zeytindefteri.app.Capture',
            str(year), str(garden_id), owner_uid
        ], text=True).splitlines()
        return out[0], out[1].split('\x1f')

all_sql, all_args = capture_query(2026, -1, 'owner-a')
garden_sql, garden_args = capture_query(2026, 1, 'owner-a')
old_year_sql, old_year_args = capture_query(2025, -1, 'owner-a')
other_owner_sql, other_owner_args = capture_query(2026, -1, 'owner-b')

# Execute the actual generated Repository SQL against an in-memory SQLite fixture.
db = sqlite3.connect(':memory:')
db.executescript('''
CREATE TABLE gardens(id INTEGER,name TEXT,district TEXT,province TEXT,owner_uid TEXT);
CREATE TABLE elek_records(id INTEGER,garden_id INTEGER,record_date TEXT,season_year INTEGER,owner_uid TEXT);
CREATE TABLE elek_grades(elek_id INTEGER,grade TEXT,kg REAL,line_total REAL);
INSERT INTO gardens VALUES
 (1,'Birinci Bahçe','Alaşehir','Manisa','owner-a'),
 (2,'İkinci Bahçe','Alaşehir','Manisa','owner-a'),
 (3,'Başka Kullanıcı','Akhisar','Manisa','owner-b');
INSERT INTO elek_records VALUES
 (1,1,'2026-09-07',2026,'owner-a'),
 (2,2,'2026-09-08',2026,'owner-a'),
 (3,1,'2025-09-07',2025,'owner-a'),
 (4,3,'2026-09-09',2026,'owner-b'),
 (5,1,'2026-09-10',2026,'owner-b'),
 (6,1,'06.09.2026',0,'');
''')
for record in range(1, 7):
    for grade in ['DOUBLE', '1', '2', '3', '4', 'YAGLIK']:
        db.execute('INSERT INTO elek_grades VALUES(?,?,?,?)', (record, grade, record, record * 10))

def record_ids(sql, args):
    rows = db.execute(sql, args).fetchall()
    return rows, [r[9] for r in rows]

rows, actual = record_ids(all_sql, all_args)
assert actual == [2, 1, 6], actual
assert sum(r[0] for r in rows) == 54
assert sum(r[1] for r in rows) == 540
assert all(r[1] / r[0] == 10 for r in rows)
assert rows[0][3:9] == (2, 2, 2, 2, 2, 2)

rows, actual = record_ids(garden_sql, garden_args)
assert actual == [1, 6], actual
rows, actual = record_ids(old_year_sql, old_year_args)
assert actual == [3], actual
rows, actual = record_ids(other_owner_sql, other_owner_args)
assert actual == [4], actual
assert 5 not in actual  # record owner and garden owner disagree: must not cross user boundary

# Reuse the actual Activity formatting helpers as host-Java tests.
def method(name):
    m = re.search(r'    private (?:String|void) ' + name + r'\s*\(', activity)
    start = m.start(); b = activity.index('{', m.end()); depth = 1; k = b + 1
    while depth:
        depth += (activity[k] == '{') - (activity[k] == '}'); k += 1
    return activity[start:k]

code = 'import java.text.*;import java.util.*;class ArchiveDateTest { final Locale turkishLocale=new Locale("tr","TR"); DecimalFormat numberFormat;'
for name in ['formatDay', 'formatTableDate', 'setupNumberFormat', 'formatNumber']:
    code += method(name)
code += '''public static void main(String[] args) {ArchiveDateTest t=new ArchiveDateTest();t.setupNumberFormat();
if(!t.formatDay("2026-09-07").equals("Pazartesi"))throw new AssertionError();
if(!t.formatDay("2026-09-05").equals("Cumartesi"))throw new AssertionError();
if(!t.formatTableDate("2026-09-07").equals("07 Eylül 2026"))throw new AssertionError();
if(!t.formatDay("invalid").isEmpty())throw new AssertionError();
if(!t.formatNumber(29306).equals("29.306"))throw new AssertionError();
System.out.println("PASS Turkish date/day and number formatting");}}'''
with tempfile.TemporaryDirectory() as d:
    f = Path(d) / 'ArchiveDateTest.java'; f.write_text(code)
    subprocess.run(['java', str(f)], check=True)

print('PASS archive Activity->Repository boundary, actual Repository SQL owner/season/garden isolation, legacy season fallback, ordering, six grades, sums, average, XML/IDs/drawables')
