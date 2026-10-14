"""Execute unchanged production helper method bodies on host SQLite via a tiny JVM bridge.
Not an Android build: Android database APIs are bridged to Python's real SQLite.
No Firebase/network/user database is used. Each CLI invocation reopens the fixture.
"""
from pathlib import Path
import ast, base64, json, re, sqlite3, subprocess, sys, tempfile

ROOT = Path(__file__).resolve().parents[2]

# SQLite bridge subprocess, kept alive for the duration of a JVM call/transaction.
if len(sys.argv) > 1 and sys.argv[1] == '--bridge':
    db = sqlite3.connect(sys.argv[2], isolation_level=None)
    db.execute('PRAGMA foreign_keys=ON')
    decode = lambda s: base64.b64decode(s).decode('utf8')
    encode = lambda s: base64.b64encode(str(s).encode('utf8')).decode('ascii')
    for line in sys.stdin:
        try:
            parts = line.rstrip('\n').split('\t')
            kind, sql = parts[0], decode(parts[1])
            cur = db.execute(sql, [decode(a) for a in parts[2:]])
            if kind == 'Q':
                rows = cur.fetchall()
                print('Q\t' + str(len(rows)) + '\t' + '\t'.join(encode(d[0]) for d in cur.description))
                for row in rows:
                    print('\t'.join('~' if v is None else encode(v) for v in row))
            else:
                print('N\t' + str(cur.lastrowid if kind == 'I' else cur.rowcount))
        except Exception as error:
            print('E\t' + encode(str(error)))
        sys.stdout.flush()
    sys.exit(0)

source = (ROOT / 'app/src/main/java/DatabaseHelper.java').read_text()
def method(signature, source=source):
    start = source.index(signature)
    opening = source.index('{', start)
    depth = 1
    i = opening + 1
    # Chosen methods have no braces in strings/comments.
    while depth:
        depth += (source[i] == '{') - (source[i] == '}')
        i += 1
    return source[start:i]

