package com.zeytindefteri.app;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Alım defterinin "Defter görünümü"nün saf (Android'e bağımsız) modeli.
 *
 * Kâğıt defterin düzenini verir: bir sayfa = 20 bölüm (4 sütun × 5 sıra), her bölüm bir alım.
 * Alımlar ESKİDEN YENİYE, soldan sağa ve yukarıdan aşağıya dizilir; 20'den fazlası ek sayfaya taşar.
 * Her bölümde D/1/2/3/4/Y numara satırları (kilo ve tutar) ve T (toplam) satırı bulunur.
 *
 * Çizim {@code TezgahNotebookView}'da; bu sınıf yalnız yerleşimi, değerleri ve arama eşleşmesini hesaplar,
 * böylece host testleriyle sınanabilir. Purchase→Entry çevirimi (decode) çağıran tarafta yapılır.
 */
public final class TezgahNotebookPage {
    public static final int COLS = 4;
    public static final int ROWS = 5;
    public static final int PER_PAGE = COLS * ROWS;
    /** Numara satırları: D (Double), 1, 2, 3, 4, Y (Yağlık). T ayrıca eklenir. */
    public static final int GRADES = 6;
    public static final String[] ROW_LABELS = {"D", "1", "2", "3", "4", "Y"};

    private static final Locale TR = new Locale("tr", "TR");

    /** Tek bir alımın defterdeki karşılığı (saf veri). */
    public static final class Entry {
        public final long id;
        public final String seller;
        public final String dateYmd;
        public final int kasa;
        public final double netKg;
        public final double gross;
        public final double[] gradeNet;
        public final double[] gradeAmount;
        public final boolean breakdown;
        public final boolean locked;

        public Entry(long id, String seller, String dateYmd, int kasa, double netKg, double gross,
                     double[] gradeNet, double[] gradeAmount, boolean breakdown, boolean locked) {
            this.id = id;
            this.seller = seller == null ? "" : seller;
            this.dateYmd = dateYmd == null ? "" : dateYmd;
            this.kasa = kasa;
            this.netKg = netKg;
            this.gross = gross;
            this.gradeNet = normalize(gradeNet);
            this.gradeAmount = normalize(gradeAmount);
            this.breakdown = breakdown;
            this.locked = locked;
        }

        private static double[] normalize(double[] in) {
            double[] out = new double[GRADES];
            if (in != null) {
                for (int g = 0; g < GRADES && g < in.length; g++) {
                    out[g] = in[g];
                }
            }
            return out;
        }
    }

    /** Defterdeki bir bölüm. */
    public static final class Section {
        public final Entry entry;
        public boolean highlighted;

        Section(Entry e) {
            this.entry = e;
        }

        /** Numara satırı dolu mu (numara ayrımı var ve o numaranın kilosu > 0). */
        public boolean filled(int grade) {
            return entry.breakdown && grade >= 0 && grade < GRADES && entry.gradeNet[grade] > 0;
        }

        public double rowKg(int grade) {
            return entry.gradeNet[grade];
        }

        public double rowAmount(int grade) {
            return entry.gradeAmount[grade];
        }

        public double totalKg() {
            return entry.netKg;
        }

        public double totalAmount() {
            return entry.gross;
        }
    }

    public final int index;
    public int total;
    public final String dateText;
    public final int gelenKasa;
    /** Boş bırakılan başlık alanları null döner (koddaki karşılığı net değil; rapora bakın). */
    public final Integer gidenKasa;
    public final Double gelenPara;
    public final List<Section> sections;

    private TezgahNotebookPage(int index, String dateText, int gelenKasa, List<Section> sections) {
        this.index = index;
        this.dateText = dateText;
        this.gelenKasa = gelenKasa;
        this.gidenKasa = null;
        this.gelenPara = null;
        this.sections = sections;
    }

    /**
     * Alımları (yeniden eskiye gelir) defter sayfalarına böler.
     * Eskiden yeniye çevirir, 20'şer bölümlük sayfalar üretir; en az bir sayfa (boş olsa da) döner.
     *
     * @param newestFirst açık dönem / Z alımları (TezgahPurchases.open / forZ sırası)
     * @param fallbackYmd hiç tarih yoksa başlıkta kullanılacak gün (yyyy-MM-dd)
     */
    public static List<TezgahNotebookPage> build(List<Entry> newestFirst, String fallbackYmd) {
        List<Entry> oldToNew = new ArrayList<>();
        if (newestFirst != null) {
            oldToNew.addAll(newestFirst);
        }
        java.util.Collections.reverse(oldToNew);
        List<TezgahNotebookPage> pages = new ArrayList<>();
        int count = oldToNew.size();
        int pageCount = Math.max(1, (count + PER_PAGE - 1) / PER_PAGE);
        for (int p = 0; p < pageCount; p++) {
            List<Section> sections = new ArrayList<>();
            int kasa = 0;
            String latest = "";
            int from = p * PER_PAGE;
            int to = Math.min(count, from + PER_PAGE);
            for (int i = from; i < to; i++) {
                Entry e = oldToNew.get(i);
                sections.add(new Section(e));
                kasa += e.kasa;
                if (e.dateYmd.compareTo(latest) > 0) {
                    latest = e.dateYmd;
                }
            }
            String ymd = latest.isEmpty() ? (fallbackYmd == null ? "" : fallbackYmd) : latest;
            pages.add(new TezgahNotebookPage(p, dateText(ymd), kasa, sections));
        }
        for (TezgahNotebookPage page : pages) {
            page.total = pages.size();
        }
        return pages;
    }

    /**
     * Arama: müstahsil adı sorguya uyan bölümleri işaretler ve [sayfa, bölüm] konumlarını döner.
     * Türkçe büyük/küçük harf duyarsız (TezgahSearch ile aynı: TurkishText.fold). Boş sorgu hiçbir şeyi işaretlemez.
     */
    public static List<int[]> search(List<TezgahNotebookPage> pages, String query) {
        List<int[]> hits = new ArrayList<>();
        String q = fold(query);
        for (TezgahNotebookPage page : pages) {
            for (int s = 0; s < page.sections.size(); s++) {
                Section sec = page.sections.get(s);
                boolean hit = !q.isEmpty() && fold(sec.entry.seller).contains(q);
                sec.highlighted = hit;
                if (hit) {
                    hits.add(new int[]{page.index, s});
                }
            }
        }
        return hits;
    }

    /** TezgahSearch.fold ile aynı normalizasyon (ad eşleştirme için). */
    static String fold(String v) {
        if (v == null) {
            return "";
        }
        return TurkishText.fold(v.trim()).replaceAll("\\s+", " ");
    }

    // ------------------------------------------------------------------ biçim

    /** Hücre kilosu: Türkçe, birimsiz, en çok 1 ondalık ("238" / "2,5"). */
    public static String cellKg(double v) {
        NumberFormat nf = NumberFormat.getNumberInstance(TR);
        nf.setMaximumFractionDigits(1);
        nf.setMinimumFractionDigits(0);
        return nf.format(v);
    }

    /** Hücre tutarı: Türkçe, birimsiz, tam sayı ("31.540"). Kâğıttaki gibi ₺ yok. */
    public static String cellTl(double v) {
        NumberFormat nf = NumberFormat.getNumberInstance(TR);
        nf.setMaximumFractionDigits(0);
        return nf.format(Math.round(v));
    }

    /** "2026-11-25" → "25 / 11 / 2026"; okunamazsa metni olduğu gibi döner. */
    static String dateText(String ymd) {
        if (ymd == null) {
            return "";
        }
        String t = ymd.trim();
        if (t.length() == 10 && t.charAt(4) == '-' && t.charAt(7) == '-') {
            String y = t.substring(0, 4);
            String m = t.substring(5, 7);
            String d = t.substring(8, 10);
            return d + " / " + m + " / " + y;
        }
        return t;
    }
}
