package com.zeytindefteri.app;
import android.database.sqlite.SQLiteDatabase;
public class NotificationInboxTest {
  static int n; static void ok(boolean c,String m){ if(!c) throw new AssertionError(m); n++; }
  public static void main(String[] a){
    SQLiteDatabase db=new SQLiteDatabase(a[0],a[1]);
    AlertRules.Alert f=new AlertRules.Alert(AlertRules.WEATHER,"frost:1:2026-10-22",3,"Don","Bu gece");
    ok(NotificationInbox.add(db,"u",f,1000)>0,"ilk ekleme");
    ok(NotificationInbox.add(db,"u",f,2000)==-1,"aynı anahtar tekrar eklenmez");
    ok(NotificationInbox.add(db,"v",f,2000)>0,"başka hesap ayrı");
    NotificationInbox.add(db,"u",AlertRules.calendar(2026,10),3000);
    ok(NotificationInbox.unread(db,"u")==2,"okunmamış 2");
    ok(NotificationInbox.list(db,"u",10).get(0).key.equals("cal:2026-10"),"yeniden eskiye");
    NotificationInbox.markAllRead(db,"u");
    ok(NotificationInbox.unread(db,"u")==0 && NotificationInbox.unread(db,"v")==1,"okundu yalnız bu hesapta");
    ok(NotificationInbox.list(db,"u",10).get(0).read,"okundu işareti");
    NotificationInbox.prune(db,1000+NotificationInbox.KEEP_MS+1500);
    ok(NotificationInbox.list(db,"u",10).size()==1,"eski kayıt temizlendi");
    NotificationInbox.delete(db,"u",NotificationInbox.list(db,"u",10).get(0).id);
    ok(NotificationInbox.list(db,"u",10).isEmpty(),"silme");
    System.out.println("PASS: "+n+" bildirim kutusu kontrolü");
  }
}
