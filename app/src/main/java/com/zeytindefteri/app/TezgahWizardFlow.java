package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.List;

/**
 * "Tartım başlat" sihirbazının sıra kuralları (saf, Android'siz).
 * Adımlar: Double → 1 → 2 → 3 → 4 → Yağlık → SELLER (satan üretici, en son) → DONE (ödeme penceresi).
 */
public final class TezgahWizardFlow {
    public static final int SELLER = TezgahModel.GRADES.length;
    public static final int DONE = SELLER + 1;
    public static final String WALK_IN = "Münferit satıcı";

    private TezgahWizardFlow() {}

    public static int next(int step) { return step >= DONE ? DONE : step + 1; }

    public static int back(int step) { return step <= 0 ? 0 : step - 1; }

    /** Başlangıç: tartımı olmayan ilk numara (boş tezgahta Double; hepsi doluysa yine Double). */
    public static int start(TezgahModel model) {
        for (int g = 0; g < TezgahModel.GRADES.length; g++) if (model.gradeCount(g) == 0) return g;
        return 0;
    }

    /** Ödeme penceresinde ad boş bırakılırsa münferit satıcı yazılır. */
    public static String sellerOrWalkIn(String name) {
        String n = name == null ? "" : name.trim();
        return n.isEmpty() ? WALK_IN : n;
    }

    /** "1 / 7" gibi adım yazısı; satan adımı "Son adım". */
    public static String progress(int step) {
        if (step < 0 || step >= DONE) return "";
        if (step == SELLER) return "Son adım";
        return (step + 1) + " / " + DONE;
    }

    /** Son satıcılar: yeniden eskiye, tekrarsız, münferit hariç, en çok limit kadar. */
    public static List<String> recent(List<String> sellersNewestFirst, int limit) {
        List<String> out = new ArrayList<>();
        if (sellersNewestFirst == null) return out;
        for (String s : sellersNewestFirst) {
            String n = s == null ? "" : s.trim();
            if (n.isEmpty() || n.equalsIgnoreCase(WALK_IN)) continue;
            boolean seen = false;
            for (String o : out) if (o.equalsIgnoreCase(n)) { seen = true; break; }
            if (!seen) out.add(n);
            if (out.size() >= limit) break;
        }
        return out;
    }

    /**
     * Son fiş, kaydedildikten sonra bu kadar süre açık döküm olarak durur (satıcıyla teyit için);
     * sonra tek satıra iner. Sıradaki üretici öncekinin adını, kilosunu ve parasını görmesin.
     */
    public static final long SLIP_OPEN_MS = 3L * 60L * 1000L;

    /** Son fişin açık kalacağı kalan süre (ms); 0 = kapalı (tek satır) gösterilir. */
    public static long slipOpenLeft(long createdAt, long now) {
        if (createdAt <= 0) return 0;
        long age = now - createdAt;
        if (age < 0) age = 0; // saat geri alınmışsa yeni kayıt sayılır
        return age < SLIP_OPEN_MS ? SLIP_OPEN_MS - age : 0;
    }
}
