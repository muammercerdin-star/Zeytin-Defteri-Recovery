package com.zeytindefteri.app;

import android.app.Application;
import android.content.res.Resources;

/** Application resources only; never retains an Activity. */
public final class ZeytinApplication extends Application {
    private static Resources paletteResources;
    @Override public void onCreate() {
        super.onCreate();
        paletteResources = getResources();
        PushRegistration.init(this);
        new LocalReminderScheduler(this).createChannel();
        // Activity resumes also reconcile locally restored/deleted cloud records; no cloud reads here.
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            private long last;
            public void onActivityResumed(android.app.Activity activity) {
                long now=android.os.SystemClock.elapsedRealtime();
                if(last==0 || now-last>60000){last=now;LocalReminderScheduler.requestReschedule(ZeytinApplication.this);}
            }
            public void onActivityCreated(android.app.Activity a,android.os.Bundle b){}
            public void onActivityStarted(android.app.Activity a){}
            public void onActivityPaused(android.app.Activity a){}
            public void onActivityStopped(android.app.Activity a){}
            public void onActivitySaveInstanceState(android.app.Activity a,android.os.Bundle b){}
            public void onActivityDestroyed(android.app.Activity a){}
        });
    }
    static Resources paletteResources() { return paletteResources; }
}
