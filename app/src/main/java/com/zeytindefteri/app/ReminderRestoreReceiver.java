package com.zeytindefteri.app;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
public final class ReminderRestoreReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent) {
        String a=intent.getAction();
        if(!Intent.ACTION_BOOT_COMPLETED.equals(a)&&!Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)
                &&!Intent.ACTION_TIME_CHANGED.equals(a)&&!Intent.ACTION_TIMEZONE_CHANGED.equals(a))return;
        final PendingResult pending=goAsync();final Context app=context.getApplicationContext();
        ElekReminder.schedule(app); // açılış/saat değişince 17:00 elek hatırlatması yeniden kurulur
        LocalReminderScheduler.WORKER.execute(()->{try{new LocalReminderScheduler(app).reschedule();}
            catch(RuntimeException e){android.util.Log.w("Reminders","Yeniden planlama tamamlanamadı",e);}finally{pending.finish();}});
    }
}
