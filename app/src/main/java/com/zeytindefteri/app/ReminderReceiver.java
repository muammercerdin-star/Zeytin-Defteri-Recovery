package com.zeytindefteri.app;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
public final class ReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        final long id=intent.getLongExtra("reminder_id",-1);if(id<=0)return;
        final PendingResult pending=goAsync();final Context app=context.getApplicationContext();
        LocalReminderScheduler.WORKER.execute(()->{try{new LocalReminderScheduler(app).receive(id);}
            catch(RuntimeException e){android.util.Log.w("Reminders","Hatırlatma işlenemedi",e);}finally{pending.finish();}});
    }
}
