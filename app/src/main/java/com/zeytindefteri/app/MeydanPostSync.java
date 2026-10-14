package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Meydan gönderilerinin telefon ↔ bulut eşleştirme kuralları. Saf Java; masaüstünde test edilir
 * (tests/meydan_sync).
 *
 * Bulut sorgusu en yeni N gönderiyi getirir (created_at azalan). Telefonda olup bu pencerede
 * bulunmayan bir gönderi ancak şu durumda "buluttan silinmiş" sayılır:
 * - anlık görüntü sunucudan gelmiştir (önbellekten değil),
 * - telefondaki kayıt senkrondur (sync_state != 1; bekleyen kendi değişikliğine dokunulmaz),
 * - ve ya bulutun tamamı gelmiştir (sonuç < N) ya da kayıt pencerenin en eski gönderisinden kesin yenidir.
 * Pencere dışındaki eski gönderiler hakkında karar verilmez (silinmiş olduklarını bilemeyiz).
 */
final class MeydanPostSync {
    static final int PENDING = 1;

    private MeydanPostSync() {}

    static final class LocalPost {
        final String cloudId;
        final long createdAt;
        final int syncState;

        LocalPost(String cloudId, long createdAt, int syncState) {
            this.cloudId = cloudId == null ? "" : cloudId;
            this.createdAt = createdAt;
            this.syncState = syncState;
        }
    }

    /**
     * @param local           telefondaki etkin gönderiler (bulut kimliği olanlar)
     * @param cloudIds        anlık görüntüdeki belge kimlikleri
     * @param snapshotSize    anlık görüntüdeki belge sayısı
     * @param limit           sorgu sınırı (N)
     * @param oldestCreatedAt penceredeki en eski created_at (pencere boşsa önemsiz)
     * @param fromCache       anlık görüntü önbellekten mi geldi
     * @return telefonda kapatılması gereken bulut kimlikleri
     */
    static List<String> missingFromCloud(Collection<LocalPost> local, Set<String> cloudIds, int snapshotSize,
                                         int limit, long oldestCreatedAt, boolean fromCache) {
        List<String> out = new ArrayList<>();
        if (fromCache || local == null || cloudIds == null || limit <= 0) return out;
        boolean wholeCollection = snapshotSize < limit;
        for (LocalPost post : local) {
            if (post == null || post.cloudId.isEmpty() || post.syncState == PENDING) continue;
            if (cloudIds.contains(post.cloudId)) continue;
            if (wholeCollection || post.createdAt > oldestCreatedAt) out.add(post.cloudId);
        }
        return out;
    }

    /**
     * Buluttan gelen belge telefona nasıl yansır.
     * @param cloudActive   bulutta is_active (alan yoksa 1 kabul edilir)
     * @param pendingLocal  telefonda buluta henüz yazılmamış değişiklik var (yeni mesaj, düzenleme ya da
     *                      silme). Yerel sürüm kazanır: silinen gönderi geri gelmez, gönderilmekte olan
     *                      mesajın kaydı kendi yankısıyla ezilmez.
     */
    static Action importAction(boolean cloudActive, boolean pendingLocal) {
        if (!cloudActive) return Action.DEACTIVATE;
        if (pendingLocal) return Action.SKIP;
        return Action.UPSERT;
    }

    enum Action { UPSERT, DEACTIVATE, SKIP }

    /** Buluttaki is_active değeri: alan yoksa ya da tanınmıyorsa etkin (eski belgeler). */
    static boolean cloudActive(Object value) {
        if (value instanceof Number) return ((Number) value).intValue() != 0;
        if (value instanceof Boolean) return (Boolean) value;
        return true;
    }
}
