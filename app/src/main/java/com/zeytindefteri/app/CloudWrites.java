package com.zeytindefteri.app;

import com.google.android.gms.tasks.OnCompleteListener;

/**
 * Eşitleme turu sırasında yapılan Firestore yazmalarını {@link CloudWriteBatch}'e bağlar.
 * Depolar yazmaya {@code .addOnCompleteListener(CloudWrites.listener())} ekler; tur dışında (tek kayıt
 * kaydedilirken) yapılan yazmalar hiçbir şeyi etkilemez.
 */
final class CloudWrites {
    private static final ThreadLocal<CloudWriteBatch> CURRENT = new ThreadLocal<>();

    private CloudWrites() {}

    static CloudWriteBatch begin() {
        CloudWriteBatch b = new CloudWriteBatch();
        CURRENT.set(b);
        return b;
    }

    static void end() { CURRENT.remove(); }

    static <T> OnCompleteListener<T> listener() {
        final CloudWriteBatch b = CURRENT.get();
        if (b == null) return task -> { };
        b.started();
        return task -> b.finished(task.isSuccessful());
    }
}
