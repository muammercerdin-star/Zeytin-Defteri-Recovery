package com.zeytindefteri.app;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import com.zeytindefteri.app.GardenAnalyticsSnapshot.Builder;

/** Local-only report source. Fixed query count; no Firebase or session mutation. */
public final class GardenAnalyticsRepository {
    private final DatabaseHelper helper;
    public GardenAnalyticsRepository(DatabaseHelper helper) { this.helper=helper; }
    public static final class Result {
        public final SeasonAnalyticsSnapshot current,previous;
        Result(SeasonAnalyticsSnapshot current,SeasonAnalyticsSnapshot previous) {this.current=current;this.previous=previous;}
    }
    public Result load(String owner,int year,boolean compare) {
        if(owner==null || year<1901 || year>9999) throw new IllegalArgumentException("Geçersiz analiz kapsamı");
        SQLiteDatabase db=helper.getReadableDatabase();
        db.beginTransactionNonExclusive();
        try {
            Map<Long,Builder> metadata=new LinkedHashMap<>();
            Query q=new Query(owner,year);
            try(Cursor c=q.run(db,"SELECT g.id,g.name,g.variety,g.tree_count,g.area_decare FROM gardens g WHERE "
                    +q.owner("g.owner_uid")+" ORDER BY g.name COLLATE NOCASE,g.id")) {
                while(c.moveToNext()) {
                    Builder b=new Builder(); b.id=lng(c,"id");b.name=str(c,"name");b.variety=str(c,"variety");
                    b.gardens=1;b.trees=num(c,"tree_count");b.area=num(c,"area_decare");
                    b.completeTrees=b.trees>0;b.completeArea=b.area>0;metadata.put(b.id,b);
                }
            }
            SeasonAnalyticsSnapshot current=season(db,owner,year,metadata);
            SeasonAnalyticsSnapshot previous=compare?season(db,owner,year-1,metadata):null;
            db.setTransactionSuccessful();return new Result(current,previous);
        } finally {db.endTransaction();}
    }
    private SeasonAnalyticsSnapshot season(SQLiteDatabase db,String uid,int year,Map<Long,Builder> metadata) {
        Map<Long,Builder> values=new LinkedHashMap<>();
        for(Builder m:metadata.values()) {
            Builder b=new Builder(); b.id=m.id;b.name=m.name;b.variety=m.variety;b.gardens=1;b.trees=m.trees;b.area=m.area;
            b.completeTrees=m.completeTrees;b.completeArea=m.completeArea;values.put(b.id,b);
        }
        Builder unassigned=new Builder();
        Query q=new Query(uid,year);
        try(Cursor c=q.run(db,"SELECT o.garden_id,COUNT(*) AS records,SUM(COALESCE(o.cost,0)) AS cost "
                +"FROM operations o INNER JOIN gardens g ON g.id=o.garden_id WHERE "
                +q.scope("o","operation_date")+" AND o.type <> 'Hasat' GROUP BY o.garden_id")) {
            while(c.moveToNext()) {Builder b=values.get(lng(c,"garden_id"));b.expense+=num(c,"cost");b.expenseRecords+=(int)lng(c,"records");}
        }
        q=new Query(uid,year);
        try(Cursor c=q.run(db,"SELECT h.garden_id,COUNT(*) AS records,SUM(COALESCE(h.total_kg,0)) AS kg,"
                +"SUM(COALESCE(h.labor_cost,0)+COALESCE(h.transport_cost,0)+COALESCE(h.other_cost,0)) AS cost "
                +"FROM harvests h INNER JOIN gardens g ON g.id=h.garden_id WHERE "
                +q.scope("h","harvest_date")+" GROUP BY h.garden_id")) {
            while(c.moveToNext()) {Builder b=values.get(lng(c,"garden_id"));b.harvest=num(c,"kg");b.harvestRecords=(int)lng(c,"records");b.expense+=num(c,"cost");b.expenseRecords+=b.harvestRecords;}
        }
        q=new Query(uid,year);
        try(Cursor c=q.run(db,"SELECT b.garden_id,COUNT(*) AS records,SUM(COALESCE(b.salt_cost,0)+COALESCE(b.packaging_cost,0)+COALESCE(b.other_cost,0)) AS cost,"
                +"SUM(COALESCE(b.olive_kg,0)) AS olives,SUM(COALESCE(b.ready_kg,0)) AS ready,SUM(CASE WHEN b.status='ACTIVE' THEN 1 ELSE 0 END) AS active "
                +"FROM brine_batches b LEFT JOIN gardens g ON g.id=b.garden_id WHERE "+q.brine()+" GROUP BY b.garden_id")) {
            while(c.moveToNext()) {Builder b=target(c,values,unassigned);b.brineCost=num(c,"cost");b.expense+=b.brineCost;b.expenseRecords+=(int)lng(c,"records");b.brineOlive=num(c,"olives");b.ready=num(c,"ready");b.activeBrine=(int)lng(c,"active");}
        }
        q=new Query(uid,year);
        try(Cursor c=q.run(db,"SELECT b.garden_id,SUM(COALESCE(e.cost,0)) AS cost FROM brine_events e "
                +"INNER JOIN brine_batches b ON b.id=e.batch_id LEFT JOIN gardens g ON g.id=b.garden_id WHERE "
                +q.brine()+" GROUP BY b.garden_id")) {
            while(c.moveToNext()) {Builder b=target(c,values,unassigned);double cost=num(c,"cost");b.brineCost+=cost;b.expense+=cost;}
        }
        // Receivable rows mirror a sale total, not additive payments. Latest owned row per sale wins.
        Map<Long,Receipt> receipts=new LinkedHashMap<>();
        q=new Query(uid,year);
        try(Cursor c=q.run(db,"SELECT r.* FROM receivables r LEFT JOIN sales s ON s.id=r.related_sale_id "
                +"LEFT JOIN gardens g ON g.id=s.garden_id WHERE "+q.owner("r.owner_uid")
                +" AND ((s.id IS NOT NULL AND s.sale_kind='SALE' AND "+q.scope("s","sale_date")+") "
                +"OR (r.related_sale_id IS NULL AND CAST(strftime('%Y',r.created_at/1000,'unixepoch') AS INTEGER)="+year+")) "
                +"ORDER BY COALESCE(r.updated_at,0) DESC,r.created_at DESC,r.id DESC")) {
            while(c.moveToNext()) {
                Receipt r=new Receipt();r.amount=num(c,"amount");r.received=num(c,"total_received");r.open="OPEN".equals(str(c,"status"));
                long saleId=lng(c,"related_sale_id");
                if(saleId<=0) {unassigned.receivable+=r.balance();}
                else if(!receipts.containsKey(saleId)) receipts.put(saleId,r);
            }
        }
        q=new Query(uid,year);
        try(Cursor c=q.run(db,"SELECT s.* FROM sales s INNER JOIN gardens g ON g.id=s.garden_id WHERE "
                +q.scope("s","sale_date")+" AND s.sale_kind='SALE'")) {
            while(c.moveToNext()) {
                Builder b=values.get(lng(c,"garden_id"));
                double gross=num(c,"gross_total"),net=gross-num(c,"transport_cost")-num(c,"deductions");
                b.saleRecords++;b.gross+=gross;b.revenue+=net;b.sold+=num(c,"total_kg");
                Receipt r=receipts.get(lng(c,"id"));
                b.collected+=r==null?num(c,"amount_received"):r.received;
                b.receivable+=r==null?Math.max(0,net-num(c,"amount_received")):r.balance();
            }
        }
        // Grade rows do not join sales: an estimated Elek value is never counted as sale revenue.
        q=new Query(uid,year);
        try(Cursor c=q.run(db,"SELECT er.garden_id,COUNT(DISTINCT er.id) AS records,"
                +"SUM(CASE WHEN eg.grade='DOUBLE' THEN COALESCE(eg.kg,0) ELSE 0 END) AS d,"
                +"SUM(CASE WHEN eg.grade='1' THEN COALESCE(eg.kg,0) ELSE 0 END) AS g1,"
                +"SUM(CASE WHEN eg.grade='2' THEN COALESCE(eg.kg,0) ELSE 0 END) AS g2,"
                +"SUM(CASE WHEN eg.grade='3' THEN COALESCE(eg.kg,0) ELSE 0 END) AS g3,"
                +"SUM(CASE WHEN eg.grade='4' THEN COALESCE(eg.kg,0) ELSE 0 END) AS g4,"
                +"SUM(CASE WHEN eg.grade='YAGLIK' THEN COALESCE(eg.kg,0) ELSE 0 END) AS oil "
                +"FROM elek_records er INNER JOIN gardens g ON g.id=er.garden_id LEFT JOIN elek_grades eg ON eg.elek_id=er.id "
                +"WHERE "+q.scope("er","record_date")+" GROUP BY er.garden_id")) {
            while(c.moveToNext()) {Builder b=values.get(lng(c,"garden_id"));b.elekRecords=(int)lng(c,"records");String[] cols={"d","g1","g2","g3","g4","oil"};for(int i=0;i<6;i++)b.grades[i]=num(c,cols[i]);}
        }
        Builder total=new Builder();total.id=-1;total.name="Tüm Bahçeler";
        Map<Long,GardenAnalyticsSnapshot> gardens=new LinkedHashMap<>();
        for(Builder b:values.values()) {total.add(b);gardens.put(b.id,new GardenAnalyticsSnapshot(b,year));}
        total.add(unassigned);total.unassignedCost=unassigned.expense;total.unassignedReceivable=unassigned.receivable;
        return new SeasonAnalyticsSnapshot(new GardenAnalyticsSnapshot(total,year),gardens);
    }
    private static Builder target(Cursor c,Map<Long,Builder> values,Builder unassigned) {
        return c.isNull(c.getColumnIndexOrThrow("garden_id"))?unassigned:values.get(lng(c,"garden_id"));
    }
    private static final class Receipt {
        double amount,received;boolean open;
        double balance() {return open?Math.max(0,amount-received):0;}
    }
    private static String str(Cursor c,String col) {String value=c.getString(c.getColumnIndexOrThrow(col));return value==null?"":value;}
    private static double num(Cursor c,String col) {
        int index=c.getColumnIndexOrThrow(col);double value=c.isNull(index)?0:c.getDouble(index);
        if(!Double.isFinite(value)) throw new IllegalArgumentException("Geçersiz sayısal kayıt: "+col);return value;
    }
    private static long lng(Cursor c,String col) {return c.getLong(c.getColumnIndexOrThrow(col));}
    private static final class Query {
        final String uid;final int year;final ArrayList<String> args=new ArrayList<>();
        Query(String uid,int year) {this.uid=uid;this.year=year;}
        String owner(String column) {
            if(uid.isEmpty()) return "("+column+" IS NULL OR TRIM("+column+")='')";
            args.add(uid);return column+"=?";
        }
        String child(String alias) {args.add(uid);return "(COALESCE("+alias+".owner_uid,'')='' OR "+alias+".owner_uid=?)";}
        String season(String alias,String date) {
            // Exact existing DatabaseHelper activeSeasonCondition semantics, explicit year parameter.
            String s=alias+".season_year",d=alias+"."+date;
            return "(COALESCE("+s+",0)="+year+" OR (COALESCE("+s+",0)=0 AND (substr(TRIM("+d+"),-4)='"+year+"' OR substr(TRIM("+d+"),1,4)='"+year+"')))";
        }
        String scope(String alias,String date) {return owner("g.owner_uid")+" AND "+child(alias)+" AND "+season(alias,date);}
        String brine() {return owner("b.owner_uid")+" AND (b.garden_id IS NULL OR "+owner("g.owner_uid")+") AND "+season("b","start_date");}
        Cursor run(SQLiteDatabase db,String sql) {return db.rawQuery(sql,args.toArray(new String[0]));}
    }
}
