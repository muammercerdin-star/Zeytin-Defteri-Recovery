package com.zeytindefteri.app;
import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.os.*;

/** Tek bağlı bilgisayar; sırlar yalnız süreç RAM'inde, START_NOT_STICKY. */
public final class TezgahWebService extends Service implements TezgahWebChanges.Listener {
    private static final String STOP="com.zeytindefteri.app.WEB_STOP",CHANNEL="tezgah_web";private static final int ID=7301;
    private static volatile TezgahWebService instance;private static Pending pending;
    private static final class Pending{final long generation=TezgahWebChanges.resetGeneration();TezgahWebPhone.Qr qr;String owner,name;Pending(TezgahWebPhone.Qr q,String o,String n){qr=q;owner=o;name=n;}}
    private String owner="",name="",room="";private volatile boolean ready,ended;private volatile long generation;private DatabaseHelper helper;private TezgahWebClient client;
    private final Handler main=new Handler(Looper.getMainLooper());
    public static synchronized void launch(Activity activity,TezgahWebPhone.Qr qr,String owner,String name){
        if(!TezgahWebClient.configured())return;pending=new Pending(qr,owner,name);
        Intent i=new Intent(activity,TezgahWebService.class);try{if(Build.VERSION.SDK_INT>=26)activity.startForegroundService(i);else activity.startService(i);}catch(RuntimeException e){pending=null;throw e;}
    }
    public static boolean running(String owner){TezgahWebService s=instance;return s!=null&&!s.ended&&s.owner.equals(owner);}
    public static boolean connected(String owner){TezgahWebService s=instance;return running(owner)&&s.ready;}
    public static boolean connected(String owner,String id){TezgahWebService s=instance;return connected(owner)&&s.room.equals(id);}
    public static boolean running(String owner,String id){TezgahWebService s=instance;return running(owner)&&s.room.equals(id);}
    public static void disconnect(Context c){TezgahWebService s=instance;if(s!=null)s.end();c.stopService(new Intent(c,TezgahWebService.class));}
    @Override public void onCreate(){super.onCreate();instance=this;TezgahWebChanges.listen(this);}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent!=null&&STOP.equals(intent.getAction())){end();return START_NOT_STICKY;}
        Pending p;synchronized(TezgahWebService.class){p=pending;pending=null;}
        if(p==null||p.generation!=TezgahWebChanges.resetGeneration()||!TezgahWebClient.configured()){end();return START_NOT_STICKY;}
        generation++;final long currentGeneration=generation;
        if(client!=null){client.close();if(!room.isEmpty())TezgahWebSessions.update(this,owner,room,name,false);}
        owner=p.owner;name=p.name;room=p.qr.room;ended=false;
        if(!sameOwner()){end();return START_NOT_STICKY;}
        try{
            Notification notification=notification();
            if(Build.VERSION.SDK_INT>=29)startForeground(ID,notification,ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);else startForeground(ID,notification);
            if(helper!=null)helper.close();helper=new DatabaseHelper(this);
            client=new TezgahWebClient(p.qr,new TezgahWebRepository(helper.getWritableDatabase(),owner),()->!ended&&generation==currentGeneration&&sameOwner(),(on,detail)->{
                if(generation!=currentGeneration)return;ready=on;TezgahWebSessions.update(this,owner,room,name,on,ended||(client!=null&&client.stopped()));main.post(()->{if(client!=null&&client.stopped())end();});
            });client.start();
        }catch(Exception e){end();android.widget.Toast.makeText(this,"Bilgisayar bağlantısı açılamadı. Yeni QR okut.",android.widget.Toast.LENGTH_LONG).show();}
        return START_NOT_STICKY;
    }
    private boolean sameOwner(){SessionManager s=new SessionManager(this);return s.hasSession()&&owner.equals(s.getUserUid());}
    private Notification notification(){
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(new NotificationChannel(CHANNEL,"Bilgisayar bağlantısı",NotificationManager.IMPORTANCE_LOW));
        PendingIntent stop=PendingIntent.getService(this,ID,new Intent(this,TezgahWebService.class).setAction(STOP),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        PendingIntent open=PendingIntent.getActivity(this,ID,new Intent(this,TezgahHomeActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        return b.setSmallIcon(android.R.drawable.stat_notify_sync).setContentTitle("Bilgisayar bağlantısı açık").setContentText("Yalnız görüntüleme · "+name).setOngoing(true).setContentIntent(open).addAction(android.R.drawable.ic_menu_close_clear_cancel,"Bağlantıyı kes",stop).build();
    }
    private synchronized void end(){if(ended)return;ended=true;generation++;ready=false;if(client!=null)client.close();if(!room.isEmpty())TezgahWebSessions.update(this,owner,room,name,false);main.post(()->{stopForeground(true);stopSelf();});}
    @Override public void changed(String changedOwner,long revision){if(owner.equals(changedOwner)&&client!=null)client.changed(revision);}
    @Override public void reset(String changedOwner){if(changedOwner==null||owner.equals(changedOwner))end();}
    @Override public void onDestroy(){end();TezgahWebChanges.remove(this);if(instance==this)instance=null;if(helper!=null){helper.close();helper=null;}super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
