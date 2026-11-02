package com.zeytindefteri.app;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class PhoneTest {
    static int n;static void ok(boolean b,String m){n++;if(!b)throw new AssertionError(m);}
    static JSONObject args(String scope,String day,int limit,Object cursor){return TezgahWebDto.json("kapsam",scope,"tarih",day,"limit",limit,"cursor",cursor);}
    static void reject(Run r,String m){try{r.run();}catch(Exception e){ok(true,m);return;}throw new AssertionError(m);}
    interface Run{void run()throws Exception;}
    public static void main(String[] args)throws Exception{
        SQLiteDatabase db=new SQLiteDatabase(args[0],args[1]);TezgahPurchases.ensure(db);TezgahPayments.ensure(db);
        db.execSQL("WITH RECURSIVE x(n) AS (SELECT 1 UNION ALL SELECT n+1 FROM x WHERE n<210) INSERT INTO tezgah_purchases(owner_uid,record_date,seller_name,total_kg,total_kasa,gross_total,amount_paid,data,created_at,z_id) SELECT 'A','2026-11-02','Uretici '||n,10,1,100,20,'',n,0 FROM x");
        db.execSQL("INSERT INTO tezgah_purchases(owner_uid,record_date,seller_name,total_kg,gross_total,amount_paid,z_id,created_at) VALUES ('A','2026-11-03','Zli',10,100,20,9,211),('B','2026-11-02','Baska hesap',99,9999,0,0,212)");
        TezgahWebRepository repo=new TezgahWebRepository(db,"A");long now=System.currentTimeMillis();
        JSONObject first=repo.read(args("acik_donem","",100,null),now);ok(first.getJSONArray("items").length()==100,"page100");ok(!first.getBoolean("complete"),"not complete");ok(first.getJSONObject("totals").getInt("count")==210,"owner isolation and no Z");ok(first.getJSONObject("totals").getString("kalanTL").equals("16800.00"),"totals text");
        JSONObject second=repo.read(args("acik_donem","",100,first.getString("nextCursor")),now+1);ok(second.getJSONArray("items").length()==100,"page2");
        JSONObject third=repo.read(args("acik_donem","",100,second.getString("nextCursor")),now+2);ok(third.getJSONArray("items").length()==10&&third.getBoolean("complete")&&third.isNull("nextCursor"),"page3 complete");
        JSONObject calendar=repo.read(args("takvim","2026-11-03",200,null),now+3);ok(calendar.getJSONArray("items").length()==1,"calendar includes Z");ok(calendar.getJSONArray("items").getJSONObject(0).getString("uretici").equals("Zli"),"calendar not open period");
        JSONObject empty=new TezgahWebRepository(db,"EMPTY").read(args("acik_donem","",100,null),now);ok(empty.getBoolean("complete")&&empty.getJSONArray("items").length()==0,"empty is success");
        reject(()->repo.read(args("acik_donem","",201,null),now),"max page limit");
        reject(()->repo.read(TezgahWebDto.json("owner","B"),now),"cannot choose owner");
        reject(()->repo.read(args("takvim","2026-02-31",100,null),now),"invalid calendar date");
        reject(()->repo.read(args("acik_donem","",100,first.getString("nextCursor")),now+61000),"snapshot TTL");
        ok(TezgahPayments.pay(db,"A",1,30,"2026-11-03",300),"payment succeeds");
        TezgahWebRepository paidRepo=new TezgahWebRepository(db,"A");JSONObject paid=paidRepo.read(args("acik_donem","",200,null),now);
        ok(paid.getJSONObject("totals").getString("odenenTL").equals("4230.00"),"subsequent payment counted once");ok(paid.getJSONObject("totals").getString("kalanTL").equals("16770.00"),"remaining includes payment");
        for(int i=0;i<paid.getJSONArray("items").length();i++)ok(!paid.getJSONArray("items").getJSONObject(i).has("phone")&&!paid.getJSONArray("items").getJSONObject(i).has("owner"),"no phone or owner DTO");
        AtomicInteger events=new AtomicInteger(),resets=new AtomicInteger();TezgahWebChanges.Listener l=new TezgahWebChanges.Listener(){public void changed(String o,long r){if(o.equals("A"))events.incrementAndGet();}public void reset(String o){resets.incrementAndGet();}};
        TezgahWebChanges.listen(l);ok(TezgahPayments.pay(db,"A",2,1,"2026-11-03",400),"real mutation hook");Thread.sleep(650);ok(events.get()==1,"payment hook fires after commit");events.set(0);for(int i=0;i<5;i++)TezgahWebChanges.committed(db,"A");Thread.sleep(650);ok(events.get()==1,"400ms coalescing");TezgahWebChanges.reset("A");ok(resets.get()==1,"reset event");TezgahWebChanges.remove(l);
        db.execSQL("DROP TABLE tezgah_purchases");
        // ensure() yeniden kurduğundan, gerçek SQL hatası için bozuk şema oluştur.
        db.execSQL("CREATE TABLE tezgah_purchases(id INTEGER PRIMARY KEY)");
        reject(()->new TezgahWebRepository(db,"A").read(args("acik_donem","",100,null),now),"SQL failure is not empty success");
        repo.close();reject(()->repo.read(new JSONObject(),now),"closed repository");
        System.out.println("PASS "+n+"/"+n+" web phone SQLite/debounce");
    }
}
