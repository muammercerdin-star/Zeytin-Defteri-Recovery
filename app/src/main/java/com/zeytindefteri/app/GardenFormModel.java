package com.zeytindefteri.app;

import java.util.ArrayList;
import java.util.List;

/**
 * Bahçe ekle / düzenle formunun saf (Android'siz) kuralları.
 * Ekleme ve düzenleme aynı formu kullanır; fark yalnızca başlık, düğme ve konum kuralındadır.
 */
public final class GardenFormModel {
    private GardenFormModel() {}

    /** Formdaki sulama seçenekleri; "Belirtilmedi" veritabanına boş yazılır. */
    public static final String[] IRRIGATION = {"Belirtilmedi", "Damla sulama", "Salma sulama", "Yağmurlama",
            "Gölet / tanker", "Kuyu / sondaj", "Kuru tarım", "Diğer"};

    public enum Mode { FIRST, ADD, EDIT }

    public static String title(Mode mode) {
        switch (mode) {
            case FIRST: return "İlk bahçeni oluşturalım";
            case EDIT: return "Bahçeyi düzenle";
            default: return "Yeni bahçe";
        }
    }

    public static String subtitle(Mode mode) {
        switch (mode) {
            case FIRST: return "Zeytin Defteri bahçelerin üzerinden çalışır. İlk zeytinliğini kaydederek başlayalım.";
            case EDIT: return "Bahçe bilgilerini güncelle";
            default: return "Zeytinliğinin temel bilgilerini kaydet";
        }
    }

    public static String saveLabel(Mode mode) {
        switch (mode) {
            case FIRST: return "Bahçemi Kaydet";
            case EDIT: return "Değişiklikleri kaydet";
            default: return "Bahçeyi Kaydet";
        }
    }

    /**
     * Gösterilecek sulama seçenekleri. Eski kayıtta listede olmayan bir değer varsa (eski düzenleme ekranı
     * serbest metindi) kaybolmasın diye sona eklenir.
     */
    public static List<String> irrigationOptions(String current) {
        List<String> out = new ArrayList<>();
        for (String s : IRRIGATION) out.add(s);
        String c = current == null ? "" : current.trim();
        if (!c.isEmpty() && indexOfIgnoreCase(out, c) < 0) out.add(c);
        return out;
    }

    /** Kayıtlı değerin seçenek sırası; boşsa "Belirtilmedi" (0). */
    public static int irrigationIndex(List<String> options, String current) {
        String c = current == null ? "" : current.trim();
        if (c.isEmpty()) return 0;
        int i = indexOfIgnoreCase(options, c);
        return i < 0 ? 0 : i;
    }

    /** Veritabanına yazılacak değer: "Belirtilmedi" → boş. */
    public static String irrigationToStore(String option) {
        return option == null || "Belirtilmedi".equals(option.trim()) ? "" : option.trim();
    }

    /** Boş → 0; negatif veya sayı değilse -1 (hata). */
    public static int parseCount(String raw) {
        String v = raw == null ? "" : raw.trim();
        if (v.isEmpty()) return 0;
        try {
            int n = Integer.parseInt(v);
            return n < 0 ? -1 : n;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Dekar: virgül veya nokta ondalık; boş → 0; negatif/geçersiz → -1. */
    public static double parseArea(String raw) {
        String v = raw == null ? "" : raw.trim().replace(" ", "").replace(',', '.');
        if (v.isEmpty()) return 0;
        try {
            double d = Double.parseDouble(v);
            return d < 0 || Double.isNaN(d) || Double.isInfinite(d) ? -1 : d;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** İlk bahçede ağaç sayısı ve yaş zorunludur (eski kural). */
    public static boolean requiresTreeAndAge(Mode mode) {
        return mode == Mode.FIRST;
    }

    public enum LocationCheck { OK, PICK_REQUIRED, PICK_AGAIN }

    /**
     * Konum kuralı:
     * - İlk bahçede il/ilçe/mahalle listeden seçilmiş olmalı (PICK_REQUIRED).
     * - Herhangi bir parça doluysa üçü de listeden seçilmiş olmalı (PICK_AGAIN);
     *   düzenlemede kayıtlı konum hiç değişmediyse kimlik aranmaz (eski kayıtlarda kimlik yoktur).
     * - Konum tamamen boşsa (ilk bahçe hariç) kabul edilir.
     */
    public static LocationCheck checkLocation(Mode mode, String province, String district, String settlement,
                                              int provinceId, int districtId, int settlementId,
                                              boolean unchangedFromSaved) {
        boolean picked = provinceId > 0 && districtId > 0 && settlementId > 0;
        if (mode == Mode.FIRST) return picked ? LocationCheck.OK : LocationCheck.PICK_REQUIRED;
        boolean any = !blank(province) || !blank(district) || !blank(settlement);
        if (!any || picked) return LocationCheck.OK;
        if (mode == Mode.EDIT && unchangedFromSaved) return LocationCheck.OK;
        return LocationCheck.PICK_AGAIN;
    }

    /** Konum satırının başlığı: en küçük birim (mahalle/köy, yoksa ilçe, yoksa il). */
    public static String locationTitle(String province, String district, String settlement) {
        if (!blank(settlement)) return settlement.trim();
        if (!blank(district)) return district.trim();
        if (!blank(province)) return province.trim();
        return "Konum seç";
    }

    /** Konum satırının alt metni: üst birimler "İlçe / İl"; konum yoksa yönlendirme. */
    public static String locationSubtitle(String province, String district, String settlement) {
        if (blank(province) && blank(district) && blank(settlement)) return "İl, ilçe ve mahalle / köy seç";
        StringBuilder b = new StringBuilder();
        if (!blank(settlement) && !blank(district)) b.append(district.trim());
        if ((!blank(settlement) || !blank(district)) && !blank(province)) {
            if (b.length() > 0) b.append(" / ");
            b.append(province.trim());
        }
        return b.toString();
    }

    /** İlk bahçe başarı kartının özeti: "120 ağaç · 14 yaş · Domat" ve alt satırda konum. */
    public static String summary(int trees, int age, String variety, String province, String district, String settlement) {
        StringBuilder b = new StringBuilder();
        if (trees > 0) b.append(trees).append(" ağaç");
        if (age > 0) { if (b.length() > 0) b.append(" · "); b.append(age).append(" yaş"); }
        if (!blank(variety)) { if (b.length() > 0) b.append(" · "); b.append(variety.trim()); }
        StringBuilder loc = new StringBuilder();
        for (String p : new String[]{province, district, settlement}) {
            if (blank(p)) continue;
            if (loc.length() > 0) loc.append(" / ");
            loc.append(p.trim());
        }
        if (loc.length() > 0) { if (b.length() > 0) b.append('\n'); b.append(loc); }
        return b.toString();
    }

    static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static int indexOfIgnoreCase(List<String> list, String value) {
        for (int i = 0; i < list.size(); i++) if (list.get(i).equalsIgnoreCase(value)) return i;
        return -1;
    }
}
