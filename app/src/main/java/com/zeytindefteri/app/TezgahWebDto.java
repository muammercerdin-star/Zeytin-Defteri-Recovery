package com.zeytindefteri.app;
import org.json.*;
import java.math.*;
/** Para/kilo wire'da noktalı metin; telefon numarası ve SQLite owner web'e gönderilmez. */
public final class TezgahWebDto {
    private TezgahWebDto(){}
    public static JSONObject json(Object... pairs){
        try{JSONObject j=new JSONObject();for(int i=0;i<pairs.length;i+=2)j.put((String)pairs[i],pairs[i+1]==null?JSONObject.NULL:pairs[i+1]);return j;}
        catch(JSONException e){throw new IllegalArgumentException("VERI_KODLANAMADI");}
    }
    static String decimal(double value,int scale){return BigDecimal.valueOf(value).setScale(scale,RoundingMode.HALF_UP).toPlainString();}
    public static JSONObject purchase(TezgahPurchases.Purchase p){return json("id",Long.toString(p.id),"uretici",p.seller,"tarih",p.date,"netKg",decimal(p.kg,1),"kasa",p.kasa,"tutarTL",decimal(p.gross,2),"odenenTL",decimal(p.paid,2),"kalanTL",decimal(p.remaining(),2),"zId",p.zId>0?Long.toString(p.zId):null);}
    public static JSONObject totals(TezgahPurchases.Day d){return json("count",d.count,"netKg",decimal(d.kg,1),"kasa",d.kasa,"tutarTL",decimal(d.gross,2),"odenenTL",decimal(d.paid,2),"kalanTL",decimal(d.remaining(),2));}
}
