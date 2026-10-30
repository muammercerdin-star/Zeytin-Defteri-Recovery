package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ana sayfadaki "Satış" toplamının dökümü (saf, Android'siz).
 * Her satışa kaynak etiketi verir: elekten oluşan, eleği sonradan silinmiş ya da elle girilmiş.
 * Elle girilen bir satış, aynı gün ve aynı bahçedeki elekten oluşmuş bir satışla aynı tutardaysa
 * "çift olabilir" diye işaretlenir; böylece hem elle hem elekten girilmiş satış kolayca bulunur.
 */
public final class SeasonSalesModel {
    private SeasonSalesModel() {}

    public enum Kind { ELEK, ELEK_DELETED, MANUAL }

    public static final class Line {
        public long id;
        public String date = "", garden = "", note = "", buyer = "";
        public double kg, net;
        /** Elekten oluşmuş satışta > 0 (yerel ya da bulut elek id'si var). */
        public long sourceElekId;
        public boolean elekExists;
        /** Elle girilmiş ve aynı gün/bahçede aynı tutarlı elek satışı var. */
        public boolean possibleDuplicate;

        public Kind kind() {
            if (sourceElekId > 0) return elekExists ? Kind.ELEK : Kind.ELEK_DELETED;
            return Kind.MANUAL;
        }
    }

    public static final class Summary {
        public double total, elek, elekDeleted, manual;
        public int count, elekCount, elekDeletedCount, manualCount, duplicateCount;
    }

    /** Elle girilen satışlardan, elekten oluşmuş bir satışla aynı gün + bahçe + tutar olanları işaretler. */
    public static void markDuplicates(List<Line> lines) {
        for (Line m : lines) {
            m.possibleDuplicate = false;
            if (m.kind() != Kind.MANUAL) continue;
            for (Line e : lines) {
                if (e == m || e.kind() == Kind.MANUAL) continue;
                if (same(e.date, m.date) && same(e.garden, m.garden) && Math.abs(e.net - m.net) < 0.5) {
                    m.possibleDuplicate = true;
                    break;
                }
            }
        }
    }

    public static Summary summarize(List<Line> lines) {
        Summary s = new Summary();
        for (Line l : lines) {
            s.total += l.net;
            s.count++;
            switch (l.kind()) {
                case ELEK: s.elek += l.net; s.elekCount++; break;
                case ELEK_DELETED: s.elekDeleted += l.net; s.elekDeletedCount++; break;
                default: s.manual += l.net; s.manualCount++; break;
            }
            if (l.possibleDuplicate) s.duplicateCount++;
        }
        return s;
    }

    public static String tag(Line l) {
        switch (l.kind()) {
            case ELEK: return "Elekten";
            case ELEK_DELETED: return "Eleği silinmiş";
            default: return l.possibleDuplicate ? "Elle girildi · çift olabilir" : "Elle girildi";
        }
    }

    /** Liste üstündeki açıklama: elek toplamı ile satış toplamı neden farklı olabilir. */
    public static String explain(Summary s) {
        if (s.count == 0) return "Bu sezon satış kaydı yok. Elek kaydederken \"Bu zeytini sattım\" dersen satış burada görünür.";
        List<String> parts = new ArrayList<>();
        if (s.elekCount > 0) parts.add(s.elekCount + " satış elekten: " + tl(s.elek));
        if (s.manualCount > 0) parts.add(s.manualCount + " satış elle girildi: " + tl(s.manual));
        if (s.elekDeletedCount > 0) parts.add(s.elekDeletedCount + " satışın eleği silinmiş: " + tl(s.elekDeleted));
        StringBuilder b = new StringBuilder(String.join("\n", parts));
        if (s.manualCount > 0 || s.elekDeletedCount > 0) {
            b.append("\n\nElek ekranındaki sezon toplamı yalnız elek kayıtlarını sayar; elle girilen ve eleği silinmiş satışlar orada yoktur.");
        }
        if (s.duplicateCount > 0) {
            b.append("\n\n").append(s.duplicateCount).append(" elle girilmiş satış, aynı gün aynı bahçedeki bir elek satışıyla aynı tutarda. Çift girilmiş olabilir; kontrol et.");
        }
        return b.toString();
    }

    /** "2026-10-29" → "29 Ekim 2026"; tanınmayan biçim olduğu gibi döner. */
    public static String date(String iso) {
        if (iso == null || iso.length() < 10 || iso.charAt(4) != '-') return iso == null ? "" : iso;
        try {
            int y = Integer.parseInt(iso.substring(0, 4)), m = Integer.parseInt(iso.substring(5, 7)), d = Integer.parseInt(iso.substring(8, 10));
            String[] months = {"Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"};
            if (m < 1 || m > 12) return iso;
            return d + " " + months[m - 1] + " " + y;
        } catch (NumberFormatException e) {
            return iso;
        }
    }

    public static String tl(double v) {
        java.text.NumberFormat nf = java.text.NumberFormat.getNumberInstance(new Locale("tr", "TR"));
        nf.setMaximumFractionDigits(0);
        return nf.format(Math.round(v)) + " TL";
    }

    public static String kg(double v) {
        java.text.NumberFormat nf = java.text.NumberFormat.getNumberInstance(new Locale("tr", "TR"));
        nf.setMaximumFractionDigits(1);
        return nf.format(Math.max(0, v)) + " kg";
    }

    private static boolean same(String a, String b) {
        return (a == null ? "" : a.trim()).equalsIgnoreCase(b == null ? "" : b.trim());
    }
}