CONTENT_VALUES = '''package android.content;
import java.util.*;
public class ContentValues extends LinkedHashMap<String,Object> {
 public void put(String k,String v){super.put(k,v);} public void put(String k,int v){super.put(k,v);}
 public void put(String k,long v){super.put(k,v);} public void put(String k,double v){super.put(k,v);}
}'''
CURSOR = '''package android.database;
public class Cursor implements AutoCloseable {
 private String[] columns;private String[][] rows; private int at=-1;
 public Cursor(String[] c,String[][] r){columns=c;rows=r;}
 public boolean moveToFirst(){at=0;return rows.length>0;}
 public boolean moveToNext(){return ++at<rows.length;}
 public int getColumnIndexOrThrow(String s){for(int i=0;i<columns.length;i++)if(columns[i].equals(s))return i;throw new IllegalArgumentException(s);}
 public String getString(int i){return rows[at][i];}
 public long getLong(int i){return getString(i)==null?0:Long.parseLong(getString(i));}
 public int getInt(int i){return (int)getLong(i);}
 public double getDouble(int i){return getString(i)==null?0:Double.parseDouble(getString(i));}
 public void close(){}
}'''
DATABASE = r'''package android.database.sqlite;
import android.content.ContentValues;import android.database.Cursor;import java.io.*;import java.util.*;
public class SQLiteDatabase implements AutoCloseable {
 private Process bridge;private BufferedReader in;private PrintWriter out;private boolean success;
 public SQLiteDatabase(String python,String script,String file)throws Exception{
  bridge=new ProcessBuilder(python,script,"--bridge",file).redirectError(ProcessBuilder.Redirect.INHERIT).start();
  in=new BufferedReader(new InputStreamReader(bridge.getInputStream(),"UTF-8"));out=new PrintWriter(new OutputStreamWriter(bridge.getOutputStream(),"UTF-8"),true);
 }
 static String enc(String v){return Base64.getEncoder().encodeToString(v.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
 static String dec(String v){return new String(Base64.getDecoder().decode(v),java.nio.charset.StandardCharsets.UTF_8);}
 private String[] request(String kind,String sql,String[] args){
  String line=kind+"\t"+enc(sql);if(args!=null)for(String a:args)line+="\t"+enc(a);out.println(line);
  try{String[] r=in.readLine().split("\t",-1);if(r[0].equals("E"))throw new RuntimeException(dec(r[1]));return r;}catch(IOException e){throw new RuntimeException(e);}
 }
 public Cursor rawQuery(String sql,String[] args){
  String[] h=request("Q",sql,args);int count=Integer.parseInt(h[1]);String[] columns=new String[h.length-2];
  for(int i=0;i<columns.length;i++)columns[i]=dec(h[i+2]);String[][] rows=new String[count][];
  try{for(int i=0;i<count;i++){String[] r=in.readLine().split("\t",-1);for(int j=0;j<r.length;j++)r[j]=r[j].equals("~")?null:dec(r[j]);rows[i]=r;}}catch(IOException e){throw new RuntimeException(e);}
  return new Cursor(columns,rows);
 }
 public int update(String table,ContentValues values,String where,String[] args){
  List<String> sets=new ArrayList<>(),bind=new ArrayList<>();for(String k:values.keySet()){sets.add(k+"=?");bind.add(String.valueOf(values.get(k)));}
  if(args!=null)Collections.addAll(bind,args);return Integer.parseInt(request("U","UPDATE "+table+" SET "+String.join(",",sets)+" WHERE "+where,bind.toArray(new String[0]))[1]);
 }
 public long insert(String table,String unused,ContentValues values){
  List<String> keys=new ArrayList<>(values.keySet()),bind=new ArrayList<>(),q=new ArrayList<>();for(String k:keys){bind.add(String.valueOf(values.get(k)));q.add("?");}
  return Long.parseLong(request("I","INSERT INTO "+table+"("+String.join(",",keys)+") VALUES("+String.join(",",q)+")",bind.toArray(new String[0]))[1]);
 }
 public void beginTransaction(){success=false;request("U","BEGIN IMMEDIATE",null);}
 public void setTransactionSuccessful(){success=true;}
 public void endTransaction(){request("U",success?"COMMIT":"ROLLBACK",null);}
 public void close(){out.close();try{bridge.waitFor();}catch(InterruptedException e){Thread.currentThread().interrupt();}}
}'''
RUNNER = '''package com.zeytindefteri.app;
import android.database.Cursor;import android.database.sqlite.SQLiteDatabase;
public class Run {
 public static void main(String[] args)throws Exception{
  try(SQLiteDatabase db=new SQLiteDatabase(args[0],args[1],args[2])){
   DatabaseHelper h=new DatabaseHelper(db,args[3],Integer.parseInt(args[4]));long id=Long.parseLong(args[6]);
   if(args[5].equals("edit"))System.out.println(new GardenRepository(h).updateGarden(id,args[7],Integer.parseInt(args[8]),15,"Gemlik",Double.parseDouble(args[9]),"Manisa","Alaşehir","Girelli","Damla sulama","Genel not","content://photo")==1);
   else if(args[5].equals("note"))System.out.println(h.addGardenNote(id,"09.09.2026 15:12",args[7],""));
   else {
    Cursor c=args[5].equals("notes")?h.getGardenNotes(id)
      :args[5].equals("filtered")?h.getOperationsFiltered(id<0?null:Integer.valueOf((int)id),null)
      :h.getGardenOperations(id,Integer.parseInt(args[7]));
    try(Cursor rows=c){while(rows.moveToNext())System.out.println(rows.getLong(rows.getColumnIndexOrThrow("id")));}
   }
  }
 }
}'''

