package com.zeytindefteri.app;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Periyodik arka plan taraması (~4 saatte bir): hava, alacak, gün sonu, takvim. */
public final class AlertJobService extends JobService {
    @Override public boolean onStartJob(final JobParameters params) {
        new Thread(() -> {
            try { AlertCenter.runSync(this); }
            finally { jobFinished(params, false); }
        }, "zd-alert-job").start();
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) { return true; }
}
