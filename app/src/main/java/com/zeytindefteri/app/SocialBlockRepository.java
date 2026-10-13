package com.zeytindefteri.app;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.*;
import org.json.JSONObject;
import java.util.*;

/** Per-account, server-confirmed block list. Never changes social records. */
public final class SocialBlockRepository {
    public interface Listener { void changed(boolean serverConfirmed); }
    public interface Completion { void complete(boolean success); }
    public static final class Entry {
        public final String uid, name, region;
        Entry(String uid,String name,String region) { this.uid=uid;this.name=name;this.region=region; }
    }
    private final String owner;
    private final SharedPreferences prefs;
    private final Map<String,Entry> entries=new LinkedHashMap<>();
    private ListenerRegistration registration;
    private FirebaseAuth.AuthStateListener authListener;
    private Listener listener;
    public SocialBlockRepository(Context context) {
        FirebaseUser user=FirebaseAuth.getInstance().getCurrentUser();
        owner=user==null?"":user.getUid();
        prefs=context.getApplicationContext().getSharedPreferences("zeytin_user_blocks",Context.MODE_PRIVATE);
        try {
            JSONObject stored=new JSONObject(prefs.getString("confirmed_"+owner,"{}"));
            Iterator<String> ids=stored.keys();
            while(ids.hasNext()) {String id=ids.next();JSONObject e=stored.getJSONObject(id);
                if(SocialReportTarget.validId(id)&&!id.equals(owner))entries.put(id,new Entry(id,e.optString("name","Üretici"),e.optString("region","")));}
        } catch(Exception ignored) { entries.clear(); }
    }
    public boolean isCurrent() {
        FirebaseUser user=FirebaseAuth.getInstance().getCurrentUser();
        return user!=null&&owner.equals(user.getUid());
    }
    public boolean canManage() {
        FirebaseUser user=FirebaseAuth.getInstance().getCurrentUser();
        return isCurrent()&&user!=null&&!user.isAnonymous();
    }
    public boolean blocked(String uid) { return isCurrent() && entries.containsKey(uid); }
    public java.util.Set<String> blockedIds() { return isCurrent()?new java.util.HashSet<>(entries.keySet()):new java.util.HashSet<>(); }
    public boolean hasBlocks() { return isCurrent()&&!entries.isEmpty(); }
    public List<Entry> list() { return isCurrent()?new ArrayList<>(entries.values()):new ArrayList<>(); }
    private CollectionReference collection() {return FirebaseFirestore.getInstance().collection("user_blocks").document(owner).collection("items");}
    public void start(Listener listener) {
        stop(); this.listener=listener;
        if(!canManage()){listener.changed(false);return;}
        authListener=auth->{if(!isCurrent()){entries.clear();listener.changed(false);}};
        FirebaseAuth.getInstance().addAuthStateListener(authListener);
        try {
            registration=collection().addSnapshotListener(MetadataChanges.INCLUDE,(snapshot,error)->{
                if(!isCurrent())return;
                if(error!=null||snapshot==null){listener.changed(false);return;}
                // Firestore's optimistic writes/deletes must not alter the confirmed UI cache.
                if(snapshot.getMetadata().hasPendingWrites()||snapshot.getMetadata().isFromCache())return;
                entries.clear();
                for(DocumentSnapshot doc:snapshot.getDocuments()) {
                    String id=doc.getId();
                    if(!id.equals(owner)&&SocialReportTarget.validId(id))
                        entries.put(id,new Entry(id,text(doc.getString("display_name")),text(doc.getString("region"))));
                }
                persist();listener.changed(true);
            });
        } catch(RuntimeException e){listener.changed(false);}
    }
    public void stop() {
        if(registration!=null)registration.remove();registration=null;
        if(authListener!=null)FirebaseAuth.getInstance().removeAuthStateListener(authListener);authListener=null;listener=null;
    }
    public void change(String blockedUid,String name,String region,boolean remove,Completion callback) {
        if(!canManage()||!SocialReportTarget.validId(blockedUid)||owner.equals(blockedUid)){callback.complete(false);return;}
        final Entry entry=new Entry(blockedUid,text(name),text(region));
        try {
            DocumentReference ref=collection().document(blockedUid);
            FirebaseFirestore.getInstance().runTransaction(tx->{
                DocumentSnapshot current=tx.get(ref);
                if(remove){if(current.exists())tx.delete(ref);}
                else if(!current.exists()) {
                    Map<String,Object> data=new HashMap<>();
                    data.put("blocker_uid",owner);data.put("blocked_uid",blockedUid);
                    data.put("created_at",FieldValue.serverTimestamp());
                    data.put("display_name",entry.name);data.put("region",entry.region);tx.set(ref,data);
                }
                return true;
            }).addOnCompleteListener(task->{
                if(!isCurrent()){callback.complete(false);return;}
                if(task.isSuccessful()) {
                    if(remove)entries.remove(blockedUid);else entries.put(blockedUid,entry);
                    persist();if(listener!=null)listener.changed(true);
                }
                callback.complete(task.isSuccessful());
            });
        }catch(RuntimeException e){callback.complete(false);}
    }
    private void persist() {
        JSONObject data=new JSONObject();
        try {for(Entry entry:entries.values()) {JSONObject value=new JSONObject();value.put("name",entry.name);value.put("region",entry.region);data.put(entry.uid,value);}
            prefs.edit().putString("confirmed_"+owner,data.toString()).apply();
        }catch(Exception ignored){}
    }
    private static String text(String value){if(value==null)return "";return value.length()>200?value.substring(0,200):value;}
}
