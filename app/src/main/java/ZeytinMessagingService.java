package com.zeytindefteri.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import java.util.Map;

public final class ZeytinMessagingService extends FirebaseMessagingService {
    public static final String CHANNEL="meydan_replies";
    static volatile String visiblePost="";
    @Override public void onNewToken(String token) { PushRegistration.saveToken(token); }
    public static void ensureChannel(Context c) {
        if(Build.VERSION.SDK_INT>=26) ((NotificationManager)c.getSystemService(NOTIFICATION_SERVICE))
                .createNotificationChannel(new NotificationChannel(CHANNEL,"Meydan Bildirimleri",NotificationManager.IMPORTANCE_DEFAULT));
    }
    @Override public void onMessageReceived(RemoteMessage message) {
        Map<String,String> d=message.getData();
        String post=d.get("post_id"),comment=d.get("comment_id"),uid=d.get("recipient_uid"),session=d.get("session_id");
        if(!"meydan_reply".equals(d.get("type")) || !validId(post)||!validId(comment)||!PushRegistration.accepts(this,uid,session))return;
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)return;
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(!manager.areNotificationsEnabled())return;
        String event=PushRegistration.hash(uid+"/"+post+"/"+comment);
        synchronized(ZeytinMessagingService.class) {
            SharedPreferences seen=getSharedPreferences("push_seen",MODE_PRIVATE);
            if(seen.contains(event))return;
            // Bounded one-day delivery TTL, retain local dedup keys for seven days.
            SharedPreferences.Editor edit=seen.edit();long cutoff=System.currentTimeMillis()-7L*86400000;
            for(Map.Entry<String,?> e:seen.getAll().entrySet())if(e.getValue() instanceof Long&&(Long)e.getValue()<cutoff)edit.remove(e.getKey());
            edit.putLong(event,System.currentTimeMillis()).commit();
            if(post.equals(visiblePost))return;
            Intent open=new Intent(this,PushOpenActivity.class).setData(Uri.parse("zeytin-push://reply/"+event));
            open.putExtra("post_cloud_id",post);open.putExtra("recipient_uid",uid);open.putExtra("session_id",session);
            PendingIntent pending=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
            ensureChannel(this);
            Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
            String body=d.get("body");if(body==null)body="Gönderine yeni bir yanıt geldi.";
            if(body.length()>240)body=body.substring(0,240);
            builder.setSmallIcon(R.drawable.ic_bell_line).setContentTitle("Zeytin Defteri").setContentText(body)
                    .setStyle(new Notification.BigTextStyle().bigText(body)).setContentIntent(pending).setAutoCancel(true)
                    .setCategory(Notification.CATEGORY_SOCIAL).setVisibility(Notification.VISIBILITY_PRIVATE).setOnlyAlertOnce(true);
            try { manager.notify(event,0,builder.build()); } catch(SecurityException ignored) { }
        }
    }
    static boolean validId(String id){return id!=null&&!id.isEmpty()&&id.length()<=1500&&!id.contains("/");}
}
