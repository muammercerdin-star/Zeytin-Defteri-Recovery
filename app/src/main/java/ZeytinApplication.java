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
            // Ekranlar telefonun boyutuna göre ölçülür (dar/yakınlaştırılmış ekran, çok büyük yazı); setContentView'den önce.
            @Override public void onActivityPreCreated(android.app.Activity a,android.os.Bundle b){ScreenFit.apply(a);}
            public void onActivityCreated(android.app.Activity a,android.os.Bundle b){
                if(android.os.Build.VERSION.SDK_INT<29)ScreenFit.apply(a);
            }
            // Android 15+ (targetSdk 36): içerik çubukların altına uzanır; ekranlar setContentView'i
            // onCreate içinde çağırdığı için içerik çerçevesi burada hazırdır.
            @Override public void onActivityPostCreated(android.app.Activity a,android.os.Bundle b){SystemBarInsets.install(a);}
            public void onActivityStarted(android.app.Activity a){}
            public void onActivityPaused(android.app.Activity a){}
            public void onActivityStopped(android.app.Activity a){}
            public void onActivitySaveInstanceState(android.app.Activity a,android.os.Bundle b){}
            public void onActivityDestroyed(android.app.Activity a){}
        });
    }
    static Resources paletteResources() { return paletteResources; }
}
