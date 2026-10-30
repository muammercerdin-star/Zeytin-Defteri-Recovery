"""Full production repository/scheduler/receivers/time/session against real SQLite + fake OS.
Not an Android device test: AlarmManager/notifications/permissions are deterministic test doubles.
"""
from pathlib import Path
import ast,re,sqlite3,subprocess,tempfile,sys,json
ROOT=Path(__file__).resolve().parents[2]
BASE=ROOT/'tests/garden_detail/check_sqlite.py'
# Keep the existing SQLite wire protocol; '~NULL' is our added actual NULL binding.
if len(sys.argv)>1 and sys.argv[1]=='--bridge':
 import base64
 db=sqlite3.connect(sys.argv[2],isolation_level=None);db.execute('PRAGMA foreign_keys=ON')
 enc=lambda v:base64.b64encode(str(v).encode()).decode();dec=lambda v:base64.b64decode(v).decode()
 for line in sys.stdin:
  try:
   p=line.rstrip('\n').split('\t');args=[dec(x) for x in p[2:]];args=[None if x=='~NULL' else x for x in args]
   c=db.execute(dec(p[1]),args)
   if p[0]=='Q':
    rows=c.fetchall();print('Q\t'+str(len(rows))+'\t'+'\t'.join(enc(x[0]) for x in c.description))
    for row in rows:print('\t'.join('~' if x is None else enc(x) for x in row))
   else:print('N\t'+str(c.lastrowid if p[0]=='I' else c.rowcount))
  except Exception as e:print('E\t'+enc(str(e)))
  sys.stdout.flush()
 sys.exit()
m=ast.parse(BASE.read_text());bridge={n.targets[0].id:ast.literal_eval(n.value) for n in m.body if isinstance(n,ast.Assign) and isinstance(n.targets[0],ast.Name) and n.targets[0].id in ('CONTENT_VALUES','CURSOR','DATABASE')}
bridge['CONTENT_VALUES']=bridge['CONTENT_VALUES'].replace(' public void put(String k,String v)', ' public void putNull(String k){super.put(k,null);}\n public void put(String k,String v)')
bridge['DATABASE']=bridge['DATABASE'].replace('String.valueOf(values.get(k))','values.get(k)==null?"~NULL":String.valueOf(values.get(k))')
bridge['DATABASE']=bridge['DATABASE'].replace(' public void close(){',''' public static final int CONFLICT_REPLACE=5;
 public long insertWithOnConflict(String t,String n,ContentValues v,int conflict){return insert(t,n,v);}
 public int delete(String t,String where,String[] args){return Integer.parseInt(request("U","DELETE FROM "+t+" WHERE "+where,args)[1]);}
 public void execSQL(String sql){request("U",sql,null);}
 public void close(){''')
