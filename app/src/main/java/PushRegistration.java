package com.zeytindefteri.app;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Keeps only application context. Data-only pushes are bound to a UID and login session. */
public final class PushRegistration {
    private static Context context;
    private static FirebaseAuth.AuthStateListener authListener;
    private PushRegistration() { }
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("zeytin_push",Context.MODE_PRIVATE); }
    public static void init(Context c) {
        context=c.getApplicationContext();
        ZeytinMessagingService.ensureChannel(context);
        if (authListener != null) return;
        authListener=auth -> {
            FirebaseUser user=auth.getCurrentUser();
            String uid=user==null?"":user.getUid();
            synchronized(PushRegistration.class) {
                SharedPreferences p=prefs(context);
                if (!uid.equals(p.getString("uid",""))) {
                    p.edit().putString("uid",uid).putString("session",UUID.randomUUID().toString()).remove("token_id").commit();
                    ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE)).cancelAll();
                }
            }
            refresh();
        };
        FirebaseAuth.getInstance().addAuthStateListener(authListener);
    }
    public static void refresh() {
        FirebaseUser user=FirebaseAuth.getInstance().getCurrentUser();
        if(context==null || user==null)return;
        SessionManager session = new SessionManager(context);
        if (!session.isLoggedInUser() || !user.getUid().equals(session.getUserUid())) return;
        final String uid=user.getUid();
        synchronized(PushRegistration.class) {
            if (!uid.equals(prefs(context).getString("uid",""))) {
                prefs(context).edit().putString("uid",uid).putString("session",UUID.randomUUID().toString()).remove("token_id").commit();
            }
        }
        FirebaseMessaging.getInstance().getToken().addOnSuccessListener(token -> {
            FirebaseUser current=FirebaseAuth.getInstance().getCurrentUser();
            if(current!=null && uid.equals(current.getUid())) saveToken(token);
        });
    }
    public static synchronized void saveToken(String token) {
        FirebaseUser user=FirebaseAuth.getInstance().getCurrentUser();
        if(context==null || user==null || token==null || token.isEmpty())return;
        SessionManager active = new SessionManager(context);
        if (!active.isLoggedInUser() || !user.getUid().equals(active.getUserUid())) return;
        SharedPreferences p=prefs(context);
        if(!user.getUid().equals(p.getString("uid","")))return;
        String id=hash(token),old=p.getString("token_id","");
        Map<String,Object> values=new HashMap<>();
        values.put("token",token);values.put("platform","android");
        values.put("session_id",p.getString("session",""));values.put("updated_at",FieldValue.serverTimestamp());
        FirebaseFirestore cloud=FirebaseFirestore.getInstance();
        cloud.collection("users").document(user.getUid()).collection("devices").document(id).set(values);
        if(!old.isEmpty()&&!old.equals(id))cloud.collection("users").document(user.getUid()).collection("devices").document(old).delete();
        p.edit().putString("token_id",id).apply();
    }
    public static synchronized void beforeSignOut(Context c) {
        SharedPreferences p=prefs(c);FirebaseUser u=FirebaseAuth.getInstance().getCurrentUser();
        String tokenId=p.getString("token_id","");
        if(u!=null&&!tokenId.isEmpty())FirebaseFirestore.getInstance().collection("users").document(u.getUid()).collection("devices").document(tokenId).delete();
        // Local invalidation is immediate even when the remote delete cannot complete offline.
        p.edit().putString("uid","").putString("session",UUID.randomUUID().toString()).remove("token_id").commit();
        ((NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE)).cancelAll();
    }
    static boolean accepts(Context c,String uid,String session) {
        FirebaseUser u=FirebaseAuth.getInstance().getCurrentUser();SharedPreferences p=prefs(c);
        return u!=null&&new SessionManager(c).isLoggedInUser()&&uid!=null&&uid.equals(new SessionManager(c).getUserUid())&&u.getUid().equals(uid)&&uid.equals(p.getString("uid",""))
                &&session!=null&&!session.isEmpty()&&session.equals(p.getString("session",""));
    }
    public static void requestPermission(Activity a) {
        if(FirebaseAuth.getInstance().getCurrentUser()==null)return;
        refresh();
        if(Build.VERSION.SDK_INT>=33 && a.checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED
                && !prefs(a).getBoolean("permission_asked",false)) {
            prefs(a).edit().putBoolean("permission_asked",true).apply();
            a.requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},7301);
        }
    }
    static String hash(String s) {
        try { byte[] b=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder out=new StringBuilder();for(byte x:b)out.append(String.format(java.util.Locale.ROOT,"%02x",x&255));return out.toString();
        } catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