with tempfile.TemporaryDirectory(prefix='zeytin-garden-sqlite-') as folder:
    temp = Path(folder)
    bodies = '\n'.join(method(signature) for signature in [
        'public boolean updateGarden(', 'public Cursor getGarden(\n',
        'public long addGardenNote(', 'public Cursor getGardenNotes(',
        'public Cursor getGardenOperations(long gardenId, int limit)',
        'public Cursor getOperationsFiltered(',
        'private String activeOwnerCondition(', 'private String activeSeasonCondition(',
        'private String safe(\n'])
    helper = '''package com.zeytindefteri.app;
import android.content.ContentValues;import android.database.Cursor;import android.database.sqlite.SQLiteDatabase;import java.util.*;
public class DatabaseHelper {
 private final SQLiteDatabase db;private final String uid;private final int year;
 public DatabaseHelper(SQLiteDatabase d,String u,int y){db=d;uid=u;year=y;}
 public SQLiteDatabase getReadableDatabase(){return db;} public SQLiteDatabase getWritableDatabase(){return db;}
 public String getActiveOwnerUid(){return uid;} public int getActiveSeasonYear(){return year;}
''' + bodies + '\n}'
    repository_source = (ROOT / 'app/src/main/java/GardenRepository.java').read_text()
    repository = ('package com.zeytindefteri.app; public class GardenRepository {'
        'private final DatabaseHelper databaseHelper;'
        'public GardenRepository(DatabaseHelper helper){databaseHelper=helper;}'
        + method('public int updateGarden(', repository_source) + '}')
    for name, text in {'com/zeytindefteri/app/GardenRepository.java':repository,
                       'android/content/ContentValues.java':CONTENT_VALUES,
                       'android/database/Cursor.java':CURSOR,
                       'android/database/sqlite/SQLiteDatabase.java':DATABASE,
                       'com/zeytindefteri/app/DatabaseHelper.java':helper,
                       'com/zeytindefteri/app/Run.java':RUNNER}.items():
        path=temp/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(text)
    subprocess.run(['java','com.sun.tools.javac.Main','--release','8','-encoding','UTF-8','-d',str(temp/'classes')]
                   + [str(f) for f in temp.rglob('*.java')],check=True)
    file=temp/'fixture.db'
    db=sqlite3.connect(file)
    schema=(ROOT/'app/src/main/java/DatabaseSchema.java').read_text()
    # Execute the production CREATE TABLE statements, not an approximation of the schema.
    for expression in re.findall(r'db\.execSQL\(\s*("CREATE TABLE IF NOT EXISTS .*?)\s*\);',schema,re.S):
        parts=re.findall(r'"(?:[^"\\]|\\.)*"',expression)
        db.execute(''.join(ast.literal_eval(s) for s in parts))
    db.execute('PRAGMA foreign_keys=ON')
    db.executemany('INSERT INTO gardens(id,name,owner_uid,cloud_id,created_at,updated_at) VALUES(?,?,?,?,?,?)',[
        (7,'Bahçe A','a','garden-a',111,2000000000000),(8,'Bahçe A2','a','garden-a2',112,222),
        (9,'Bahçe B','b','garden-b',113,223),(10,'Guest','', '',114,224),(11,'Legacy guest',None,'',115,225)])
    ops=[(1,7,'a',2026,'09.09.2026',100),(2,8,'a',2026,'09.09.2026',200),
         (3,9,'b',2026,'09.09.2026',300),(4,7,'a',2025,'09.09.2025',400),
         (5,7,'b',2026,'09.09.2026',500),(6,7,'',0,'08.09.2026',600),
         (7,7,None,0,'2026-09-09',700),(8,7,'a',0,'2025-09-09',800),
         (9,10,'',2026,'09.09.2026',900),(10,10,'a',2026,'09.09.2026',1000),
         (11,7,'a',2026,'09.09.2026',700)]
    db.executemany("INSERT INTO operations(id,garden_id,owner_uid,season_year,operation_date,created_at,type,quantity,unit,cost,note) VALUES(?,?,?,?,?,?,'Sulama',20,'L',30,'İşlem notu')",ops)
    db.execute("INSERT INTO harvests(id,garden_id,harvest_date,created_at,total_kg) VALUES(1,7,'09.09.2026',123,250)")
    db.execute("INSERT INTO elek_records(id,garden_id,record_date,created_at) VALUES(1,7,'09.09.2026',123)")
    db.execute("INSERT INTO sales(id,garden_id,sale_date,created_at) VALUES(1,7,'09.09.2026',123)")
    db.execute("INSERT INTO brine_batches(id,garden_id,batch_name,start_date,created_at) VALUES(1,7,'Bidon','09.09.2026',123)")
    db.execute("INSERT INTO harvest_grades(harvest_id,grade,kg) VALUES(1,'1',250)")
    db.execute("INSERT INTO elek_grades(elek_id,grade,kg,price_per_kg) VALUES(1,'1',250,80)")
    db.execute("INSERT INTO sale_grades(sale_id,grade,kg,price_per_kg) VALUES(1,'1',250,80)")
    db.execute("INSERT INTO brine_events(batch_id,event_date,event_type,created_at) VALUES(1,'09.09.2026','CHECK',123)")
    db.execute("INSERT INTO receivables(title,related_sale_id,amount,created_at) VALUES('Alacak',1,20000,123)")
    db.execute("INSERT INTO reminders(garden_id,title,due_at,created_at) VALUES(7,'Hatırlatma',456,123)")
    db.execute("INSERT INTO garden_notes(id,garden_id,note_date,note,created_at,owner_uid) VALUES(10,7,'09.09.2026','Wrong owner',1,'b')")
    db.execute("INSERT INTO garden_notes(id,garden_id,note_date,note,created_at,owner_uid) VALUES(11,9,'09.09.2026','Wrong garden',1,'a')")
    db.commit()
    def run(action, garden=7, owner='a', year=2026, extra=None):
        return subprocess.check_output(['java','-cp',str(temp/'classes'),'com.zeytindefteri.app.Run',
            sys.executable,str(Path(__file__).resolve()),str(file),owner,str(year),action,str(garden)]
            + (extra or ['0']),text=True).strip()
    checks=0
    def check(condition, label):
        global checks
        assert condition,label
        checks+=1;print('PASS',label)
    related=['operations','harvests','elek_records','sales','brine_batches','garden_notes',
             'harvest_grades','elek_grades','sale_grades','brine_events','receivables','reminders']
    before={t:db.execute('SELECT * FROM '+t).fetchall() for t in related}
    check(run('edit',extra=['Yeni Bahçe', '120','2.5'])=='true','existing garden updated')
    g=db.execute('SELECT id,owner_uid,cloud_id,created_at,updated_at,name,tree_count,area_decare FROM gardens WHERE id=7').fetchone()
    check(g[:4]==(7,'a','garden-a',111),'garden_id/owner/cloud_id/created_at unchanged')
    check(g[4]>2000000000000 and g[5:]==('Yeni Bahçe',120,2.5),'all edited data persisted, sync timestamp increases')
    check(db.execute('SELECT COUNT(*) FROM gardens').fetchone()[0]==5,'edit creates no garden')
    for t in related: check(db.execute('SELECT * FROM '+t).fetchall()==before[t],t+' rows and links unchanged')
    for owner,garden in [('b',7),('a',9),('',7),('a',999)]:
        check(run('edit',garden,owner,extra=['No', '5','1'])=='false','unauthorized/missing edit rejected '+owner+str(garden))
    for values in [['   ','120','2'],['Invalid','-1','2'],['Invalid','1','NaN'],['Invalid','1','-2']]:
        check(run('edit',extra=values)=='false','invalid edit rejected '+str(values))
    check(run('edit',10,'',extra=['Misafir', '12','0'])=='true','guest edit works locally')
    check(run('edit',11,'',extra=['Eski Misafir', '12','0'])=='true','null-owner guest behavior preserved')
    note_id=int(run('note',extra=['  Yeni not 🌿  ']))
    check(note_id>0,'note saved offline')
    check(run('notes')==str(note_id),'saved note read by reopening database; wrong owner note excluded')
    check(db.execute('SELECT note,owner_uid,cloud_id FROM garden_notes WHERE id=?',(note_id,)).fetchone()[:2]==('Yeni not 🌿','a'),'note trimmed and correct owner')
    check(bool(db.execute('SELECT cloud_id FROM garden_notes WHERE id=?',(note_id,)).fetchone()[0]),'existing note cloud identity assigned')
    for owner,garden in [('a',8),('b',7),('a',9),('',7)]:
        check(run('notes',garden,owner)=='','notes exclude other garden/owner '+owner+str(garden))
    count=db.execute('SELECT COUNT(*) FROM garden_notes').fetchone()[0]
    for owner,garden,note in [('a',7,'  '),('b',7,'No'),('a',9,'No'),('a',999,'No'),('',7,'No')]:
        check(run('note',garden,owner,extra=[note])=='-1','empty/foreign/missing note rejected '+owner+str(garden))
    check(db.execute('SELECT COUNT(*) FROM garden_notes').fetchone()[0]==count,'rejected notes insert nothing')
    guest_id=run('note',10,'',extra=['Guest note'])
    check(run('notes',10,'')==guest_id,'guest note saved and reopened')
    for owner,year,garden,expected in [('a',2026,7,[11,7,6,1]),('a',2025,7,[8,4]),
            ('a',2026,8,[2]),('a',2026,9,[]),('b',2026,7,[]),('b',2026,9,[3]),
            ('',2026,10,[9]),('',2026,7,[]),('a',2027,7,[])]:
        actual=[int(v) for v in run('history',garden,owner,year).splitlines()]
        check(actual==expected,'history owner/season/garden '+str((owner,year,garden)))
    check(run('history',extra=['2']).splitlines()==['11','7'],'recent preview shares scope and stable ordering')
    # İşlem geçmişi ekranı (getOperationsFiltered) bahçe geçmişiyle aynı sahip/sezon kuralını uygular.
    for owner,year,garden in [('a',2026,7),('a',2025,7),('a',2026,8),('b',2026,7),('b',2026,9),('',2026,10),('a',2027,7)]:
        filtered=sorted(int(v) for v in run('filtered',garden,owner,year).splitlines())
        history=sorted(int(v) for v in run('history',garden,owner,year).splitlines())
        check(filtered==history,'operation history screen matches garden history '+str((owner,year,garden)))
    check(sorted(int(v) for v in run('filtered',-1,'a',2026).splitlines())==[1,2,6,7,11],
          'all-gardens history: own gardens only, foreign-owner op 5 and op on guest garden excluded')
    check(sorted(int(v) for v in run('filtered',-1,'',2026).splitlines())==[9],'guest all-gardens history')
    check(not db.execute('PRAGMA foreign_key_check').fetchall(),'all fixture foreign keys valid')
    db.close()
    print(f'PASS {checks} real SQLite checks using compiled production helper bodies; no Android/device claim')
