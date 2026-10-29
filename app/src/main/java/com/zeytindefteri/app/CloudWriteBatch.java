package com.zeytindefteri.app;

/**
 * Bir eşitleme turunda başlatılan bulut yazmalarının sayacı (saf, Android'siz).
 * Her yazma başlarken {@link #started()}, bitince {@link #finished(boolean)} çağrılır; tur kapanıp
 * ({@link #close(Done)}) bütün yazmalar bitince Done bir kez çağrılır. Süre dolarsa {@link #timeout()}
 * bekleyenleri "bekliyor" sayarak bir kez bildirir; geç gelen sonuçlar artık bildirim üretmez.
 */
public final class CloudWriteBatch {
    public interface Done { void done(int written, int failed, int pending); }

    private int started, ok, failed;
    private boolean closed, fired;
    private Done done;

    public synchronized void started() {
        if (!fired) started++;
    }

    public synchronized void finished(boolean success) {
        if (fired) return;
        if (success) ok++; else failed++;
        maybeFire(false);
    }

    /** Yazma başlatma bitti; artık hepsi bitince (ya da hiç yazma yoksa hemen) Done çağrılır. */
    public synchronized void close(Done d) {
        done = d;
        closed = true;
        maybeFire(false);
    }

    /** Süre doldu: bitmeyenler "bekliyor" (internet yok ya da yavaş) sayılır. */
    public synchronized void timeout() {
        if (closed) maybeFire(true);
    }

    public synchronized boolean isFired() { return fired; }

    private void maybeFire(boolean force) {
        if (fired || !closed) return;
        int pending = started - ok - failed;
        if (pending > 0 && !force) return;
        fired = true;
        if (done != null) done.done(ok, failed, Math.max(0, pending));
    }
}