STUBS={
'android/content/SharedPreferences.java':'''package android.content;import java.util.*;
public class SharedPreferences {private Map<String,Object> m=new HashMap<>();public Map<String,?> getAll(){return new HashMap<>(m);}public String getString(String k,String d){return (String)m.getOrDefault(k,d);}public long getLong(String k,long d){return (Long)m.getOrDefault(k,d);}public boolean getBoolean(String k,boolean d){return (Boolean)m.getOrDefault(k,d);}public Editor edit(){return new Editor();}
public class Editor {Map<String,Object> changes=new HashMap<>();boolean clear;public Editor putString(String k,String v){changes.put(k,v);return this;}public Editor putBoolean(String k,boolean v){changes.put(k,v);return this;}public Editor putLong(String k,long v){changes.put(k,v);return this;}public Editor remove(String k){changes.put(k,null);return this;}public Editor clear(){clear=true;return this;}public boolean commit(){if(clear)m.clear();for(String k:changes.keySet())if(changes.get(k)==null)m.remove(k);else m.put(k,changes.get(k));return true;}public void apply(){commit();}}}''',
'android/content/Context.java':'''package android.content;import java.util.*;public class Context {
public static final String ALARM_SERVICE="alarm",NOTIFICATION_SERVICE="notification",USER_SERVICE="user";public static final int MODE_PRIVATE=0;public int permission=0;
private Map<String,SharedPreferences> prefs=new HashMap<>();public final android.app.AlarmManager alarms=new android.app.AlarmManager();public final android.app.NotificationManager notifications=new android.app.NotificationManager();public final android.os.UserManager user=new android.os.UserManager();
public Context getApplicationContext(){return this;}public Object getSystemService(String s){return s.equals(ALARM_SERVICE)?alarms:s.equals(NOTIFICATION_SERVICE)?notifications:user;}public SharedPreferences getSharedPreferences(String s,int mode){return prefs.computeIfAbsent(s,k->new SharedPreferences());}public int checkSelfPermission(String p){return permission;}}''',
'android/content/Intent.java':'''package android.content;import java.util.*;public class Intent {public static final int FLAG_ACTIVITY_CLEAR_TOP=1,FLAG_ACTIVITY_SINGLE_TOP=2;public static final String ACTION_BOOT_COMPLETED="boot",ACTION_MY_PACKAGE_REPLACED="replace",ACTION_TIME_CHANGED="time",ACTION_TIMEZONE_CHANGED="zone";
public String action="",data="",target="";public Map<String,Object> extras=new HashMap<>();public Intent(Context c,Class<?> cls){target=cls.getName();}public Intent setAction(String a){action=a;return this;}public String getAction(){return action;}public Intent setData(android.net.Uri u){data=u.toString();return this;}public Intent putExtra(String k,long v){extras.put(k,v);return this;}public Intent putExtra(String k,String v){extras.put(k,v);return this;}public long getLongExtra(String k,long d){return (Long)extras.getOrDefault(k,d);}public Intent addFlags(int f){return this;}}''',
'android/content/BroadcastReceiver.java':'''package android.content;public abstract class BroadcastReceiver {public abstract void onReceive(Context c,Intent i);public PendingResult goAsync(){return new PendingResult();}public static class PendingResult {public void finish(){}}}''',
'android/content/pm/PackageManager.java':'package android.content.pm;public class PackageManager {public static final int PERMISSION_GRANTED=0;}',
'android/net/Uri.java':'package android.net;public class Uri {String s;public static Uri parse(String s){Uri u=new Uri();u.s=s;return u;}public String toString(){return s;}}',
'android/os/Build.java':'package android.os;public class Build {public static class VERSION {public static int SDK_INT=34;}}',
'android/os/UserManager.java':'package android.os;public class UserManager {public boolean unlocked=true;public boolean isUserUnlocked(){return unlocked;}}',
'android/os/Looper.java':'package android.os;public class Looper {public static Looper getMainLooper(){return new Looper();}}',
'android/os/Handler.java':'package android.os;public class Handler {public Handler(Looper l){}public void post(Runnable r){r.run();}}',
'android/util/Log.java':'package android.util;public class Log {public static int w(String t,String m,Throwable e){System.err.println(m+e);return 0;}}',
'android/app/PendingIntent.java':'''package android.app;import android.content.*;import java.util.*;public class PendingIntent {
public static final int FLAG_NO_CREATE=1,FLAG_UPDATE_CURRENT=2,FLAG_IMMUTABLE=4;static Map<String,PendingIntent> all=new HashMap<>();public String key;public Intent intent;public int flags;
static PendingIntent get(String kind,Intent i,int f){String k=kind+i.target+i.action+i.data;PendingIntent p=all.get(k);if(p==null&&(f&FLAG_NO_CREATE)!=0)return null;if(p==null){p=new PendingIntent();p.key=k;all.put(k,p);}p.intent=i;p.flags=f;return p;}
public static PendingIntent getBroadcast(Context c,int code,Intent i,int f){return get("broadcast",i,f);}public static PendingIntent getActivity(Context c,int code,Intent i,int f){return get("activity",i,f);}public void cancel(){all.remove(key);}}''',
'android/app/AlarmManager.java':'''package android.app;import java.util.*;public class AlarmManager {public static final int RTC_WAKEUP=0;public Map<String,Long> active=new HashMap<>();public int sets,cancels;public void cancel(PendingIntent p){active.remove(p.key);cancels++;}public void setAndAllowWhileIdle(int type,long due,PendingIntent p){active.put(p.key,due);sets++;}}''',
'android/app/NotificationChannel.java':'''package android.app;public class NotificationChannel {public String id;public int importance;public NotificationChannel(String id,String n,int i){this.id=id;importance=i;}public void setDescription(String d){}public int getImportance(){return importance;}}''',
'android/app/NotificationManager.java':'''package android.app;import java.util.*;public class NotificationManager {public static final int IMPORTANCE_DEFAULT=3,IMPORTANCE_NONE=0;public boolean enabled=true,deny=false;public int posted;public Map<String,Notification> active=new HashMap<>();public Map<String,NotificationChannel> channels=new HashMap<>();public boolean areNotificationsEnabled(){return enabled;}public void createNotificationChannel(NotificationChannel c){channels.putIfAbsent(c.id,c);}public NotificationChannel getNotificationChannel(String id){return channels.get(id);}public void notify(String tag,int id,Notification n){if(deny)throw new SecurityException();active.put(tag,n);posted++;}public void cancel(String tag,int id){active.remove(tag);}}''',
'android/app/Notification.java':'''package android.app;import android.content.Context;public class Notification {public static final int VISIBILITY_PRIVATE=0;public String title,text;public PendingIntent open;public static class BigTextStyle {public BigTextStyle bigText(String s){return this;}}
public static class Builder {Notification n=new Notification();public Builder(Context c){}public Builder(Context c,String ch){}public Builder setSmallIcon(int i){return this;}public Builder setContentTitle(String s){n.title=s;return this;}public Builder setContentText(String s){n.text=s;return this;}public Builder setStyle(BigTextStyle s){return this;}public Builder setContentIntent(PendingIntent p){n.open=p;return this;}public Builder setAutoCancel(boolean v){return this;}public Builder setOnlyAlertOnce(boolean v){return this;}public Builder setVisibility(int v){return this;}public Notification build(){return n;}}}''',
'com/zeytindefteri/app/R.java':'package com.zeytindefteri.app;public class R {public static class drawable {public static final int ic_bell_line=1;}}',
'com/zeytindefteri/app/RemindersActivity.java':'package com.zeytindefteri.app;public class RemindersActivity {}',
'com/zeytindefteri/app/PushRegistration.java':'package com.zeytindefteri.app;public class PushRegistration {public static void beforeSignOut(android.content.Context c){}}',
}
def method(s,signature):
 a=s.index(signature);i=s.index('{',a)+1;depth=1
 while depth:depth+=(s[i]=='{')-(s[i]=='}');i+=1
 return s[a:i]
