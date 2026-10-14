"""LocalAccountWipe'ı (üretim kodu, değiştirilmeden) gerçek SQLite üzerinde çalıştırır.
Android SQLiteDatabase çağrıları küçük bir köprüyle Python sqlite3'e aktarılır; şema üretimdeki
DatabaseSchema CREATE TABLE ifadelerinden kurulur ve yabancı anahtarlar açıktır.
Android derlemesi veya cihaz testi değildir."""
from pathlib import Path
import ast, base64, re, sqlite3, subprocess, sys, tempfile

ROOT = Path(__file__).resolve().parents[2]

if len(sys.argv) > 1 and sys.argv[1] == '--bridge':
    db = sqlite3.connect(sys.argv[2], isolation_level=None)
    db.execute('PRAGMA foreign_keys=ON')
    dec = lambda s: base64.b64decode(s).decode('utf8')
    enc = lambda s: base64.b64encode(str(s).encode('utf8')).decode('ascii')
    for line in sys.stdin:
        try:
            parts = line.rstrip('\n').split('\t')
            cur = db.execute(dec(parts[1]), [dec(a) for a in parts[2:]])
            if parts[0] == 'Q':
                rows = cur.fetchall()
                print('Q\t' + str(len(rows)) + '\t' + '\t'.join(enc(d[0]) for d in (cur.description or [])))
                for row in rows:
                    print('\t'.join('~' if v is None else enc(v) for v in row))
            else:
                print('N\t' + str(cur.rowcount))
        except Exception as error:
            print('E\t' + enc(str(error)))
        sys.stdout.flush()
    sys.exit(0)

CURSOR = '''package android.database;
public class Cursor implements AutoCloseable {
 private final String[] columns;private final String[][] rows;private int at=-1;
 public Cursor(String[] c,String[][] r){columns=c;rows=r;}
 public boolean moveToFirst(){at=0;return rows.length>0;}
 public boolean moveToNext(){return ++at<rows.length;}
 public int getColumnIndex(String s){for(int i=0;i<columns.length;i++)if(columns[i].equals(s))return i;return -1;}
 public String getString(int i){return rows[at][i];}
 public void close(){}
}'''
DATABASE = r'''package android.database.sqlite;
import android.database.Cursor;import java.io.*;import java.util.*;
public class SQLiteDatabase implements AutoCloseable {
 private final Process bridge;private final BufferedReader in;private final PrintWriter out;private boolean success;public int transactions;
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
 public int delete(String table,String where,String[] args){return Integer.parseInt(request("U","DELETE FROM "+table+" WHERE "+where,args)[1]);}
 public void beginTransaction(){success=false;transactions++;request("U","BEGIN IMMEDIATE",null);}
 public void setTransactionSuccessful(){success=true;}
 public void endTransaction(){request("U",success?"COMMIT":"ROLLBACK",null);}
 public void close(){out.close();try{bridge.waitFor();}catch(InterruptedException e){Thread.currentThread().interrupt();}}
}'''
RUNNER = '''package com.zeytindefteri.app;
import android.database.sqlite.SQLiteDatabase;
public class Run { public static void main(String[] a)throws Exception{
 try(SQLiteDatabase db=new SQLiteDatabase(a[0],a[1],a[2])){System.out.println(LocalAccountWipe.wipe(db,a[3]));}
}}'''

