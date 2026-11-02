package com.zeytindefteri.app;
import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import org.json.*;

/** Yalnız metadata. noBackupFilesDir: Android otomatik yedeğine ve TezgahBackup'a girmez. */
public final class TezgahWebSessions {
    private TezgahWebSessions(){}
    private static AtomicFile file(Context c,String owner){String key=TezgahWebCrypto.base64(TezgahWebCrypto.sha256(TezgahWebProtocol.utf8(owner)));return new AtomicFile(new File(c.getNoBackupFilesDir(),"tezgah-web-"+key+".json"));}
    public static synchronized JSONArray list(Context c,String owner){try{return new JSONArray(new String(file(c,owner).readFully(),java.nio.charset.StandardCharsets.UTF_8));}catch(Exception e){return new JSONArray();}}
    public static void update(Context c,String owner,String id,String name,boolean active){update(c,owner,id,name,active,!active);}
    public static synchronized void update(Context c,String owner,String id,String name,boolean active,boolean revoked){
        JSONArray old=list(c,owner),out=new JSONArray();long began=System.currentTimeMillis();
        for(int i=0;i<old.length();i++){JSONObject row=old.optJSONObject(i);if(row==null)continue;if(id.equals(row.optString("id")))began=row.optLong("baslangic",began);else if(out.length()<19)out.put(row);}
        out.put(TezgahWebDto.json("id",id,"ad",name,"baslangic",began,"sonGorulme",System.currentTimeMillis(),"aktif",active,"iptal",revoked));
        AtomicFile f=file(c,owner);FileOutputStream stream=null;try{stream=f.startWrite();stream.write(out.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));f.finishWrite(stream);}catch(IOException e){if(stream!=null)f.failWrite(stream);}
    }
}
