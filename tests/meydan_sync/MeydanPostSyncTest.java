package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Meydan telefon ↔ bulut eşleştirme kuralları. Gerçek MeydanPostSync ile çalışır. */
public class MeydanPostSyncTest {
    static int checks = 0;
    static void check(boolean ok, String m) { checks++; if (!ok) throw new AssertionError(m); }
    static void eq(Object a, Object e, String m) { check(e.equals(a), m + ": beklenen " + e + ", gelen " + a); }
    static MeydanPostSync.LocalPost p(String id, long at, int state) { return new MeydanPostSync.LocalPost(id, at, state); }
    static Set<String> ids(String... v) { return new HashSet<>(Arrays.asList(v)); }

    public static void main(String[] args) {
        List<MeydanPostSync.LocalPost> local = new ArrayList<>(Arrays.asList(
                p("a", 500, 2), p("b", 400, 2), p("c", 300, 2), p("old", 100, 2), p("mine", 450, 1), p("", 480, 2), null));

        // Önbellek görüntüsü: hiçbir şey kapatılmaz.
        eq(MeydanPostSync.missingFromCloud(local, ids("a"), 1, 3, 500, true), new ArrayList<String>(), "önbellek");
        // Bulutun tamamı geldi (2 < 250): eksik olan her senkron gönderi silinmiştir; bekleyen ve kimliksiz atlanır.
        eq(MeydanPostSync.missingFromCloud(local, ids("a", "c"), 2, 250, 300, false), Arrays.asList("b", "old"), "tam koleksiyon");
        // Pencere dolu (3 == 3): yalnızca pencerenin en eski gönderisinden yeni olanlar kesin silinmiştir.
        eq(MeydanPostSync.missingFromCloud(local, ids("a", "c", "x"), 3, 3, 300, false), Arrays.asList("b"), "dolu pencere");
        // Pencerenin en eskisiyle aynı zaman: karar verilmez (eşitlikte sınır belirsiz).
        eq(MeydanPostSync.missingFromCloud(Arrays.asList(p("t", 300, 2)), ids("a", "c", "x"), 3, 3, 300, false),
                new ArrayList<String>(), "eşit zaman");
        // Kendi bekleyen gönderisine (sync_state=1) hiç dokunulmaz.
        eq(MeydanPostSync.missingFromCloud(Arrays.asList(p("mine", 999, 1)), ids(), 0, 250, 0, false),
                new ArrayList<String>(), "bekleyen");
        // Boş bulut (sunucudan, 0 belge): senkron gönderiler silinmiş sayılır.
        eq(MeydanPostSync.missingFromCloud(Arrays.asList(p("z", 1, 2)), ids(), 0, 250, 0, false), Arrays.asList("z"), "boş bulut");
        eq(MeydanPostSync.missingFromCloud(null, ids(), 0, 250, 0, false), new ArrayList<String>(), "null yerel");
        eq(MeydanPostSync.missingFromCloud(local, null, 0, 250, 0, false), new ArrayList<String>(), "null bulut");
        eq(MeydanPostSync.missingFromCloud(local, ids(), 0, 0, 0, false), new ArrayList<String>(), "sınır 0");

        eq(MeydanPostSync.importAction(true, false), MeydanPostSync.Action.UPSERT, "etkin");
        eq(MeydanPostSync.importAction(true, true), MeydanPostSync.Action.SKIP, "yerelde silinmiş, bulutta bekliyor");
        eq(MeydanPostSync.importAction(false, false), MeydanPostSync.Action.DEACTIVATE, "bulutta silinmiş");
        eq(MeydanPostSync.importAction(false, true), MeydanPostSync.Action.DEACTIVATE, "iki tarafta silinmiş");

        check(MeydanPostSync.cloudActive(null), "alan yok → etkin (eski belge)");
        check(MeydanPostSync.cloudActive(1L) && MeydanPostSync.cloudActive(1) && MeydanPostSync.cloudActive(1.0d), "1");
        check(!MeydanPostSync.cloudActive(0L) && !MeydanPostSync.cloudActive(0), "0");
        check(MeydanPostSync.cloudActive(Boolean.TRUE) && !MeydanPostSync.cloudActive(Boolean.FALSE), "boolean");
        check(MeydanPostSync.cloudActive("0"), "metin tanınmaz → etkin (gizlemek yerine göstermek)");
        System.out.println("MeydanPostSyncTest: " + checks + " kontrol geçti");
    }
}
