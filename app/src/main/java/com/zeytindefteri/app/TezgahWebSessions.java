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
        boolean full = false;
        JSONArray others = new JSONArray();
        String diagnosis = "";
        for (int i = 0; i < old.length(); i++) {
            JSONObject row = old.optJSONObject(i);
            if (row == null) continue;
            if (id.equals(row.optString("id"))) {
                began = row.optLong("baslangic", began);
                full = row.optBoolean("tamKullanim");
                diagnosis = row.optString("tani");
            } else others.put(row);
        }
        for (int i = Math.max(0, others.length() - 19); i < others.length(); i++) out.put(others.optJSONObject(i));
        out.put(TezgahWebDto.json("id",id,"ad",name,"baslangic",began,"sonGorulme",System.currentTimeMillis(),"aktif",active,"iptal",revoked,"tamKullanim",full,"tani",diagnosis));
        AtomicFile f=file(c,owner);FileOutputStream stream=null;try{stream=f.startWrite();stream.write(out.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));f.finishWrite(stream);}catch(IOException e){if(stream!=null)f.failWrite(stream);}
    }
    public static synchronized void permission(Context c, String owner, String id, boolean full) {
        JSONArray rows = list(c, owner);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row != null && id.equals(row.optString("id"))) {
                try {
                    row.put("tamKullanim", full);
                } catch (JSONException ignored) { }
            }
        }
        AtomicFile f = file(c, owner);
        FileOutputStream stream = null;
        try {
            stream = f.startWrite();
            stream.write(rows.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            f.finishWrite(stream);
        } catch (IOException e) {
            if (stream != null) f.failWrite(stream);
        }
    }

    public static synchronized JSONArray recent(Context c, String owner) {
        JSONArray rows = list(c, owner);
        JSONArray out = new JSONArray();
        int closed = 0;
        for (int i = rows.length() - 1; i >= 0; i--) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null) continue;
            if (TezgahWebService.running(owner, row.optString("id")) || row.optBoolean("kayitli") || closed++ < 3) out.put(row);
        }
        return out;
    }

    public static synchronized void clearHistory(Context c, String owner) {
        JSONArray rows = list(c, owner);
        JSONArray out = new JSONArray();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row != null && (row.optBoolean("kayitli") || TezgahWebService.running(owner, row.optString("id")))) out.put(row);
        }
        write(c, owner, out);
    }

    public static synchronized void diagnosis(Context c, String owner, String id, String detail) {
        JSONArray rows = list(c, owner);
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i);
            if (row == null || !id.equals(row.optString("id"))) continue;
            try {
                row.put("tani", detail);
            } catch (JSONException ignored) { }
        }
        write(c, owner, rows);
    }

    private static void write(Context c, String owner, JSONArray rows) {
        AtomicFile f = file(c, owner);
        FileOutputStream stream = null;
        try {
            stream = f.startWrite();
            stream.write(rows.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            f.finishWrite(stream);
        } catch (IOException e) {
            if (stream != null) f.failWrite(stream);
        }
    }
}
