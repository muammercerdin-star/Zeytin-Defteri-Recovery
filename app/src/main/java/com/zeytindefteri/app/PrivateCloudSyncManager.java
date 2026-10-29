package com.zeytindefteri.app;

/**
 * Zeytin Defteri'nin özel kullanıcı verileri için tek senkronizasyon motoru.
 *
 * Sıra önemlidir:
 * 1) Yerel silmeleri buluta yaz.
 * 2) Diğer cihazlardan gelen silmeleri uygula.
 * 3) Bahçeleri her zaman buluttan birleştir.
 * 4) Bahçe altı kayıtları buluttan birleştir.
 * 5) Yerelde daha yeni olan değişiklikleri buluta gönder.
 *
 * Böylece yeni telefon geri yükleme + çoklu cihaz + son değişiklik kazanır
 * politikası tek noktadan yürütülür.
 *
 * onComplete: buluttakiler bu telefona indi (giriş akışı bunu bekler, yüklemeyi beklemez).
 * onUploaded: 5. adımdaki yazmaların sunucudan sonucu geldi (ya da 90 sn doldu, bekleyenler sayılır).
 * Tur, yüklemeler bitene kadar "çalışıyor" sayılır; aynı kayıt üst üste gönderilmez. Başarısız ya da
 * bekleyen kayıtlar "yüklenmedi" işaretli kalır ve sonraki turda yeniden gönderilir.
 */
public class PrivateCloudSyncManager {

    public interface Callback {
        void onComplete();
        void onError(Exception error);

        /** Yüklemelerin sonucu: yazılan, başarısız ve süre dolduğunda hâlâ bekleyen kayıt sayısı. */
        default void onUploaded(int written, int failed, int pending) { }
    }

    /** Son yükleme turunun özeti (ekranlarda "bulut eşitlemesi bekliyor" demek için). */
    public static volatile int lastWritten = -1, lastFailed, lastPending;
    public static volatile long lastUploadAt;

    private static final long UPLOAD_WAIT_MS = 90_000L;
    private static final android.os.Handler MAIN = new android.os.Handler(android.os.Looper.getMainLooper());

    private final GardenCloudRepository gardens;
    private final OperationCloudRepository operations;
    private final ProductionCloudRepository production;
    private final SupportCloudRepository support;
    private final CloudDeletionRepository deletions;

    private boolean running = false;
    private boolean rerunRequested = false;

    public PrivateCloudSyncManager(DatabaseHelper databaseHelper) {
        gardens = new GardenCloudRepository(databaseHelper);
        operations = new OperationCloudRepository(databaseHelper);
        production = new ProductionCloudRepository(databaseHelper);
        support = new SupportCloudRepository(databaseHelper);
        deletions = new CloudDeletionRepository(databaseHelper);
    }

    public synchronized void sync(String uid, Callback callback) {
        uid = safe(uid);
        if (uid.isEmpty()) {
            if (callback != null) callback.onComplete();
            return;
        }

        if (running) {
            rerunRequested = true;
            return;
        }
        running = true;
        runDeletePush(uid, callback);
    }

    private void runDeletePush(final String uid, final Callback callback) {
        deletions.pushPending(uid, new CloudDeletionRepository.Callback() {
            @Override public void onComplete() { runDeletePull(uid, callback); }
            @Override public void onError(Exception error) {
                // Silme yazılamadıysa eski veri tekrar dirilmesin; bu turda normal restore'a geçmiyoruz.
                finish(uid, callback, error);
            }
        });
    }

    private void runDeletePull(final String uid, final Callback callback) {
        deletions.pullAndApply(uid, new CloudDeletionRepository.Callback() {
            @Override public void onComplete() { runGardenRestore(uid, callback); }
            @Override public void onError(Exception error) {
                finish(uid, callback, error);
            }
        });
    }

    private void runGardenRestore(final String uid, final Callback callback) {
        gardens.restoreGardens(uid, new GardenCloudRepository.RestoreCallback() {
            @Override public void onComplete(int restoredCount) { runOperationRestore(uid, callback); }
            @Override public void onError(Exception error) { finish(uid, callback, error); }
        });
    }

    private void runOperationRestore(final String uid, final Callback callback) {
        operations.restoreAllOperations(uid, new OperationCloudRepository.RestoreCallback() {
            @Override public void onComplete(int restoredCount) { runProductionRestore(uid, callback); }
            @Override public void onError(Exception error) { finish(uid, callback, error); }
        });
    }

    private void runProductionRestore(final String uid, final Callback callback) {
        production.restoreAll(uid, new ProductionCloudRepository.RestoreCallback() {
            @Override public void onComplete(int restoredCount) { runSupportRestore(uid, callback); }
            @Override public void onError(Exception error) { finish(uid, callback, error); }
        });
    }

    private void runSupportRestore(final String uid, final Callback callback) {
        support.restoreAll(uid, new SupportCloudRepository.RestoreCallback() {
            @Override public void onComplete(int restoredCount) { uploadLocal(uid, callback); }
            @Override public void onError(Exception error) { finish(uid, callback, error); }
        });
    }

    private void uploadLocal(final String uid, final Callback callback) {
        // İndirme bitti: giriş/ana ekran beklemesin.
        if (callback != null) callback.onComplete();
        final CloudWriteBatch batch = CloudWrites.begin();
        try {
            gardens.syncAllLocalGardens(uid);
            operations.syncAllLocalOperations(uid);
            production.syncAllLocal(uid);
            support.syncAllLocal(uid);
        } catch (RuntimeException e) {
            // İndirme zaten bitti (onComplete gitti); kayıtlar "yüklenmedi" kalır, sonraki turda yeniden denenir.
            lastFailed = 1;
            lastUploadAt = System.currentTimeMillis();
            finish(uid, null, null);
            return;
        } finally {
            CloudWrites.end();
        }
        batch.close((written, failed, pending) -> MAIN.post(() -> {
            lastWritten = written;
            lastFailed = failed;
            lastPending = pending;
            lastUploadAt = System.currentTimeMillis();
            if (callback != null) callback.onUploaded(written, failed, pending);
            finish(uid, null, null);
        }));
        MAIN.postDelayed(batch::timeout, UPLOAD_WAIT_MS);
    }

    private synchronized void finish(String uid, Callback callback, Exception error) {
        running = false;
        if (error != null) {
            if (callback != null) callback.onError(error);
        } else if (callback != null) {
            callback.onComplete();
        }

        if (rerunRequested) {
            rerunRequested = false;
            sync(uid, null);
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}