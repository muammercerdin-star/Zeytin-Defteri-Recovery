package com.zeytindefteri.app;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.io.*;
import java.util.*;
public final class PhoneBridge {
    public static void main(String[] args)throws Exception{
        SQLiteDatabase db=new SQLiteDatabase(args[0],args[1]);TezgahPurchases.ensure(db);
        db.execSQL("INSERT INTO tezgah_purchases(owner_uid,record_date,seller_name,total_kg,gross_total,amount_paid,created_at) VALUES ('A','2026-11-03','Java Uretici',10,100,25,1)");
        BufferedReader in=new BufferedReader(new InputStreamReader(System.in,"UTF-8"));ArrayList<String> out=new ArrayList<>();TezgahWebPhone phone=null;
        for(String line;(line=in.readLine())!=null;){out.clear();JSONObject command=new JSONObject(line);String error=null;
            try{
                String cmd=command.getString("cmd");
                if(cmd.equals("qr")){if(phone!=null)phone.close();TezgahWebPhone.Qr q=TezgahWebPhone.Qr.parse(command.getString("text"),System.currentTimeMillis()/1000);phone=new TezgahWebPhone(q,new TezgahWebRepository(db,"A"),out::add,v->{});phone.opened();}
                else if(cmd.equals("approve"))phone.approve();
                else if(cmd.equals("receive"))phone.receive(command.getString("frame"),System.nanoTime()/1000000);
                else if(cmd.equals("change"))phone.changed(2);
                else if(cmd.equals("lost"))phone.disconnected(System.nanoTime()/1000000);
                else if(cmd.equals("close")){phone.close();break;}
            }catch(Exception e){error=e.getMessage()==null?"ERROR":e.getMessage();}
            JSONArray messages=new JSONArray();for(String m:out)messages.put(new JSONObject(m));System.out.println(TezgahWebDto.json("frames",messages,"error",error));System.out.flush();
        }
    }
}
