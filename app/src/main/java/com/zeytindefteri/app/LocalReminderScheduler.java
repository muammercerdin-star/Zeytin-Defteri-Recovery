package com.zeytindefteri.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.UserManager;
import java.text.DateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** SQLite remains authoritative. Preferences hold only OS delivery/registration bookkeeping. */
public final class LocalReminderScheduler {
    public static final Object LOCK=new Object();
    public static final String CHANNEL="zeytin_reminders";
    public static final ExecutorService WORKER=Executors.newSingleThreadExecutor();
    private static final long DELIVERY_GRACE=6*60*60*1000L;
    private static boolean queued;
    private final Context context;
    private final AlarmManager alarms;
    private final NotificationManager notifications;
    private final SharedPreferences state;
    public LocalReminderScheduler(Context context) {
        this.context=context.getApplicationContext();
        alarms=(AlarmManager)this.context.getSystemService(Context.ALARM_SERVICE);
        notifications=(NotificationManager)this.context.getSystemService(Context.NOTIFICATION_SERVICE);
        state=this.context.getSharedPreferences("local_reminder_delivery",Context.MODE_PRIVATE);
    }
    public void createChannel() {
        if(Build.VERSION.SDK_INT>=26 && notifications!=null) {
            NotificationChannel channel=new NotificationChannel(CHANNEL,"Zeytin Defteri Hatırlatmaları",NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Bahçe işlemleri ve üretici hatırlatmaları");notifications.createNotificationChannel(channel);
        }
    }
    public boolean enabled() {
        if(notifications==null||!notifications.areNotificationsEnabled())return false;
        if(Build.VERSION.SDK_INT>=33 && context.checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED)return false;
        if(Build.VERSION.SDK_INT>=26) {NotificationChannel c=notifications.getNotificationChannel(CHANNEL);if(c!=null&&c.getImportance()==NotificationManager.IMPORTANCE_NONE)return false;}
        return true;
    }
    private PendingIntent alarm(long id,int flags) {
        Intent i=new Intent(context,ReminderReceiver.class).setAction("com.zeytindefteri.app.REMINDER")
                .setData(Uri.parse("zeytin-reminder://alarm/"+id)).putExtra("reminder_id",id);
        return PendingIntent.getBroadcast(context,0,i,flags|PendingIntent.FLAG_IMMUTABLE);
    }
    private String token(Cursor c) {return text(c,"owner_uid")+"\n"+number(c,"due_at");}
    public void cancel(long id) {synchronized(LOCK){cancelAlarm(id);if(notifications!=null)notifications.cancel("reminder:"+id,0);state.edit().remove("notice_"+id).apply();}}
    private void cancelAlarm(long id) {
        PendingIntent p=alarm(id,PendingIntent.FLAG_NO_CREATE);if(p!=null){if(alarms!=null)alarms.cancel(p);p.cancel();}
        state.edit().remove("alarm_"+id).apply();
    }
    public void resetDelivery(long id) {state.edit().remove("sent_"+id).commit();}
    public void schedule(ReminderRepository repo,long id) {synchronized(LOCK){
        try(Cursor c=repo.getReminder(id)) {
            if(!c.moveToFirst()||number(c,"is_done")!=0){cancel(id);return;}
            cancelAlarm(id);
            long due=number(c,"due_at"),now=System.currentTimeMillis();
            if(!ReminderTime.valid(due)||!ReminderTime.supported(text(c,"repeat_rule")))return;
            boolean delivered=token(c).equals(state.getString("sent_"+id,""));
            if(due<=now) {
                if(!delivered && now-due<=DELIVERY_GRACE) {
                    // Re-arm the SAME occurrence. Its SQLite due_at and delivery token stay intact.
                    due=now+1000L;
                } else {
                    long next=ReminderTime.next(due,text(c,"repeat_rule"),now,TimeZone.getDefault());
                    if(next==0||!repo.advance(id,due,next))return;due=next;
                }
            } else if(delivered)return;
            if(alarms!=null) {
                // Stable data URI, not a truncated long requestCode, distinguishes every ID.
                state.edit().putBoolean("alarm_"+id,true).commit();
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,due,alarm(id,PendingIntent.FLAG_UPDATE_CURRENT));
            }
        }
    }}
    public void receive(long id) {synchronized(LOCK){
        if(id<=0||!unlocked()||!new SessionManager(context).hasSession())return;
        try(DatabaseHelper db=new DatabaseHelper(context)) {
            ReminderRepository repo=new ReminderRepository(db);
            try(Cursor c=repo.getReminder(id)) {
                if(!c.moveToFirst()||number(c,"is_done")!=0){cancel(id);return;}
                long due=number(c,"due_at"),now=System.currentTimeMillis();String repeat=text(c,"repeat_rule");
                if(!ReminderTime.valid(due)||!ReminderTime.supported(repeat)) {cancel(id);return;}
                if(due>now){schedule(repo,id);return;} // An edited/stale broadcast cannot fire early.
                String token=token(c);
                if(token.equals(state.getString("sent_"+id,"")))return;
                boolean deliver=now-due<=DELIVERY_GRACE;
                // Persist BEFORE notify: duplicate broadcasts/process restarts cannot replay this occurrence.
                if(!state.edit().putString("sent_"+id,token).commit())return;
                cancelAlarm(id);
                long next=ReminderTime.next(due,repeat,now,TimeZone.getDefault());
                if(next>0&&repo.advance(id,due,next))schedule(repo,id);
                if(deliver&&enabled())show(c,id,due,db.getActiveOwnerUid());
            }
        }
    }}
    private void show(Cursor c,long id,long due,String owner) {
        createChannel();String garden=text(c,"garden_name"),title=text(c,"title"),note=text(c,"note");
        String body=(note.isEmpty()?"Hatırlatma zamanı geldi.":note)+" · "+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(due));
        Intent i=new Intent(context,RemindersActivity.class).setData(Uri.parse("zeytin-reminder://open/"+id))
                .putExtra("reminder_id",id).putExtra("reminder_owner",owner).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent open=PendingIntent.getActivity(context,0,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(context,CHANNEL):new Notification.Builder(context);
        b.setSmallIcon(R.drawable.ic_bell_line).setContentTitle(garden.isEmpty()?title:garden+" – "+title)
                .setContentText(body).setStyle(new Notification.BigTextStyle().bigText(body)).setContentIntent(open)
                .setAutoCancel(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE);
        try {notifications.notify("reminder:"+id,0,b.build());state.edit().putBoolean("notice_"+id,true).apply();}
        catch(SecurityException ignored) { /* Permission revocation never deletes the SQLite reminder. */ }
    }
    private boolean unlocked() {UserManager u=(UserManager)context.getSystemService(Context.USER_SERVICE);return u==null||u.isUserUnlocked();}
    public void reschedule() {synchronized(LOCK){
        if(!unlocked())return;
        if(!new SessionManager(context).hasSession()){beforeSessionChange(context);return;}
        Set<Long> tracked=trackedIds("alarm_");
        try(DatabaseHelper db=new DatabaseHelper(context)) {
            ReminderRepository repo=new ReminderRepository(db);
            // Validate tracked records before cancelling: an inexact alarm can be overdue but undelivered.
            for(long id:tracked)schedule(repo,id);
            // Also find new future/repeating rows; do not sweep old one-shot history.
            try(Cursor c=repo.getSchedulable(System.currentTimeMillis())) {
                while(c.moveToNext()){long id=number(c,"id");if(!tracked.contains(id))schedule(repo,id);}
            }
            for(long id:trackedIds("notice_")) {
                try(Cursor c=repo.getReminder(id)){if(!c.moveToFirst()||number(c,"is_done")!=0)cancel(id);}
            }
        }
    }}
    public static void requestReschedule(Context context) {
        final Context app=context.getApplicationContext();synchronized(LOCK){if(queued)return;queued=true;}
        WORKER.execute(()->{synchronized(LOCK){try{new LocalReminderScheduler(app).reschedule();}catch(RuntimeException e){android.util.Log.w("Reminders","Yerel planlama tamamlanamadı",e);}finally{queued=false;}}});
    }
    public static void beforeSessionChange(Context context) {synchronized(LOCK){
        LocalReminderScheduler s=new LocalReminderScheduler(context);
        Set<Long> ids=s.trackedIds("alarm_");ids.addAll(s.trackedIds("notice_"));
        for(long id:ids)s.cancel(id);
    }}
    private Set<Long> trackedIds(String prefix) {
        Set<Long> ids=new LinkedHashSet<>();
        // Snapshot the keys: cleanup/cancellation can mutate preferences during iteration.
        for(String key:new ArrayList<>(state.getAll().keySet()))if(key.startsWith(prefix)) {
            String suffix=key.substring(prefix.length());
            try {
                long id=Long.parseLong(suffix);
                if(id>0 && Long.toString(id).equals(suffix)){ids.add(id);continue;}
            } catch(NumberFormatException ignored) { }
            state.edit().remove(key).apply();
        }
        return ids;
    }
    static String text(Cursor c,String name){String s=c.getString(c.getColumnIndexOrThrow(name));return s==null?"":s;}
    static long number(Cursor c,String name){return c.getLong(c.getColumnIndexOrThrow(name));}
}
