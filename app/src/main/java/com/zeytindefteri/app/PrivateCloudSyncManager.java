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
 */
public class PrivateCloudSyncManager {

    public interface Callback {
        void onComplete();
        void onError(Exception error);
    }

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

    private void uploadLocal(String uid, Callback callback) {
        gardens.syncAllLocalGardens(uid);
        operations.syncAllLocalOperations(uid);
        production.syncAllLocal(uid);
        support.syncAllLocal(uid);
        finish(uid, callback, null);
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