source=(ROOT/'app/src/main/java/DatabaseHelper.java').read_text()
STUBS['com/zeytindefteri/app/DatabaseHelper.java']='''package com.zeytindefteri.app;import android.database.*;import android.database.sqlite.*;import android.content.*;
public class DatabaseHelper implements AutoCloseable {public static SQLiteDatabase shared;public static int season=2026;private Context c;public DatabaseHelper(Context c){this.c=c;}public SQLiteDatabase getReadableDatabase(){return shared;}public SQLiteDatabase getWritableDatabase(){return shared;}public String getActiveOwnerUid(){return new SessionManager(c).getUserUid();}public int getActiveSeasonYear(){return season;}private String safe(String v){return v==null?"":v.trim();}public void close(){}
'''+method(source,'public void recordCloudDeletion(')+method(source,'private boolean isSyncDeleteTable(')+'}'
STUBS['android/content/ContentValues.java']=bridge['CONTENT_VALUES'];STUBS['android/database/Cursor.java']=bridge['CURSOR'];STUBS['android/database/sqlite/SQLiteDatabase.java']=bridge['DATABASE']
RUNNER=r'''package com.zeytindefteri.app;
import android.content.*;import android.database.*;import android.database.sqlite.*;import java.util.*;
public class Run {
static int count;static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);count++;System.out.println("PASS "+name);}
static long due(ReminderRepository r,long id){try(Cursor c=r.getReminder(id)){return c.moveToFirst()?c.getLong(c.getColumnIndexOrThrow("due_at")):0;}}
static boolean exists(ReminderRepository r,long id){return due(r,id)>0;}
static int rows(Cursor c){try(Cursor close=c){int n=0;while(c.moveToNext())n++;return n;}}
static long time(int year,int month,int day,int hour,TimeZone z){Calendar c=Calendar.getInstance(z);c.clear();c.set(year,month-1,day,hour,0);return c.getTimeInMillis();}
public static void main(String[] args)throws Exception {
Context ctx=new Context();SessionManager session=new SessionManager(ctx);
try(SQLiteDatabase sql=new SQLiteDatabase(args[0],args[1],args[2])){
 DatabaseHelper.shared=sql;
 synchronized(LocalReminderScheduler.LOCK){
 session.startUserSession("A","","A");ReminderRepository r=new ReminderRepository(new DatabaseHelper(ctx));LocalReminderScheduler s=new LocalReminderScheduler(ctx);s.createChannel();s.createChannel();
 check(ctx.notifications.channels.size()==1,"idempotent channel");long now=System.currentTimeMillis();
 long id=r.addReminder(7,"Sulama","GENERAL",now+600000,"DAILY","Yerel not");check(id>0,"SQLite insert and identity");s.schedule(r,id);check(ctx.alarms.active.size()==1&&ctx.alarms.active.containsValue(now+600000),"A new record -> correct schedule");
 s.schedule(r,id);check(ctx.alarms.active.size()==1,"E repeated schedule -> one active alarm");int cancels=ctx.alarms.cancels;
 r.updateReminder(id,7,"Yeni başlık","GENERAL",now+1200000,"WEEKLY","Yeni not");s.schedule(r,id);
 check(ctx.alarms.active.size()==1&&ctx.alarms.active.containsValue(now+1200000)&&ctx.alarms.cancels>cancels,"B edit cancels old and installs one new alarm");
 try(Cursor c=r.getReminder(id)){c.moveToFirst();check(c.getLong(c.getColumnIndexOrThrow("garden_id"))==7&&c.getString(c.getColumnIndexOrThrow("owner_uid")).equals("A")&&!c.getString(c.getColumnIndexOrThrow("cloud_id")).isEmpty(),"H garden owner cloud ID preserved");}
 s.receive(id);check(ctx.notifications.posted==0,"stale/early broadcast cannot notify edited reminder");
 long fire=r.addReminder(7,"Sulama","GENERAL",now-1000,"NONE","İnternet yok");s.receive(fire);check(ctx.notifications.posted==1,"real production notification builder invoked");
 android.app.Notification notification=ctx.notifications.active.get("reminder:"+fire);
 check(notification.title.contains("Afşar")&&notification.text.contains("İnternet yok")&&notification.open.intent.extras.get("reminder_id").equals(fire),"notification content and scoped click ID");
 s.receive(fire);s.reschedule();s.receive(fire);check(ctx.notifications.posted==1,"delivery ledger prevents replay after reopen/reschedule");
 r.markReminderDone(id);s.cancel(id);s.receive(id);check(ctx.alarms.active.isEmpty()&&ctx.notifications.posted==1,"C complete cancels repeating reminder; no notification");
 long del=r.addReminder(7,"Sil","GENERAL",now+600000,"NONE","");s.schedule(r,del);check(r.deleteReminder(del),"delete succeeds");s.cancel(del);s.receive(del);check(!exists(r,del)&&ctx.alarms.active.isEmpty(),"D delete cancels alarm");
 try(Cursor c=sql.rawQuery("SELECT COUNT(*) FROM sync_tombstones WHERE entity_type='reminders'",null)){c.moveToFirst();check(c.getInt(0)==1,"existing production tombstone recorded");}
 long a=r.addReminder(7,"A özel","GENERAL",now+600000,"NONE","");s.schedule(r,a);
 session.startUserSession("B","","B");ReminderRepository b=new ReminderRepository(new DatabaseHelper(ctx));check(rows(b.getReminder(a))==0&&rows(r.getAllReminders())==0,"F other owner and stale repository cannot read");
 s.receive(a);check(ctx.notifications.active.isEmpty()&&ctx.alarms.active.isEmpty(),"account switch clears notifications and old schedules");
 check(b.updateReminder(a,8,"hack","GENERAL",now+600000,"NONE","")==0&&!b.deleteReminder(a)&&b.markReminderDone(a)==0,"foreign mutation rejected");check(b.addReminder(7,"foreign garden","GENERAL",now+600000,"NONE","")<0,"foreign garden insert rejected");
 session.startUserSession("A","","A");r=new ReminderRepository(new DatabaseHelper(ctx));
 DatabaseHelper.season=2025;check(exists(r,a),"G reminders intentionally cross report seasons (schema has no season_year)");DatabaseHelper.season=2026;
 long past=r.addReminder(7,"Ancient","GENERAL",now-86400000,"NONE","");s.schedule(r,past);s.receive(past);check(ctx.notifications.posted==1,"I old one-shot not delivered on restart");
 long repeat=r.addReminder(7,"Repeat","GENERAL",now-1000,"DAILY","");s.receive(repeat);check(due(r,repeat)>System.currentTimeMillis()&&ctx.notifications.posted==2,"J repeat advances once into future");s.receive(repeat);check(ctx.notifications.posted==2,"repeat has no immediate loop");
 long blocked=r.addReminder(null,"Denied","GENERAL",now-1000,"NONE","");ctx.permission=-1;s.receive(blocked);check(exists(r,blocked)&&ctx.notifications.posted==2,"N permission denied preserves SQLite without crash");ctx.permission=0;
 long revoked=r.addReminder(null,"Revoked","GENERAL",now-1000,"NONE","");ctx.notifications.deny=true;s.receive(revoked);check(exists(r,revoked),"permission revoked during notify does not crash or delete");ctx.notifications.deny=false;
 check(r.addReminder(7,"","GENERAL",now+1000,"NONE","")<0&&r.addReminder(7,"bad","GENERAL",0,"NONE","")<0&&r.addReminder(7,"bad","GENERAL",Long.MAX_VALUE,"NONE","")<0,"K blank/zero/overflow time rejected");
 sql.execSQL("INSERT INTO reminders(id,title,due_at,created_at,owner_uid,garden_id,repeat_rule) VALUES(500,'bad',0,1,'A',7,'UNKNOWN')");s.receive(500);check(ctx.notifications.posted==2,"malformed persisted time/rule safely ignored");
 ctx.alarms.active.clear();s.reschedule();check(ctx.alarms.active.size()==2,"L reboot reconciles only open future/repeating rows");s.reschedule();check(ctx.alarms.active.size()==2,"reboot reschedule remains idempotent");
 ctx.user.unlocked=false;ctx.alarms.active.clear();s.reschedule();check(ctx.alarms.active.isEmpty(),"locked credential storage safely deferred");ctx.user.unlocked=true;
 session.startGuestSession();ReminderRepository guest=new ReminderRepository(new DatabaseHelper(ctx));check(rows(guest.getAllReminders())==1,"null-owner guest fixture isolated");check(guest.addReminder(7,"wrong","GENERAL",now+600000,"NONE","")<0,"guest cannot attach signed-in garden");
 session.logout();s.receive(900);check(ctx.notifications.posted==2,"no session produces no notification");
 TimeZone z=TimeZone.getTimeZone("Europe/Berlin");long jan=time(2026,1,31,10,z),feb=ReminderTime.next(jan,"MONTHLY",jan,z);Calendar c=Calendar.getInstance(z);c.setTimeInMillis(feb);check(c.get(Calendar.MONTH)==1&&c.get(Calendar.DAY_OF_MONTH)==28,"monthly month-end clamp");
 long dec=time(2026,12,31,10,z);c.setTimeInMillis(ReminderTime.next(dec,"DAILY",dec,z));check(c.get(Calendar.YEAR)==2027&&c.get(Calendar.DAY_OF_MONTH)==1,"year boundary");
 long dst=time(2026,3,28,10,z);c.setTimeInMillis(ReminderTime.next(dst,"DAILY",dst,z));check(c.get(Calendar.HOUR_OF_DAY)==10&&c.get(Calendar.DAY_OF_MONTH)==29,"DST calendar day preserves local hour");
 check(ReminderTime.next(jan,"WEEKLY",jan,z)==time(2026,2,7,10,z),"weekly recurrence");check(ReminderTime.next(0,null,now,z)==0&&ReminderTime.next(now,"UNKNOWN",now,z)==0,"NULL invalid recurrence safe");
 check(ReminderTime.next(jan,"DAILY",now,z)>now,"missed repeats skip to future without notification storm");
 check(rows(sql.rawQuery("PRAGMA foreign_key_check",null))==0,"all fixture child links intact");
 System.out.println("PASS "+count+" runtime SQLite/production scheduler checks; OS test doubles, NOT Android build/device PASS");
 }
 // Let queued reconciliations finish before closing SQLite.
 LocalReminderScheduler.WORKER.submit(()->{}).get();LocalReminderScheduler.WORKER.shutdown();
}
}
}'''
with tempfile.TemporaryDirectory(prefix='zeytin-reminders-') as folder:
 t=Path(folder);db=t/'fixture.sqlite';conn=sqlite3.connect(db)
 schema=(ROOT/'app/src/main/java/DatabaseSchema.java').read_text()
 for block in re.findall(r'db\.execSQL\(\s*((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+)\)',schema):
  sql=''.join(ast.literal_eval(x) for x in re.findall(r'"(?:[^"\\]|\\.)*"',block))
  if sql.startswith('CREATE TABLE'):conn.execute(sql)
 conn.execute("INSERT INTO gardens(id,name,owner_uid,created_at) VALUES(7,'Afşar','A',1),(8,'B Bahçe','B',1),(9,'Guest',NULL,1)")
 conn.execute("INSERT INTO reminders(id,title,due_at,created_at,owner_uid,garden_id) VALUES(900,'legacy guest',9999999999999,1,NULL,9)")
 conn.commit();conn.close()
 for name in ['ReminderTime','LocalReminderScheduler','ReminderActions','ReminderReceiver','ReminderRestoreReceiver','SessionManager']:
  STUBS[f'com/zeytindefteri/app/{name}.java']=(ROOT/f'app/src/main/java/com/zeytindefteri/app/{name}.java').read_text()
 # 17:00 elek hatırlatmasının saat hesabı (AlertRules.nextAt) tests/alerts'ta sınanır; burada yalnız çağrıldığı yer tutulur.
 assert 'ElekReminder.schedule(app);' in STUBS['com/zeytindefteri/app/ReminderRestoreReceiver.java'], 'açılışta elek hatırlatması yeniden kurulmuyor'
 STUBS['com/zeytindefteri/app/ElekReminder.java']='package com.zeytindefteri.app;\nfinal class ElekReminder{static int calls;static void schedule(android.content.Context c){calls++;}}\n'
 STUBS['com/zeytindefteri/app/ReminderRepository.java']=(ROOT/'app/src/main/java/ReminderRepository.java').read_text();STUBS['com/zeytindefteri/app/Run.java']=RUNNER
 for name,content in STUBS.items():p=t/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(content)
 subprocess.run(['java','com.sun.tools.javac.Main','--release','8','-encoding','UTF-8','-d',str(t/'classes')]+[str(x) for x in t.rglob('*.java')],check=True)
 subprocess.run(['java','-cp',str(t/'classes'),'com.zeytindefteri.app.Run',sys.executable,str(Path(__file__).resolve()),str(db)],check=True,timeout=45)
