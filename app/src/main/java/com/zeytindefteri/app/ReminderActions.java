package com.zeytindefteri.app;
import android.content.Context;
/** Coordinates successful local writes with OS scheduling; UI never controls AlarmManager. */
public final class ReminderActions {
    public interface Change {long apply(ReminderRepository repository);}
    public interface Result {void done(boolean saved,boolean scheduled);}
    public static void run(Context context,String owner,boolean cancel,Change change,Result callback) {
        Context app=context.getApplicationContext();
        LocalReminderScheduler.WORKER.execute(()->{
            boolean saved=false,scheduled=false;
            synchronized(LocalReminderScheduler.LOCK) {
                try(DatabaseHelper db=new DatabaseHelper(app)) {
                    if(new SessionManager(app).hasSession()&&owner.equals(db.getActiveOwnerUid())) {
                        ReminderRepository repo=new ReminderRepository(db);long id=change.apply(repo);
                        if(id>0){saved=true;LocalReminderScheduler s=new LocalReminderScheduler(app);s.cancel(id);
                            s.resetDelivery(id);if(!cancel){s.schedule(repo,id);}scheduled=true;}
                    }
                }catch(RuntimeException e){android.util.Log.w("Reminders","Yerel işlem tamamlanamadı",e);}
            }
            final boolean ok=saved,alarm=scheduled;new android.os.Handler(android.os.Looper.getMainLooper()).post(()->callback.done(ok,alarm));
        });
    }
}