with tempfile.TemporaryDirectory(prefix='zeytin-wipe-') as folder:
    temp = Path(folder)
    for name, text in {'android/database/Cursor.java': CURSOR,
                       'android/database/sqlite/SQLiteDatabase.java': DATABASE,
                       'com/zeytindefteri/app/Run.java': RUNNER}.items():
        p = temp / name; p.parent.mkdir(parents=True, exist_ok=True); p.write_text(text)
    wipe_src = ROOT / 'app/src/main/java/com/zeytindefteri/app/LocalAccountWipe.java'
    subprocess.run(['java', 'com.sun.tools.javac.Main', '--release', '8', '-encoding', 'UTF-8', '-d', str(temp / 'classes'),
                    str(wipe_src)] + [str(f) for f in temp.rglob('*.java')], check=True)

    def fixture():
        file = temp / 'f.db'
        if file.exists(): file.unlink()
        db = sqlite3.connect(file)
        schema = (ROOT / 'app/src/main/java/DatabaseSchema.java').read_text(encoding='utf8')
        for expression in re.findall(r'db\.execSQL\(\s*("CREATE TABLE IF NOT EXISTS .*?)\s*\);', schema, re.S):
            db.execute(''.join(ast.literal_eval(s) for s in re.findall(r'"(?:[^"\\]|\\.)*"', expression)))
        db.execute('PRAGMA foreign_keys=ON')
        g = "INSERT INTO gardens(id,name,owner_uid,cloud_id,created_at,updated_at) VALUES(?,?,?,?,1,1)"
        db.executemany(g, [(1, 'A bahçe', 'A', 'ga'), (2, 'B bahçe', 'B', 'gb'), (3, 'Misafir', '', 'gg')])
        db.executemany("INSERT INTO operations(id,garden_id,owner_uid,operation_date,created_at,type) VALUES(?,?,?,'01.10.2026',1,'Sulama')",
                       [(1, 1, 'A'), (2, 1, ''), (3, 2, 'B'), (4, 3, '')])
        db.execute("INSERT INTO harvests(id,garden_id,owner_uid,harvest_date,created_at,total_kg) VALUES(1,1,'',  '01.10.2026',1,100)")
        db.execute("INSERT INTO harvest_grades(harvest_id,grade,kg) VALUES(1,'1',100)")
        db.execute("INSERT INTO elek_records(id,garden_id,owner_uid,record_date,created_at) VALUES(1,1,'A','01.10.2026',1)")
        db.execute("INSERT INTO elek_grades(elek_id,grade,kg,price_per_kg) VALUES(1,'1',50,90)")
        db.execute("INSERT INTO elek_records(id,garden_id,owner_uid,record_date,created_at) VALUES(2,2,'B','01.10.2026',1)")
        db.execute("INSERT INTO elek_grades(elek_id,grade,kg,price_per_kg) VALUES(2,'1',50,90)")
        db.execute("INSERT INTO buyers(id,name,owner_uid,created_at) VALUES(1,'Alıcı A','A',1)")
        db.execute("INSERT INTO buyers(id,name,owner_uid,created_at) VALUES(2,'Alıcı B','B',1)")
        db.execute("INSERT INTO sales(id,garden_id,owner_uid,buyer_id,sale_date,created_at) VALUES(1,1,'A',1,'01.10.2026',1)")
        db.execute("INSERT INTO sale_grades(sale_id,grade,kg,price_per_kg) VALUES(1,'1',10,90)")
        db.execute("INSERT INTO receivables(title,owner_uid,related_sale_id,amount,created_at) VALUES('A alacak','A',1,10,1)")
        db.execute("INSERT INTO receivables(title,owner_uid,amount,created_at) VALUES('B alacak','B',10,1)")
        db.execute("INSERT INTO brine_batches(id,garden_id,owner_uid,batch_name,start_date,created_at) VALUES(1,NULL,'A','Bidon','01.10.2026',1)")
        db.execute("INSERT INTO brine_events(batch_id,event_date,event_type,created_at) VALUES(1,'01.10.2026','CHECK',1)")
        db.execute("INSERT INTO reminders(garden_id,owner_uid,title,due_at,created_at) VALUES(1,'A','Hatırlatma',2,1)")
        db.execute("INSERT INTO posts(id,cloud_id,user_id,user_name,content,created_at) VALUES(1,'pa','A','A','A gönderi',1)")
        db.execute("INSERT INTO posts(id,cloud_id,user_id,user_name,content,created_at) VALUES(2,'pb','B','B','B gönderi',1)")
        db.execute("INSERT INTO post_comments(post_id,user_key,user_name,content,created_at) VALUES(2,'firebase:A','A','A yorum',1)")
        db.execute("INSERT INTO post_comments(post_id,user_key,user_name,content,created_at) VALUES(2,'firebase:B','B','B yorum',1)")
        db.execute("INSERT INTO post_reactions(post_id,user_key,reaction_type,created_at) VALUES(2,'firebase:A','USEFUL',1)")
        db.executemany("INSERT INTO app_settings(setting_key,setting_value) VALUES(?,?)",
                       [('market_contribution_opt_in_A', '1'), ('active_season_year:A', '2026'), ('market_contribution_opt_in_B', '1'), ('global', 'x')])
        db.commit(); db.close()
        return file

    def run(file, uid):
        return subprocess.check_output(['java', '-cp', str(temp / 'classes'), 'com.zeytindefteri.app.Run', sys.executable,
                                        str(Path(__file__).resolve()), str(file), uid], text=True).strip()

    checks = 0
    def check(ok, label):
        global checks
        assert ok, label
        checks += 1
        print('PASS', label)

    file = fixture()
    deleted = int(run(file, 'A'))
    db = sqlite3.connect(file)
    q = lambda sql: db.execute(sql).fetchall()
    check(deleted > 0, 'satırlar silindi')
    check(q("SELECT id FROM gardens ORDER BY id") == [(2,), (3,)], 'yalnız A bahçesi silindi; B ve misafir duruyor')
    check(q("SELECT id FROM operations ORDER BY id") == [(3,), (4,)], 'A bahçesinin işlemleri (sahipsiz eski kayıt dahil) gitti')
    check(q("SELECT COUNT(*) FROM harvests")[0][0] == 0 and q("SELECT COUNT(*) FROM harvest_grades")[0][0] == 0, 'hasat ve boyları zincirle silindi')
    check(q("SELECT id FROM elek_records") == [(2,)] and q("SELECT elek_id FROM elek_grades") == [(2,)], 'B elek kaydı korundu')
    check(q("SELECT name FROM buyers") == [('Alıcı B',)], 'A alıcısı silindi')
    check(q("SELECT COUNT(*) FROM sales")[0][0] == 0 and q("SELECT COUNT(*) FROM sale_grades")[0][0] == 0, 'A satışı ve boyları silindi')
    check(q("SELECT title FROM receivables") == [('B alacak',)], 'A alacağı silindi')
    check(q("SELECT COUNT(*) FROM brine_batches")[0][0] == 0 and q("SELECT COUNT(*) FROM brine_events")[0][0] == 0, 'bahçesiz salamura ve olayları silindi')
    check(q("SELECT COUNT(*) FROM reminders")[0][0] == 0, 'hatırlatma silindi')
    check(q("SELECT cloud_id FROM posts") == [('pb',)], 'Meydan önbelleğinde A gönderisi silindi')
    check(q("SELECT user_key FROM post_comments") == [('firebase:B',)] and q("SELECT COUNT(*) FROM post_reactions")[0][0] == 0, 'A yorumu ve tepkisi silindi')
    check(sorted(q("SELECT setting_key FROM app_settings")) == [('global',), ('market_contribution_opt_in_B',)], 'A ayar anahtarları silindi')
    check(not q("PRAGMA foreign_key_check"), 'yabancı anahtarlar tutarlı')
    db.close()

    file = fixture()
    run(file, '')
    db = sqlite3.connect(file)
    check(db.execute("SELECT id FROM gardens ORDER BY id").fetchall() == [(1,), (2,)], 'misafir silmesi yalnız sahipsiz bahçeyi siler')
    check(db.execute("SELECT id FROM operations WHERE garden_id=1 ORDER BY id").fetchall() == [(1,), (2,)], 'misafir silmesi hesaplı bahçenin eski kaydına dokunmaz')
    check(db.execute("SELECT COUNT(*) FROM app_settings").fetchone()[0] == 4, 'misafir silmesi ayarlara dokunmaz')
    db.close()

    src = wipe_src.read_text(encoding='utf8')
    check('pragma_table_info' not in src.replace('// pragma_table_info()', ''), 'Android 7 (SQLite 3.9) uyumlu PRAGMA kullanılıyor')
    print(f'PASS {checks} gerçek SQLite kontrolü (üretim LocalAccountWipe); Android cihaz testi değildir')
