package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.List;

/**
 * "Tartım başlat" sihirbazının sıra kuralları (saf, Android'siz).
 * Adımlar: SELLER (satan üretici) → Double → 1 → 2 → 3 → 4 → Yağlık → DONE (ödeme penceresi).
 */
public final class TezgahWizardFlow {
    public static final int SELLER = -1;
    public static final int DONE = TezgahModel.GRADES.length;
    public static final String WALK_IN = "Münferit satıcı";

    private TezgahWizardFlow() {}

    public static int next(int step) { return step >= DONE ? DONE : step + 1; }

    public static int back(int step) { return step <= SELLER ? SELLER : step - 1; }

    /** Başlangıç: satan bilinmiyorsa satan adımı; biliniyorsa tartımı olmayan ilk numara (hepsi doluysa Double). */
    public static int start(String seller, TezgahModel model) {
        if (seller == null || seller.trim().isEmpty()) return SELLER;
        for (int g = 0; g < TezgahModel.GRADES.length; g++) if (model.gradeCount(g) == 0) return g;
        return 0;
    }

    /** Ödeme penceresinde ad boş bırakılırsa münferit satıcı yazılır. */
    public static String sellerOrWalkIn(String name) {
        String n = name == null ? "" : name.trim();
        return n.isEmpty() ? WALK_IN : n;
    }

    /** "1 / 6" gibi adım yazısı (satan adımı sayılmaz). */
    public static String progress(int step) {
        if (step < 0 || step >= DONE) return "";
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
}
