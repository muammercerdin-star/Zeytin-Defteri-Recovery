package com.zeytindefteri.app;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.text.*;
import java.util.*;

/** Tek telefon owner'ına bağlı salt okuma. Owner, SQL ve tablo adı istekten alınmaz. */
public final class TezgahWebRepository implements AutoCloseable {
    private final SQLiteDatabase db;private final String owner;
    private final Map<String,Snapshot> snapshots=new LinkedHashMap<>();private boolean closed;
    private static final class Snapshot {String scope,date,asOf;long at,revision;List<JSONObject> rows=new ArrayList<>();JSONObject totals;}
    public TezgahWebRepository(SQLiteDatabase db,String owner){if(db==null||owner==null)throw new IllegalArgumentException("OTURUM_YOK");this.db=db;this.owner=owner;}
    public synchronized JSONObject read(JSONObject args,long now)throws Exception{
        if(closed)throw new IllegalArgumentException("OTURUM_IPTAL");
        for(Iterator<String> it=args.keys();it.hasNext();)if(!Arrays.asList("kapsam","tarih","limit","cursor").contains(it.next()))throw new IllegalArgumentException("GECERSIZ_ISTEK");
        String scope=args.optString("kapsam","acik_donem"),date=args.optString("tarih","");
        if(!scope.equals("acik_donem")&&!scope.equals("takvim"))throw new IllegalArgumentException("GECERSIZ_ISTEK");
        if(scope.equals("takvim")){SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.US);f.setLenient(false);if(!date.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")||f.parse(date)==null)throw new IllegalArgumentException("GECERSIZ_TARIH");}
        else if(!date.isEmpty())throw new IllegalArgumentException("GECERSIZ_ISTEK");
        int limit=args.optInt("limit",100);if(args.has("limit")&&(!(args.get("limit") instanceof Number)||((Number)args.get("limit")).doubleValue()!=limit))throw new IllegalArgumentException("GECERSIZ_LIMIT");if(limit<1||limit>200)throw new IllegalArgumentException("GECERSIZ_LIMIT");
        snapshots.entrySet().removeIf(e->now-e.getValue().at>60000);
        String cursor=args.isNull("cursor")?"":args.optString("cursor","");String id;int start=0;Snapshot s;
        if(cursor.isEmpty()){
            List<TezgahPurchases.Purchase> list=scope.equals("acik_donem")?TezgahPurchases.open(db,owner):TezgahPurchases.forDay(db,owner,date);
            s=new Snapshot();s.scope=scope;s.date=date;s.at=now;s.revision=TezgahWebChanges.revision();
            s.asOf=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX",Locale.US).format(new Date(now));
            int bytes=0;for(TezgahPurchases.Purchase p:list){JSONObject row=TezgahWebDto.purchase(p);bytes+=TezgahWebProtocol.utf8(row.toString()).length;if(bytes>10*1024*1024)throw new IllegalArgumentException("ARSIV_COK_BUYUK");s.rows.add(row);}
            s.totals=TezgahWebDto.totals(TezgahPurchases.sum(list));id=TezgahWebCrypto.base64(TezgahWebCrypto.random(16));
            if(snapshots.size()>=2)snapshots.remove(snapshots.keySet().iterator().next());snapshots.put(id,s);
        }else{
            if(!cursor.matches("[A-Za-z0-9_-]{22}:[0-9]{1,7}"))throw new IllegalArgumentException("SNAPSHOT_GECERSIZ");
            String[] f=cursor.split(":");id=f[0];start=Integer.parseInt(f[1]);s=snapshots.get(id);
            if(s==null||!s.scope.equals(scope)||!s.date.equals(date)||start>s.rows.size())throw new IllegalArgumentException("SNAPSHOT_GECERSIZ");
        }
        JSONArray items=new JSONArray();int end=start,bytes=0;
        while(end<s.rows.size()&&end<start+limit){JSONObject row=s.rows.get(end);int n=TezgahWebProtocol.utf8(row.toString()).length;if(n>24000)throw new IllegalArgumentException("KAYIT_COK_BUYUK");if(bytes+n>24000)break;items.put(row);bytes+=n;end++;}
        boolean complete=end==s.rows.size();return TezgahWebDto.json("kapsam",scope,"snapshot",id,"sourceEpoch",TezgahWebChanges.SOURCE,"revision",Long.toString(s.revision),"asOf",s.asOf,"timezone",TimeZone.getDefault().getID(),"items",items,"totals",s.totals,"nextCursor",complete?null:id+":"+end,"complete",complete);
    }
    public synchronized void close(){closed=true;snapshots.clear();}
}
